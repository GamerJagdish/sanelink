package com.sanelink.app.ui.screens

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.layout.layout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.animateContentSize
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.sanelink.app.R
import com.sanelink.app.service.SaneLinkTileService
import com.sanelink.app.ui.components.ExpressiveLeadingBadge
import com.sanelink.app.ui.components.ExpressiveSegmentedListItem
import com.sanelink.app.ui.components.ExpressiveTactileButton
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme
import com.sanelink.app.ui.theme.googleSansFlex
import com.sanelink.app.ui.theme.rememberReducedMotion
import com.sanelink.app.util.performClickHapticIfEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor

private const val PAGE_COUNT = 4
private val MAX_BLOB = 300.dp

/** Big, round, extra-wide display face for onboarding titles. */
private val TitleFont = googleSansFlex(weight = 900, width = 125f, roundness = 100f, opticalSize = 40f)

/**
 * First-run welcome guide: what SaneLink does, the ways to use it, automatic cleaning, and
 * privacy. A shape behind the artwork morphs between Material 3 Expressive shapes as the
 * pages are swiped.
 *
 * [onFinish] gets true when the user asked SaneLink to clean automatically, so the caller
 * can open the Auto-Sanitize setup next.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onFinish: (setUpAutoClean: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    var wantsAutoClean by rememberSaveable { mutableStateOf(false) }
    val isLastPage = pagerState.currentPage == PAGE_COUNT - 1

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { context.performClickHapticIfEnabled() }
    }

    // Height (px) each page leaves for its artwork once its text is laid out
    val heroHeights = remember { mutableStateListOf(*Array(PAGE_COUNT) { 0f }) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    // Short screens (small phones, large display size) get tighter type so every page fits
    CompositionLocalProvider(LocalCompactOnboarding provides (maxHeight < COMPACT_HEIGHT)) {

        // Morphing shape behind each page's artwork (outside the pager, so it morphs rather than
        // slides). It follows the pages' artwork areas, growing or shrinking between them.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = HERO_TOP)
        ) {
            MorphingBlob(
                pagerState = pagerState,
                heroHeightPx = {
                    val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                        .coerceIn(0f, (PAGE_COUNT - 1).toFloat())
                    val index = floor(position).toInt()
                    val next = (index + 1).coerceAtMost(PAGE_COUNT - 1)
                    val fraction = position - index
                    heroHeights[index] + (heroHeights[next] - heroHeights[index]) * fraction
                }
            )
        }

        HorizontalPager(
            state = pagerState,
            // Every page stays laid out, so each artwork height is known before it's swiped to
            beyondViewportPageCount = PAGE_COUNT,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val isActive = pagerState.settledPage == page
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = HERO_TOP)
            ) {
                // Artwork gets the space the text doesn't need, and scales down to fit it
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { heroHeights[page] = it.height.toFloat() }
                        .graphicsLayer {
                            // Fades and shrinks away by mid-swipe, so two pages' artwork never overlap
                            val visibility = (1f - abs(pageOffset) * 2.2f).coerceIn(0f, 1f)
                            translationX = pageOffset * size.width * 0.5f
                            alpha = visibility
                            scaleX = 0.85f + 0.15f * visibility
                            scaleY = 0.85f + 0.15f * visibility
                        },
                    contentAlignment = Alignment.Center
                ) {
                    FitInside {
                        when (page) {
                            0 -> LinkCleanDemo(active = isActive)
                            1 -> WaysHero(active = isActive)
                            2 -> LazyHero()
                            else -> PrivacyHero(active = isActive)
                        }
                    }
                }
                // Text sits just above the bottom bar, at its natural height
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 96.dp)
                ) {
                    when (page) {
                        0 -> WelcomePage(active = isActive)
                        1 -> WaysPage()
                        2 -> LazyPage(
                            active = isActive,
                            wantsAutoClean = wantsAutoClean,
                            onToggle = { wantsAutoClean = !wantsAutoClean }
                        )
                        else -> PrivacyPage()
                    }
                }
            }
        }

        // Skip, top-right; gone on the last page where the main button finishes anyway
        AnimatedVisibility(
            visible = !isLastPage,
            enter = fadeIn(LocalMotionScheme.current.fastEffects),
            exit = fadeOut(LocalMotionScheme.current.fastEffects),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            TextButton(onClick = { onFinish(wantsAutoClean) }) {
                Text("Skip", fontWeight = FontWeight.SemiBold)
            }
        }

        OnboardingBottomBar(
            pagerState = pagerState,
            isLastPage = isLastPage,
            onNext = {
                if (isLastPage) onFinish(wantsAutoClean)
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    }
}

/** True on short screens: titles and the big word shrink a step so every page fits. */
private val LocalCompactOnboarding = staticCompositionLocalOf { false }
private val COMPACT_HEIGHT = 720.dp
private val HERO_TOP = 24.dp

