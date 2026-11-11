package com.sanelink.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.hazeSource
import com.sanelink.app.R
import com.sanelink.app.core.model.SanitizationStats
import com.sanelink.app.core.model.SanitizerResult
import com.sanelink.app.ui.theme.GoogleSansCode
import com.sanelink.app.ui.theme.LocalMotionScheme
import com.sanelink.app.ui.theme.googleSansFlex

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionScheme = LocalMotionScheme.current

    // Tactile squash-and-stretch on touch/drag: switch track vertically squishes and horizontally expands
    val switchScaleX by animateFloatAsState(
        targetValue = if (isPressed) 1.04f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "switchScaleX"
    )
    val switchScaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "switchScaleY"
    )

    // Dynamic rotation and scale for thumb icon
    val iconRotation by animateFloatAsState(
        targetValue = if (checked) 0f else -90f,
        animationSpec = motionScheme.fastSpatial,
        label = "switchThumbIconRot"
    )
    val iconPressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.18f else 1.0f,
        animationSpec = motionScheme.fastSpatial,
        label = "switchThumbIconScale"
    )

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        interactionSource = interactionSource,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    (scaleIn(
                        animationSpec = motionScheme.fastSpatial,
                        initialScale = 0.4f
                    ) + fadeIn(animationSpec = motionScheme.fastEffects))
                        .togetherWith(
                            scaleOut(
                                animationSpec = motionScheme.fastSpatial,
                                targetScale = 0.4f
                            ) + fadeOut(animationSpec = motionScheme.fastEffects)
                        )
                },
                label = "switchThumbMorph"
            ) { isChecked ->
                Icon(
                    painter = painterResource(if (isChecked) R.drawable.ic_check else R.drawable.ic_close),
                    contentDescription = null,
                    modifier = Modifier
                        .size(SwitchDefaults.IconSize)
                        .graphicsLayer {
                            rotationZ = iconRotation
                            scaleX = iconPressScale
                            scaleY = iconPressScale
                        }
                )
            }
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedIconColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = modifier.graphicsLayer {
            scaleX = switchScaleX
            scaleY = switchScaleY
        }
    )
}

