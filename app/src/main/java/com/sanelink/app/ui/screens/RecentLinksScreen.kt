package com.sanelink.app.ui.screens

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanelink.app.R
import com.sanelink.app.SaneLinkApp
import com.sanelink.app.core.history.RecentLink
import com.sanelink.app.core.history.RecentLinks
import com.sanelink.app.ui.components.AutoFitTitleText
import com.sanelink.app.ui.components.ExpressiveSingleActionButtonBar
import com.sanelink.app.ui.components.RecentLinkListItem
import com.sanelink.app.ui.components.copyFromRecent
import com.sanelink.app.ui.components.relativeTime
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Full list of recently cleaned links, grouped by day. Each link expands in place to show the
 * original and the actions. Copying the original is the way to undo a clean: it is written
 * as SaneLink's own clipboard content, so it isn't cleaned again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentLinksScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = SaneLinkApp.instance
    val scope = rememberCoroutineScope()
    val links by app.preferences.recentLinks.collectAsState(initial = emptyList())
    val limit by app.preferences.historyLimit.collectAsState(initial = RecentLinks.DEFAULT_LIMIT)
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val groups = remember(links) { groupByDay(context, links) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { AutoFitTitleText(text = "Recent Links", modifier = Modifier.padding(horizontal = 8.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = { ExpressiveSingleActionButtonBar(text = "Close", onClick = onBack) }
    ) { innerPadding ->
        if (links.isEmpty()) {
            EmptyRecentLinks(
                isOff = limit == RecentLinks.OFF,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                groups.forEachIndexed { g, group ->
                    item(key = "header-${group.title}") {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .animateItem()
                                .padding(start = 12.dp, bottom = 6.dp, top = if (g == 0) 0.dp else 18.dp)
                        )
                    }
                    itemsIndexed(group.links, key = { _, link -> link.id }) { i, link ->
                        RecentLinkListItem(
                            link = link,
                            index = i,
                            count = group.links.size,
                            expanded = expandedId == link.id,
                            timeLabel = if (group.isToday) relativeTime(link.timestamp) else timeOfDay(context, link.timestamp),
                            onToggle = { expandedId = if (expandedId == link.id) null else link.id },
                            onCopyClean = { copyFromRecent(context, link.clean, isOriginal = false) },
                            onCopyOriginal = { copyFromRecent(context, link.original, isOriginal = true) },
                            onDelete = { scope.launch { app.preferences.deleteRecentLink(link.id) } },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                item(key = "clear-all") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .padding(top = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = { confirmClear = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Clear all", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Clear recent links?", fontWeight = FontWeight.Bold) },
            text = { Text("All ${links.size} saved links will be removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { app.preferences.clearRecentLinks() }
                        confirmClear = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(32.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }
}

@Composable
private fun EmptyRecentLinks(isOff: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (isOff) "Recent links are off" else "Nothing here yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isOff) {
                "Turn them on in Settings to keep the links SaneLink cleans."
            } else {
                "Links SaneLink cleans will show up here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private class DayGroup(val title: String, val isToday: Boolean, val links: List<RecentLink>)

/** Splits the (newest-first) log into "Today", "Yesterday" and dated groups. */
private fun groupByDay(context: Context, links: List<RecentLink>): List<DayGroup> {
    val cal = Calendar.getInstance()
    fun dayKey(ts: Long): Int {
        cal.timeInMillis = ts
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }
    val now = System.currentTimeMillis()
    val today = dayKey(now)
    val yesterday = dayKey(now - DateUtils.DAY_IN_MILLIS)
    return links.groupBy { dayKey(it.timestamp) }.map { (key, items) ->
        val title = when (key) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> DateUtils.formatDateTime(
                context, items.first().timestamp,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
            )
        }
        DayGroup(title, key == today, items)
    }
}

private fun timeOfDay(context: Context, timestamp: Long): String =
    DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME)