/**
 * Lays [content] out at its natural size, centered, and scales it down when the space is
 * smaller. When there's too little room to read it at all, it fades away instead.
 */
@Composable
private fun FitInside(content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth))
        val scale = if (placeable.height == 0) 1f else (constraints.maxHeight.toFloat() / placeable.height).coerceAtMost(1f)
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer(
                (constraints.maxWidth - placeable.width) / 2,
                (constraints.maxHeight - placeable.height) / 2
            ) {
                scaleX = scale
                scaleY = scale
                alpha = ((scale - 0.45f) / 0.2f).coerceIn(0f, 1f)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------------------------

@Composable
private fun PageTitle(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    val compact = LocalCompactOnboarding.current
    Text(
        text = text,
        fontFamily = TitleFont,
        fontSize = if (compact) 32.sp else 38.sp,
        lineHeight = if (compact) 36.sp else 42.sp,
        color = color
    )
}

@Composable
private fun PageBody(text: String) {
    Text(
        text = text,
        style = if (LocalCompactOnboarding.current) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun WelcomePage(active: Boolean) {
    PageTitle("Make your links")
    // "sane." has no tall letters, so the room the font keeps for them would show as a gap
    HeroWord(text = "sane.", active = active, entrance = HeroEntrance.Pop, topTrim = 30.dp)
    PageBody("SaneLink cuts the trackers and junk out of links, and keeps the parts that matter.")
}

private enum class HeroEntrance { Pop, TiltDrop }

/** Takes [amount] off the top of this element's layout height (it still draws in full). */
private fun Modifier.trimTop(amount: Dp): Modifier = if (amount == 0.dp) this else layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val cut = amount.roundToPx().coerceAtMost(placeable.height)
    layout(placeable.width, placeable.height - cut) { placeable.place(0, -cut) }
}

/**
 * The one huge, colored word on a page ("sane.", "lazy.") that plays its entrance every time
 * the page is reached: a bouncy pop, or a tilted drop that lands slightly askew.
 */
@Composable
private fun HeroWord(text: String, active: Boolean, entrance: HeroEntrance, topTrim: Dp = 0.dp) {
    val reducedMotion = rememberReducedMotion()
    val restingTilt = if (entrance == HeroEntrance.TiltDrop) -4f else 0f
    val scale = remember { Animatable(1f) }
    val tilt = remember { Animatable(restingTilt) }
    val drop = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active || reducedMotion) return@LaunchedEffect
        when (entrance) {
            HeroEntrance.Pop -> {
                scale.snapTo(0.6f)
                scale.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))
            }
            HeroEntrance.TiltDrop -> {
                tilt.snapTo(-14f)
                drop.snapTo(-40f)
                launch { drop.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                tilt.animateTo(restingTilt, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))
            }
        }
    }
    val compact = LocalCompactOnboarding.current
    Text(
        text = text,
        fontFamily = TitleFont,
        fontSize = if (compact) 64.sp else 80.sp,
        lineHeight = if (compact) 64.sp else 80.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .trimTop(if (compact) topTrim * 0.8f else topTrim)
            .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = tilt.value
            translationY = drop.value * density
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
        }
    )
}

@Composable
private fun WaysPage() {
    PageTitle("Clean links your way.")
    PageBody("Use whichever fits the moment.")
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        WayItem(0, R.drawable.ic_share, "Share to SaneLink", "It cleans the link and copies it.")
        WayItem(1, R.drawable.ic_text_fields, "Select text", "Tap “Clean Link” in the menu.")
        WayItem(2, R.drawable.ic_cleaning_services, "Quick Settings tile", "Cleans whatever you copied.", trailing = { AddTileButton() })
        WayItem(3, R.drawable.ic_content_copy, "Test & Clean", "Paste a link on the dashboard.")
    }
}

