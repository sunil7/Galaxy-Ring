package com.galaxy.ring.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsScreenTest {

    @Test
    fun check_colorHexCountReducedToZero() {
        val sourceFile = File("src/main/java/com/galaxy/ring/ui/screens/SettingsScreen.kt")
        val content = if (sourceFile.exists()) {
            sourceFile.readText()
        } else {
            File("app/src/main/java/com/galaxy/ring/ui/screens/SettingsScreen.kt").readText()
        }

        val occurrences0xFF = content.split("0xFF").size - 1
        assertEquals("Color(0xFF) count in SettingsScreen.kt must be zero", 0, occurrences0xFF)

        val occurrencesColorCall = content.split("Color(").size - 1
        assertEquals("Color( calls in SettingsScreen.kt must be zero", 0, occurrencesColorCall)
    }

    @Test
    fun check_adminEntryOnlyUnderMore() {
        val sourceFile = File("src/main/java/com/galaxy/ring/ui/screens/SettingsScreen.kt")
        val content = if (sourceFile.exists()) {
            sourceFile.readText()
        } else {
            File("app/src/main/java/com/galaxy/ring/ui/screens/SettingsScreen.kt").readText()
        }

        assertTrue("SettingsScreen must contain More section", content.contains("SectionHeader(\n                title = \"More\"") || content.contains("title = \"More\""))
        assertTrue("Admin button must be present in SettingsScreen", content.contains("testTag(\"open_admin_panel_button\")"))

        // Ensure top Admin card is gone
        assertFalse("Top admin debug card must be removed", content.contains("admin_debug_top_card"))
    }

    @Test
    fun check_sectionHeadersUsedForGrouping() {
        val sourceFile = File("src/main/java/com/galaxy/ring/ui/screens/SettingsScreen.kt")
        val content = if (sourceFile.exists()) {
            sourceFile.readText()
        } else {
            File("app/src/main/java/com/galaxy/ring/ui/screens/SettingsScreen.kt").readText()
        }

        assertTrue("Must use SectionHeader component", content.contains("SectionHeader("))
        assertTrue("Must group Connection & Pairing", content.contains("title = \"Connection & Pairing\""))
        assertTrue("Must group Health Monitoring", content.contains("title = \"Health Monitoring\""))
        assertTrue("Must group Health Connect", content.contains("title = \"Health Connect\""))
    }
}
