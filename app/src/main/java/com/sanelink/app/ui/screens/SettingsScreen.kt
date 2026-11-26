package com.sanelink.app.ui.screens

import com.sanelink.app.ui.components.ExpressiveConnectedButtonGroup
import com.sanelink.app.core.history.RecentLinks
import com.sanelink.app.ui.components.ExpressiveTactileButton
import com.sanelink.app.util.performClickHaptic
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.ui.components.ExpressiveLeadingBadge
import com.sanelink.app.ui.components.ExpressiveSegmentedListItem
import com.sanelink.app.ui.components.ExpressiveSwitch
import com.sanelink.app.ui.components.LocalHazeState
import com.sanelink.app.ui.theme.LocalMotionScheme
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onOpenAutoSanitize: () -> Unit,
    onOpenRecentLinks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = SaneLinkApp.instance
    val scope = rememberCoroutineScope()
    val hazeState = LocalHazeState.current
    val motionScheme = LocalMotionScheme.current

    var showAboutDialog by remember { mutableStateOf(false) }

    val hapticEnabled by app.preferences.hapticFeedbackEnabled.collectAsState(initial = true)
    val toastEnabled by app.preferences.showToastEnabled.collectAsState(initial = true)

    val versionName = remember(context) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Group 1: System Background Protection (Material 3 Expressive Segmented Container)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "System",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp)
            )

            AutoSanitizeSettingsEntry(onClick = onOpenAutoSanitize)
        }

        // Group 2: Preferences (Material 3 Expressive Segmented Container)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val onToggleHaptic: (Boolean) -> Unit = { enabled ->
                    scope.launch { app.preferences.setHapticFeedback(enabled) }
                    if (enabled) {
                        context.performClickHaptic()
                    }
                }

                // Haptic Feedback Row
                ExpressiveSegmentedListItem(
                    index = 0,
                    count = 2,
                    leading = {
                        ExpressiveLeadingBadge(
                            icon = painterResource(R.drawable.ic_vibration)
                        )
                    },
                    headline = {
                        Text(
                            text = "Haptic feedback",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supporting = {
                        Text(
                            text = "Vibrate when tracking parameters are stripped",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        ExpressiveSwitch(
                            checked = hapticEnabled,
                            onCheckedChange = onToggleHaptic
                        )
                    },
                    onClick = {
                        onToggleHaptic(!hapticEnabled)
                    }
                )

                // Toast Notification Row
                ExpressiveSegmentedListItem(
                    index = 1,
                    count = 2,
                    leading = {
                        ExpressiveLeadingBadge(
                            icon = painterResource(R.drawable.ic_notifications)
                        )
                    },
                    headline = {
                        Text(
                            text = "Toast notification",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supporting = {
                        Text(
                            text = "Show on-screen alert when a link is cleaned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailing = {
                        ExpressiveSwitch(
                            checked = toastEnabled,
                            onCheckedChange = { scope.launch { app.preferences.setShowToast(it) } }
                        )
                    },
                    onClick = {
                        scope.launch { app.preferences.setShowToast(!toastEnabled) }
                    }
                )
            }
        }

        // Group 3: Your data (recent links, backup)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Your data",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RecentLinksSettingRow(index = 0, count = 1, onOpen = onOpenRecentLinks)
            }
        }

        // Group 4: About & Info (Material 3 Expressive Segmented Container)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // About SaneLink Row
                ExpressiveSegmentedListItem(
                    index = 0,
                    count = 2,
                    leading = {
                        ExpressiveLeadingBadge(
                            icon = painterResource(R.drawable.ic_info)
                        )
                    },
                    headline = {
                        Text(
                            text = "About SaneLink",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailing = {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    onClick = { showAboutDialog = true }
                )

                // Version Row
                ExpressiveSegmentedListItem(
                    index = 1,
                    count = 2,
                    leading = {
                        ExpressiveLeadingBadge(
                            icon = painterResource(R.drawable.ic_code)
                        )
                    },
                    headline = {
                        Text(
                            text = "Version",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailing = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "v$versionName",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                )
            }
        }

        // Bottom blank area to avoid floating navbar overlap
        Spacer(modifier = Modifier.height(110.dp))
    }

    // About Popup Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "About SaneLink",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "A privacy-first, 100% offline link sanitizer for Android. Strips tracking parameters, referral tokens, and surveillance redirects completely on-device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Developer",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "GamerJagdish",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "License",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "MIT License",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Rule Catalog",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "ClearURLs Ruleset",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    ExpressiveTactileButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/GamerJagdish/sanelink"))
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_code),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Source Code on GitHub",
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_open_in_new),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAboutDialog = false }
                ) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }
}

/** Recent links log: how many to keep (Off / 10 / 20 / 50 / All), and a way to open it. */
@Composable
private fun RecentLinksSettingRow(index: Int, count: Int, onOpen: () -> Unit) {
    val app = SaneLinkApp.instance
    val scope = rememberCoroutineScope()
    val limit by app.preferences.historyLimit.collectAsState(initial = RecentLinks.DEFAULT_LIMIT)
    val choices = RecentLinks.LIMIT_CHOICES
    val labels = choices.map {
        when (it) {
            RecentLinks.OFF -> "Off"
            RecentLinks.UNLIMITED -> "All"
            else -> it.toString()
        }
    }
    ExpressiveSegmentedListItem(
        index = index,
        count = count,
        leading = { ExpressiveLeadingBadge(icon = painterResource(R.drawable.ic_history)) },
        headline = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Recent links",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = "Open recent links",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        supporting = {
            Column {
                Text(
                    text = if (limit == RecentLinks.OFF) {
                        "Off. Cleaned links aren't saved."
                    } else {
                        "Saved only on this phone, to copy a link again or get its original back."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExpressiveConnectedButtonGroup(
                    options = labels,
                    selectedIndex = choices.indexOf(limit).coerceAtLeast(0),
                    onSelect = { i -> scope.launch { app.preferences.setHistoryLimit(choices[i]) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        onClick = onOpen
    )
}