@Composable
private fun WayItem(
    index: Int,
    icon: Int,
    title: String,
    body: String,
    trailing: (@Composable () -> Unit)? = null
) {
    ExpressiveSegmentedListItem(
        index = index,
        count = 4,
        leading = { ExpressiveLeadingBadge(icon = painterResource(icon)) },
        headline = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supporting = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailing = trailing
    )
}

/** One-tap "add the tile" on Android 13+. Optional: nothing waits on it. */
@Composable
private fun AddTileButton() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var added by rememberSaveable { mutableStateOf(false) }
    AnimatedContent(
        targetState = added,
        transitionSpec = {
            (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn())
                .togetherWith(scaleOut() + fadeOut())
        },
        label = "addTile"
    ) { isAdded ->
        if (isAdded) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = "Tile added",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            ExpressiveTactileButton(
                onClick = { requestAddTile(context) { added = true } },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) { Text("Add", fontWeight = FontWeight.SemiBold) }
        }
    }
}

private fun requestAddTile(context: Context, onAdded: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val manager = context.getSystemService(StatusBarManager::class.java) ?: return
    runCatching {
        manager.requestAddTileService(
            ComponentName(context, SaneLinkTileService::class.java),
            context.getString(R.string.tile_label),
            AndroidIcon.createWithResource(context, R.drawable.ic_tile),
            context.mainExecutor
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                onAdded()
            }
        }
    }
}

@Composable
private fun LazyPage(active: Boolean, wantsAutoClean: Boolean, onToggle: () -> Unit) {
    val motion = LocalMotionScheme.current
    PageTitle("Or just be")
    HeroWord(text = "lazy.", active = active, entrance = HeroEntrance.TiltDrop)
    PageBody("Copy a link anywhere and SaneLink cleans it before you paste. No sharing, no tapping, nothing.")
    Spacer(Modifier.height(24.dp))

    val container by animateColorAsState(
        targetValue = if (wantsAutoClean) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
        animationSpec = motion.defaultColorEffects,
        label = "lazyButtonColor"
    )
    val content by animateColorAsState(
        targetValue = if (wantsAutoClean) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
        animationSpec = motion.defaultColorEffects,
        label = "lazyButtonContent"
    )
    ExpressiveTactileButton(
        onClick = onToggle,
        filled = true,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = wantsAutoClean,
            transitionSpec = {
                (slideInVertically(motion.fastSpatialSpec()) { it } + fadeIn(motion.fastEffects))
                    .togetherWith(slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffects))
            },
            label = "lazyButtonText"
        ) { on ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(if (on) R.drawable.ic_check else R.drawable.ic_auto_fix_high),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (on) "Deal. We'll set it up next." else "Let SaneLink handle it",
                    style = if (LocalCompactOnboarding.current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
    Text(
        text = "Uses Accessibility to notice copies. Turn it off anytime.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 8.dp)
    )
}

@Composable
private fun PrivacyPage() {
    PageTitle("Yours.\nOnly yours.")
    PageBody("Everything happens on your phone.")
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PrivacyItem(0, R.drawable.ic_shield, "No internet access", "SaneLink can't go online, by design.")
        PrivacyItem(1, R.drawable.ic_sentiment_calm, "No analytics, no ads", "Nothing is counted or sent anywhere.")
        PrivacyItem(2, R.drawable.ic_history, "History stays here", "Recent links never leave this phone.")
    }
}

