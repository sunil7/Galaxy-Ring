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
}
