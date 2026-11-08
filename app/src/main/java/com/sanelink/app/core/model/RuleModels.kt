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
    val forceRedirection: Boolean = false
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
    val isEnabled: Boolean = true
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
