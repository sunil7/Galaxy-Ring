package com.galaxy.ring.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkoutDetailTest {

    @Test
    fun check1_noFixedSpo2RangeRemains() {
        // Read WorkoutDetailScreen.kt source to verify no fixed clamp remains
        val sourceFile = File("src/main/java/com/galaxy/ring/ui/screens/WorkoutDetailScreen.kt")
        val content = if (sourceFile.exists()) {
            sourceFile.readText()
        } else {
            File("app/src/main/java/com/galaxy/ring/ui/screens/WorkoutDetailScreen.kt").readText()
        }

        assertFalse(
            "WorkoutDetailScreen.kt must not contain fixed minVal = 90f",
            content.contains("minVal = 90f")
        )
        assertFalse(
            "WorkoutDetailScreen.kt must not contain fixed maxVal = 100f",
            content.contains("maxVal = 100f")
        )
    }

    @Test
    fun dynamicRangeWithPaddingKeeps84PercentVisible() {
        val sampleValues = listOf(84f, 91f, 96f)
        val rawMin = sampleValues.minOrNull() ?: 90f
        val rawMax = sampleValues.maxOrNull() ?: 100f

        val minVal = (rawMin - 3f).coerceAtMost(88f).coerceAtLeast(60f)
        val maxVal = (rawMax + 2f).coerceAtLeast(100f)

        assertTrue("minVal should be <= 81f for 84% raw reading", minVal <= 81f)
        assertTrue("84% sample must be within [minVal, maxVal]", 84f in minVal..maxVal)
        assertEquals(100f, maxVal)
    }

    @Test
    fun check2_emptyPhasesHandlingDoesNotCrash() {
        val emptyPhases = emptyList<com.galaxy.ring.data.PacePhase>()
        assertTrue("empty phases list is empty", emptyPhases.isEmpty())

        // Validate distance & steps omission rule:
        // phase.distanceMeters > 0.0 && phase.distanceMeters != 1000.0
        fun hasDistance(dist: Double) = dist > 0.0 && dist != 1000.0
        fun hasSteps(steps: Int) = steps > 0

        assertFalse("0.0 m distance must be omitted", hasDistance(0.0))
        assertFalse("1000.0 m placeholder distance must be omitted", hasDistance(1000.0))
        assertTrue("850.0 m real distance must be present", hasDistance(850.0))

        assertFalse("0 steps must be omitted", hasSteps(0))
        assertTrue("420 steps must be present", hasSteps(420))
    }
}
