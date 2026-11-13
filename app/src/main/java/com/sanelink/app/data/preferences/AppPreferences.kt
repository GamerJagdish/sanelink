package com.sanelink.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.model.SanitizationStats
import com.sanelink.app.core.model.UserPatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sanelink_settings")

class AppPreferences(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val KEY_AUTO_SANITIZE = booleanPreferencesKey("auto_sanitize_enabled")
        val KEY_HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback_enabled")
        val KEY_SHOW_TOAST = booleanPreferencesKey("show_toast_enabled")
        val KEY_DISABLED_PROVIDERS = stringSetPreferencesKey("disabled_provider_ids")
        val KEY_CUSTOM_RULES = stringPreferencesKey("custom_rules_json")
        val KEY_USER_PATCHES = stringPreferencesKey("user_patches_json")
        val KEY_TOTAL_CLEANED = longPreferencesKey("total_links_cleaned")
        val KEY_TOTAL_TRACKERS = longPreferencesKey("total_trackers_removed")

        // User will add custom rules themselves
        val DEFAULT_CUSTOM_RULES = emptyList<CustomRule>()
    }

    val autoSanitizeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_SANITIZE] ?: true
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HAPTIC_FEEDBACK] ?: true
    }

    val showToastEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SHOW_TOAST] ?: true
    }

    val disabledProviderIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_DISABLED_PROVIDERS] ?: emptySet()
    }

    val customRules: Flow<List<CustomRule>> = context.dataStore.data.map { prefs ->
        val rawJson = prefs[KEY_CUSTOM_RULES]
        if (rawJson.isNullOrBlank()) {
            DEFAULT_CUSTOM_RULES
        } else {
            try {
                json.decodeFromString<List<CustomRule>>(rawJson)
            } catch (_: Exception) {
                DEFAULT_CUSTOM_RULES
            }
        }
    }

    val userPatches: Flow<Map<String, UserPatch>> = context.dataStore.data.map { prefs ->
        val rawJson = prefs[KEY_USER_PATCHES]
        if (rawJson.isNullOrBlank()) {
            emptyMap()
        } else {
            try {
                json.decodeFromString<Map<String, UserPatch>>(rawJson)
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    val stats: Flow<SanitizationStats> = context.dataStore.data.map { prefs ->
        SanitizationStats(
            linksCleaned = prefs[KEY_TOTAL_CLEANED] ?: 0L,
            trackersRemoved = prefs[KEY_TOTAL_TRACKERS] ?: 0L
        )
    }

    suspend fun setAutoSanitize(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SANITIZE] = enabled }
    }

    suspend fun setHapticFeedback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun setShowToast(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_TOAST] = enabled }
    }

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_DISABLED_PROVIDERS]?.toMutableSet() ?: mutableSetOf()
            if (enabled) {
                current.remove(providerId)
            } else {
                current.add(providerId)
            }
            prefs[KEY_DISABLED_PROVIDERS] = current
        }
    }

    suspend fun disableAllProviders(allProviderIds: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISABLED_PROVIDERS] = allProviderIds
        }
    }

    suspend fun enableAllProviders() {
        context.dataStore.edit { prefs ->
            prefs[KEY_DISABLED_PROVIDERS] = emptySet()
        }
    }

    suspend fun saveCustomRule(rule: CustomRule) {
        context.dataStore.edit { prefs ->
            val rawJson = prefs[KEY_CUSTOM_RULES]
            val currentList = if (rawJson.isNullOrBlank()) {
                mutableListOf()
            } else {
                try {
                    json.decodeFromString<List<CustomRule>>(rawJson).toMutableList()
                } catch (_: Exception) {
                    mutableListOf()
                }
            }

            val index = currentList.indexOfFirst { it.id == rule.id }
            if (index >= 0) {
                currentList[index] = rule
            } else {
                currentList.add(0, rule)
            }
            prefs[KEY_CUSTOM_RULES] = json.encodeToString(currentList)
        }
    }

    suspend fun deleteCustomRule(ruleId: String) {
        context.dataStore.edit { prefs ->
            val rawJson = prefs[KEY_CUSTOM_RULES]
            val currentList = if (rawJson.isNullOrBlank()) {
                mutableListOf()
            } else {
                try {
                    json.decodeFromString<List<CustomRule>>(rawJson).toMutableList()
                } catch (_: Exception) {
                    mutableListOf()
                }
            }
            currentList.removeAll { it.id == ruleId }
            prefs[KEY_CUSTOM_RULES] = json.encodeToString(currentList)
        }
    }

    suspend fun toggleCustomRule(ruleId: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val rawJson = prefs[KEY_CUSTOM_RULES]
            val currentList = if (rawJson.isNullOrBlank()) {
                mutableListOf()
            } else {
                try {
                    json.decodeFromString<List<CustomRule>>(rawJson).toMutableList()
                } catch (_: Exception) {
                    mutableListOf()
                }
            }
            val index = currentList.indexOfFirst { it.id == ruleId }
            if (index >= 0) {
                currentList[index] = currentList[index].copy(isEnabled = enabled)
                prefs[KEY_CUSTOM_RULES] = json.encodeToString(currentList)
            }
        }
    }

    suspend fun recordSanitization(trackersRemovedCount: Int) {
        context.dataStore.edit { prefs ->
            val currentCleaned = prefs[KEY_TOTAL_CLEANED] ?: 0L
            val currentTrackers = prefs[KEY_TOTAL_TRACKERS] ?: 0L
            prefs[KEY_TOTAL_CLEANED] = currentCleaned + 1
            prefs[KEY_TOTAL_TRACKERS] = currentTrackers + trackersRemovedCount
        }
    }

    suspend fun saveUserPatch(patch: UserPatch) {
        context.dataStore.edit { prefs ->
            val rawJson = prefs[KEY_USER_PATCHES]
            val currentMap = if (rawJson.isNullOrBlank()) {
                mutableMapOf()
            } else {
                try {
                    json.decodeFromString<Map<String, UserPatch>>(rawJson).toMutableMap()
                } catch (_: Exception) {
                    mutableMapOf()
                }
            }
            currentMap[patch.providerId] = patch
            prefs[KEY_USER_PATCHES] = json.encodeToString(currentMap)
        }
    }

    suspend fun deleteUserPatch(providerId: String) {
        context.dataStore.edit { prefs ->
            val rawJson = prefs[KEY_USER_PATCHES]
            val currentMap = if (rawJson.isNullOrBlank()) {
                mutableMapOf()
            } else {
                try {
                    json.decodeFromString<Map<String, UserPatch>>(rawJson).toMutableMap()
                } catch (_: Exception) {
                    mutableMapOf()
                }
            }
            currentMap.remove(providerId)
            prefs[KEY_USER_PATCHES] = json.encodeToString(currentMap)
        }
    }
}
