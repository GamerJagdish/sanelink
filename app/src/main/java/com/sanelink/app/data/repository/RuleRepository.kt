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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

class RuleRepository(
    private val context: Context,
    private val preferences: AppPreferences,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var rawBaseRules: ClearUrlsData = ClearUrlsData()
    private var bundledPatches: SaneLinkPatchesData = SaneLinkPatchesData()

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

    private var sanitizer: UrlSanitizer = UrlSanitizer()

    init {
        loadBundledFiles()

        scope.launch {
            preferences.disabledProviderIds.collect { disabled ->
                _disabledProviderIds.value = disabled
                updateSanitizer()
            }
        }

        scope.launch {
            preferences.customRules.collect { customs ->
                _customRules.value = customs
                updateSanitizer()
            }
        }

        scope.launch {
            preferences.userPatches.collect { patches ->
                _userPatches.value = patches
                recomputeEffectiveRules()
            }
        }
    }

    private fun loadBundledFiles() {
        // 1. Load Base ClearURLs
        try {
            context.assets.open("rules.json").use { inputStream ->
                val text = InputStreamReader(inputStream).readText()
                rawBaseRules = json.decodeFromString<ClearUrlsData>(text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Load SaneLink Patches
        try {
            context.assets.open("patches.json").use { inputStream ->
                val text = InputStreamReader(inputStream).readText()
                bundledPatches = json.decodeFromString<SaneLinkPatchesData>(text)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        recomputeEffectiveRules()
    }

    private fun recomputeEffectiveRules() {
        val merged = rawBaseRules.providers.toMutableMap()
        val bundledPatched = mutableSetOf<String>()
        val userPatched = mutableSetOf<String>()

        // Layer 1: Apply Bundled SaneLink Patches
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
                val newRules = (existing.rules + patch.addRules)
                    .filterNot { patch.removeRules.contains(it) }.distinct()
                val newRaw = (existing.rawRules + patch.addRawRules)
                    .filterNot { patch.removeRawRules.contains(it) }.distinct()
                val newReferral = (existing.referralMarketing + patch.addReferralMarketing)
                    .filterNot { patch.removeReferralMarketing.contains(it) }.distinct()
                val newExceptions = (existing.exceptions + patch.addExceptions)
                    .filterNot { patch.removeExceptions.contains(it) }.distinct()
                val newRedirections = (existing.redirections + patch.addRedirections)
                    .filterNot { patch.removeRedirections.contains(it) }.distinct()

                merged[id] = existing.copy(
                    urlPattern = patch.urlPattern ?: existing.urlPattern,
                    completeProvider = patch.completeProvider ?: existing.completeProvider,
                    rules = newRules,
                    rawRules = newRaw,
                    referralMarketing = newReferral,
                    exceptions = newExceptions,
                    redirections = newRedirections,
                    forceRedirection = patch.forceRedirection ?: existing.forceRedirection
                )
                bundledPatched.add(id)
            }
        }

        // Layer 2: Apply User Patches
        for ((id, patch) in _userPatches.value) {
            if (!patch.isEnabled) continue
            val current = merged[id] ?: continue
            val newRules = (current.rules + patch.addedRules)
                .filterNot { patch.removedRules.contains(it) }.distinct()
            val newExceptions = (current.exceptions + patch.customExceptions).distinct()
            val newUrlPattern = if (!patch.customUrlPattern.isNullOrBlank()) {
                patch.customUrlPattern
            } else {
                current.urlPattern
            }

            merged[id] = current.copy(
                urlPattern = newUrlPattern,
                rules = newRules,
                exceptions = newExceptions
            )
            userPatched.add(id)
        }

        _bundledPatchedProviderIds.value = bundledPatched
        _userPatchedProviderIds.value = userPatched
        _clearUrlsData.value = ClearUrlsData(providers = merged)
        updateSanitizer()
    }

    private fun updateSanitizer() {
        sanitizer = UrlSanitizer(
            clearUrlsData = _clearUrlsData.value,
            customRules = _customRules.value,
            disabledProviderIds = _disabledProviderIds.value
        )
    }

    fun getSanitizer(): UrlSanitizer = sanitizer

    fun getUserPatch(providerId: String): UserPatch? = _userPatches.value[providerId]

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
