package com.galaxy.ring.ui.theme

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
    fun typographyIsConfiguredForLargeNumberDashboard() {
        assertNotNull(Typography)
        assertNotNull(Typography.displayLarge)
        assertNotNull(Typography.displayMedium)
        assertNotNull(Typography.displaySmall)
        assertNotNull(Typography.labelMedium)
        assertEquals(30f, Typography.displaySmall.fontSize.value, 0.1f)
    }

    @Test
    fun shapesAndSpacingAreConfigured() {
        assertNotNull(Shapes)
        assertNotNull(Shapes.small)
        assertNotNull(Shapes.medium)
        assertNotNull(Shapes.large)

        assertEquals(12f, CornerRadiusSmall.value, 0.1f)
        assertEquals(16f, CornerRadiusMedium.value, 0.1f)
        assertEquals(24f, CornerRadiusLarge.value, 0.1f)

        assertNotNull(Spacing)
        assertEquals(4f, Spacing.extraSmall.value, 0.1f)
        assertEquals(8f, Spacing.small.value, 0.1f)
        assertEquals(12f, Spacing.medium.value, 0.1f)
        assertEquals(16f, Spacing.large.value, 0.1f)
        assertEquals(24f, Spacing.extraLarge.value, 0.1f)

        assertEquals(4f, Spacing.s4.value, 0.1f)
        assertEquals(8f, Spacing.s8.value, 0.1f)
        assertEquals(12f, Spacing.s12.value, 0.1f)
        assertEquals(16f, Spacing.s16.value, 0.1f)
        assertEquals(24f, Spacing.s24.value, 0.1f)
    }
}
