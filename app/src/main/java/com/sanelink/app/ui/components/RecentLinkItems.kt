package com.sanelink.app.ui.components

import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanelink.app.R
import com.sanelink.app.core.history.RecentLink
import com.sanelink.app.service.ClipboardCleaner
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme

/**
 * Copies a link from the recent log as SaneLink's own clipboard write, so background
 * auto-clean leaves it alone. This is how the original (uncleaned) link can be recovered.
 *
 * Android 13+ already confirms every copy with its own overlay, so the toast is only
 * shown on older versions.
 */
fun copyFromRecent(context: Context, text: String, isOriginal: Boolean) {
    ClipboardCleaner.writeOwnClip(context, text)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        val msg = if (isOriginal) "Original link copied" else "Clean link copied"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}

private val URL_HOST = Regex("""https?://(?:www\.)?([^/\s?#:]+)""", RegexOption.IGNORE_CASE)
private val URL_SCHEME = Regex("""^https?://(www\.)?""", RegexOption.IGNORE_CASE)

/** "youtube.com" for a link, used as the item's title. */
fun recentLinkDomain(link: RecentLink): String =
    (URL_HOST.find(link.clean) ?: URL_HOST.find(link.original))
        ?.groupValues?.get(1)?.lowercase()
        ?: "Link"

private fun removedLabel(link: RecentLink): String = when (link.removed.size) {
    0 -> "Cleaned"
    1 -> "1 tracker removed"
    else -> "${link.removed.size} trackers removed"
}

/** "Just now", "5 min. ago", "Yesterday"… */
fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String =
    if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) {
        "Just now"
    } else {
        DateUtils.getRelativeTimeSpanString(
            timestamp, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

/**
 * Letter avatar for a site. The tone (primary, secondary or tertiary container) comes from
 * the domain, so the same site always looks the same and a list doesn't turn monotone.
 */
@Composable
fun DomainMonogram(domain: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (Math.floorMod(domain.hashCode(), 3)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = domain.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

/** Two-line list item for the dashboard preview. Tapping it copies the clean link. */
@Composable
fun RecentLinkPreviewItem(link: RecentLink, index: Int, count: Int, onCopyClean: () -> Unit) {
    val domain = remember(link.id) { recentLinkDomain(link) }
    ExpressiveSegmentedListItem(
        index = index,
        count = count,
        onClick = onCopyClean,
        leading = { DomainMonogram(domain) },
        headline = {
            Text(
                text = domain,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supporting = {
            Text(
                text = "${removedLabel(link)} · ${relativeTime(link.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = {
            Icon(
                painter = painterResource(R.drawable.ic_content_copy),
                contentDescription = "Copy clean link",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

/**
 * Three-line list item for the Recent links page: site, clean link, and how and when it was
 * cleaned. Tapping it expands in place to show the original link, what was removed and the
 * actions. While open, its corners round out and it moves to a brighter container, so it
 * reads as one open item within the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentLinkListItem(
    link: RecentLink,
    index: Int,
    count: Int,
    expanded: Boolean,
    timeLabel: String,
    onToggle: () -> Unit,
    onCopyClean: () -> Unit,
    onCopyOriginal: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalMotionScheme.current
    val domain = remember(link.id) { recentLinkDomain(link) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val innerRadius by animateDpAsState(
        targetValue = when {
            expanded -> 24.dp
            isPressed -> 10.dp
            else -> 4.dp
        },
        animationSpec = motion.fastSpatialSpec(),
        label = "recentItemCorner"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = motion.fastSpatial,
        label = "recentItemScale"
    )
    val container by animateColorAsState(
        targetValue = if (expanded) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        animationSpec = motion.defaultColorEffects,
        label = "recentItemColor"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 90f,
        animationSpec = motion.fastSpatial,
        label = "recentItemChevron"
    )

    Surface(
        onClick = onToggle,
        shape = expressiveSegmentShape(index, count, innerRadius),
        color = container,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DomainMonogram(domain)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = link.clean.replaceFirst(URL_SCHEME, ""),
                        fontFamily = GoogleSansCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface.copy(alpha = 0.85f),
                        maxLines = if (expanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${link.source.label} · ${removedLabel(link)} · $timeLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.fastEffects),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffects)
            ) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailBlock(label = "Original") {
                        Text(
                            text = link.original,
                            fontFamily = GoogleSansCode,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (link.removed.isNotEmpty()) {
                        DetailBlock(label = "Removed") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val shown = link.removed.take(MAX_TAGS)
                                shown.forEach { param ->
                                    RuleTag(text = param, containerColor = scheme.errorContainer, contentColor = scheme.onErrorContainer)
                                }
                                if (link.removed.size > shown.size) {
                                    RuleTag(
                                        text = "+${link.removed.size - shown.size}",
                                        containerColor = scheme.surfaceContainerHighest,
                                        contentColor = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpressiveTactileButton(
                            onClick = onCopyClean,
                            filled = true,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Copy clean", fontWeight = FontWeight.SemiBold, maxLines = 1) }
                        ExpressiveTactileButton(
                            onClick = onCopyOriginal,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Copy original", fontWeight = FontWeight.SemiBold, maxLines = 1) }
                        ExpressiveTactileIconButton(onClick = onDelete) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = "Delete from recent links",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBlock(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

private const val MAX_TAGS = 8
