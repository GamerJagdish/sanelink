package com.sanelink.app.data.repository

import android.content.Context
import com.sanelink.app.core.model.ClearUrlsData
import com.sanelink.app.core.model.CustomRule
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

    private val _clearUrlsData = MutableStateFlow(ClearUrlsData())
    val clearUrlsData: StateFlow<ClearUrlsData> = _clearUrlsData.asStateFlow()

    private val _customRules = MutableStateFlow<List<CustomRule>>(emptyList())
    val customRules: StateFlow<List<CustomRule>> = _customRules.asStateFlow()

    private val _disabledProviderIds = MutableStateFlow<Set<String>>(emptySet())
    val disabledProviderIds: StateFlow<Set<String>> = _disabledProviderIds.asStateFlow()

    private var sanitizer: UrlSanitizer = UrlSanitizer()

    init {
        loadBundledRules()

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
    }

    private fun loadBundledRules() {
        try {
            context.assets.open("rules.json").use { inputStream ->
                val text = InputStreamReader(inputStream).readText()
                val loaded = json.decodeFromString<ClearUrlsData>(text)
                _clearUrlsData.value = loaded
                updateSanitizer()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateSanitizer() {
        sanitizer = UrlSanitizer(
            clearUrlsData = _clearUrlsData.value,
            customRules = _customRules.value,
            disabledProviderIds = _disabledProviderIds.value
        )
    }

    fun getSanitizer(): UrlSanitizer = sanitizer

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
