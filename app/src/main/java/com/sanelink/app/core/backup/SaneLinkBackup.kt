package com.sanelink.app.core.backup

import com.sanelink.app.core.model.CustomRule
import com.sanelink.app.core.model.UserPatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything a user can set up in SaneLink, as a portable JSON document: custom rules, patches
 * to built-in rules, switched-off providers and preferences. The recent links log is
 * deliberately not included.
 */
@Serializable
data class SaneLinkBackup(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val exportedAt: Long = 0,
    val customRules: List<CustomRule> = emptyList(),
    val userPatches: Map<String, UserPatch> = emptyMap(),
    val disabledProviders: Set<String> = emptySet(),
    val settings: BackupSettings = BackupSettings()
) {
    companion object {
        const val FORMAT = "sanelink-backup"
        const val VERSION = 1
    }
}

@Serializable
data class BackupSettings(
    val autoSanitize: Boolean = true,
    val hapticFeedback: Boolean = true,
    val showToast: Boolean = true,
    val historyLimit: Int = 20
)

/** Result of reading a backup the user pasted or picked. */
sealed interface BackupParseResult {
    data class Success(val backup: SaneLinkBackup) : BackupParseResult
    data class Error(val message: String) : BackupParseResult
}

object BackupCodec {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: SaneLinkBackup): String = json.encodeToString(SaneLinkBackup.serializer(), backup)

    fun decode(text: String): BackupParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return BackupParseResult.Error("Nothing to import. Paste a SaneLink backup first.")
        val backup = try {
            json.decodeFromString(SaneLinkBackup.serializer(), trimmed)
        } catch (_: Exception) {
            return BackupParseResult.Error("This isn't a SaneLink backup, or it was cut off while copying.")
        }
        if (backup.format != SaneLinkBackup.FORMAT) {
            return BackupParseResult.Error("This isn't a SaneLink backup.")
        }
        if (backup.version > SaneLinkBackup.VERSION) {
            return BackupParseResult.Error("This backup is from a newer SaneLink. Update the app, then try again.")
        }
        return BackupParseResult.Success(backup)
    }

    /**
     * Combines an imported backup with what's on the phone.
     *
     * Replace: the backup becomes the whole setup, including preferences.
     * Merge: rules and patches from the backup are added (same rule id or provider overwrites),
     * switched-off providers are combined, and the phone's own preferences are kept.
     */
    fun combine(current: SaneLinkBackup, incoming: SaneLinkBackup, replace: Boolean): SaneLinkBackup {
        if (replace) return incoming.copy(exportedAt = current.exportedAt)
        val incomingIds = incoming.customRules.map { it.id }.toSet()
        return current.copy(
            customRules = incoming.customRules + current.customRules.filterNot { it.id in incomingIds },
            userPatches = current.userPatches + incoming.userPatches,
            disabledProviders = current.disabledProviders + incoming.disabledProviders
        )
    }

    /** "3 custom rules, 1 patch, 2 switched-off rules" for the import confirmation. */
    fun summary(backup: SaneLinkBackup): String {
        fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
        return listOf(
            count(backup.customRules.size, "custom rule", "custom rules"),
            count(backup.userPatches.size, "patch", "patches"),
            count(backup.disabledProviders.size, "switched-off rule", "switched-off rules")
        ).joinToString(", ")
    }
}
