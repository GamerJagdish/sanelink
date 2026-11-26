package com.sanelink.app.core.sanitizer

import com.sanelink.app.core.model.ClearUrlsData
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.model.DomainSwap
import com.sanelink.app.core.model.SanitizerResult
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Cleans URLs using, in priority order: user custom rules, then the built-in ClearURLs
 * providers (with bundled and user patches already merged in by the repository).
 *
 * Follows ClearURLs semantics: a provider (its redirections, raw rules and parameter rules)
 * only applies when its `urlPattern` matches and none of its `exceptions` do. The catch-all
 * `globalRules` provider obeys its own exceptions too.
 *
 * All regexes are compiled once per instance; build a new instance when rules change.
 */
class UrlSanitizer(
    clearUrlsData: ClearUrlsData = ClearUrlsData(),
    customRules: List<CustomRule> = emptyList(),
    disabledProviderIds: Set<String> = emptySet()
) {

    private class CompiledProvider(
        val name: String,
        val pattern: Regex,
        val exceptions: List<Regex>,
        val redirections: List<Regex>,
        val rawRules: List<Regex>,
        val paramRules: List<Regex>,
        val frontendSwaps: List<DomainSwap>
    ) {
        fun appliesTo(url: String): Boolean =
            pattern.containsMatchIn(url) && exceptions.none { it.containsMatchIn(url) }
    }

    private class CompiledCustom(
        val rule: CustomRule,
        val pattern: Regex,
        val rewrite: Regex?
    )

    private val providers: List<CompiledProvider>
    private val globalProvider: CompiledProvider?
    private val customs: List<CompiledCustom>

    init {
        val compiled = clearUrlsData.providers
            .filterKeys { it !in disabledProviderIds }
            .mapNotNull { (name, rule) ->
                val pattern = compileOrNull(rule.urlPattern) ?: return@mapNotNull null
                CompiledProvider(
                    name = name,
                    pattern = pattern,
                    exceptions = rule.exceptions.mapNotNull(::compileOrNull),
                    redirections = rule.redirections.mapNotNull(::compileOrNull),
                    rawRules = rule.rawRules.mapNotNull(::compileOrNull),
                    paramRules = (rule.rules + rule.referralMarketing).mapNotNull { compileOrNull("^(?:$it)$") },
                    frontendSwaps = rule.frontendSwaps
                )
            }
        globalProvider = compiled.firstOrNull { it.name == GLOBAL_RULES }
        providers = compiled.filter { it.name != GLOBAL_RULES }

        customs = customRules.filter { it.isEnabled }.mapNotNull { custom ->
            val pattern = compileOrNull(custom.urlPattern) ?: return@mapNotNull null
            val rewrite = if (!custom.rewritePattern.isNullOrBlank() && !custom.rewriteReplacement.isNullOrBlank()) {
                compileOrNull(custom.rewritePattern)
            } else null
            CompiledCustom(custom, pattern, rewrite)
        }
    }

    /**
     * Sanitizes input text, detecting and cleaning all URLs while preserving surrounding text.
     */
    fun sanitize(input: String): SanitizerResult {
        if (input.isBlank()) {
            return SanitizerResult(originalText = input, cleanText = input)
        }

        val matcher = URL_IN_TEXT.matcher(input)
        val out = StringBuilder(input.length)
        val allRemovedParams = mutableListOf<String>()
        val matchedSites = mutableListOf<String>()
        var modified = false
        var unwrappedUrl: String? = null
        var copiedUpTo = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = start + trimmedUrlLength(matcher.group())
            val originalUrl = input.substring(start, end)
            val result = sanitizeSingleUrl(originalUrl)

            out.append(input, copiedUpTo, start)
            if (result.wasModified) {
                modified = true
                allRemovedParams.addAll(result.removedParameters)
                matchedSites.addAll(result.matchedSites)
                if (result.unwrappedUrl != null) unwrappedUrl = result.unwrappedUrl
                out.append(result.cleanText)
            } else {
                out.append(originalUrl)
            }
            copiedUpTo = end
        }
        out.append(input, copiedUpTo, input.length)

        return SanitizerResult(
            originalText = input,
            cleanText = out.toString(),
            removedParameters = allRemovedParams.distinct(),
            wasModified = modified,
            matchedSites = matchedSites.distinct(),
            unwrappedUrl = unwrappedUrl
        )
    }

    /**
     * Sanitizes a single standalone URL string.
     */
    fun sanitizeSingleUrl(rawUrl: String): SanitizerResult = sanitizeSingleUrl(rawUrl, depth = 0)

    private fun sanitizeSingleUrl(rawUrl: String, depth: Int): SanitizerResult {
        val original = rawUrl.trim()
        var currentUrl = original
        val removedParams = mutableListOf<String>()
        val matchedSites = mutableListOf<String>()

        // 1. Custom rules (priority 1). Unless a rule keeps built-in cleaning, it is the only
        //    thing applied to matching URLs.
        var stopAfterCustom = false
        // Domain swaps run last, so built-in rules still match the original domain.
        val customSwaps = mutableListOf<DomainSwap>()
        for (custom in customs) {
            if (!custom.pattern.containsMatchIn(currentUrl)) continue
            val rule = custom.rule
            if (!rule.keepBuiltInCleaning) stopAfterCustom = true
            matchedSites.add(rule.name)
            if (!rule.matchDomain.isNullOrBlank() && !rule.swapToDomain.isNullOrBlank()) {
                customSwaps.add(DomainSwap(rule.matchDomain, rule.swapToDomain))
            }
            if (rule.stripParameters.isNotEmpty()) {
                val names = rule.stripParameters.map { it.trim().lowercase() }.toSet()
                val (stripped, removed) = QueryStripper.strip(currentUrl) { it.lowercase() in names }
                currentUrl = stripped
                removedParams.addAll(removed)
            }
            val rewrite = custom.rewrite
            if (rewrite != null && rewrite.containsMatchIn(currentUrl)) {
                try {
                    currentUrl = currentUrl.replace(rewrite, rule.rewriteReplacement!!)
                } catch (_: Exception) {
                    // Invalid group reference in the replacement: leave the URL as is
                }
            }
        }

        if (!stopAfterCustom) {
            // 2. Built-in providers that apply to this URL (pattern matches, no exception)
            val matching = providers.filter { it.appliesTo(currentUrl) }

            // 3. Redirect wrappers: unwrap and clean the target instead
            if (depth < MAX_REDIRECT_DEPTH) {
                for (provider in matching) {
                    val target = provider.redirections.firstNotNullOfOrNull { extractRedirectTarget(it, currentUrl) }
                        ?: continue
                    val nested = sanitizeSingleUrl(target, depth + 1)
                    val finalUrl = DomainSwapper.applyFirst(nested.cleanText, customSwaps) ?: nested.cleanText
                    return SanitizerResult(
                        originalText = rawUrl,
                        cleanText = finalUrl,
                        removedParameters = (removedParams + "redirect:${provider.name}" + nested.removedParameters).distinct(),
                        wasModified = true,
                        matchedSites = (matchedSites + provider.name + nested.matchedSites).distinct(),
                        unwrappedUrl = finalUrl
                    )
                }
            }

            // 4. Raw rules (path cleanup, e.g. Amazon /ref=...)
            for (provider in matching) {
                for (raw in provider.rawRules) {
                    val cleaned = currentUrl.replace(raw, "")
                    if (cleaned != currentUrl) {
                        currentUrl = cleaned
                        matchedSites.add(provider.name)
                    }
                }
            }

            // 5. Query parameters: global rules (unless excepted) + matching providers
            val paramRules = buildList {
                globalProvider?.takeIf { it.appliesTo(currentUrl) }?.let { addAll(it.paramRules) }
                for (provider in matching) {
                    if (provider.paramRules.isNotEmpty()) matchedSites.add(provider.name)
                    addAll(provider.paramRules)
                }
            }
            if (paramRules.isNotEmpty()) {
                val (stripped, removed) = QueryStripper.strip(currentUrl) { key -> paramRules.any { it.matches(key) } }
                currentUrl = stripped
                removedParams.addAll(removed)
            }

            // 6. Frontend swaps: custom rules first, then built-in patches
            val providerSwaps = matching.flatMap { it.frontendSwaps }
            val swapped = DomainSwapper.applyFirst(currentUrl, customSwaps)
                ?: DomainSwapper.applyFirst(currentUrl, providerSwaps)
            if (swapped != null) {
                currentUrl = swapped
                matchedSites.addAll(matching.filter { it.frontendSwaps.isNotEmpty() }.map { it.name })
            }
        } else {
            DomainSwapper.applyFirst(currentUrl, customSwaps)?.let { currentUrl = it }
        }

        return SanitizerResult(
            originalText = rawUrl,
            cleanText = currentUrl,
            removedParameters = removedParams.distinct(),
            wasModified = currentUrl != original,
            matchedSites = matchedSites.distinct(),
            unwrappedUrl = null
        )
    }

    /**
     * Returns the absolute http(s) URL captured by [redirect]'s first group, or null.
     * Handles percent-encoded targets, upper-case schemes and scheme-less captures such as
     * Google AMP's `/amp/s/example.com/article`.
     */
    private fun extractRedirectTarget(redirect: Regex, url: String): String? {
        val match = redirect.find(url) ?: return null
        val captured = match.groups[1]?.value?.takeIf { it.isNotBlank() } ?: return null
        var target = safeUrlDecode(captured).trim()
        // Decoding turns '+' into spaces; a space can never be part of a working URL.
        target = target.replace(" ", "%20")
        when {
            HTTP_SCHEME.containsMatchIn(target) -> Unit
            target.startsWith("//") -> target = "https:$target"
            BARE_HOST.containsMatchIn(target) -> target = "https://$target"
            else -> return null
        }
        return target
    }

    private fun safeUrlDecode(value: String): String = try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    } catch (_: Exception) {
        value
    }

    companion object {
        private const val GLOBAL_RULES = "globalRules"
        private const val MAX_REDIRECT_DEPTH = 5

        // Detects http(s) URLs inside arbitrary text (trailing punctuation trimmed separately)
        private val URL_IN_TEXT: Pattern = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
        )
        private val HTTP_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)
        private val BARE_HOST = Regex("^[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?:[:/?#]|$)", RegexOption.IGNORE_CASE)

        private fun compileOrNull(pattern: String?): Regex? {
            if (pattern.isNullOrBlank()) return null
            return try {
                Regex(pattern, RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Length of [match] without sentence punctuation that follows a URL in prose:
         * trailing `.,;:!?'"` and closing brackets that have no opening partner in the URL.
         */
        internal fun trimmedUrlLength(match: String): Int {
            var end = match.length
            while (end > 0) {
                val c = match[end - 1]
                val candidate = match.substring(0, end)
                val drop = when (c) {
                    '.', ',', ';', ':', '!', '?', '\'', '"' -> true
                    ')' -> candidate.count { it == ')' } > candidate.count { it == '(' }
                    ']' -> candidate.count { it == ']' } > candidate.count { it == '[' }
                    else -> false
                }
                if (!drop) break
                end--
            }
            return end
        }
    }
}

/**
 * Removes query parameters by plain string handling, so URLs that `java.net.URI` rejects
 * (braces, pipes, stray `%`) are still cleaned and everything else, including credentials,
 * ports and the fragment, is kept byte-for-byte.
 */
internal object QueryStripper {

    /** Returns the URL without parameters whose (raw) name matches [shouldRemove], and the removed names. */
    fun strip(url: String, shouldRemove: (String) -> Boolean): Pair<String, List<String>> {
        val hashIdx = url.indexOf('#')
        val base = if (hashIdx >= 0) url.substring(0, hashIdx) else url
        val fragment = if (hashIdx >= 0) url.substring(hashIdx) else ""
        val queryIdx = base.indexOf('?')
        if (queryIdx < 0) return url to emptyList()

        val prefix = base.substring(0, queryIdx)
        val removed = mutableListOf<String>()
        val kept = mutableListOf<String>()
        for (pair in base.substring(queryIdx + 1).split('&')) {
            if (pair.isEmpty()) continue
            val key = pair.substringBefore('=')
            if (key.isNotEmpty() && shouldRemove(key)) removed.add(key) else kept.add(pair)
        }
        if (removed.isEmpty()) return url to emptyList()

        val query = if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
        return prefix + query + fragment to removed
    }
}
