package com.sanelink.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape Tokens.
 *
 * Implements the official Material 3 Expressive corner radius scale:
 * - None (0dp): Full-bleed edge-to-edge surfaces.
 * - Extra Small (4dp): Connected inner button joints, compact indicators.
 * - Small (8dp): Badges, stripped tracker chips, parameter tags.
 * - Medium (12dp): Small cards, compact input fields.
 * - Large (16dp): Action buttons, text input fields, code preview containers.
 * - Large Increased (20dp): Provider cards, grouped segmented list containers.
 * - Extra Large (28dp): Dialogs, hero cards.
 * - Extra Large Increased (32dp): Modal bottom sheets, floating navigation bars.
 * - Extra Extra Large (48dp): Prominent expressive hero containers.
 * - Full (50% / Pill): Action buttons, toggles, icon circles.
 */
object ExpressiveShapeTokens {
    val None = 0.dp
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val LargeIncreased = 20.dp
    val ExtraLarge = 28.dp
    val ExtraLargeIncreased = 32.dp
    val ExtraExtraLarge = 48.dp

    val ShapeNone = RoundedCornerShape(None)
    val ShapeExtraSmall = RoundedCornerShape(ExtraSmall)
    val ShapeSmall = RoundedCornerShape(Small)
    val ShapeMedium = RoundedCornerShape(Medium)
    val ShapeLarge = RoundedCornerShape(Large)
    val ShapeLargeIncreased = RoundedCornerShape(LargeIncreased)
    val ShapeExtraLarge = RoundedCornerShape(ExtraLarge)
    val ShapeExtraLargeIncreased = RoundedCornerShape(ExtraLargeIncreased)
    val ShapeExtraExtraLarge = RoundedCornerShape(ExtraExtraLarge)
    val ShapeFull = CircleShape
}

/**
 * Material 3 Expressive Shapes palette applied to MaterialExpressiveTheme.
 */
val ExpressiveShapes = Shapes(
    extraSmall = ExpressiveShapeTokens.ShapeExtraSmall,
    small = ExpressiveShapeTokens.ShapeSmall,
    medium = ExpressiveShapeTokens.ShapeLarge,          // 16dp in M3 Expressive medium
    large = ExpressiveShapeTokens.ShapeLargeIncreased,   // 20dp in M3 Expressive large
    extraLarge = ExpressiveShapeTokens.ShapeExtraLarge   // 28dp in M3 Expressive extraLarge
)
