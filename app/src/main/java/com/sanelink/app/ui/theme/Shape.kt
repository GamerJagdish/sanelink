package com.sanelink.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape Scale.
 *
 * Implements the expanded M3 Expressive geometric scale:
 * - Extra Small (4dp): Connected inner button joints, compact indicators.
 * - Small (8dp): Badges, stripped tracker chips, parameter tags.
 * - Medium (16dp): Action buttons, text input fields, code preview containers.
 * - Large (20dp): Provider cards, grouped segmented list containers.
 * - Extra Large (28dp - 32dp): Hero protection card, modal bottom sheets, alert dialogs.
 */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