@Composable
fun HeroProtectionCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val motionScheme = LocalMotionScheme.current

    val scale by animateFloatAsState(
        targetValue = if (isEnabled) 1.0f else 0.98f,
        animationSpec = motionScheme.defaultSpatial,
        label = "heroScale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = motionScheme.defaultColorEffects,
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = motionScheme.fastColorEffects,
        label = "contentColor"
    )

    Card(
        onClick = { onToggle(!isEnabled) },
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isEnabled,
                        transitionSpec = {
                            (scaleIn(animationSpec = motionScheme.fastSpatial) + fadeIn(animationSpec = motionScheme.fastEffects))
                                .togetherWith(scaleOut(animationSpec = motionScheme.fastSpatial) + fadeOut(animationSpec = motionScheme.fastEffects))
                        },
                        label = "heroIconTransition"
                    ) { enabled ->
                        Icon(
                            painter = painterResource(
                                if (enabled) R.drawable.ic_sentiment_calm else R.drawable.ic_sentiment_extremely_dissatisfied
                            ),
                            contentDescription = null,
                            tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (isEnabled) "Sane links on" else "Insane links on",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = if (isEnabled) "Your links will be sane now" else "Good luck with your insane links ig",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.75f)
                    )
                }
            }

            ExpressiveSwitch(
                checked = isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
fun ExpressiveStatsRow(
    stats: SanitizationStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StatCard(
            label = "Cleaned",
            value = stats.linksCleaned,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Trackers Removed",
            value = stats.trackersRemoved,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: Long,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionScheme = LocalMotionScheme.current

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "statCardScale"
    )

    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        interactionSource = interactionSource,
        onClick = {}
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    val offsetSpec = spring<androidx.compose.ui.unit.IntOffset>(
                        dampingRatio = motionScheme.fastSpatial.dampingRatio,
                        stiffness = motionScheme.fastSpatial.stiffness
                    )
                    if (targetState > initialState) {
                        (slideInVertically(animationSpec = offsetSpec) { -it } + fadeIn(animationSpec = motionScheme.fastEffects))
                            .togetherWith(slideOutVertically(animationSpec = offsetSpec) { it } + fadeOut(animationSpec = motionScheme.fastEffects))
                    } else {
                        (slideInVertically(animationSpec = offsetSpec) { it } + fadeIn(animationSpec = motionScheme.fastEffects))
                            .togetherWith(slideOutVertically(animationSpec = offsetSpec) { -it } + fadeOut(animationSpec = motionScheme.fastEffects))
                    }
                },
                label = "statCounter"
            ) { count ->
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpressiveSandboxCard(
    inputText: String,
    onInputChanged: (String) -> Unit,
    result: SanitizerResult?,
    onCopyClean: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Test & Clean",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (inputText.isNotEmpty()) {
                    IconButton(
                        onClick = { onInputChanged("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                placeholder = {
                    Text(
                        "Paste any link with tracking parameters...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansCode,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f),
                    unfocusedBorderColor = Color.Transparent
                )
            )

            val motionScheme = LocalMotionScheme.current
            val copyInteractionSource = remember { MutableInteractionSource() }
            val isCopyPressed by copyInteractionSource.collectIsPressedAsState()
            val copyScale by animateFloatAsState(
                targetValue = if (isCopyPressed) 0.96f else 1f,
                animationSpec = motionScheme.fastSpatial,
                label = "copyCleanScale"
            )

            AnimatedVisibility(
                visible = result != null && result.wasModified,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                        stiffness = motionScheme.defaultSpatial.stiffness
                    )
                ) + fadeIn(animationSpec = motionScheme.fastEffects),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = motionScheme.defaultSpatial.dampingRatio,
                        stiffness = motionScheme.defaultSpatial.stiffness
                    )
                ) + fadeOut(animationSpec = motionScheme.fastEffects)
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Removed parameters badges
                    if (result != null && result.removedParameters.isNotEmpty()) {
                        Text(
                            text = "STRIPPED TRACKERS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            result.removedParameters.forEach { param ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = param,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = GoogleSansCode,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Clean URL result box
                    if (result != null) {
                        Text(
                            text = "SANITIZED URL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Text(
                                text = result.cleanText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = GoogleSansCode,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ElevatedButton(
                            onClick = { onCopyClean(result.cleanText) },
                            interactionSource = copyInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    scaleX = copyScale
                                    scaleY = copyScale
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_content_copy),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Copy Clean URL",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive Connected Button Group item model.
 */
data class ExpressiveConnectedButtonItem(
    val label: String,
    val count: Int? = null,
    val icon: Painter? = null
)

/**
 * Material 3 Expressive Connected Button Group.
 *
 * Implements the official Material 3 Expressive specification replacing legacy Segmented Buttons.
 * Connected buttons feature asymmetric rounded outer corners and straight inner edges when unselected,
 * and dynamically morph into a standout, fully rounded pill shape when selected with spring animations.
 */
@Composable
fun ExpressiveConnectedButtonGroup(
    items: List<ExpressiveConnectedButtonItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = items.size
    val interactionSources = remember(totalCount) {
        List(totalCount) { MutableInteractionSource() }
    }
    val pressedStates = interactionSources.map { source ->
        source.collectIsPressedAsState().value
    }
    val anyPressedIndex = pressedStates.indexOfFirst { it }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedIndex == index
            val interactionSource = interactionSources[index]
            val isPressed = pressedStates[index]

            // Dynamic M3 Expressive Spring Width Physics with real-time neighbor compression on touch
            val pressDelta = when {
                anyPressedIndex == index -> 0.12f * (totalCount - 1)
                anyPressedIndex != -1 -> -0.12f
                else -> 0f
            }
            val baseWeight = if (isSelected) {
                1f + (0.15f * (totalCount - 1))
            } else {
                1f - 0.15f
            }
            val motionScheme = LocalMotionScheme.current
            val animatedWeight by animateFloatAsState(
                targetValue = baseWeight + pressDelta,
                animationSpec = motionScheme.fastSpatial,
                label = "btnGroupWeight_$index"
            )

            // Tactile press scale feedback (squish & bounce)
            val pressScale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1f,
                animationSpec = motionScheme.fastSpatial,
                label = "btnGroupPressScale_$index"
            )

            val animatedBgColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                animationSpec = motionScheme.defaultColorEffects,
                label = "btnGroupBg_$index"
            )

            val contentColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = motionScheme.fastColorEffects,
                label = "btnGroupContent_$index"
            )

            // Shape morphing: transforms to full pill on selection OR during touch press!
            val fullRadius = 24.dp
            val innerRadius = 6.dp

            val startRadius by animateDpAsState(
                targetValue = when {
                    isSelected || isPressed -> fullRadius
                    index == 0 -> fullRadius // Leading outer left is rounded
                    else -> innerRadius // Non-leading left is connected
                },
                animationSpec = spring(
                    dampingRatio = motionScheme.fastSpatial.dampingRatio,
                    stiffness = motionScheme.fastSpatial.stiffness
                ),
                label = "startRadius_$index"
            )

            val endRadius by animateDpAsState(
                targetValue = when {
                    isSelected || isPressed -> fullRadius
                    index == totalCount - 1 -> fullRadius // Trailing outer right is rounded
                    else -> innerRadius // Non-trailing right is connected
                },
                animationSpec = spring(
                    dampingRatio = motionScheme.fastSpatial.dampingRatio,
                    stiffness = motionScheme.fastSpatial.stiffness
                ),
                label = "endRadius_$index"
            )

            val shape = RoundedCornerShape(
                topStart = startRadius,
                bottomStart = startRadius,
                topEnd = endRadius,
                bottomEnd = endRadius
            )

            Box(
                modifier = Modifier
                    .weight(animatedWeight)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(shape)
                    .background(animatedBgColor)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple()
                    ) {
                        onSelect(index)
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (item.icon != null) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = contentColor
                    )
                    if (item.count != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val badgeScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "badgeScale_$index"
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = badgeScale
                                    scaleY = badgeScale
                                }
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${item.count}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@JvmName("ExpressiveConnectedButtonGroupFromStrings")
@Composable
fun ExpressiveConnectedButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveConnectedButtonGroup(
        items = options.map { ExpressiveConnectedButtonItem(label = it) },
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        modifier = modifier
    )
}

/**
 * Material 3 Expressive Search Bar.
 *
 * Implements the official Material 3 Search Bar specification:
 * - 56dp height and 28dp pill shape (CornerFull)
 * - surfaceContainerHigh background without boundary stroke
 * - 24dp leading search icon
 * - bodyLarge typography for query and hint text
 * - Animated trailing clear icon button
 */
@Composable
fun ExpressiveSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search rules...",
    onClear: () -> Unit = { onQueryChange("") }
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(onClick = onClear) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(28.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    )
}

/**
 * Material 3 Expressive Connected Icon Button Group.
 *
 * Implements the Material 3 Expressive Icon Button Group specification with connected outer-rounded /
 * inner-flat geometry, real-time neighbor compression, tactile press scale feedback, and spring motion physics.
 */
@Composable
fun ExpressiveConnectedIconButtonGroup(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editInteractionSource = remember { MutableInteractionSource() }
    val isEditPressed by editInteractionSource.collectIsPressedAsState()

    val deleteInteractionSource = remember { MutableInteractionSource() }
    val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()

    val motionScheme = LocalMotionScheme.current

    // Real-time neighbor width compression with spring physics
    val editWidth by animateDpAsState(
        targetValue = when {
            isEditPressed -> 44.dp
            isDeletePressed -> 34.dp
            else -> 38.dp
        },
        animationSpec = spring(
            dampingRatio = motionScheme.fastSpatial.dampingRatio,
            stiffness = motionScheme.fastSpatial.stiffness
        ),
        label = "editWidth"
    )

    val deleteWidth by animateDpAsState(
        targetValue = when {
            isDeletePressed -> 44.dp
            isEditPressed -> 34.dp
            else -> 38.dp
        },
        animationSpec = spring(
            dampingRatio = motionScheme.fastSpatial.dampingRatio,
            stiffness = motionScheme.fastSpatial.stiffness
        ),
        label = "deleteWidth"
    )

    // Tactile press scale (squish & bounce)
    val editPressScale by animateFloatAsState(
        targetValue = if (isEditPressed) 0.94f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "editPressScale"
    )

    val deletePressScale by animateFloatAsState(
        targetValue = if (isDeletePressed) 0.94f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "deletePressScale"
    )

    // Shapes: 18dp outer, 4dp connected inner
    val editShape = RoundedCornerShape(
        topStart = 18.dp,
        bottomStart = 18.dp,
        topEnd = 4.dp,
        bottomEnd = 4.dp
    )
    val deleteShape = RoundedCornerShape(
        topStart = 4.dp,
        bottomStart = 4.dp,
        topEnd = 18.dp,
        bottomEnd = 18.dp
    )

    Row(
        modifier = modifier.height(38.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Edit Action
        Box(
            modifier = Modifier
                .width(editWidth)
                .height(38.dp)
                .graphicsLayer {
                    scaleX = editPressScale
                    scaleY = editPressScale
                }
                .clip(editShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(
                    interactionSource = editInteractionSource,
                    indication = ripple()
                ) {
                    onEdit()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = "Edit rule",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        // Delete Action
        Box(
            modifier = Modifier
                .width(deleteWidth)
                .height(38.dp)
                .graphicsLayer {
                    scaleX = deletePressScale
                    scaleY = deletePressScale
                }
                .clip(deleteShape)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                .clickable(
                    interactionSource = deleteInteractionSource,
                    indication = ripple(color = MaterialTheme.colorScheme.error)
                ) {
                    onDelete()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = "Delete rule",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * CompositionLocal providing access to the screen HazeState for hardware-accelerated frosted blur effects.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Material 3 Expressive Floating Action and Primary Pill Button
 *
 * Implements Material 3 Expressive specifications, spring physics, and frosted blur matching the navbar:
 * - Frosted blur glassmorphism via HazeBlurStyle.Material3 with translucent dynamic Monet primary color
 * - Dynamic shape morphing from resting full pill (26dp) into subtle squircle (18dp) on touch
 * - Bouncy spring scale squash and recovery (0.96f scale on press)
 * - Instantaneous touch-down tracking using interactionSource and collectIsPressedAsState
 * - High-emphasis Monet styling with optional leading icon and bold typography
 */
@Composable
fun ExpressiveFloatingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.80f),
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    hazeState: HazeState? = LocalHazeState.current,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionScheme = LocalMotionScheme.current

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedDragOffset by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = motionScheme.fastSpatial,
        label = "floatingBtnDragOffset"
    )

    // Tactile squish feedback: on press/drag, button squishes vertically and slightly expands horizontally
    val pressScaleX by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "floatingBtnScaleX"
    )
    val pressScaleY by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "floatingBtnScaleY"
    )

    // Dynamic M3 Expressive shape morphing: from smooth pill (26dp) to tactile squircle (14dp) on touch
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 14.dp else 26.dp,
        animationSpec = spring(
            dampingRatio = motionScheme.fastSpatial.dampingRatio,
            stiffness = motionScheme.fastSpatial.stiffness
        ),
        label = "floatingBtnCornerRadius"
    )

    val shape = RoundedCornerShape(cornerRadius)
    val blurStyle = HazeBlurStyle.Material3()

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = border,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        interactionSource = interactionSource,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScaleX
                scaleY = pressScaleY
                translationX = animatedDragOffset
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startX = down.position.x
                    try {
                        do {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val current = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (current.pressed) {
                                val rawDelta = current.position.x - startX
                                dragOffsetX = (rawDelta * 0.35f).coerceIn(-48f, 48f)
                            }
                        } while (event.changes.any { it.id == down.id && it.pressed })
                    } finally {
                        dragOffsetX = 0f
                    }
                }
            }
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeBlur(
                        input = HazeInput.Sources(hazeState),
                        style = blurStyle,
                        performanceMode = HazePerformanceMode.Performance
                    )
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
            }
        }
    }
}


