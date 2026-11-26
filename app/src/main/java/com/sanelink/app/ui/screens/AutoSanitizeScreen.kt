package com.sanelink.app.ui.screens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.service.ClipboardAccessibilityService
import com.sanelink.app.service.ClipboardMonitor
import com.sanelink.app.shizuku.ShizukuManager
import com.sanelink.app.ui.components.AutoFitTitleText
import com.sanelink.app.ui.components.ExpressiveLeadingBadge
import com.sanelink.app.ui.components.ExpressiveSegmentedListItem
import com.sanelink.app.ui.components.ExpressiveSingleActionButtonBar
import com.sanelink.app.ui.components.ExpressiveTactileButton
import com.sanelink.app.ui.components.ExpressiveTactileIconButton
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme
import kotlinx.coroutines.delay

// ---- Shared state -------------------------------------------------------------------------

/** Overall state of background cleaning, derived from the live monitor status. */
internal enum class AutoSanitizeState { PAUSED, NEEDS_SETUP, PARTIAL, FULL }

/** How copies are currently detected, for display. */
private enum class DetectionMethod { DIRECT, SHIZUKU, ADB, COPY_BUTTONS, NONE }

private val needsSystemSignal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

private fun deriveState(
    autoEnabled: Boolean,
    serviceEnabled: Boolean,
    status: ClipboardMonitor.Status,
    shizuku: ShizukuManager.State
): AutoSanitizeState = when {
    !autoEnabled -> AutoSanitizeState.PAUSED
    !serviceEnabled -> AutoSanitizeState.NEEDS_SETUP
    !needsSystemSignal -> AutoSanitizeState.FULL
    status.shizukuActive || shizuku == ShizukuManager.State.ACTIVE || status.logAccessGranted -> AutoSanitizeState.FULL
    else -> AutoSanitizeState.PARTIAL
}

private fun deriveMethod(
    serviceEnabled: Boolean,
    status: ClipboardMonitor.Status,
    shizuku: ShizukuManager.State
): DetectionMethod = when {
    !serviceEnabled -> DetectionMethod.NONE
    !needsSystemSignal -> DetectionMethod.DIRECT
    status.shizukuActive || shizuku == ShizukuManager.State.ACTIVE -> DetectionMethod.SHIZUKU
    status.logAccessGranted -> DetectionMethod.ADB
    else -> DetectionMethod.COPY_BUTTONS
}

/** Tracks whether our accessibility service is enabled, re-checking on every resume. */
@Composable
internal fun rememberAccessibilityServiceEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = isAccessibilityServiceEnabled(context)
                ClipboardMonitor.refreshPermissionState(context)
                ShizukuManager.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

internal fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    val enabledByManager = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        ?.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName &&
                it.resolveInfo.serviceInfo.name == ClipboardAccessibilityService::class.java.name
        } == true
    if (enabledByManager) return true

    val expected = "${context.packageName}/${ClipboardAccessibilityService::class.java.name}"
    val enabledString = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledString.split(":").any { it.equals(expected, ignoreCase = true) }
}

// ---- Settings entry -----------------------------------------------------------------------

/** Compact row on the main Settings page that opens [AutoSanitizeScreen]. */
@Composable
fun AutoSanitizeSettingsEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = SaneLinkApp.instance
    val serviceEnabled = rememberAccessibilityServiceEnabled()
    val status by ClipboardMonitor.status.collectAsState()
    val shizuku by ShizukuManager.state.collectAsState()
    val autoEnabled by app.preferences.autoSanitizeEnabled.collectAsState(initial = true)
    val state = deriveState(autoEnabled, serviceEnabled, status, shizuku)
    val motionScheme = LocalMotionScheme.current

    ExpressiveSegmentedListItem(
        modifier = modifier,
        leading = { ExpressiveLeadingBadge(icon = painterResource(R.drawable.ic_shield)) },
        headline = {
            Text(
                text = "Background Auto-Sanitize",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                // Very narrow screens: scroll instead of cutting the title off
                modifier = Modifier.basicMarquee()
            )
        },
        supporting = {
            // Status pill sits under the summary so neither the title nor the summary is
            // squeezed by trailing content; the summary may wrap instead of being cut off.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(motionScheme.fastEffects).togetherWith(fadeOut(motionScheme.fastEffects)) },
                    label = "autoEntrySummary"
                ) { s ->
                    Text(
                        text = when (s) {
                            AutoSanitizeState.FULL -> "Cleaning every link you copy"
                            AutoSanitizeState.PARTIAL -> "Cleaning links from Copy buttons"
                            AutoSanitizeState.NEEDS_SETUP -> "Tap to set up"
                            AutoSanitizeState.PAUSED -> "Paused from the dashboard"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(state)
            }
        },
        trailing = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = onClick
    )
}

