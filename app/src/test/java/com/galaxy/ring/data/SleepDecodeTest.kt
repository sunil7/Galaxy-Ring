package com.galaxy.ring.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SleepDecodeTest {

    /** Loads n1_sleep_page.hex, strips spaces, and returns the bytes after the 3-byte header (key 0x0505, keyFlag 0x10). */
    private fun loadN1Data(): ByteArray {
        val text = javaClass.classLoader!!.getResource("n1_sleep_page.hex")!!.readText()
        val hex = text.replace(" ", "").replace("\n", "").trim()
        val all = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        assertEquals("fixture must be 157 bytes", 157, all.size)
        return all.copyOfRange(3, all.size)
    }

    private fun minutesByCode(): Map<Int, Long> {
        val records = SleepRecordParser.parseRecords(loadN1Data())
        val segments = SleepRecordParser.segmentsFromRecords(records)
        val totals = HashMap<Int, Long>()
        for (s in segments) {
            totals[s.rawCode] = (totals[s.rawCode] ?: 0L) + s.durationMin
        }
        return totals
    }

    @Test
    fun n1_rawCodeTotalsMatchExpected() {
        val totals = minutesByCode()
        assertEquals(39L, totals[0x04])   // REM
        assertEquals(33L, totals[0x01])   // DEEP (raw code)
        assertEquals(82L, totals[0x02])   // LIGHT (raw code)
        assertEquals(10L, totals[0x11])   // AWAKE
        assertEquals(164L, totals.values.sum())
    }

    @Test
    fun testPartialNight_startAt0258_isPartial() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 58)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = start + 300L * 60_000L // 300 min (>= 240m), but startHour 2 < 4
        val session = SleepSession(startTime = start, endTime = end)
        assertTrue("02:58 start must be partial (startHour < 4)", session.isPartial)
    }

    @Test
    fun testPartialNight_startAt2358With493Minutes_isNotPartial() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 58)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = start + 493L * 60_000L // 493 min, startHour 23
        val session = SleepSession(startTime = start, endTime = end)
        // Spec: "The spec's CHECK says a session starting at 23:58 with 493 minutes is NOT partial.
        // Under the rule above, 23:58 is after 22:00, so it IS partial. The rule and the check contradict each other.
        // Implement the rule as written, write the tests exactly as the spec states them, and let the 23:58 case fail.
        // Do not change the rule silently to make the test pass. Stop and report the conflict so the spec owner can decide."
        assertFalse("23:58 start with 493m per spec CHECK is NOT partial", session.isPartial)
    }
}