@Composable
private fun PrivacyItem(index: Int, icon: Int, title: String, body: String) {
    ExpressiveSegmentedListItem(
        index = index,
        count = 3,
        leading = { ExpressiveLeadingBadge(icon = painterResource(icon)) },
        headline = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supporting = {
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

// ---------------------------------------------------------------------------------------------
// Artwork
// ---------------------------------------------------------------------------------------------

/**
 * The welcome story on loop: a normal-looking link, then its tracking parts light up, then
 * each one bursts apart where it stands and the link closes the gap, keeping the part that
 * matters (the timestamp). Pauses while its page isn't showing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkCleanDemo(active: Boolean) {
    val motion = LocalMotionScheme.current
    val scheme = MaterialTheme.colorScheme
    // 0 plain, 1 trackers lit, 2 first bursts, 3 first gone + second bursts, 4 second gone, 5 clean
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) {
            phase = 0
            return@LaunchedEffect
        }
        while (true) {
            phase = 0
            delay(1500)
            phase = 1
            delay(1100)
            phase = 2
            delay(BURST_MILLIS.toLong())
            phase = 3
            delay(BURST_MILLIS.toLong())
            phase = 4
            delay(450)
            phase = 5
            delay(2400)
        }
    }
    val border by animateColorAsState(
        targetValue = when (phase) {
            0 -> scheme.outlineVariant
            5 -> scheme.primary
            else -> scheme.error.copy(alpha = 0.7f)
        },
        animationSpec = motion.defaultColorEffects,
        label = "demoBorder"
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(2.dp, border),
        shadowElevation = 6.dp,
        modifier = Modifier.width(296.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Only the link's own height animates (calmly, no bounce), so the caption below
            // glides up as the second line empties instead of jumping
            FlowRow(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.animateContentSize(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
            ) {
                LinkPart("youtu.be/a1B2c3")
                LinkPart("?t=42")
                TrackerPart("&si=Xk29fQ", lit = phase >= 1, bursting = phase >= 2, visible = phase < 3)
                TrackerPart("&feature=share", lit = phase >= 1, bursting = phase >= 3, visible = phase < 4)
            }
            // Fixed height (the check's size), so the check appearing never pushes the caption down
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(28.dp)
            ) {
                val caption = when (phase) {
                    0 -> 0
                    5 -> 2
                    else -> 1
                }
                // Captions swap in the same spot: the new one rises in as the old one lifts away
                AnimatedContent(
                    targetState = caption,
                    transitionSpec = {
                        (slideInVertically(motion.fastSpatialSpec()) { it / 2 } + fadeIn(motion.defaultEffects))
                            .togetherWith(slideOutVertically(motion.fastSpatialSpec()) { -it / 2 } + fadeOut(motion.fastEffects))
                            .using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.CenterStart,
                    label = "demoCaption",
                    modifier = Modifier.weight(1f)
                ) { step ->
                    Text(
                        text = when (step) {
                            0 -> "Looks harmless, right?"
                            1 -> "2 trackers hiding in it"
                            else -> "Trackers gone, timestamp kept"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = when (step) {
                            0 -> scheme.onSurfaceVariant
                            1 -> scheme.error
                            else -> scheme.primary
                        }
                    )
                }
                AnimatedVisibility(
                    visible = phase == 5,
                    enter = scaleIn(spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow)) + fadeIn(motion.fastEffects),
                    exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffects)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private const val BURST_MILLIS = 460

@Composable
private fun LinkPart(text: String) {
    Text(
        text = text,
        fontFamily = GoogleSansCode,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/** A spark thrown out when a tracker bursts: where it starts, where it heads, how big it is. */
private class Spark(val startX: Float, val startY: Float, val dirX: Float, val dirY: Float, val reach: Float, val radius: Float)

/**
 * A tracking part of the demo link. Looks like the rest of the link until [lit], then gets a
 * red highlight. When [bursting] it breaks into sparks right where it is (no sliding away),
 * and once not [visible] it collapses so the link closes up.
 */
@Composable
private fun TrackerPart(text: String, lit: Boolean, bursting: Boolean, visible: Boolean) {
    val motion = LocalMotionScheme.current
    val scheme = MaterialTheme.colorScheme
    val highlight by animateColorAsState(
        targetValue = if (lit) scheme.errorContainer else scheme.errorContainer.copy(alpha = 0f),
        // Lights up smoothly; on the loop's reset it simply starts plain again
        animationSpec = if (lit) motion.defaultColorEffects else snap(),
        label = "trackerHighlight"
    )
    val textColor by animateColorAsState(
        targetValue = if (lit) scheme.onErrorContainer else scheme.onSurface,
        animationSpec = if (lit) motion.defaultColorEffects else snap(),
        label = "trackerText"
    )
    val burst = remember { Animatable(0f) }
    LaunchedEffect(bursting) {
        if (bursting) burst.animateTo(1f, tween(BURST_MILLIS, easing = FastOutSlowInEasing))
        else burst.snapTo(0f)
    }
    val sparks = remember(text) {
        val random = Random(text.hashCode())
        List(28) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            Spark(
                startX = random.nextFloat(),
                startY = 0.2f + random.nextFloat() * 0.6f,
                dirX = cos(angle),
                dirY = sin(angle),
                reach = 0.5f + random.nextFloat() * 0.9f,
                radius = 2f + random.nextFloat() * 3.5f
            )
        }
    }
    val sparkColors = listOf(scheme.error, scheme.errorContainer, scheme.tertiary)

    AnimatedVisibility(
        visible = visible,
        // Comes back at full size (growing sideways would re-wrap the link mid-animation)
        enter = fadeIn(motion.defaultEffects),
        exit = shrinkHorizontally(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
    ) {
        Box(
            modifier = Modifier.drawWithContent {
                val p = burst.value
                // Highlight and text swell a touch and vanish where they are
                val swell = 1f + 0.12f * p
                scale(swell, swell) {
                    drawRoundRect(
                        color = highlight.copy(alpha = highlight.alpha * (1f - p)),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
                if (p < 1f) {
                    drawContext.canvas.saveLayer(
                        Rect(Offset.Zero, size),
                        androidx.compose.ui.graphics.Paint().apply { alpha = (1f - p * 1.8f).coerceIn(0f, 1f) }
                    )
                    scale(swell, swell) { this@drawWithContent.drawContent() }
                    drawContext.canvas.restore()
                }
                // Sparks fly outward from where the text was
                if (p > 0f && p < 1f) {
                    val maxReach = 52.dp.toPx()
                    sparks.forEachIndexed { k, spark ->
                        val travel = maxReach * spark.reach * p
                        drawCircle(
                            color = sparkColors[k % sparkColors.size].copy(alpha = 1f - p),
                            radius = spark.radius.dp.toPx() * (1f - 0.5f * p),
                            center = Offset(
                                spark.startX * size.width + spark.dirX * travel,
                                spark.startY * size.height + spark.dirY * travel
                            )
                        )
                    }
                }
            }
        ) {
            Text(
                text = text,
                fontFamily = GoogleSansCode,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                softWrap = false,
                modifier = Modifier.padding(vertical = 2.dp, horizontal = 1.dp)
            )
        }
    }
}

/** The four ways in, as tiles that pop in one after another. */
@Composable
private fun WaysHero(active: Boolean) {
    val icons = listOf(R.drawable.ic_share, R.drawable.ic_text_fields, R.drawable.ic_cleaning_services, R.drawable.ic_content_copy)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (col in 0..1) {
                    val i = row * 2 + col
                    PopInTile(icon = icons[i], active = active, delayMillis = i * 90L)
                }
            }
        }
    }
}

@Composable
private fun PopInTile(icon: Int, active: Boolean, delayMillis: Long) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            scale.snapTo(0.5f)
            delay(delayMillis)
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
        }
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}

