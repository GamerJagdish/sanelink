package com.sanelink.app.core.sanitizer

import com.sanelink.app.core.model.ClearUrlsData
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.model.ProviderRule
import com.sanelink.app.core.model.SanitizerResult
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class UrlSanitizer(
    private var clearUrlsData: ClearUrlsData = ClearUrlsData(),
    private var customRules: List<CustomRule> = emptyList(),
    private val disabledProviderIds: Set<String> = emptySet()
) {

    // Regex to detect HTTP/HTTPS URLs even within mixed text
    private val urlPattern: Pattern = Pattern.compile(
        "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
        Pattern.CASE_INSENSITIVE
    )

    // Precompiled regex caches for fast matching
    private var compiledProviderPatterns: Map<String, Regex> = emptyMap()
    private var compiledCustomPatterns: List<Pair<CustomRule, Regex>> = emptyList()

    init {
        rebuildCompiledPatterns()
    }

    fun updateRules(
        newClearUrlsData: ClearUrlsData,
        newCustomRules: List<CustomRule> = customRules
    ) {
        this.clearUrlsData = newClearUrlsData
        this.customRules = newCustomRules
        rebuildCompiledPatterns()
    }

    fun updateCustomRules(newCustomRules: List<CustomRule>) {
        this.customRules = newCustomRules
        rebuildCompiledPatterns()
    }

    private fun rebuildCompiledPatterns() {
        compiledProviderPatterns = clearUrlsData.providers.mapNotNull { (name, rule) ->
            if (rule.urlPattern.isNotBlank()) {
                try {
                    name to Regex(rule.urlPattern, RegexOption.IGNORE_CASE)
                } catch (_: Exception) {
                    null
                }
            } else null
        }.toMap()

        compiledCustomPatterns = customRules.filter { it.isEnabled }.mapNotNull { custom ->
            try {
                custom to Regex(custom.urlPattern, RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Sanitizes input text, detecting and cleaning all URLs while preserving surrounding text.
     */
    fun sanitize(input: String): SanitizerResult {
        if (input.isBlank()) {
            return SanitizerResult(originalText = input, cleanText = input)
        }

        val matcher = urlPattern.matcher(input)
        val sb = StringBuffer()
        val allRemovedParams = mutableListOf<String>()
        val matchedSites = mutableListOf<String>()
        var modified = false
        var unwrappedUrl: String? = null

        while (matcher.find()) {
            val originalUrl = matcher.group()
            val singleResult = sanitizeSingleUrl(originalUrl)

            if (singleResult.wasModified) {
                modified = true
                allRemovedParams.addAll(singleResult.removedParameters)
                matchedSites.addAll(singleResult.matchedSites)
                if (singleResult.unwrappedUrl != null) {
                    unwrappedUrl = singleResult.unwrappedUrl
                }
                matcher.appendReplacement(sb, MatcherQuote(singleResult.cleanText))
            } else {
                matcher.appendReplacement(sb, MatcherQuote(originalUrl))
            }
        }
        matcher.appendTail(sb)

        val cleanResultText = sb.toString()
        return SanitizerResult(
            originalText = input,
            cleanText = cleanResultText,
            removedParameters = allRemovedParams.distinct(),
            wasModified = modified,
            matchedSites = matchedSites.distinct(),
            unwrappedUrl = unwrappedUrl
        )
    }

    /**
     * Sanitizes a single standalone URL string.
     */
    fun sanitizeSingleUrl(rawUrl: String): SanitizerResult {
        var currentUrl = rawUrl.trim()
        val removedParams = mutableListOf<String>()
        val matchedSites = mutableListOf<String>()
        var unwrappedTarget: String? = null
        var wasModified = false

        // 1. Check for Redirections in ClearURLs providers
        for ((providerName, providerRule) in clearUrlsData.providers) {
            if (disabledProviderIds.contains(providerName)) continue
            for (redirectionRegexStr in providerRule.redirections) {
                try {
                    val redirectRegex = Regex(redirectionRegexStr, RegexOption.IGNORE_CASE)
                    val match = redirectRegex.find(currentUrl)
                    if (match != null && match.groupValues.size > 1) {
                        val extracted = safeUrlDecode(match.groupValues[1])
                        if (extracted.startsWith("http://") || extracted.startsWith("https://")) {
                            unwrappedTarget = extracted
                            val nested = sanitizeSingleUrl(extracted)
                            return SanitizerResult(
                                originalText = rawUrl,
                                cleanText = nested.cleanText,
                                removedParameters = (listOf("redirect:$providerName") + nested.removedParameters).distinct(),
                                wasModified = true,
                                matchedSites = (listOf(providerName) + nested.matchedSites).distinct(),
                                unwrappedUrl = nested.cleanText
                            )
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Apply Custom Rules (Stripping + Frontend Rewriting, e.g. Reddit -> Redlib)
        for ((custom, regex) in compiledCustomPatterns) {
            if (regex.containsMatchIn(currentUrl)) {
                matchedSites.add(custom.name)

                // Query stripping from custom rule
                if (custom.stripParameters.isNotEmpty()) {
                    val strippedUrlResult = stripCustomQueryParams(currentUrl, custom.stripParameters)
                    if (strippedUrlResult.first != currentUrl) {
                        currentUrl = strippedUrlResult.first
                        removedParams.addAll(strippedUrlResult.second)
                        wasModified = true
                    }
                }

                // Frontend / URL rewriting
                if (!custom.rewritePattern.isNullOrBlank() && !custom.rewriteReplacement.isNullOrBlank()) {
                    try {
                        val rewriteRegex = Regex(custom.rewritePattern, RegexOption.IGNORE_CASE)
                        if (rewriteRegex.containsMatchIn(currentUrl)) {
                            val rewritten = currentUrl.replace(rewriteRegex, custom.rewriteReplacement)
                            if (rewritten != currentUrl) {
                                currentUrl = rewritten
                                wasModified = true
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // 3. Apply ClearURLs Matching Providers
        val matchingProviders = mutableListOf<Pair<String, ProviderRule>>()
        for ((name, regex) in compiledProviderPatterns) {
            if (disabledProviderIds.contains(name)) continue
            val rule = clearUrlsData.providers[name] ?: continue

            // Check exceptions: if matched, skip this provider
            var hasException = false
            for (exc in rule.exceptions) {
                try {
                    if (Regex(exc, RegexOption.IGNORE_CASE).containsMatchIn(currentUrl)) {
                        hasException = true
                        break
                    }
                } catch (_: Exception) {}
            }
            if (hasException) continue

            if (regex.containsMatchIn(currentUrl)) {
                matchingProviders.add(name to rule)
            }
        }

        // Apply rawRules from matching providers (e.g. Amazon /ref=...)
        for ((name, rule) in matchingProviders) {
            for (raw in rule.rawRules) {
                try {
                    val rawRegex = Regex(raw, RegexOption.IGNORE_CASE)
                    if (rawRegex.containsMatchIn(currentUrl)) {
                        val cleaned = currentUrl.replace(rawRegex, "")
                        if (cleaned != currentUrl) {
                            currentUrl = cleaned
                            wasModified = true
                            matchedSites.add(name)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Gather all query parameter rules from globalRules + matching providers
        val paramRulesToStrip = mutableListOf<Regex>()
        val globalRule = clearUrlsData.providers["globalRules"]
        if (globalRule != null && !disabledProviderIds.contains("globalRules")) {
            for (r in (globalRule.rules + globalRule.referralMarketing)) {
                try {
                    paramRulesToStrip.add(Regex("^($r)$", RegexOption.IGNORE_CASE))
                } catch (_: Exception) {}
            }
        }

        for ((name, rule) in matchingProviders) {
            if (name == "globalRules") continue
            val allRules = rule.rules + rule.referralMarketing
            if (allRules.isNotEmpty()) {
                matchedSites.add(name)
            }
            for (r in allRules) {
                try {
                    paramRulesToStrip.add(Regex("^($r)$", RegexOption.IGNORE_CASE))
                } catch (_: Exception) {}
            }
        }

        // 4. Strip matching query parameters
        if (paramRulesToStrip.isNotEmpty()) {
            val strippedResult = stripQueryParamsWithRegex(currentUrl, paramRulesToStrip)
            if (strippedResult.first != currentUrl) {
                currentUrl = strippedResult.first
                removedParams.addAll(strippedResult.second)
                wasModified = true
            }
        }

        if (currentUrl != rawUrl.trim()) {
            wasModified = true
        }

        return SanitizerResult(
            originalText = rawUrl,
            cleanText = currentUrl,
            removedParameters = removedParams.distinct(),
            wasModified = wasModified,
            matchedSites = matchedSites.distinct(),
            unwrappedUrl = unwrappedTarget
        )
    }

    private fun stripQueryParamsWithRegex(
        urlStr: String,
        patterns: List<Regex>
    ): Pair<String, List<String>> {
        val uri = try { URI(urlStr) } catch (_: Exception) { return urlStr to emptyList() }
        val rawQuery = uri.rawQuery ?: return urlStr to emptyList()

        val removed = mutableListOf<String>()
        val keptParams = mutableListOf<String>()

        val pairs = rawQuery.split("&")
        for (pair in pairs) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf("=")
            val key = if (idx > 0) pair.substring(0, idx) else pair

            val matchesRule = patterns.any { regex -> regex.matches(key) }
            if (matchesRule) {
                removed.add(key)
            } else {
                keptParams.add(pair)
            }
        }

        if (removed.isEmpty()) return urlStr to emptyList()

        val newQuery = if (keptParams.isEmpty()) "" else "?" + keptParams.joinToString("&")
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: ""
        val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
        val path = uri.rawPath ?: ""
        val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""

        val newUrl = "$scheme://$host$port$path$newQuery$fragment"
        return newUrl to removed
    }

    private fun stripCustomQueryParams(
        urlStr: String,
        paramsToStrip: List<String>
    ): Pair<String, List<String>> {
        val uri = try { URI(urlStr) } catch (_: Exception) { return urlStr to emptyList() }
        val rawQuery = uri.rawQuery ?: return urlStr to emptyList()

        val removed = mutableListOf<String>()
        val keptParams = mutableListOf<String>()
        val lowerToStrip = paramsToStrip.map { it.trim().lowercase() }.toSet()

        val pairs = rawQuery.split("&")
        for (pair in pairs) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf("=")
            val key = if (idx > 0) pair.substring(0, idx) else pair
            if (lowerToStrip.contains(key.lowercase())) {
                removed.add(key)
            } else {
                keptParams.add(pair)
            }
        }

        if (removed.isEmpty()) return urlStr to emptyList()

        val newQuery = if (keptParams.isEmpty()) "" else "?" + keptParams.joinToString("&")
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: ""
        val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
        val path = uri.rawPath ?: ""
        val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""

        val newUrl = "$scheme://$host$port$path$newQuery$fragment"
        return newUrl to removed
    }

    private fun safeUrlDecode(value: String): String {
        return try {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }

    private fun MatcherQuote(s: String): String {
        val sb = StringBuilder()
        for (i in 0 until s.length) {
            val c = s[i]
            if (c == '\\' || c == '$') {
                sb.append('\\')
            }
            sb.append(c)
        }
        return sb.toString()
    }
}