// ---- Full page ----------------------------------------------------------------------------

/**
 * Setup and live status for background clipboard cleaning.
 *
 * Required: the accessibility service (hosts the monitor and lets it briefly take focus).
 * Android 10+ optional boost: Shizuku or a one-time ADB grant so every copy is detected,
 * not just recognisable "Copy" buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoSanitizeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = SaneLinkApp.instance
    val serviceEnabled = rememberAccessibilityServiceEnabled()
    val status by ClipboardMonitor.status.collectAsState()
    val shizuku by ShizukuManager.state.collectAsState()
    val autoEnabled by app.preferences.autoSanitizeEnabled.collectAsState(initial = true)
    val state = deriveState(autoEnabled, serviceEnabled, status, shizuku)
    val method = deriveMethod(serviceEnabled, status, shizuku)

    // Pick up an ADB grant made while this page is open, without needing a restart.
    LaunchedEffect(status.logAccessGranted) {
        while (needsSystemSignal && !ClipboardMonitor.status.value.logAccessGranted) {
            delay(2_000)
            ClipboardMonitor.refreshPermissionState(context)
            if (ClipboardMonitor.status.value.logAccessGranted) ClipboardMonitor.onAppForeground(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    AutoFitTitleText(
                        text = "Auto-Sanitize",
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            ExpressiveSingleActionButtonBar(text = "Close", onClick = onBack)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            StatusHeroCard(state = state, method = method, verified = status.logStreamVerified)

            SectionColumn(title = "Required") {
                AccessibilityRow(serviceEnabled = serviceEnabled)
            }

            if (needsSystemSignal) {
                SectionColumn(
                    title = "Catch every copy",
                    subtitle = "Optional. Pick one. Without it, SaneLink only notices Copy buttons such as the share sheet's, not copying selected text."
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ShizukuRow(shizuku = shizuku, serviceEnabled = serviceEnabled, index = 0, count = 2)
                        AdbRow(status = status, shizukuActive = method == DetectionMethod.SHIZUKU, index = 1, count = 2)
                    }
                }
            }

            Text(
                text = "Everything happens on your device. SaneLink only looks for copy actions and the system's \"clipboard changed\" signal; it never stores, uploads or reads anything else. When a link is copied in another app, SaneLink briefly opens an invisible window, because Android only lets the app on screen read the clipboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun SectionColumn(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        content()
    }
}

@Composable
private fun StatusHeroCard(state: AutoSanitizeState, method: DetectionMethod, verified: Boolean) {
    val motionScheme = LocalMotionScheme.current
    val scheme = MaterialTheme.colorScheme
    val active = state == AutoSanitizeState.FULL || state == AutoSanitizeState.PARTIAL
    val container by animateColorAsState(
        when (state) {
            AutoSanitizeState.FULL -> scheme.primaryContainer
            AutoSanitizeState.PARTIAL -> scheme.tertiaryContainer
            AutoSanitizeState.NEEDS_SETUP -> scheme.errorContainer
            AutoSanitizeState.PAUSED -> scheme.surfaceContainer
        },
        motionScheme.defaultColorEffects,
        label = "heroContainer"
    )
    val content by animateColorAsState(
        when (state) {
            AutoSanitizeState.FULL -> scheme.onPrimaryContainer
            AutoSanitizeState.PARTIAL -> scheme.onTertiaryContainer
            AutoSanitizeState.NEEDS_SETUP -> scheme.onErrorContainer
            AutoSanitizeState.PAUSED -> scheme.onSurface
        },
        motionScheme.fastColorEffects,
        label = "heroContent"
    )
    val corner by animateDpAsState(
        if (active) 32.dp else 24.dp,
        motionScheme.defaultSpatialSpec(),
        label = "heroCorner"
    )
    val iconScale by animateFloatAsState(if (active) 1f else 0.9f, motionScheme.defaultSpatial, label = "heroIconScale")

    Surface(
        shape = RoundedCornerShape(corner),
        color = container,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                    .clip(RoundedCornerShape(if (active) 26.dp else 16.dp))
                    .background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (active) R.drawable.ic_shield else R.drawable.ic_info),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(26.dp)
                )
            }
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(motionScheme.fastEffects) + scaleIn(motionScheme.fastSpatial, initialScale = 0.96f))
                        .togetherWith(fadeOut(motionScheme.fastEffects))
                },
                label = "heroText"
            ) { s ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when (s) {
                            AutoSanitizeState.FULL -> "Every copy is cleaned"
                            AutoSanitizeState.PARTIAL -> "Partly active"
                            AutoSanitizeState.NEEDS_SETUP -> "Not set up yet"
                            AutoSanitizeState.PAUSED -> "Paused"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (s) {
                            AutoSanitizeState.FULL -> "Tracking is stripped from copied links before you paste them."
                            AutoSanitizeState.PARTIAL -> "Links copied with Copy buttons (like the share sheet's) are cleaned. Copying selected text needs Shizuku or the ADB grant below."
                            AutoSanitizeState.NEEDS_SETUP -> "Turn on the accessibility service below to start cleaning copied links."
                            AutoSanitizeState.PAUSED -> "Protection is off. Turn it back on from the dashboard."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.85f)
                    )
                }
            }
            AnimatedVisibility(
                visible = method != DetectionMethod.NONE,
                enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.fastEffects),
                exit = shrinkVertically(motionScheme.defaultSpatialSpec()) + fadeOut(motionScheme.fastEffects)
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = content.copy(alpha = 0.10f)) {
                    Text(
                        text = when (method) {
                            DetectionMethod.DIRECT -> "Detection: direct (Android 9 and below)"
                            DetectionMethod.SHIZUKU -> if (verified) "Detection: Shizuku · verified" else "Detection: Shizuku"
                            DetectionMethod.ADB -> if (verified) "Detection: log access · verified" else "Detection: log access"
                            DetectionMethod.COPY_BUTTONS -> "Detection: Copy buttons only"
                            DetectionMethod.NONE -> ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessibilityRow(serviceEnabled: Boolean) {
    val context = LocalContext.current
    ExpressiveSegmentedListItem(
        leading = { CheckBadge(done = serviceEnabled, icon = R.drawable.ic_settings) },
        headline = { RowTitle("Accessibility service") },
        supporting = {
            Column {
                RowBody(
                    if (needsSystemSignal) {
                        "Settings → Accessibility → SaneLink → On. Keeps SaneLink ready in the background and lets it read the clipboard right after you copy."
                    } else {
                        "Settings → Accessibility → SaneLink → On. Keeps SaneLink running so it can clean every copied link."
                    }
                )
                Spacer(Modifier.height(12.dp))
                TactileActionButton(
                    text = if (serviceEnabled) "Enabled" else "Open Accessibility settings",
                    icon = if (serviceEnabled) R.drawable.ic_check else R.drawable.ic_open_in_new,
                    filled = !serviceEnabled,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }
        }
    )
}

@Composable
private fun ShizukuRow(shizuku: ShizukuManager.State, serviceEnabled: Boolean, index: Int, count: Int) {
    val context = LocalContext.current
    val active = shizuku == ShizukuManager.State.ACTIVE
    ExpressiveSegmentedListItem(
        index = index,
        count = count,
        leading = { CheckBadge(done = active, icon = R.drawable.ic_code) },
        headline = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RowTitle("Shizuku")
                Spacer(Modifier.size(8.dp))
                SmallTag("Recommended")
            }
        },
        supporting = {
            Column {
                RowBody(
                    when (shizuku) {
                        ShizukuManager.State.NOT_INSTALLED -> "No computer needed after Shizuku is set up, and no log-access prompts. Install Shizuku, start it, then come back here."
                        ShizukuManager.State.NOT_RUNNING -> "Shizuku is installed but not running. Start it in the Shizuku app (needed again after each reboot unless your device is rooted)."
                        ShizukuManager.State.PERMISSION_REQUIRED -> "Shizuku is running. Allow SaneLink to use it."
                        ShizukuManager.State.READY -> if (serviceEnabled) "Allowed. Connecting…" else "Allowed. It starts working once the accessibility service is on."
                        ShizukuManager.State.ACTIVE -> "Connected. Every copy is detected through Shizuku. To stop, turn SaneLink off in Shizuku's authorized apps. Shizuku then closes SaneLink, which also switches off its accessibility service, so turn that back on above."
                        ShizukuManager.State.UNAVAILABLE -> "This Shizuku version isn't supported or the connection failed. Update Shizuku and restart it."
                    }
                )
                Spacer(Modifier.height(12.dp))
                when (shizuku) {
                    ShizukuManager.State.NOT_INSTALLED -> TactileActionButton("Get Shizuku", R.drawable.ic_open_in_new) {
                        openShizukuDownload(context)
                    }
                    ShizukuManager.State.NOT_RUNNING,
                    ShizukuManager.State.UNAVAILABLE -> TactileActionButton("Open Shizuku", R.drawable.ic_open_in_new) {
                        ShizukuManager.launchIntent(context)?.let(context::startActivity)
                    }
                    ShizukuManager.State.PERMISSION_REQUIRED -> TactileActionButton("Allow SaneLink", R.drawable.ic_check, filled = true) {
                        ShizukuManager.requestPermission()
                    }
                    ShizukuManager.State.READY,
                    ShizukuManager.State.ACTIVE -> TactileActionButton(
                        text = if (active) "Connected · Manage" else "Allowed · Manage",
                        icon = R.drawable.ic_open_in_new,
                        // Access is granted and revoked in Shizuku itself, so take the user there.
                        onClick = {
                            val intent = ShizukuManager.launchIntent(context)
                            if (intent != null) context.startActivity(intent) else ShizukuManager.refresh()
                        }
                    )
                }
            }
        }
    )
}

@Composable
private fun AdbRow(status: ClipboardMonitor.Status, shizukuActive: Boolean, index: Int, count: Int) {
    val context = LocalContext.current
    val motionScheme = LocalMotionScheme.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val adbCommand = remember(context) {
        "adb shell pm grant ${context.packageName} android.permission.READ_LOGS"
    }
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f, motionScheme.fastSpatial, label = "adbChevron")

    ExpressiveSegmentedListItem(
        index = index,
        count = count,
        onClick = { expanded = !expanded },
        leading = { CheckBadge(done = status.logAccessGranted, icon = R.drawable.ic_code) },
        headline = { RowTitle("ADB command") },
        supporting = {
            Column {
                RowBody(
                    when {
                        status.logAccessGranted && shizukuActive -> "Granted. Used automatically if Shizuku stops."
                        status.logAccessGranted -> logStreamDetail(status)
                        else -> "No extra app. Run one command from a computer, once."
                    }
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(motionScheme.defaultSpatialSpec()) + fadeIn(motionScheme.fastEffects),
                    exit = shrinkVertically(motionScheme.defaultSpatialSpec()) + fadeOut(motionScheme.fastEffects)
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RowBody("Turn on USB debugging, connect your computer and run:")
                        CommandBox(command = adbCommand)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            RowBody("Android 13+: when asked \"Allow SaneLink to access all device logs?\", choose Allow. Android asks again after a reboot or force-stop; just open SaneLink once.")
                        }
                    }
                }
            }
        },
        trailing = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = if (expanded) "Hide command" else "Show command",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }
    )
}

private fun logStreamDetail(status: ClipboardMonitor.Status): String = when {
    !status.serviceConnected -> "Granted. Starts once the accessibility service is on."
    status.logStreamVerified -> "Granted and verified."
    status.logStreamRunning -> "Granted and listening. Copy any link to confirm."
    status.logStreamNeedsAppOpen -> "Granted. Starting while SaneLink is open…"
    else -> "Granted."
}

private fun openShizukuDownload(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${ShizukuManager.SHIZUKU_PACKAGE}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (_: Exception) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

// ---- Small building blocks ----------------------------------------------------------------

@Composable
private fun RowTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RowBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SmallTag(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
internal fun StatusPill(state: AutoSanitizeState) {
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

/** Leading badge that morphs into a filled check circle once its step is done. */
@Composable
private fun CheckBadge(done: Boolean, icon: Int) {
    val motionScheme = LocalMotionScheme.current
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (done) scheme.primary else scheme.surfaceContainerHighest.copy(alpha = 0.65f),
        motionScheme.defaultColorEffects,
        label = "badgeContainer"
    )
    val content by animateColorAsState(
        if (done) scheme.onPrimary else scheme.primary,
        motionScheme.fastColorEffects,
        label = "badgeContent"
    )
    val corner by animateDpAsState(
        if (done) 21.dp else 13.dp,
        motionScheme.fastSpatialSpec(),
        label = "badgeCorner"
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(corner))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = done,
            transitionSpec = {
                (scaleIn(motionScheme.fastSpatial) + fadeIn(motionScheme.fastEffects))
                    .togetherWith(scaleOut(motionScheme.fastSpatial) + fadeOut(motionScheme.fastEffects))
            },
            label = "badgeIcon"
        ) { isDone ->
            Icon(
                painter = painterResource(if (isDone) R.drawable.ic_check else icon),
                contentDescription = if (isDone) "Done" else null,
                tint = content,
                modifier = Modifier.size(20.dp)
            )
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
        ExpressiveTactileIconButton(
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("adb command", command))
                Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
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
private fun TactileActionButton(
    text: String,
    icon: Int,
    filled: Boolean = false,
    onClick: () -> Unit
) {
    val motionScheme = LocalMotionScheme.current
    ExpressiveTactileButton(
        onClick = onClick,
        filled = filled,
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = text to icon,
            transitionSpec = {
                (scaleIn(motionScheme.fastSpatial) + fadeIn(motionScheme.fastEffects))
                    .togetherWith(scaleOut(motionScheme.fastSpatial) + fadeOut(motionScheme.fastEffects))
            },
            label = "actionBtnContent"
        ) { (label, iconRes) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(text = label, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.size(8.dp))
                Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
