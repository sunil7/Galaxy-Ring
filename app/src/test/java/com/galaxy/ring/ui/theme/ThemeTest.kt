package com.galaxy.ring.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeTest {

    @Test
    fun colorConstantsAreConfigured() {
        assertNotNull(CyberCyan)
        assertNotNull(DarkBackground)
        assertNotNull(DarkSurface)
        assertNotNull(HeartRateRed)
        assertNotNull(Spo2Cyan)
        assertNotNull(SleepViolet)
        assertNotNull(ActivityGreen)
        assertNotNull(StressAmber)
        assertNotNull(HrvTeal)
    }

    @Test
    fun material3ColorSchemesAreConfigured() {
        // Dark scheme
        assertNotNull(GalaxyRingDarkColorScheme)
        assertEquals(PrimaryDark, GalaxyRingDarkColorScheme.primary)
        assertEquals(DarkSurface, GalaxyRingDarkColorScheme.surface)
        assertEquals(DarkSurfaceContainer, GalaxyRingDarkColorScheme.surfaceContainer)
        assertEquals(DarkOnSurface, GalaxyRingDarkColorScheme.onSurface)

        // Light scheme
        assertNotNull(GalaxyRingLightColorScheme)
        assertEquals(PrimaryLight, GalaxyRingLightColorScheme.primary)
        assertEquals(LightSurface, GalaxyRingLightColorScheme.surface)
        assertEquals(LightSurfaceContainer, GalaxyRingLightColorScheme.surfaceContainer)
        assertEquals(LightOnSurface, GalaxyRingLightColorScheme.onSurface)
    }

    @Test
    fun fullTypographyScaleIsConfiguredWithSystemFont() {
        assertNotNull(Typography)

        // Display
        assertNotNull(Typography.displayLarge)
        assertEquals(57f, Typography.displayLarge.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.displayLarge.fontFamily)

        assertNotNull(Typography.displayMedium)
        assertEquals(45f, Typography.displayMedium.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.displayMedium.fontFamily)

        assertNotNull(Typography.displaySmall)
        assertEquals(36f, Typography.displaySmall.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.displaySmall.fontFamily)

        // Headline
        assertNotNull(Typography.headlineLarge)
        assertEquals(32f, Typography.headlineLarge.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.headlineLarge.fontFamily)

        assertNotNull(Typography.headlineMedium)
        assertEquals(28f, Typography.headlineMedium.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.headlineMedium.fontFamily)

        assertNotNull(Typography.headlineSmall)
        assertEquals(24f, Typography.headlineSmall.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.headlineSmall.fontFamily)

        // Title
        assertNotNull(Typography.titleLarge)
        assertEquals(22f, Typography.titleLarge.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.titleLarge.fontFamily)

        assertNotNull(Typography.titleMedium)
        assertEquals(16f, Typography.titleMedium.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.titleMedium.fontFamily)

        assertNotNull(Typography.titleSmall)
        assertEquals(14f, Typography.titleSmall.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.titleSmall.fontFamily)

        // Body
        assertNotNull(Typography.bodyLarge)
        assertEquals(16f, Typography.bodyLarge.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.bodyLarge.fontFamily)

        assertNotNull(Typography.bodyMedium)
        assertEquals(14f, Typography.bodyMedium.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.bodyMedium.fontFamily)

        assertNotNull(Typography.bodySmall)
        assertEquals(12f, Typography.bodySmall.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.bodySmall.fontFamily)

        // Label
        assertNotNull(Typography.labelLarge)
        assertEquals(14f, Typography.labelLarge.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.labelLarge.fontFamily)

        assertNotNull(Typography.labelMedium)
        assertEquals(12f, Typography.labelMedium.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.labelMedium.fontFamily)

        assertNotNull(Typography.labelSmall)
        assertEquals(11f, Typography.labelSmall.fontSize.value, 0.1f)
        assertEquals(FontFamily.Default, Typography.labelSmall.fontFamily)
    }

    @Test
    fun shapesAndCornerSizesAreConfigured() {
        assertNotNull(Shapes)
        assertNotNull(Shapes.small)
        assertNotNull(Shapes.medium)
        assertNotNull(Shapes.large)

        assertEquals(12f, CornerSmall.value, 0.01f)
        assertEquals(16f, CornerMedium.value, 0.01f)
        assertEquals(24f, CornerLarge.value, 0.01f)
    }

    @Test
    fun spacingTokensAreConfigured() {
        assertNotNull(Spacing)
        assertEquals(4f, Spacing.xxs.value, 0.01f)
        assertEquals(8f, Spacing.xs.value, 0.01f)
        assertEquals(12f, Spacing.sm.value, 0.01f)
        assertEquals(16f, Spacing.md.value, 0.01f)
        assertEquals(24f, Spacing.lg.value, 0.01f)

        // Alternate aliases
        assertEquals(4f, Spacing.space4.value, 0.01f)
        assertEquals(8f, Spacing.space8.value, 0.01f)
        assertEquals(12f, Spacing.space12.value, 0.01f)
        assertEquals(16f, Spacing.space16.value, 0.01f)
        assertEquals(24f, Spacing.space24.value, 0.01f)
    }
}

