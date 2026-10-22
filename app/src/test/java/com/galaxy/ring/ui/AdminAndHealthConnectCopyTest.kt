package com.galaxy.ring.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdminAndHealthConnectCopyTest {

    @Test
    fun check1_noOneTapClearOrDedupeInAdminScreen() {
        val sourceFile = File("src/main/java/com/galaxy/ring/ui/screens/AdminScreen.kt")
        val content = if (sourceFile.exists()) {
            sourceFile.readText()
        } else {
            File("app/src/main/java/com/galaxy/ring/ui/screens/AdminScreen.kt").readText()
        }

        // Verify that Deduplicate History and Clear Local History triggers open dialogs
        assertTrue("AdminScreen must have showDeduplicateDialog state", content.contains("var showDeduplicateDialog by remember"))
        assertTrue("AdminScreen must have showClearHistoryDialog state", content.contains("var showClearHistoryDialog by remember"))

        // Verify dialog text explains local database only and does not change Health Connect
        assertTrue(
            "Dialog must state it affects local database only and does not change Health Connect data",
            content.contains("affects this app's local database only and does NOT change Health Connect data") ||
            content.contains("local database only and does NOT change Health Connect data")
        )

        // Ensure both trigger buttons set dialog visibility to true
        assertTrue("Deduplicate button onClick must set showDeduplicateDialog", content.contains("onClick = { showDeduplicateDialog = true }"))
        assertTrue("Clear button onClick must set showClearHistoryDialog", content.contains("onClick = { showClearHistoryDialog = true }"))

        // Verify that AlertDialogs enclose the destructive/dedupe repository invocations
        assertTrue("Deduplicate AlertDialog must exist", content.contains("if (showDeduplicateDialog)"))
        assertTrue("Clear History AlertDialog must exist", content.contains("if (showClearHistoryDialog)"))
    }

    @Test
    fun check2_rationaleScreenDoesNotContainEncrypted() {
        val sourceFile = File("src/main/java/com/galaxy/ring/health/PermissionsRationaleActivity.kt")
        val content = if (sourceFile.exists()) {
            sourceFile.readText()
        } else {
            File("app/src/main/java/com/galaxy/ring/health/PermissionsRationaleActivity.kt").readText()
        }

        assertFalse("PermissionsRationaleActivity must not contain 'encrypted'", content.contains("encrypted", ignoreCase = true))
        assertFalse("PermissionsRationaleActivity must not contain 'never shared without your permission'", content.contains("never shared without your permission", ignoreCase = true))
        assertFalse("PermissionsRationaleActivity must not claim resting heart rate", content.contains("resting heart rate", ignoreCase = true))

        val expectedCopy = "Your health data is stored on this device. Health Connect controls what other apps can read."
        assertTrue("PermissionsRationaleActivity must contain exact replacement privacy text", content.contains(expectedCopy))
    }

    @Test
    fun healthConnectWriter_labelsDescribeSkinTemperature() {
        val sourceFile = File("src/main/java/com/galaxy/ring/health/HealthConnectWriter.kt")
        val content = if (sourceFile.exists()) {
            sourceFile.readText()
        } else {
            File("app/src/main/java/com/galaxy/ring/health/HealthConnectWriter.kt").readText()
        }

        assertFalse("HealthConnectWriter must not claim resting heart rate", content.contains("resting heart rate", ignoreCase = true))
        assertTrue("HealthConnectWriter must describe skin temperature", content.contains("skin temperature", ignoreCase = true))
    }
}
