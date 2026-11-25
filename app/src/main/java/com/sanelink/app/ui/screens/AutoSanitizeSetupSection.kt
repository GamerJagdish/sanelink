package com.sanelink.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.service.ClipboardMonitor
import com.sanelink.app.ui.components.ExpressiveLeadingBadge
import com.sanelink.app.ui.components.ExpressiveSegmentedListItem
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme
import kotlinx.coroutines.delay

/** Overall state of background cleaning, derived from the live monitor status. */
private enum class AutoSanitizeState { PAUSED, NEEDS_SETUP, PARTIAL, FULL }

/**
 * Setup guide + live status for background clipboard cleaning.
 *
 * Step 1: accessibility service (required; hosts the monitor and lets it gain focus).
 * Step 2: one-time ADB grant of READ_LOGS (Android 10+, optional) so every copy in every app
 *         is detected, not just recognisable "Copy" buttons.
 */
@Composable
fun AutoSanitizeSetupSection(
    isServiceEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = SaneLinkApp.instance
    val motionScheme = LocalMotionScheme.current
    val status by ClipboardMonitor.status.collectAsState()
    val autoEnabled by app.preferences.autoSanitizeEnabled.collectAsState(initial = true)

    val needsLogGrant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val state = when {
        !autoEnabled -> AutoSanitizeState.PAUSED
        !isServiceEnabled -> AutoSanitizeState.NEEDS_SETUP
        !needsLogGrant -> AutoSanitizeState.FULL
        status.logAccessGranted -> AutoSanitizeState.FULL
        else -> AutoSanitizeState.PARTIAL
    }

    // Pick up an ADB grant made while this screen is open, without needing a restart.
    LaunchedEffect(needsLogGrant, status.logAccessGranted) {
        while (needsLogGrant && !ClipboardMonitor.status.value.logAccessGranted) {
            delay(2_000)
            ClipboardMonitor.refreshPermissionState(context)
            if (ClipboardMonitor.status.value.logAccessGranted) ClipboardMonitor.onAppForeground(context)
        }
    }

    val adbCommand = remember(context) {
        "adb shell pm grant ${context.packageName} android.permission.READ_LOGS"
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val stepCount = if (needsLogGrant) 3 else 2

        // Header: overall status
        ExpressiveSegmentedListItem(
            index = 0,
            count = stepCount,
            leading = { ExpressiveLeadingBadge(icon = painterResource(R.drawable.ic_shield)) },
            headline = {
                Text(
                    text = "Background Auto-Sanitize",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supporting = {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        fadeIn(motionScheme.fastEffects).togetherWith(fadeOut(motionScheme.fastEffects))
                    },
                    label = "autoSanitizeSummary"
                ) { s ->
                    Text(
                        text = when (s) {
                            AutoSanitizeState.PAUSED -> "Paused from the dashboard. Turn protection on to clean copied links automatically."
                            AutoSanitizeState.NEEDS_SETUP -> "Copied links are not cleaned yet. Finish step 1 below."
                            AutoSanitizeState.PARTIAL -> "Cleaning links copied with \"Copy\" / \"Copy link\" buttons and menus. Complete step 2 to catch every copy in every app."
                            AutoSanitizeState.FULL -> if (needsLogGrant) {
                                "Every copied link is cleaned before you paste it."
                            } else {
                                "Every copied link is cleaned instantly on this Android version."
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailing = { StatusPill(state) }
        )

        // Step 1: accessibility
        ExpressiveSegmentedListItem(
            index = 1,
            count = stepCount,
            leading = { StepBadge(number = 1, done = isServiceEnabled) },
            headline = {
                Text(
                    text = "Enable the SaneLink service",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            supporting = {
                Column {
                    Text(
                        text = if (needsLogGrant) {
                            "Accessibility → SaneLink → On. It notices copy actions and briefly takes focus so Android lets SaneLink read and clean the clipboard. Nothing else is read or stored."
                        } else {
                            "Accessibility → SaneLink → On. Keeps SaneLink running so it can clean every copied link."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    TactileTonalButton(
                        text = if (isServiceEnabled) "Service enabled" else "Open Accessibility settings",
                        iconRes = if (isServiceEnabled) R.drawable.ic_check else R.drawable.ic_open_in_new,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    )
                }
            }
        )

        // Step 2: READ_LOGS over ADB (Android 10+)
        if (needsLogGrant) {
            ExpressiveSegmentedListItem(
                index = 2,
                count = stepCount,
                leading = { StepBadge(number = 2, done = status.logAccessGranted) },
                headline = {
                    Text(
                        text = "Catch every copy (one-time ADB)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                supporting = {
                    Column {
                        Text(
                            text = "Recommended. Lets SaneLink see Android's \"clipboard changed\" signal, so links copied anywhere are cleaned, even without a Copy button. Run this once from a computer with USB debugging on:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        CommandBox(command = adbCommand)

                        AnimatedVisibility(
                            visible = status.logAccessGranted,
                            enter = expandVertically(
                                spring(
                                    dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                                    stiffness = motionScheme.defaultSpatial.stiffness
                                )
                            ) + fadeIn(motionScheme.fastEffects),
                            exit = shrinkVertically(
                                spring(
                                    dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                                    stiffness = motionScheme.defaultSpatial.stiffness
                                )
                            ) + fadeOut(motionScheme.fastEffects)
                        ) {
                            Text(
                                text = logStreamDetail(status, isServiceEnabled),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Text(
                                text = "Android 13+: when asked \"Allow SaneLink to access all device logs?\", choose Allow. Android asks again after a reboot or force-stop; just open SaneLink once.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            )
        }
    }
}

private fun logStreamDetail(status: ClipboardMonitor.Status, serviceEnabled: Boolean): String = when {
    !serviceEnabled -> "Permission granted. Finish step 1 to start watching."
    status.logStreamVerified -> "Granted and verified: clipboard changes are being detected."
    status.logStreamRunning -> "Granted and listening. Copy any link to confirm it works."
    status.logStreamNeedsAppOpen -> "Granted. Starting when SaneLink is open…"
    else -> "Permission granted."
}

@Composable
private fun StatusPill(state: AutoSanitizeState) {
    val motionScheme = LocalMotionScheme.current
    val scheme = MaterialTheme.colorScheme
    val (label, target) = when (state) {
        AutoSanitizeState.FULL -> "Active" to scheme.primaryContainer
        AutoSanitizeState.PARTIAL -> "Partial" to scheme.tertiaryContainer
        AutoSanitizeState.NEEDS_SETUP -> "Setup" to scheme.errorContainer
        AutoSanitizeState.PAUSED -> "Paused" to scheme.surfaceContainerHighest
    }
    val onTarget = when (state) {
        AutoSanitizeState.FULL -> scheme.onPrimaryContainer
        AutoSanitizeState.PARTIAL -> scheme.onTertiaryContainer
        AutoSanitizeState.NEEDS_SETUP -> scheme.onErrorContainer
        AutoSanitizeState.PAUSED -> scheme.onSurfaceVariant
    }
    val container by animateColorAsState(target, motionScheme.defaultColorEffects, label = "pillColor")
    val content by animateColorAsState(onTarget, motionScheme.fastColorEffects, label = "pillContent")

    Surface(shape = RoundedCornerShape(12.dp), color = container) {
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (scaleIn(motionScheme.fastSpatial) + fadeIn(motionScheme.fastEffects))
                    .togetherWith(scaleOut(motionScheme.fastSpatial) + fadeOut(motionScheme.fastEffects))
            },
            label = "pillLabel"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = content,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun StepBadge(number: Int, done: Boolean) {
    val motionScheme = LocalMotionScheme.current
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (done) scheme.primary else scheme.surfaceContainerHighest,
        motionScheme.defaultColorEffects,
        label = "stepContainer"
    )
    val content by animateColorAsState(
        if (done) scheme.onPrimary else scheme.primary,
        motionScheme.fastColorEffects,
        label = "stepContent"
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(if (done) 21.dp else 13.dp))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = done,
            transitionSpec = {
                (scaleIn(motionScheme.fastSpatial) + fadeIn(motionScheme.fastEffects))
                    .togetherWith(scaleOut(motionScheme.fastSpatial) + fadeOut(motionScheme.fastEffects))
            },
            label = "stepIcon"
        ) { isDone ->
            if (isDone) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Done",
                    tint = content,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = content
                )
            }
        }
    }
}

@Composable
private fun CommandBox(command: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = command,
            fontFamily = GoogleSansCode,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            if (pressed) 0.88f else 1f,
            LocalMotionScheme.current.fastSpatial,
            label = "copyCmdScale"
        )
        FilledTonalIconButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("adb command", command))
                Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
            },
            interactionSource = interaction,
            shape = CircleShape,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_content_copy),
                contentDescription = "Copy command",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TactileTonalButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    val motionScheme = LocalMotionScheme.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, motionScheme.fastSpatial, label = "btnScale")
    val corner by animateFloatAsState(if (pressed) 14f else 24f, motionScheme.fastSpatial, label = "btnCorner")

    FilledTonalButton(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corner.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        AnimatedContent(
            targetState = text to iconRes,
            transitionSpec = {
                (scaleIn(motionScheme.fastSpatial) + fadeIn(motionScheme.fastEffects))
                    .togetherWith(scaleOut(motionScheme.fastSpatial) + fadeOut(motionScheme.fastEffects))
            },
            label = "btnContent"
        ) { (label, icon) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(text = label, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.size(8.dp))
                Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
