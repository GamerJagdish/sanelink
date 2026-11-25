package com.sanelink.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.sanelink.app.R
import com.sanelink.app.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Size the platform draws a splash icon that has no icon background (Android 12+ spec). */
private val DefaultSplashIconSize = 288.dp

/** Viewport size of `ic_splash_logo.xml`. */
private const val LogoViewport = 108f

/** Thickness of the "i" stem left around the portal once it is fully open, in logo units. */
private const val PortalRim = 0.75f

/** How far the "i" dot hops up before the portal opens, in logo units. */
private const val DotHop = 2.2f

/**
 * The platform draws a background-less splash icon larger than its nominal 288dp box
 * (ratio observed on device). The overlay starts at this scale so the hand-off is seamless,
 * then settles back to the nominal size while the dot hops.
 */
private const val SystemSplashScale = 1.2f

private class PortalLogo(
    val letters: Path,
    val dot: Path,
    val stem: Path,
    val stemBounds: Rect
)

private fun ImageVector.findPath(name: String): VectorPath? {
    fun search(group: VectorGroup): VectorPath? {
        for (node: VectorNode in group) {
            when (node) {
                is VectorPath -> if (node.name == name) return node
                is VectorGroup -> search(node)?.let { return it }
            }
        }
        return null
    }
    return search(root)
}

private fun VectorPath.toComposePath(): Path = PathParser().addPathNodes(pathData).toPath()

/**
 * X-style "portal" launch animation.
 *
 * Picks up exactly where the system splash (`ic_splash_logo`) leaves off: the same solid
 * wordmark at the same position and size. Then:
 *  1. the "i" dot hops up and bounces back while the whole logo settles back a little,
 *  2. once the dot lands, a slit opens down the "i" stem and springs wide, revealing the app,
 *  3. the logo dips and zooms through the stem until the portal swallows the screen.
 *
 * @param visible whether the overlay should run (cold start only).
 * @param splashIconBounds where the system splash drew its icon, in window pixels, if known.
 *   Used for a pixel-exact hand-off; falls back to the platform default (288dp, centered).
 * @param onFinished called once the app is fully revealed.
 */
@Composable
fun PortalSplash(
    visible: Boolean,
    splashIconBounds: Rect?,
    onFinished: () -> Unit,
    content: @Composable () -> Unit
) {
    // 0 → 1: how far the portal in the stem has opened.
    val open = remember { Animatable(0f) }
    // Upward offset of the "i" dot, in logo units.
    val dotLift = remember { Animatable(0f) }
    // Logo scale relative to the nominal splash icon size; starts matching the system splash.
    val settle = remember { Animatable(if (visible) SystemSplashScale else 1f) }
    // Anticipation "dip" before the zoom.
    val dip = remember { Animatable(1f) }
    // 0 → 1: zoom through the portal. Starts at 1 when skipped so content is drawn at rest.
    val zoom = remember { Animatable(if (visible) 0f else 1f) }
    val overlayAlpha = remember { Animatable(1f) }
    val reducedMotion = rememberReducedMotion()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // App content settles back from a slight zoom as it comes through the portal.
                    val s = 1.08f - 0.08f * zoom.value
                    scaleX = s
                    scaleY = s
                }
        ) {
            content()
        }

        if (visible) {
            val vector = ImageVector.vectorResource(R.drawable.ic_splash_logo)
            val logo = remember(vector) {
                val stem = vector.findPath("i_stem")!!.toComposePath()
                PortalLogo(
                    letters = vector.findPath("letters")!!.toComposePath(),
                    dot = vector.findPath("i_dot")!!.toComposePath(),
                    stem = stem,
                    stemBounds = stem.getBounds()
                )
            }
            val background = colorResource(R.color.splash_bg)
            val foreground = colorResource(R.color.splash_fg)

            LaunchedEffect(Unit) {
                if (reducedMotion) {
                    zoom.snapTo(1f)
                    overlayAlpha.animateTo(
                        0f,
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                    onFinished()
                    return@LaunchedEffect
                }

                // 1: the dot hops and lands with a bounce while the logo settles back to its nominal size.
                launch {
                    settle.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                    )
                }
                dotLift.animateTo(
                    DotHop,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 2200f)
                )
                dotLift.animateTo(
                    0f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = 2200f,
                        visibilityThreshold = 0.05f
                    )
                )

                // 2: the portal springs open once the dot has landed.
                open.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = 0.01f
                    )
                )

                // 3: dip, then zoom through the stem.
                dip.animateTo(
                    0.9f,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                )
                launch {
                    dip.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
                    )
                }
                zoom.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 80f,
                        visibilityThreshold = 0.002f
                    )
                )
                onFinished()
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = overlayAlpha.value
                        // Offscreen so BlendMode.Clear punches a real hole down to the app content.
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                val iconSize = splashIconBounds?.width ?: DefaultSplashIconSize.toPx()
                val iconTopLeft = splashIconBounds?.topLeft
                    ?: Offset(center.x - iconSize / 2f, center.y - iconSize / 2f)
                val unit = iconSize / LogoViewport

                val stem = logo.stemBounds
                // Fully open portal, in logo units.
                val innerW = stem.width - 2 * PortalRim
                val innerH = stem.height - 2 * PortalRim
                // Scale at which the portal fully covers the screen (with margin for rounded corners).
                val endScale = max(size.width / (innerW * unit), size.height / (innerH * unit)) * 1.2f

                val t = zoom.value
                val scale = unit * settle.value * endScale.pow(t) * (dip.value + (1f - dip.value) * t)
                // Stem center drifts from its resting spot to the screen center while zooming.
                // (The logo settles around the icon center, so scale the stem's offset from it.)
                val iconCenter = iconTopLeft + Offset(iconSize / 2f, iconSize / 2f)
                val logoCenter = Offset(LogoViewport / 2f, LogoViewport / 2f)
                val restCenter = iconCenter + (stem.center - logoCenter) * (unit * settle.value)
                val focus = lerp(restCenter, center, t)

                // Portal grows from a thin slit to a capsule; allow a hint of spring overshoot.
                val o = open.value.coerceIn(0f, 1.06f)
                val holeW = innerW * o
                val holeH = innerH * (0.45f + 0.55f * o)
                // Pill-shaped while it is a slit, settling to a rounded rectangle that echoes the stem.
                val corner = (min(holeW, holeH) / 2f).coerceAtMost(0.7f)

                drawRect(background)
                withTransform({
                    translate(focus.x, focus.y)
                    scale(scale, scale, pivot = Offset.Zero)
                    translate(-stem.center.x, -stem.center.y)
                }) {
                    drawPath(logo.letters, foreground)
                    drawPath(logo.stem, foreground)
                    withTransform({ translate(top = -dotLift.value) }) {
                        drawPath(logo.dot, foreground)
                    }
                    if (holeW > 0f) {
                        drawRoundRect(
                            color = Color.Black,
                            topLeft = Offset(stem.center.x - holeW / 2f, stem.center.y - holeH / 2f),
                            size = Size(holeW, holeH),
                            cornerRadius = CornerRadius(corner),
                            blendMode = BlendMode.Clear
                        )
                    }
                }
            }
        }
    }
}
