package com.galaxy.ring.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard corner radius constants.
 */
val CornerRadiusSmall: Dp = 12.dp
val CornerRadiusMedium: Dp = 16.dp
val CornerRadiusLarge: Dp = 24.dp

val ShapeSmall: RoundedCornerShape = RoundedCornerShape(CornerRadiusSmall)
val ShapeMedium: RoundedCornerShape = RoundedCornerShape(CornerRadiusMedium)
val ShapeLarge: RoundedCornerShape = RoundedCornerShape(CornerRadiusLarge)

/**
 * Material 3 Shapes specification with 12, 16, and 24 dp corner sizes.
 */
val Shapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/**
 * Application spacing scale with 4, 8, 12, 16, and 24 dp tokens.
 */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp

    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val extraLarge: Dp = 24.dp

    // Numeric shorthand accessors
    val s4: Dp = 4.dp
    val s8: Dp = 8.dp
    val s12: Dp = 12.dp
    val s16: Dp = 16.dp
    val s24: Dp = 24.dp

    // Direct dp accessors
    val dp4: Dp = 4.dp
    val dp8: Dp = 8.dp
    val dp12: Dp = 12.dp
    val dp16: Dp = 16.dp
    val dp24: Dp = 24.dp
}