/** A very relaxed face, with z's drifting up. */
@Composable
private fun LazyHero() {
    val reducedMotion = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "zzz")
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sentiment_calm),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
        }
        if (!reducedMotion) {
            for (i in 0..2) {
                val t by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2400, delayMillis = 0, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = androidx.compose.animation.core.StartOffset(i * 800)
                    ),
                    label = "z$i"
                )
                Text(
                    text = "z",
                    fontFamily = TitleFont,
                    fontSize = (18 + i * 6).sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .offset(x = 72.dp + (t * 26).dp, y = (-40).dp - (t * 70).dp)
                        .graphicsLayer {
                            alpha = if (t < 0.2f) t / 0.2f else 1f - (t - 0.2f) / 0.8f
                            rotationZ = -12f + t * 10f
                        }
                )
            }
        }
    }
}

/** Shield that settles in with a bounce when its page is reached. */
@Composable
private fun PrivacyHero(active: Boolean) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (active) {
            scale.snapTo(0.7f)
            scale.animateTo(1f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessLow))
        }
    }
    Box(
        modifier = Modifier
            .size(116.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_shield),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(60.dp)
        )
    }
}

/**
 * Shape behind the artwork. Follows the pager position, so dragging between pages morphs
 * it smoothly from one Material shape to the next, and it slowly turns on its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingBlob(pagerState: PagerState, heroHeightPx: () -> Float) {
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = rememberReducedMotion()
    val shapes = remember {
        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.SoftBurst, MaterialShapes.Cookie12Sided)
    }
    val morphs = remember(shapes) { shapes.zipWithNext { a, b -> Morph(a, b) } }
    val colors = listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer, scheme.primaryContainer)

    val spin = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "blobSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 40_000, easing = LinearEasing)),
            label = "blobAngle"
        )
        angle
    }
    val path = remember { Path() }

    Box(
        modifier = Modifier
            // Sized and centered in the current artwork area (read at layout time, so swiping
            // between pages resizes it smoothly without recomposing)
            .layout { measurable, constraints ->
                val area = heroHeightPx()
                val side = kotlin.math.min(area * 0.92f, MAX_BLOB.toPx()).coerceAtLeast(0f).toInt()
                val placeable = measurable.measure(Constraints.fixed(side, side))
                layout(constraints.maxWidth, area.toInt().coerceAtLeast(0)) {
                    placeable.placeWithLayer((constraints.maxWidth - side) / 2, ((area - side) / 2).toInt()) {
                        // Too small to be a shape worth showing: fade it out
                        alpha = ((side.toDp() - 80.dp) / 40.dp).coerceIn(0f, 1f)
                    }
                }
            }
            .graphicsLayer { rotationZ = spin }
            .drawBehind {
                val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, (PAGE_COUNT - 1).toFloat())
                val index = floor(position).toInt().coerceAtMost(morphs.size - 1)
                val fraction = position - index
                val color = lerp(colors[index], colors[index + 1], fraction)
                path.reset()
                val cubics = morphs[index].asCubics(fraction)
                cubics.forEachIndexed { i, c ->
                    if (i == 0) path.moveTo(c.anchor0X * this.size.width, c.anchor0Y * this.size.height)
                    path.cubicTo(
                        c.control0X * this.size.width, c.control0Y * this.size.height,
                        c.control1X * this.size.width, c.control1Y * this.size.height,
                        c.anchor1X * this.size.width, c.anchor1Y * this.size.height
                    )
                }
                path.close()
                drawPath(path, color)
            }
    )
}

// ---------------------------------------------------------------------------------------------
// Bottom bar
// ---------------------------------------------------------------------------------------------

/**
 * Page dots on the left and the main button on the right. On the last page the dots step
 * aside and the button stretches into a full-width "Get started".
 */
