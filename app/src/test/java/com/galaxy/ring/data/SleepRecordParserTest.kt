package com.galaxy.ring.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SleepRecordParserTest {

    @Before
    fun setUp() {
        SleepPageStore.clear()
    }

    private fun deviceSeconds(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }
        return cal.timeInMillis / 1000L - 946_684_800L
    }

    private fun localMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun record(sec: Long, code: Int): ByteArray = byteArrayOf(
        (sec shr 24).toByte(), (sec shr 16).toByte(), (sec shr 8).toByte(), sec.toByte(),
        code.toByte(), 0x00, 0x00
    )

    private fun overnightBytes(): ByteArray =
        record(deviceSeconds(2026, 10, 7, 23, 35), 0x01) +
            record(deviceSeconds(2026, 10, 7, 23, 48), 0x02) +
            record(deviceSeconds(2026, 10, 8, 0, 5), 0x01) +
            record(deviceSeconds(2026, 10, 8, 1, 0), 0x04) +
            record(deviceSeconds(2026, 10, 8, 5, 33), 0x00)

    @Test
    fun overnightSessionUsesRealTimestamps() {
        val sessions = SleepRecordParser.buildSessions(SleepRecordParser.parseRecords(overnightBytes()))
        assertEquals(1, sessions.size)
        val s = sessions[0]
        assertEquals(localMillis(2026, 10, 7, 23, 35), s.startTime)
        assertEquals(localMillis(2026, 10, 8, 5, 33), s.endTime)
        assertEquals(358L, s.durationMinutes) // 5h58m
        assertEquals(4, s.stages.size)
    }

    @Test
    fun segmentDurationsAndStageMapping() {
        val records = SleepRecordParser.parseRecords(overnightBytes())
        val segs = SleepRecordParser.segmentsFromRecords(records)
        assertEquals(listOf(13L, 17L, 55L, 273L), segs.map { it.durationMin })
        assertEquals(
            listOf(SleepStage.LIGHT, SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM),
            segs.map { it.stage }
        )
        assertEquals(0x02, segs[1].rawCode)
        assertEquals(358L, segs.sumOf { it.durationMin })
    }

    @Test
    fun awakeCodesMapToAwake() {
        assertEquals(SleepStage.AWAKE, SleepRecordParser.stageForCode(0x00))
        assertEquals(SleepStage.AWAKE, SleepRecordParser.stageForCode(0x11))
        assertEquals(SleepStage.AWAKE, SleepRecordParser.stageForCode(0x22))
        assertTrue(SleepRecordParser.isKnownCode(0x22))
        assertFalse(SleepRecordParser.isKnownCode(0x7F))
    }

    @Test
    fun pagesMergeIntoOneNight() {
        val all = SleepRecordParser.parseRecords(overnightBytes())
        SleepPageStore.addPage(all.take(2))
        SleepPageStore.addPage(all.drop(2))
        SleepPageStore.addPage(all.drop(2)) // duplicate page must be harmless
        val sessions = SleepRecordParser.buildSessions(SleepPageStore.snapshot())
        assertEquals(1, sessions.size)
        assertEquals(358L, sessions[0].durationMinutes)
        assertEquals(4, sessions[0].stages.size)
    }

    @Test
    fun gapOverFourHoursSplitsNights() {
        val bytes = overnightBytes() +
            record(deviceSeconds(2026, 10, 9, 23, 10), 0x01) +
            record(deviceSeconds(2026, 10, 10, 6, 0), 0x00)
        val sessions = SleepRecordParser.buildSessions(SleepRecordParser.parseRecords(bytes))
        assertEquals(2, sessions.size)
        assertEquals(358L, sessions[0].durationMinutes)
        assertEquals(410L, sessions[1].durationMinutes)
    }

    @Test
    fun implausibleTimestampsAreIgnored() {
        val bytes = record(0L, 0x01) + record(12345L, 0x02)
        assertTrue(SleepRecordParser.parseRecords(bytes).isEmpty())
    }

    @Test
    fun midnightLabelOnlyWhenRangeCrossesMidnight() {
        assertTrue(
            SleepRecordParser.crossesLocalMidnight(
                localMillis(2026, 10, 7, 23, 35), localMillis(2026, 10, 8, 5, 33)
            )
        )
        assertFalse(
            SleepRecordParser.crossesLocalMidnight(
                localMillis(2026, 10, 8, 1, 0), localMillis(2026, 10, 8, 7, 0)
            )
        )
    }

    @Test
    fun completedWhenEndedMoreThanTwoMinutesAgo() {
        val now = 1_000_000_000_000L
        val ended = SleepSession(startTime = now - 8 * 3_600_000L, endTime = now - 3 * 60_000L)
        val live = SleepSession(startTime = now - 8 * 3_600_000L, endTime = now - 60_000L)
        assertTrue(ended.isCompleted(now))
        assertFalse(live.isCompleted(now))
    }

    @Test
    fun stagesRoundTripThroughStorageString() {
        val session = SleepRecordParser.buildSessions(SleepRecordParser.parseRecords(overnightBytes())).first()
        val encoded = SleepRecordParser.encodeStages(session.stages)
        val decoded = SleepRecordParser.decodeSegments(encoded)
        assertEquals(session.stages.size, decoded.size)
        assertEquals(session.stages.map { it.stage }, decoded.map { it.stage })
        assertEquals(session.startTime, decoded.first().start)
        assertEquals(session.endTime, decoded.last().end)
        assertTrue(SleepRecordParser.decodeSegments("").isEmpty())
    }

    @Test
    fun segmentLabelFormat() {
        val seg = SleepSegment(
            start = localMillis(2026, 10, 7, 23, 48),
            end = localMillis(2026, 10, 8, 0, 5),
            stage = SleepStage.DEEP,
            durationMin = 17L
        )
        assertEquals("Deep sleep  23:48 – 00:05  (17 min)", SleepRecordParser.formatSegment(seg))
    }
}
