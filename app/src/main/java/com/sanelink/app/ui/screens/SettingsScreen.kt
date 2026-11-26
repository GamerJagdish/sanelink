package com.sanelink.app.ui.screens

import com.sanelink.app.util.performClickHaptic
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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

        // Group 3: About & Info (Material 3 Expressive Segmented Container)
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

                    val ghInteraction = remember { MutableInteractionSource() }
                    val isGhPressed by ghInteraction.collectIsPressedAsState()
                    var ghDragOffsetX by remember { mutableFloatStateOf(0f) }
                    val animatedGhDragOffset by animateFloatAsState(
                        targetValue = ghDragOffsetX,
                        animationSpec = motionScheme.fastSpatial,
                        label = "ghDragOffset"
                    )

                    val ghScaleX by animateFloatAsState(
                        targetValue = if (isGhPressed) 0.98f else 1f,
                        animationSpec = motionScheme.fastSpatial,
                        label = "ghScaleX"
                    )
                    val ghScaleY by animateFloatAsState(
                        targetValue = if (isGhPressed) 0.92f else 1f,
                        animationSpec = motionScheme.fastSpatial,
                        label = "ghScaleY"
                    )
                    val ghCornerRadius by animateDpAsState(
                        targetValue = if (isGhPressed) 14.dp else 24.dp,
                        animationSpec = spring(
                            dampingRatio = motionScheme.fastSpatial.dampingRatio,
                            stiffness = motionScheme.fastSpatial.stiffness
                        ),
                        label = "ghCornerRadius"
                    )

                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/GamerJagdish/sanelink")
                            )
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(ghCornerRadius),
                        interactionSource = ghInteraction,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = ghScaleX
                                scaleY = ghScaleY
                                translationX = animatedGhDragOffset
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    val startX = down.position.x
                                    try {
                                        do {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                            val current = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (current.pressed) {
                                                val rawDelta = current.position.x - startX
                                                ghDragOffsetX = (rawDelta * 0.35f).coerceIn(-48f, 48f)
                                            }
                                        } while (event.changes.any { it.id == down.id && it.pressed })
                                    } finally {
                                        ghDragOffsetX = 0f
                                    }
                                }
                            }
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
