package com.galaxy.ring.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ActivityScreenTest {

    @Test
    fun check_noHardcodedColorsRemainInActivityScreen() {
        val sourceFile = File("src/main/java/com/galaxy/ring/ui/screens/ActivityScreen.kt")
        val content = if (sourceFile.exists()) {
            sourceFile.readText()
        } else {
            File("app/src/main/java/com/galaxy/ring/ui/screens/ActivityScreen.kt").readText()
        }

        assertFalse(
            "ActivityScreen.kt must not contain any 0xFF hard-coded hex colors",
            content.contains("0xFF")
        )
        assertFalse(
            "ActivityScreen.kt must not contain Color( constructor calls",
            content.contains("Color(")
        )
    }

    @Test
    fun check_existingWorkoutEmptyStateCopyPreserved() {
        val sourceFile = File("src/main/java/com/galaxy/ring/ui/screens/ActivityScreen.kt")
        val content = if (sourceFile.exists()) {
            sourceFile.readText()
        } else {
            File("app/src/main/java/com/galaxy/ring/ui/screens/ActivityScreen.kt").readText()
        }

        val expectedCopy = "No workouts on the ring for this day. Start a sport mode on the ring, then Sync."
        assertTrue(
            "ActivityScreen.kt must preserve exact workout empty state copy",
            content.contains(expectedCopy)
        )
        assertTrue(
            "ActivityScreen.kt must use MetricCard for steps summary",
            content.contains("MetricCard(")
        )
        assertTrue(
            "ActivityScreen.kt must use EmptyState component",
            content.contains("EmptyState(")
        )
    }
}
