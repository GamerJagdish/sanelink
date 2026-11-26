package com.sanelink.app.ui.screens

import com.sanelink.app.ui.components.ExpressiveLeadingBadge
import com.sanelink.app.ui.components.ExpressiveSegmentedListItem
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import com.sanelink.app.ui.components.RecentLinkPreviewItem
import com.sanelink.app.core.history.RecentLink
import com.sanelink.app.ui.components.copyFromRecent
import com.sanelink.app.core.history.RecentLinks
import com.sanelink.app.service.ClipboardCleaner
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.model.SanitizationStats
import com.sanelink.app.ui.components.ExpressiveFloatingButton
import com.sanelink.app.ui.components.ExpressiveSandboxCard
import com.sanelink.app.ui.components.ExpressiveStatsRow
import com.sanelink.app.ui.components.HeroProtectionCard
import com.sanelink.app.ui.components.LocalHazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenRecentLinks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = SaneLinkApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hazeState = LocalHazeState.current

    val isAutoSanitize by app.preferences.autoSanitizeEnabled.collectAsState(initial = true)
    val stats by app.preferences.stats.collectAsState(initial = SanitizationStats())
    val recentLinks by app.preferences.recentLinks.collectAsState(initial = emptyList())
    val historyLimit by app.preferences.historyLimit.collectAsState(initial = RecentLinks.DEFAULT_LIMIT)

    var sandboxInput by rememberSaveable { mutableStateOf("") }
    // Follows rule changes (custom rules, patches, disabled providers) while on screen
    val sanitizer by app.repository.sanitizer.collectAsState()

    val sandboxResult = remember(sandboxInput, sanitizer) {
        if (sandboxInput.isNotBlank()) sanitizer.sanitize(sandboxInput) else null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Hero Card
            HeroProtectionCard(
                isEnabled = isAutoSanitize,
                onToggle = { enabled ->
                    scope.launch { app.preferences.setAutoSanitize(enabled) }
                }
            )

            // Metrics Summary
            ExpressiveStatsRow(stats = stats)

            // Test & Clean Sandbox
            ExpressiveSandboxCard(
                inputText = sandboxInput,
                onInputChanged = { sandboxInput = it },
                result = sandboxResult,
                onCopyClean = { cleanUrl ->
                    ClipboardCleaner.writeOwnClip(context, cleanUrl)
                    Toast.makeText(context, "Clean URL copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            )

            // Latest cleaned links (hidden when the log is turned off)
            if (historyLimit != RecentLinks.OFF) {
                RecentLinksPreviewCard(
                    links = recentLinks.take(3),
                    total = recentLinks.size,
                    onCopyClean = { copyFromRecent(context, it, isOriginal = false) },
                    onSeeAll = onOpenRecentLinks
                )
            }

            // Bottom blank area so content scrolls cleanly past floating button and navbar
            Spacer(modifier = Modifier.height(200.dp))
        }

        // Material 3 Expressive Floating "Paste Link" button with spring physics and frosted blur
        ExpressiveFloatingButton(
            text = "Paste Link",
            icon = painterResource(R.drawable.ic_content_copy),
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = cm?.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).coerceToText(context).toString()
                    if (text.isNotBlank()) {
                        sandboxInput = text.trim()
                        Toast.makeText(context, "Link pasted from clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
                .height(52.dp)
        )
    }
}

/**
 * "Recent links" section on the dashboard: a group header with "See all", then the latest
 * links as a segmented list, the same list style as Settings.
 */
@Composable
private fun RecentLinksPreviewCard(
    links: List<RecentLink>,
    total: Int,
    onCopyClean: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 40.dp)
        ) {
            Text(
                text = "Recent links",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            if (total > 0) {
                TextButton(onClick = onSeeAll) {
                    Text(if (total > links.size) "See all $total" else "See all", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (links.isEmpty()) {
                ExpressiveSegmentedListItem(
                    leading = { ExpressiveLeadingBadge(icon = painterResource(R.drawable.ic_history)) },
                    headline = {
                        Text(
                            text = "Nothing cleaned yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supporting = {
                        Text(
                            text = "Links SaneLink cleans will show up here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            } else {
                links.forEachIndexed { i, link ->
                    key(link.id) {
                        RecentLinkPreviewItem(
                            link = link,
                            index = i,
                            count = links.size,
                            onCopyClean = { onCopyClean(link.clean) }
                        )
                    }
                }
            }
        }
    }
}