@Composable
private fun OnboardingBottomBar(
    pagerState: PagerState,
    isLastPage: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalMotionScheme.current
    val background = MaterialTheme.colorScheme.background
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Content that scrolls under the bar fades out instead of colliding with it
            .background(Brush.verticalGradient(0f to background.copy(alpha = 0f), 0.35f to background))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 36.dp, bottom = 20.dp)
    ) {
        val buttonWidth by animateDpAsState(
            targetValue = if (isLastPage) maxWidth else 132.dp,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
            label = "nextWidth"
        )
        val dotsAlpha by animateFloatAsState(if (isLastPage) 0f else 1f, motion.fastEffects, label = "dotsAlpha")

        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer { alpha = dotsAlpha },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
            repeat(PAGE_COUNT) { i ->
                val closeness = (1f - abs(position - i)).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(8.dp + 20.dp * closeness)
                        .clip(CircleShape)
                        .background(
                            lerp(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.primary, closeness)
                        )
                )
            }
        }

        ExpressiveTactileButton(
            onClick = onNext,
            filled = true,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(buttonWidth)
        ) {
            AnimatedContent(
                targetState = isLastPage,
                transitionSpec = {
                    (slideInVertically(motion.fastSpatialSpec()) { it } + fadeIn(motion.fastEffects))
                        .togetherWith(slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffects))
                },
                label = "nextLabel"
            ) { last ->
                Text(
                    text = if (last) "Get started" else "Next",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
