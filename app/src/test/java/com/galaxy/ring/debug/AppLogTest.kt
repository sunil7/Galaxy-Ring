package com.galaxy.ring.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppLogTest {

    @Before
    fun setup() {
        AppLog.clear()
        AppLog.lastTxHex = null
        AppLog.lastRxHex = null
        AppLog.lastError = null
        AppLog.lastMeasureResult = null
    }

    @Test
    fun testAppLogAppendsAndRetrievesEntries() {
        AppLog.d("GalaxyRingBLE", "Debug packet")
        AppLog.i("GalaxyRingSync", "Info telemetry")
        AppLog.w("GalaxyRingApp", "Warning state")
        AppLog.e("GalaxyRingBLE", "Error timeout", RuntimeException("Stack trace test"))

        val logs = AppLog.logsFlow.value
        assertEquals(4, logs.size)

        assertEquals("D", logs[0].level)
        assertEquals("GalaxyRingBLE", logs[0].tag)
        assertEquals("Debug packet", logs[0].message)

        assertEquals("I", logs[1].level)
        assertEquals("GalaxyRingSync", logs[1].tag)

        assertEquals("W", logs[2].level)

        assertEquals("E", logs[3].level)
        assertEquals("Error timeout", logs[3].message)
        assertNotNull(logs[3].throwableSnippet)
        assertTrue(logs[3].throwableSnippet!!.contains("RuntimeException"))

        val allText = AppLog.getAllLogsText()
        assertTrue(allText.contains("Debug packet"))
        assertTrue(allText.contains("Error timeout"))
    }

    @Test
    fun testAppLogBufferCapacity() {
        // AppLog capacity is 2000 entries
        for (i in 1..2050) {
            AppLog.d("TestTag", "Message $i")
        }

        val logs = AppLog.logsFlow.value
        assertEquals(2000, logs.size)
        // Earliest retained message should be Message 51
        assertTrue(logs.first().message.contains("Message 51"))
        assertTrue(logs.last().message.contains("Message 2050"))
    }

    @Test
    fun testClearRemovesAllEntries() {
        AppLog.i("Test", "Entry 1")
        AppLog.i("Test", "Entry 2")
        assertEquals(2, AppLog.logsFlow.value.size)

        AppLog.clear()
        assertEquals(0, AppLog.logsFlow.value.size)
        assertTrue(AppLog.getAllLogsText().isEmpty())
    }

    @Test
    fun testTxRxSnippetAndExportHeader() {
        AppLog.lastTxHex = "AB 02 02 24 55 AA"
        AppLog.lastRxHex = "BA 04 02 24 00 48"

        val snippet = AppLog.getLastTxRxSnippet()
        assertTrue(snippet.contains("Last TX: AB 02 02 24 55 AA"))
        assertTrue(snippet.contains("Last RX: BA 04 02 24 00 48"))

        val header = AppLog.generateExportHeader(
            appName = "Galaxy Ring",
            appVersion = "1.0",
            connectionState = "Ready",
            lastDevice = "Galaxy Ring (AA:BB:CC:DD:EE:FF)",
            gattSummary = "Service 0xA00A=true, Write 0xB002=true"
        )

        assertTrue(header.contains("GALAXY RING DIAGNOSTIC LOG EXPORT"))
        assertTrue(header.contains("Galaxy Ring v1.0"))
        assertTrue(header.contains("Connection State: Ready"))
        assertTrue(header.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(header.contains("AB 02 02 24 55 AA"))
    }
}