/**
 * Material 3 Expressive Leading Badge for list items.
 *
 * Standardized 42dp rounded squircle container for icons or initials/monograms.
 */
@Composable
fun ExpressiveLeadingBadge(
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    monogram: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        } else if (monogram != null) {
            Text(
                text = monogram.take(2).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

/**
 * Material 3 Expressive Segmented List Item.
 *
 * Implements M3 Expressive Segmented List specifications:
 * - Position-aware connected geometry: rounded outer corners with connected 4dp inner corners
 * - Tactile spring scale squash (0.98f) and corner morphing on touch press
 * - Standardized slot architecture: leading, headline, supporting, and trailing slots
 * - Accessible touch targets with min height constraints (56dp single-line, 72dp two-line)
 */
@Composable
fun ExpressiveSegmentedListItem(
    headline: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supporting: @Composable (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    index: Int = 0,
    count: Int = 1,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val motionScheme = LocalMotionScheme.current

    // Tactile press scale feedback with M3 Expressive bouncy spring
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.98f else 1f,
        animationSpec = motionScheme.fastSpatial,
        label = "segmentedItemScale"
    )

    // Dynamic M3 Expressive shape morphing: inner connected corners flex on touch
    val innerRadius by animateDpAsState(
        targetValue = if (isPressed && onClick != null) 10.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = motionScheme.fastSpatial.dampingRatio,
            stiffness = motionScheme.fastSpatial.stiffness
        ),
        label = "segmentedItemInnerRadius"
    )

    val outerRadius = 24.dp

    val shape = when {
        count <= 1 -> RoundedCornerShape(outerRadius)
        index == 0 -> RoundedCornerShape(
            topStart = outerRadius,
            topEnd = outerRadius,
            bottomStart = innerRadius,
            bottomEnd = innerRadius
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = innerRadius,
            topEnd = innerRadius,
            bottomStart = outerRadius,
            bottomEnd = outerRadius
        )
        else -> RoundedCornerShape(innerRadius)
    }

    val itemContent = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (supporting != null) 72.dp else 56.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(modifier = Modifier.width(14.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (trailing != null) 12.dp else 0.dp),
                verticalArrangement = Arrangement.Center
            ) {
                headline()
                if (supporting != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    supporting()
                }
            }

            if (trailing != null) {
                trailing()
            }
        }
    }

    if (onClick != null) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            interactionSource = interactionSource,
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
        ) {
            itemContent()
        }
    } else {
        Surface(
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = modifier.fillMaxWidth()
        ) {
            itemContent()
        }
    }
}

