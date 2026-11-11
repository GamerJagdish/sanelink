package com.sanelink.app.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material 3 Expressive Motion Scheme.
 *
 * Implements the Material 3 Expressive Motion Physics System:
 * - Spatial Springs: Animate physical geometric changes (position, scale, size, corner radius).
 *   Deliberately allow overshoot and bounce (dampingRatio < 1.0f) for a tactile, energetic feel.
 * - Effects Springs: Animate non-geometric style properties (color, alpha, elevation tint).
 *   Enforce critical damping (dampingRatio = 1.0f) so visual effects never oscillate or bounce.
 */
@Immutable
data class SaneLinkMotionScheme(
    // Spatial Springs (Geometric movement, scale, corner radius, shape morphing)
    val fastSpatial: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    ),
    val defaultSpatial: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    ),
    val slowSpatial: SpringSpec<Float> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessVeryLow
    ),

    // Effects Springs (Alpha fades, state layers)
    val fastEffects: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    val defaultEffects: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    ),

    // Color Effects Springs (Critically damped color transitions)
    val fastColorEffects: SpringSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    val defaultColorEffects: SpringSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
)

/**
 * CompositionLocal providing the current [SaneLinkMotionScheme].
 */
val LocalMotionScheme = staticCompositionLocalOf { SaneLinkMotionScheme() }
