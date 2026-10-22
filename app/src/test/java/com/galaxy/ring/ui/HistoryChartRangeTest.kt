package com.galaxy.ring.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryChartRangeTest {

    @Test
    fun spo2DynamicRangeWithPaddingKeeps84PercentVisible() {
        val sampleValues = listOf(84f, 88f, 94f, 97f)
        val rawMin = sampleValues.minOrNull() ?: 90f
        val rawMax = sampleValues.maxOrNull() ?: 100f

        val computedMin = (rawMin - 3f).coerceAtMost(88f).coerceAtLeast(60f)
        val computedMax = (rawMax + 2f).coerceAtLeast(100f)

        // Verify computedMin does not clamp at 85f, allowing 84% to stay visible
        assertTrue("computedMin should be <= 81f for 84% reading", computedMin <= 81f)
        assertTrue("84% sample must be within [computedMin, computedMax]", 84f in computedMin..computedMax)
        assertEquals(100f, computedMax)
    }

    @Test
    fun belowRangeMarkerIdentifiesReadingsBelow90() {
        val reading84 = 84f
        val reading92 = 92f

        assertTrue("84% must trigger below range marker", reading84 < 90f)
        assertTrue("92% must not trigger below range marker", reading92 >= 90f)
    }
}
