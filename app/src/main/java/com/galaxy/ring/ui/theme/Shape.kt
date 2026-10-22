package com.galaxy.ring.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// =============================================================================
// Material 3 Shapes — Corner Sizes (12 dp / 16 dp / 24 dp)
// =============================================================================

val CornerSmall: Dp = 12.dp
val CornerMedium: Dp = 16.dp
val CornerLarge: Dp = 24.dp

val ShapeSmall = RoundedCornerShape(CornerSmall)
val ShapeMedium = RoundedCornerShape(CornerMedium)
val ShapeLarge = RoundedCornerShape(CornerLarge)

/**
 * Material 3 Shapes specification for Galaxy Ring companion.
 * Follows calm, rounded card styling with 12 / 16 / 24 dp corner hierarchy.
 */
val Shapes = Shapes(
    small = ShapeSmall,   // 12 dp: Chips, small cards, tooltips, stat pills
    medium = ShapeMedium, // 16 dp: Standard metric cards, sheets, lists
    large = ShapeLarge    // 24 dp: Hero banners, container surfaces, dialogs
)

// =============================================================================
// Material 3 Spacing Hierarchy (4 dp / 8 dp / 12 dp / 16 dp / 24 dp)
// =============================================================================

/**
 * Standard Spacing tokens for consistent padding and margins across all screens.
 * Supports t-shirt sizes, semantic names, and direct dp access.
 */
object Spacing {
    // Standard scales
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp

    // Semantic scales
    val extraSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val extraLarge: Dp = 24.dp

    // Direct dp access
    val space4: Dp = 4.dp
    val space8: Dp = 8.dp
    val space12: Dp = 12.dp
    val space16: Dp = 16.dp
    val space24: Dp = 24.dp

    val dp4: Dp = 4.dp
    val dp8: Dp = 8.dp
    val dp12: Dp = 12.dp
    val dp16: Dp = 16.dp
    val dp24: Dp = 24.dp
}