/**
 * Auto-fitting title text using Google Sans Flex variable font.
 * Automatically detects when the title text overflows the available width (e.g. on high-DPI screens
 * or larger font scales) and dynamically scales down the font size and width axis so that the title
 * fits completely on a single line without being cut off.
 */
@Composable
fun AutoFitTitleText(
    text: String,
    modifier: Modifier = Modifier,
    targetFontSize: TextUnit = 24.sp,
    minFontSize: TextUnit = 13.sp,
    targetWeight: Int = 1000,
    targetWidth: Float = 151f,
    minWidth: Float = 60f,
    roundness: Float = 10f,
    letterSpacing: TextUnit = 0.5.sp,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Center
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val maxWidthPx = if (constraints.hasBoundedWidth && constraints.maxWidth > 0) {
            constraints.maxWidth
        } else {
            with(density) { (LocalConfiguration.current.screenWidthDp.dp - 32.dp).roundToPx() }
        }
        val textMeasurer = rememberTextMeasurer()

        val (bestSize, bestWidth) = remember(
            text,
            maxWidthPx,
            targetFontSize,
            minFontSize,
            targetWeight,
            targetWidth,
            minWidth,
            roundness,
            density
        ) {
            if (text.isEmpty() || maxWidthPx <= 0) {
                targetFontSize to targetWidth
            } else {
                fun measureText(fontSizeSp: Float, fontWidth: Float): Int {
                    val style = TextStyle(
                        fontFamily = googleSansFlex(
                            weight = targetWeight,
                            width = fontWidth,
                            roundness = roundness,
                            opticalSize = fontSizeSp
                        ),
                        fontSize = fontSizeSp.sp,
                        fontWeight = FontWeight(targetWeight),
                        letterSpacing = letterSpacing
                    )
                    return textMeasurer.measure(
                        text = text,
                        style = style,
                        maxLines = 1,
                        softWrap = false
                    ).size.width
                }

                val initialWidth = measureText(targetFontSize.value, targetWidth)
                if (initialWidth <= maxWidthPx) {
                    targetFontSize to targetWidth
                } else {
                    // Overflow detected! Scale font size proportionally with a safe buffer
                    val scaleFactor = (maxWidthPx.toFloat() / initialWidth.toFloat()) * 0.98f
                    var candidateSp = (targetFontSize.value * scaleFactor).coerceAtLeast(minFontSize.value)
                    var candidateWidth = targetWidth
                    var currentWidth = measureText(candidateSp, candidateWidth)

                    // If minFontSize is reached and text still overflows, scale down the font's wdth axis
                    if (currentWidth > maxWidthPx && candidateSp <= minFontSize.value) {
                        val widthScale = (maxWidthPx.toFloat() / currentWidth.toFloat()) * 0.98f
                        candidateWidth = (targetWidth * widthScale).coerceIn(minWidth, targetWidth)
                        currentWidth = measureText(candidateSp, candidateWidth)
                    }

                    // Fine-tuning pass to guarantee text fits strictly within maxWidthPx
                    while (currentWidth > maxWidthPx && (candidateSp > minFontSize.value || candidateWidth > minWidth)) {
                        if (candidateSp > minFontSize.value) {
                            candidateSp = (candidateSp - 0.5f).coerceAtLeast(minFontSize.value)
                        } else {
                            candidateWidth = (candidateWidth - 5f).coerceAtLeast(minWidth)
                        }
                        currentWidth = measureText(candidateSp, candidateWidth)
                    }

                    candidateSp.sp to candidateWidth
                }
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = googleSansFlex(
                    weight = targetWeight,
                    width = bestWidth,
                    roundness = roundness,
                    opticalSize = bestSize.value
                ),
                fontSize = bestSize,
                fontWeight = FontWeight(targetWeight),
                letterSpacing = letterSpacing,
                color = color
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
    }
}
