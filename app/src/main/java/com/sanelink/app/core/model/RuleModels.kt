package com.sanelink.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ClearUrlsData(
    val providers: Map<String, ProviderRule> = emptyMap()
)

@Serializable
data class ProviderRule(
    val urlPattern: String = "",
    val completeProvider: Boolean = false,
    val rules: List<String> = emptyList(),
    val referralMarketing: List<String> = emptyList(),
    val rawRules: List<String> = emptyList(),
    val exceptions: List<String> = emptyList(),
    val redirections: List<String> = emptyList(),
    val forceRedirection: Boolean = false,
    /** SaneLink extension: open matching links on an alternative frontend domain. */
    val frontendSwaps: List<DomainSwap> = emptyList()
)

/**
 * Plain-domain frontend swap, e.g. `instagram.com` → `oginstagram.com`. No regex involved;
 * see [com.sanelink.app.core.sanitizer.DomainSwapper].
 */
@Serializable
data class DomainSwap(
    val from: String,
    val to: String
)

/**
 * Patch specification to extend, override, or remove ClearURLs provider rules.
 */
@Serializable
data class ProviderPatch(
    val urlPattern: String? = null,
    val completeProvider: Boolean? = null,
    val addRules: List<String> = emptyList(),
    val removeRules: List<String> = emptyList(),
    val addRawRules: List<String> = emptyList(),
    val removeRawRules: List<String> = emptyList(),
    val addReferralMarketing: List<String> = emptyList(),
    val removeReferralMarketing: List<String> = emptyList(),
    val addExceptions: List<String> = emptyList(),
    val removeExceptions: List<String> = emptyList(),
    val addRedirections: List<String> = emptyList(),
    val removeRedirections: List<String> = emptyList(),
    val forceRedirection: Boolean? = null,
    val removeProvider: Boolean = false
)

/**
 * Bundled official SaneLink patches file format (patches.json).
 */
@Serializable
data class SaneLinkPatchesData(
    val version: Int = 1,
    val description: String = "SaneLink official overrides and extensions on top of ClearURLs",
    val providers: Map<String, ProviderPatch> = emptyMap()
)

/**
 * User-created in-app patch to customize what is stripped or kept for a specific provider.
 */
@Serializable
data class UserPatch(
    val providerId: String,
    val isEnabled: Boolean = true,
    val addedRules: List<String> = emptyList(),
    val removedRules: List<String> = emptyList(),
    val customUrlPattern: String? = null,
    val customExceptions: List<String> = emptyList(),
    /** Open this site's links on another domain after trackers are stripped. */
    val frontendSwaps: List<DomainSwap> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class CustomRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val urlPattern: String,
    val stripParameters: List<String> = emptyList(),
    val rewritePattern: String? = null,
    val rewriteReplacement: String? = null,
    val unwrapParam: String? = null,
    val isEnabled: Boolean = true,
    /**
     * Simple mode: the plain domain this rule applies to ("reddit.com"). When set, [urlPattern]
     * is generated from it and the user never sees regex.
     */
    val matchDomain: String? = null,
    /** Simple mode: open matching links on this domain instead ("redlib.catsarch.com"). */
    val swapToDomain: String? = null,
    /**
     * When true, built-in (ClearURLs + patch) cleaning still runs after this rule. Defaults to
     * false so rules saved before this option existed keep their "custom rule wins" behaviour.
     */
    val keepBuiltInCleaning: Boolean = false
)

data class SanitizerResult(
    val originalText: String,
    val cleanText: String,
    val removedParameters: List<String> = emptyList(),
    val wasModified: Boolean = false,
    val matchedSites: List<String> = emptyList(),
    val unwrappedUrl: String? = null
)

data class SanitizationStats(
    val linksCleaned: Long = 0,
    val trackersRemoved: Long = 0
)
