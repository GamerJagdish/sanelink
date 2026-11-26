package com.sanelink.app.data.preferences

import kotlinx.coroutines.flow.first
import com.sanelink.app.core.backup.BackupCodec
import com.sanelink.app.core.backup.BackupSettings
import com.sanelink.app.core.backup.SaneLinkBackup
import com.sanelink.app.core.history.RecentLinks
import com.sanelink.app.core.history.RecentLink
import androidx.datastore.preferences.core.intPreferencesKey
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
        val KEY_RECENT_LINKS = stringPreferencesKey("recent_links_json")
        val KEY_HISTORY_LIMIT = intPreferencesKey("recent_links_limit")
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    // Custom rules and user patches are stored as JSON strings. Unreadable JSON counts as empty.
    private fun decodeCustomRules(raw: String?): List<CustomRule> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<CustomRule>>(raw) }.getOrDefault(emptyList())

    private fun decodeUserPatches(raw: String?): Map<String, UserPatch> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { json.decodeFromString<Map<String, UserPatch>>(raw) }.getOrDefault(emptyMap())

    private suspend fun editCustomRules(change: (MutableList<CustomRule>) -> Unit) {
        context.dataStore.edit { prefs ->
            val rules = decodeCustomRules(prefs[KEY_CUSTOM_RULES]).toMutableList()
            change(rules)
            prefs[KEY_CUSTOM_RULES] = json.encodeToString(rules)
        }
    }

    private fun decodeRecentLinks(raw: String?): List<RecentLink> =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString<List<RecentLink>>(raw) }.getOrDefault(emptyList())

    private suspend fun editRecentLinks(change: (List<RecentLink>, Int) -> List<RecentLink>) {
        context.dataStore.edit { prefs ->
            val limit = prefs[KEY_HISTORY_LIMIT] ?: RecentLinks.DEFAULT_LIMIT
            val updated = change(decodeRecentLinks(prefs[KEY_RECENT_LINKS]), limit)
            if (updated.isEmpty()) prefs.remove(KEY_RECENT_LINKS)
            else prefs[KEY_RECENT_LINKS] = json.encodeToString(updated)
        }
    }

    private suspend fun editUserPatches(change: (MutableMap<String, UserPatch>) -> Unit) {
        context.dataStore.edit { prefs ->
            val patches = decodeUserPatches(prefs[KEY_USER_PATCHES]).toMutableMap()
            change(patches)
            prefs[KEY_USER_PATCHES] = json.encodeToString(patches)
        }
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
        decodeCustomRules(prefs[KEY_CUSTOM_RULES])
    }

    val userPatches: Flow<Map<String, UserPatch>> = context.dataStore.data.map { prefs ->
        decodeUserPatches(prefs[KEY_USER_PATCHES])
    }

    /** Recently cleaned links, newest first. Stored only on this device. */
    val recentLinks: Flow<List<RecentLink>> = context.dataStore.data.map { prefs ->
        decodeRecentLinks(prefs[KEY_RECENT_LINKS])
    }

    /** How many recent links to keep: [RecentLinks.OFF], a number, or [RecentLinks.UNLIMITED]. */
    val historyLimit: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_HISTORY_LIMIT] ?: RecentLinks.DEFAULT_LIMIT
    }

    val stats: Flow<SanitizationStats> = context.dataStore.data.map { prefs ->
        SanitizationStats(
            linksCleaned = prefs[KEY_TOTAL_CLEANED] ?: 0L,
            trackersRemoved = prefs[KEY_TOTAL_TRACKERS] ?: 0L
        )
    }

    /**
     * Whether the welcome guide should open on launch. Until it has been finished or skipped,
     * it only shows on a fresh install: someone updating who has already cleaned links
     * clearly knows the app.
     */
    val onboardingNeeded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val done = prefs[KEY_ONBOARDING_DONE]
        done?.not() ?: ((prefs[KEY_TOTAL_CLEANED] ?: 0L) == 0L)
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = true }
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

    suspend fun saveCustomRule(rule: CustomRule) = editCustomRules { rules ->
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) rules[index] = rule else rules.add(0, rule)
    }

    suspend fun deleteCustomRule(ruleId: String) = editCustomRules { rules ->
        rules.removeAll { it.id == ruleId }
    }

    suspend fun toggleCustomRule(ruleId: String, enabled: Boolean) = editCustomRules { rules ->
        val index = rules.indexOfFirst { it.id == ruleId }
        if (index >= 0) rules[index] = rules[index].copy(isEnabled = enabled)
    }

    suspend fun recordSanitization(trackersRemovedCount: Int) {
        context.dataStore.edit { prefs ->
            val currentCleaned = prefs[KEY_TOTAL_CLEANED] ?: 0L
            val currentTrackers = prefs[KEY_TOTAL_TRACKERS] ?: 0L
            prefs[KEY_TOTAL_CLEANED] = currentCleaned + 1
            prefs[KEY_TOTAL_TRACKERS] = currentTrackers + trackersRemovedCount
        }
    }

    suspend fun saveUserPatch(patch: UserPatch) = editUserPatches { patches ->
        patches[patch.providerId] = patch
    }

    suspend fun deleteUserPatch(providerId: String) = editUserPatches { patches ->
        patches.remove(providerId)
    }

    suspend fun addRecentLink(entry: RecentLink) = editRecentLinks { links, limit ->
        RecentLinks.add(links, entry, limit)
    }

    suspend fun deleteRecentLink(id: String) = editRecentLinks { links, _ ->
        links.filterNot { it.id == id }
    }

    suspend fun clearRecentLinks() = editRecentLinks { _, _ -> emptyList() }

    /** Changes how many links are kept; lowering it (or turning it off) trims the log right away. */
    suspend fun setHistoryLimit(limit: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HISTORY_LIMIT] = limit
            val trimmed = RecentLinks.trim(decodeRecentLinks(prefs[KEY_RECENT_LINKS]), limit)
            if (trimmed.isEmpty()) prefs.remove(KEY_RECENT_LINKS)
            else prefs[KEY_RECENT_LINKS] = json.encodeToString(trimmed)
        }
    }

    /** Snapshot of the user's setup for export (the recent links log is not included). */
    suspend fun exportBackup(): SaneLinkBackup {
        val prefs = context.dataStore.data.first()
        return SaneLinkBackup(
            exportedAt = System.currentTimeMillis(),
            customRules = decodeCustomRules(prefs[KEY_CUSTOM_RULES]),
            userPatches = decodeUserPatches(prefs[KEY_USER_PATCHES]),
            disabledProviders = prefs[KEY_DISABLED_PROVIDERS] ?: emptySet(),
            settings = BackupSettings(
                autoSanitize = prefs[KEY_AUTO_SANITIZE] ?: true,
                hapticFeedback = prefs[KEY_HAPTIC_FEEDBACK] ?: true,
                showToast = prefs[KEY_SHOW_TOAST] ?: true,
                historyLimit = prefs[KEY_HISTORY_LIMIT] ?: RecentLinks.DEFAULT_LIMIT
            )
        )
    }

    /** Applies an imported backup in one write, merged with the current setup or replacing it. */
    suspend fun importBackup(incoming: SaneLinkBackup, replace: Boolean) {
        val result = BackupCodec.combine(exportBackup(), incoming, replace)
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_RULES] = json.encodeToString(result.customRules)
            prefs[KEY_USER_PATCHES] = json.encodeToString(result.userPatches)
            prefs[KEY_DISABLED_PROVIDERS] = result.disabledProviders
            prefs[KEY_AUTO_SANITIZE] = result.settings.autoSanitize
            prefs[KEY_HAPTIC_FEEDBACK] = result.settings.hapticFeedback
            prefs[KEY_SHOW_TOAST] = result.settings.showToast
            prefs[KEY_HISTORY_LIMIT] = result.settings.historyLimit
            val trimmed = RecentLinks.trim(decodeRecentLinks(prefs[KEY_RECENT_LINKS]), result.settings.historyLimit)
            if (trimmed.isEmpty()) prefs.remove(KEY_RECENT_LINKS)
            else prefs[KEY_RECENT_LINKS] = json.encodeToString(trimmed)
        }
    }
}
