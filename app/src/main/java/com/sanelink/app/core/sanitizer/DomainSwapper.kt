package com.sanelink.app.core.sanitizer

import com.sanelink.app.core.model.DomainSwap

/**
 * Regex-free "open this site on a different domain" support (alternative frontends such as
 * instagram.com → oginstagram.com, reddit.com → redlib.catsarch.com).
 *
 * Users type plain domains; everything here is literal string handling, so no regex knowledge
 * is needed and user input can never produce an invalid or catastrophic pattern.
 */
object DomainSwapper {

    /** Subdomains that are treated as the site itself and replaced by the new domain. */
    private val aliasPrefixes = listOf("www.", "m.", "mobile.")

    private val domainChars = Regex("^[a-z0-9.-]+$")

    /**
     * Turns loose user input ("https://www.Instagram.com/", "instagram.com/reel") into a bare
     * domain ("instagram.com"), or null if it isn't a usable domain.
     */
    fun normalizeDomain(input: String?): String? {
        if (input == null) return null
        var d = input.trim().lowercase()
        d = d.substringAfter("://", d)
        d = d.substringBefore('/').substringBefore('?').substringBefore('#')
        d = d.substringAfterLast('@') // drop credentials
        d = d.substringBefore(':') // drop port
        d = d.trim('.')
        for (prefix in aliasPrefixes) {
            if (d.startsWith(prefix) && d.length > prefix.length) {
                d = d.removePrefix(prefix)
                break
            }
        }
        if (d.isEmpty() || !d.contains('.') || !domainChars.matches(d)) return null
        if (d.split('.').any { it.isEmpty() || it.startsWith('-') || it.endsWith('-') }) return null
        return d
    }

    /** True when [host] is [domain] or one of its subdomains. */
    fun hostMatches(host: String, domain: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return h == domain || h.endsWith(".$domain")
    }

    /**
     * Regex (for [com.sanelink.app.core.model.CustomRule.urlPattern]) matching http(s) URLs on
     * [domain] or any of its subdomains. Built from an escaped literal, never from user regex.
     */
    fun urlPatternFor(domain: String): String =
        "^https?://([^/?#@]*@)?([a-z0-9-]+\\.)*" + Regex.escape(domain) + "(?=[:/?#]|$)"

    /**
     * Applies [swap] to [url]: when the URL's host is the source domain (or its www./m./mobile.
     * alias), the host is replaced by the target domain. Path, query, fragment, port and
     * credentials are kept byte-for-byte. Other subdomains (help.instagram.com) are left alone,
     * because alternative frontends rarely mirror them.
     *
     * Returns the new URL, or null if the swap doesn't apply.
     */
    fun apply(url: String, swap: DomainSwap): String? {
        val from = normalizeDomain(swap.from) ?: return null
        val to = normalizeDomain(swap.to) ?: return null
        if (from == to) return null

        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null

        val authorityStart = schemeEnd + 3
        var authorityEnd = url.length
        for (i in authorityStart until url.length) {
            val c = url[i]
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i
                break
            }
        }
        val authority = url.substring(authorityStart, authorityEnd)
        val hostStart = authority.lastIndexOf('@') + 1
        val portIdx = authority.indexOf(':', hostStart)
        val hostEnd = if (portIdx >= 0) portIdx else authority.length
        val host = authority.substring(hostStart, hostEnd).lowercase().trimEnd('.')

        val isSite = host == from || aliasPrefixes.any { host == it + from }
        if (!isSite) return null

        val newAuthority = authority.substring(0, hostStart) + to + authority.substring(hostEnd)
        return url.substring(0, authorityStart) + newAuthority + url.substring(authorityEnd)
    }

    /** Applies the first swap in [swaps] that matches [url]; null if none does. */
    fun applyFirst(url: String, swaps: List<DomainSwap>): String? {
        for (swap in swaps) {
            apply(url, swap)?.let { return it }
        }
        return null
    }

    /**
     * Best-effort guess of a provider's main domain from its ClearURLs regex, used to prefill
     * the "from" field when patching a built-in rule. E.g.
     * `^https?:\/\/(?:[a-z0-9-]+\.)*?instagram(?:\.[a-z]{2,}){1,}` → `instagram.com`.
     */
    fun guessDomain(urlPattern: String, providerId: String): String? {
        val unescaped = urlPattern.replace("\\/", "/").replace("\\.", ".")
        // A literal domain such as "youtube.com" or "youtu.be"
        Regex("([a-z0-9-]{2,}(?:\\.[a-z0-9-]+)*\\.[a-z]{2,})(?![a-z])", RegexOption.IGNORE_CASE)
            .findAll(unescaped)
            .map { it.groupValues[1].lowercase() }
            .firstOrNull { candidate -> normalizeDomain(candidate) != null && !candidate.startsWith("a-z") }
            ?.let { return normalizeDomain(it) }
        // A name followed by a generic TLD group: "instagram(?:.[a-z]{2,})"
        Regex("([a-z0-9-]{2,})\\(\\?:\\.\\[a-z]", RegexOption.IGNORE_CASE)
            .find(unescaped)
            ?.let { return normalizeDomain(it.groupValues[1].lowercase() + ".com") }
        val id = providerId.lowercase().filter { it.isLetterOrDigit() || it == '-' }
        return if (id.isNotEmpty() && id != "globalrules") normalizeDomain("$id.com") else null
    }
}
