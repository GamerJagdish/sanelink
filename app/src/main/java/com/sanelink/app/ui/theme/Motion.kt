package com.sanelink.app.ui.theme

import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.ContentTransform
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
) {
    // The tokens above are typed for Float. These give the same springs for any type
    // (Dp, IntOffset, IntSize, ...), e.g. animateDpAsState(animationSpec = fastSpatialSpec()).
    fun <T> fastSpatialSpec(): SpringSpec<T> = fastSpatial.retyped()
    fun <T> defaultSpatialSpec(): SpringSpec<T> = defaultSpatial.retyped()
    fun <T> fastEffectsSpec(): SpringSpec<T> = fastEffects.retyped()
    fun <T> defaultEffectsSpec(): SpringSpec<T> = defaultEffects.retyped()

    private fun <T> SpringSpec<Float>.retyped(): SpringSpec<T> = spring(dampingRatio, stiffness)
}

/**
 * CompositionLocal providing the current [SaneLinkMotionScheme].
 */
val LocalMotionScheme = staticCompositionLocalOf { SaneLinkMotionScheme() }

/**
 * Detects whether the user or system has enabled "Remove animations" / "Reduced Motion"
 * in Android Accessibility settings, as mandated by Material 3 Motion guidelines.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val animScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            val transScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            )
            animScale == 0f || transScale == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/**
 * Directional horizontal slide + fade for switching between tabs. [direction] is 1 when
 * moving forward (content enters from the right) and -1 when moving back. Fades only when
 * reduced motion is on.
 */
fun SaneLinkMotionScheme.tabSwitchTransition(direction: Int, reducedMotion: Boolean): ContentTransform =
    if (reducedMotion) {
        fadeIn(fastEffects) togetherWith fadeOut(fastEffects)
    } else {
        (slideInHorizontally(defaultSpatialSpec()) { width -> direction * (width / 4) } + fadeIn(defaultEffects))
            .togetherWith(slideOutHorizontally(defaultEffectsSpec()) { width -> -direction * (width / 4) } + fadeOut(fastEffects))
    }

/** Title swap in a top app bar: new title drops in from above, old one slides down and out. */
fun SaneLinkMotionScheme.titleSwapTransition(reducedMotion: Boolean): ContentTransform =
    if (reducedMotion) {
        fadeIn(fastEffects) togetherWith fadeOut(fastEffects)
    } else {
        (slideInVertically(fastSpatialSpec()) { -it / 2 } + fadeIn(defaultEffects))
            .togetherWith(slideOutVertically(defaultEffectsSpec()) { it / 2 } + fadeOut(fastEffects))
    }
