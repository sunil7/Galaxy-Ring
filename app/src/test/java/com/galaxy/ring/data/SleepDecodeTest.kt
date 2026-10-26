package com.galaxy.ring.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(33L, totals[0x01])   // DEEP
        assertEquals(82L, totals[0x02])   // LIGHT
        assertEquals(10L, totals[0x11])   // AWAKE
        assertEquals(164L, totals.values.sum())
    }

    @Test
    fun n1_stageMappingIsCorrect() {
        assertEquals(SleepStage.DEEP, SleepRecordParser.stageForCode(0x01))
        assertEquals(SleepStage.LIGHT, SleepRecordParser.stageForCode(0x02))
        assertEquals(SleepStage.REM, SleepRecordParser.stageForCode(0x04))
        assertEquals(SleepStage.AWAKE, SleepRecordParser.stageForCode(0x11))
    }

    @Test
    fun n1_stageTotalsByStageMatchExpected() {
        val records = SleepRecordParser.parseRecords(loadN1Data())
        val segments = SleepRecordParser.segmentsFromRecords(records)
        val byStage = segments.groupBy { it.stage }.mapValues { (_, v) -> v.sumOf { it.durationMin } }
        assertEquals(33L, byStage[SleepStage.DEEP])
        assertEquals(82L, byStage[SleepStage.LIGHT])
        assertEquals(39L, byStage[SleepStage.REM])
        assertEquals(10L, byStage[SleepStage.AWAKE])
    }
}
