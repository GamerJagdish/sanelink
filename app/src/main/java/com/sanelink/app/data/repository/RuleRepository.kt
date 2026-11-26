package com.sanelink.app.data.repository

import android.content.Context
import com.sanelink.app.core.model.ClearUrlsData
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.model.ProviderRule
import com.sanelink.app.core.model.SaneLinkPatchesData
import com.sanelink.app.core.model.UserPatch
import com.sanelink.app.core.sanitizer.UrlSanitizer
import com.sanelink.app.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/**
 * Single source of truth for the effective rule set:
 * ClearURLs base → bundled SaneLink patches → user patches, plus custom rules and disabled
 * providers.
 *
 * Bundled files are parsed off the main thread, and every change of the user's saved rules
 * produces one new immutable [UrlSanitizer] built from a consistent snapshot. Entry points
 * that may run right after a cold start (share sheet, text selection, tile, clipboard
 * reader) call [awaitSanitizer] so they never clean with the user's rules missing.
 */
class RuleRepository(
    private val context: Context,
    private val preferences: AppPreferences,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _clearUrlsData = MutableStateFlow(ClearUrlsData())
    val clearUrlsData: StateFlow<ClearUrlsData> = _clearUrlsData.asStateFlow()

    private val _customRules = MutableStateFlow<List<CustomRule>>(emptyList())
    val customRules: StateFlow<List<CustomRule>> = _customRules.asStateFlow()

    private val _userPatches = MutableStateFlow<Map<String, UserPatch>>(emptyMap())
    val userPatches: StateFlow<Map<String, UserPatch>> = _userPatches.asStateFlow()

    private val _bundledPatchedProviderIds = MutableStateFlow<Set<String>>(emptySet())
    val bundledPatchedProviderIds: StateFlow<Set<String>> = _bundledPatchedProviderIds.asStateFlow()

    private val _userPatchedProviderIds = MutableStateFlow<Set<String>>(emptySet())
    val userPatchedProviderIds: StateFlow<Set<String>> = _userPatchedProviderIds.asStateFlow()

    private val _disabledProviderIds = MutableStateFlow<Set<String>>(emptySet())
    val disabledProviderIds: StateFlow<Set<String>> = _disabledProviderIds.asStateFlow()

    private val _sanitizer = MutableStateFlow(UrlSanitizer())

    /** The current sanitizer; collect it in UI so previews follow rule changes. */
    val sanitizer: StateFlow<UrlSanitizer> = _sanitizer.asStateFlow()

    private val ready = MutableStateFlow(false)

    /** Base + bundled patches, without the user's own patches (what a patch is edited against). */
    @Volatile
    private var builtInRules: Map<String, ProviderRule> = emptyMap()

    init {
        scope.launch {
            val (base, bundled) = loadBundledFiles()
            combine(
                preferences.customRules,
                preferences.userPatches,
                preferences.disabledProviderIds
            ) { customs, patches, disabled -> Triple(customs, patches, disabled) }
                .collect { (customs, patches, disabled) ->
                    val merged = mergeRules(base, bundled, patches)
                    // Publish UI state first, then the sanitizer built from the same snapshot.
                    _customRules.value = customs
                    _userPatches.value = patches
                    _disabledProviderIds.value = disabled
                    _bundledPatchedProviderIds.value = merged.bundledPatched
                    _userPatchedProviderIds.value = merged.userPatched
                    builtInRules = merged.builtIn
                    _clearUrlsData.value = merged.data
                    _sanitizer.value = UrlSanitizer(merged.data, customs, disabled)
                    ready.value = true
                }
        }
    }

    /** Current sanitizer without waiting; may still lack the user's rules right after launch. */
    fun getSanitizer(): UrlSanitizer = _sanitizer.value

    /**
     * Sanitizer that includes the user's saved rules. Waits for the first load (normally a
     * few milliseconds) and falls back to the current one after [timeoutMillis].
     */
    suspend fun awaitSanitizer(timeoutMillis: Long = 3_000): UrlSanitizer {
        withTimeoutOrNull(timeoutMillis) { ready.first { it } }
        return _sanitizer.value
    }

    private fun loadBundledFiles(): Pair<ClearUrlsData, SaneLinkPatchesData> {
        val base = try {
            context.assets.open("rules.json").bufferedReader().use { json.decodeFromString<ClearUrlsData>(it.readText()) }
        } catch (e: Exception) {
            e.printStackTrace()
            ClearUrlsData()
        }
        val bundled = try {
            context.assets.open("patches.json").bufferedReader().use { json.decodeFromString<SaneLinkPatchesData>(it.readText()) }
        } catch (e: Exception) {
            e.printStackTrace()
            SaneLinkPatchesData()
        }
        return base to bundled
    }

    private class MergedRules(
        val data: ClearUrlsData,
        val builtIn: Map<String, ProviderRule>,
        val bundledPatched: Set<String>,
        val userPatched: Set<String>
    )

    private fun mergeRules(
        base: ClearUrlsData,
        bundledPatches: SaneLinkPatchesData,
        userPatches: Map<String, UserPatch>
    ): MergedRules {
        val merged = base.providers.toMutableMap()
        val bundledPatched = mutableSetOf<String>()
        val userPatched = mutableSetOf<String>()

        // Layer 1: bundled SaneLink patches
        for ((id, patch) in bundledPatches.providers) {
            if (patch.removeProvider) {
                merged.remove(id)
                bundledPatched.add(id)
                continue
            }
            val existing = merged[id]
            if (existing == null) {
                if (!patch.urlPattern.isNullOrBlank()) {
                    merged[id] = ProviderRule(
                        urlPattern = patch.urlPattern,
                        completeProvider = patch.completeProvider ?: false,
                        rules = patch.addRules,
                        rawRules = patch.addRawRules,
                        referralMarketing = patch.addReferralMarketing,
                        exceptions = patch.addExceptions,
                        redirections = patch.addRedirections,
                        forceRedirection = patch.forceRedirection ?: false
                    )
                    bundledPatched.add(id)
                }
            } else {
                merged[id] = existing.copy(
                    urlPattern = patch.urlPattern ?: existing.urlPattern,
                    completeProvider = patch.completeProvider ?: existing.completeProvider,
                    rules = (existing.rules + patch.addRules).filterNot { it in patch.removeRules }.distinct(),
                    rawRules = (existing.rawRules + patch.addRawRules).filterNot { it in patch.removeRawRules }.distinct(),
                    referralMarketing = (existing.referralMarketing + patch.addReferralMarketing)
                        .filterNot { it in patch.removeReferralMarketing }.distinct(),
                    exceptions = (existing.exceptions + patch.addExceptions).filterNot { it in patch.removeExceptions }.distinct(),
                    redirections = (existing.redirections + patch.addRedirections)
                        .filterNot { it in patch.removeRedirections }.distinct(),
                    forceRedirection = patch.forceRedirection ?: existing.forceRedirection
                )
                bundledPatched.add(id)
            }
        }

        val builtIn = merged.toMap()

        // Layer 2: user patches
        for ((id, patch) in userPatches) {
            if (!patch.isEnabled) continue
            val current = merged[id] ?: continue
            merged[id] = current.copy(
                urlPattern = patch.customUrlPattern?.takeIf { it.isNotBlank() } ?: current.urlPattern,
                rules = (current.rules + patch.addedRules).filterNot { it in patch.removedRules }.distinct(),
                // "Kept" parameters can come from either list; the patch editor shows both.
                referralMarketing = current.referralMarketing.filterNot { it in patch.removedRules },
                exceptions = (current.exceptions + patch.customExceptions).distinct(),
                frontendSwaps = (patch.frontendSwaps + current.frontendSwaps).distinct()
            )
            userPatched.add(id)
        }

        return MergedRules(ClearUrlsData(providers = merged), builtIn, bundledPatched, userPatched)
    }

    fun getUserPatch(providerId: String): UserPatch? = _userPatches.value[providerId]

    /** The provider's rule with bundled patches applied but without the user's patch. */
    fun builtInRule(providerId: String): ProviderRule? = builtInRules[providerId]

    suspend fun saveUserPatch(patch: UserPatch) {
        preferences.saveUserPatch(patch)
    }

    suspend fun resetUserPatch(providerId: String) {
        preferences.deleteUserPatch(providerId)
    }

    suspend fun toggleProvider(providerId: String, enable: Boolean) {
        preferences.setProviderEnabled(providerId, enable)
    }

    suspend fun disableAllProviders() {
        preferences.disableAllProviders(_clearUrlsData.value.providers.keys)
    }

    suspend fun enableAllProviders() {
        preferences.enableAllProviders()
    }

    suspend fun saveCustomRule(rule: CustomRule) {
        preferences.saveCustomRule(rule)
    }

    suspend fun deleteCustomRule(ruleId: String) {
        preferences.deleteCustomRule(ruleId)
    }

    suspend fun toggleCustomRule(ruleId: String, enable: Boolean) {
        preferences.toggleCustomRule(ruleId, enable)
    }
}
