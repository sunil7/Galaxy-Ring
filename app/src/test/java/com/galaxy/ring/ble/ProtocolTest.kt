package com.galaxy.ring.ble

import com.galaxy.ring.data.PacePhase
import com.galaxy.ring.data.SleepPageStore
import com.galaxy.ring.data.SleepStage
import com.galaxy.ring.data.SportHrPoint
import com.galaxy.ring.data.WorkoutRecordParser
import com.galaxy.ring.data.WorkoutSession
import com.galaxy.ring.data.toEntity
import com.galaxy.ring.data.toWorkoutSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ProtocolTest {

    @Before
    fun clearSleepPages() {
        SleepPageStore.clear()
    }

    private fun bytesFromHex(hex: String): ByteArray {
        val clean = hex.replace(" ", "").trim()
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val index = i * 2
            result[i] = clean.substring(index, index + 2).toInt(16).toByte()
        }
        return result
    }

    private fun hexFromBytes(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    /** Seconds since 2000-01-01 for a device wall-clock time. */
    private fun deviceSeconds(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }
        return cal.timeInMillis / 1000L - 946_684_800L
    }

    /** One 0x0505 record: 4-byte BE timestamp, stage code, 00 00. */
    private fun sleepRecord(sec: Long, code: Int): ByteArray = byteArrayOf(
        (sec shr 24).toByte(), (sec shr 16).toByte(), (sec shr 8).toByte(), sec.toByte(),
        code.toByte(), 0x00, 0x00
    )

    private fun sleepFrame(data: ByteArray) = Protocol.Frame(
        flag = Protocol.FLAG_PUSH,
        key = Protocol.KEY_SLEEP,
        keyFlag = 0x10,
        data = data,
        fullPayload = byteArrayOf(0x05, 0x05, 0x10) + data,
        crcOk = true
    )

    @Test
    fun testCrc16ArcVector() {
        // CRC-16/ARC of ASCII "123456789" = 0xBB3D
        val checkBytes = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = Protocol.crc16Arc(checkBytes)
        assertEquals(0xBB3D, crc)
    }

    @Test
    fun testSessionInitVector() {
        // Session init: AB 01 00 07 E5 72 03 02 20 00 00 00 01
        val frame = Protocol.buildSessionInit()
        val expected = "AB 01 00 07 E5 72 03 02 20 00 00 00 01"
        assertEquals(expected, hexFromBytes(frame))
    }

    @Test
    fun testFunctionListVector() {
        // Function list: AB 01 00 03 FC 88 02 63 10
        val frame = Protocol.buildFunctionListCommand()
        val expected = "AB 01 00 03 FC 88 02 63 10"
        assertEquals(expected, hexFromBytes(frame))
    }

    @Test
    fun testPassword0000Vector() {
        // Password 0000: AB 01 00 07 5C E8 03 04 10 30 30 30 30
        val frame = Protocol.buildPasswordCommand("0000")
        val expected = "AB 01 00 07 5C E8 03 04 10 30 30 30 30"
        assertEquals(expected, hexFromBytes(frame))
    }

    @Test
    fun testHeartRateStartVector() {
        // HR start: AB 01 00 06 F7 EE 06 09 00 03 05 01
        val frame = Protocol.buildHeartRateStartCommand()
        val expected = "AB 01 00 06 F7 EE 06 09 00 03 05 01"
        assertEquals(expected, hexFromBytes(frame))
    }

    @Test
    fun testHeartRateStopVector() {
        // HR stop: AB 01 00 06 37 2F 06 09 00 03 05 00
        val frame = Protocol.buildHeartRateStopCommand()
        val expected = "AB 01 00 06 37 2F 06 09 00 03 05 00"
        assertEquals(expected, hexFromBytes(frame))
    }

    @Test
    fun testSpO2StartVector() {
        // SpO2 start: AB 01 00 06 F5 CE 06 09 00 09 05 01
        val frame = Protocol.buildSpO2StartCommand()
        val expected = "AB 01 00 06 F5 CE 06 09 00 09 05 01"
        assertEquals(expected, hexFromBytes(frame))
    }

    @Test
    fun testAckFor0302Vector() {
        // ACK for 0302: AB 11 00 03 B8 F0 03 02 20
        val frame = Protocol.buildAckFrame(Protocol.KEY_SESSION_INIT, Protocol.KEY_FLAG_SETUP)
        val expected = "AB 11 00 03 B8 F0 03 02 20"
        assertEquals(expected, hexFromBytes(frame))
    }

    @Test
    fun testParseFramesFromStreamComplete() {
        val frameBytes = bytesFromHex("AB 01 00 07 E5 72 03 02 20 00 00 00 01")
        val (frames, remainder) = Protocol.parseFramesFromStream(frameBytes)

        assertEquals(1, frames.size)
        assertEquals(0, remainder.size)

        val f = frames[0]
        assertEquals(Protocol.FLAG_COMMAND, f.flag)
        assertEquals(Protocol.KEY_SESSION_INIT, f.key)
        assertEquals(Protocol.KEY_FLAG_SETUP, f.keyFlag)
        assertArrayEquals(byteArrayOf(0x00, 0x00, 0x00, 0x01), f.data)
        assertTrue(f.crcOk)
    }

    @Test
    fun testParseCoalescedAndSplitFrames() {
        val f1 = bytesFromHex("AB 01 00 03 FC 88 02 63 10")
        val f2 = bytesFromHex("AB 11 00 03 B8 F0 03 02 20")

        // 1. Coalesced: both frames together
        val combined = f1 + f2
        val (frames1, rem1) = Protocol.parseFramesFromStream(combined)
        assertEquals(2, frames1.size)
        assertEquals(0, rem1.size)
        assertEquals(Protocol.KEY_FUNCTION_LIST, frames1[0].key)
        assertEquals(Protocol.KEY_SESSION_INIT, frames1[1].key)

        // 2. Split: f1 split across two packets
        val partA = f1.copyOfRange(0, 5)
        val partB = f1.copyOfRange(5, f1.size)

        val (firstPassFrames, remA) = Protocol.parseFramesFromStream(partA)
        assertEquals(0, firstPassFrames.size)
        assertEquals(5, remA.size)

        val (secondPassFrames, remB) = Protocol.parseFramesFromStream(remA + partB)
        assertEquals(1, secondPassFrames.size)
        assertEquals(0, remB.size)
        assertEquals(Protocol.KEY_FUNCTION_LIST, secondPassFrames[0].key)
    }

    @Test
    fun testParseBiometricPushRecord() {
        // Construct a push frame (Flag 0x21, Key 0x0224, 6-byte record: timestamp(4), bpm 78(1), flag 0(1))
        val recData = byteArrayOf(0x00, 0x01, 0x02, 0x03, 78.toByte(), 0x00)
        val frameBytes = Protocol.encodeFrame(Protocol.FLAG_PUSH, Protocol.KEY_HR_PUSH, Protocol.KEY_FLAG_WRITE, recData)

        val (frames, _) = Protocol.parseFramesFromStream(frameBytes)
        assertEquals(1, frames.size)

        val (hr, spo2) = Protocol.parseBiometricPush(frames[0])
        assertNotNull(hr)
        assertEquals(78, hr?.bpm)
        assertNull(spo2)
    }

    @Test
    fun testParseBatteryLevelStandard() {
        val data = byteArrayOf(85)
        val battery = Protocol.parseBatteryLevel(data)
        assertNotNull(battery)
        assertEquals(85, battery?.level)
    }

    @Test
    fun testParseRealHrPayloadFromLogs() {
        // Confirmed from real SR16 logs:
        // key=0x0224 payload: 02 24 00 32 5D 6F 59 3F 00 -> value byte 0x3F = 63 bpm
        val payload = bytesFromHex("02 24 00 32 5D 6F 59 3F 00")
        val (hr, spo2) = Protocol.parseBiometricPayload(payload)

        assertNotNull(hr)
        assertEquals(63, hr?.bpm)
        assertNull(spo2)
    }

    @Test
    fun testParseRealSpo2PayloadFromLogs() {
        // Confirmed from real SR16 logs:
        // key=0x024E payload: 02 4E 00 32 5D 6F 9C 61 00 -> value byte 0x61 = 97%
        val payload = bytesFromHex("02 4E 00 32 5D 6F 9C 61 00")
        val (hr, spo2) = Protocol.parseBiometricPayload(payload)

        assertNull(hr)
        assertNotNull(spo2)
        assertEquals(97f, spo2?.percentage ?: 0f, 0.01f)
    }

    @Test
    fun testParseBiometricFrameRegardlessOfFlag() {
        // Frame received with Flag 0x11 (ACK-style, as seen in real logs)
        val payload = bytesFromHex("02 24 00 32 5D 6F 59 3F 00")
        val crc = Protocol.crc16Arc(payload)
        val frameBytes = byteArrayOf(
            0xAB.toByte(),
            Protocol.FLAG_ACK, // 0x11!
            (payload.size shr 8).toByte(),
            (payload.size and 0xFF).toByte(),
            (crc shr 8).toByte(),
            (crc and 0xFF).toByte()
        ) + payload

        val (frames, _) = Protocol.parseFramesFromStream(frameBytes)
        assertEquals(1, frames.size)
        assertEquals(Protocol.FLAG_ACK, frames[0].flag)
        assertEquals(Protocol.KEY_HR_PUSH, frames[0].key)

        val (hr, _) = Protocol.parseBiometricFrame(frames[0])
        assertNotNull(hr)
        assertEquals(63, hr?.bpm)
    }

    @Test
    fun testBiometricZeroValueIgnored() {
        // Value 0 is "no reading yet" and must be ignored
        val payload = bytesFromHex("02 24 00 32 5D 6F 59 00 00")
        val (hr, spo2) = Protocol.parseBiometricPayload(payload)
        assertNull(hr)
        assertNull(spo2)
    }

    @Test
    fun testMeasurementCompletedStatusFrames() {
        // 06 09 00 03 05 00 (HR done)
        val hrDonePayload = bytesFromHex("06 09 00 03 05 00")
        val hrDoneFrame = Protocol.Frame(
            flag = Protocol.FLAG_ACK,
            key = Protocol.KEY_MEASURE_CONTROL,
            keyFlag = 0x00,
            data = hrDonePayload.copyOfRange(3, 6),
            fullPayload = hrDonePayload,
            crcOk = true
        )
        assertTrue(Protocol.isMeasurementCompletedStatus(hrDoneFrame))
        assertTrue(Protocol.isMeasurementCompletedStatus(hrDoneFrame, Protocol.MEASURE_TYPE_HR))
        assertFalse(Protocol.isMeasurementCompletedStatus(hrDoneFrame, Protocol.MEASURE_TYPE_SPO2))
        assertEquals(Protocol.MEASURE_TYPE_HR, Protocol.getCompletedMeasurementType(hrDoneFrame))

        // 06 09 00 09 05 00 (SpO2 done)
        val spo2DonePayload = bytesFromHex("06 09 00 09 05 00")
        val spo2DoneFrame = Protocol.Frame(
            flag = Protocol.FLAG_ACK,
            key = Protocol.KEY_MEASURE_CONTROL,
            keyFlag = 0x00,
            data = spo2DonePayload.copyOfRange(3, 6),
            fullPayload = spo2DonePayload,
            crcOk = true
        )
        assertTrue(Protocol.isMeasurementCompletedStatus(spo2DoneFrame))
        assertTrue(Protocol.isMeasurementCompletedStatus(spo2DoneFrame, Protocol.MEASURE_TYPE_SPO2))
        assertFalse(Protocol.isMeasurementCompletedStatus(spo2DoneFrame, Protocol.MEASURE_TYPE_HR))
        assertEquals(Protocol.MEASURE_TYPE_SPO2, Protocol.getCompletedMeasurementType(spo2DoneFrame))

        // Start response (06 09 00 03 01 00) must NOT be complete status
        val startAckPayload = bytesFromHex("06 09 00 03 01 00")
        val startAckFrame = Protocol.Frame(
            flag = Protocol.FLAG_ACK,
            key = Protocol.KEY_MEASURE_CONTROL,
            keyFlag = 0x00,
            data = startAckPayload.copyOfRange(3, 6),
            fullPayload = startAckPayload,
            crcOk = true
        )
        assertFalse(Protocol.isMeasurementCompletedStatus(startAckFrame))
        assertNull(Protocol.getCompletedMeasurementType(startAckFrame))
    }

    @Test
    fun testSleepKeysAndCommandEnums() {
        assertEquals(0x0505, Protocol.KEY_SLEEP)
        assertEquals(0x0505, Protocol.KEY_SLEEP_PUSH)
        assertEquals(0x051B, Protocol.KEY_SMOKING_DETAIL_DATA)
        assertEquals(0x0285, Protocol.KEY_SCREEN_CONTROL)
        assertEquals(Protocol.KEY_SCREEN_CONTROL, Protocol.DISPLAY_CANDIDATE_KEYS.first())
    }

    @Test
    fun testSleepQueryCommandsAThroughE() {
        // Sleep Query Variant A: 0505 keyFlag 0x10, no data
        val cmdA = Protocol.buildSleepCommandA()
        assertEquals(0xAB.toByte(), cmdA[0])
        assertEquals(Protocol.FLAG_COMMAND, cmdA[1])
        // Key 0505
        val fA = Protocol.parseFramesFromStream(cmdA).first[0]
        assertEquals(Protocol.KEY_SLEEP, fA.key)
        assertEquals(Protocol.KEY_FLAG_READ, fA.keyFlag)
        assertEquals(0, fA.data.size)

        // Variant B: 0505 keyFlag 0x00, no data
        val cmdB = Protocol.buildSleepCommandB()
        val fB = Protocol.parseFramesFromStream(cmdB).first[0]
        assertEquals(Protocol.KEY_SLEEP, fB.key)
        assertEquals(Protocol.KEY_FLAG_WRITE, fB.keyFlag)
        assertEquals(0, fB.data.size)

        // Variant C: 0505 keyFlag 0x10, data [0x00]
        val cmdC = Protocol.buildSleepCommandC()
        val fC = Protocol.parseFramesFromStream(cmdC).first[0]
        assertEquals(Protocol.KEY_SLEEP, fC.key)
        assertEquals(byteArrayOf(0x00).toList(), fC.data.toList())

        // Variant D: 0505 keyFlag 0x10, data [0x01]
        val cmdD = Protocol.buildSleepCommandD()
        val fD = Protocol.parseFramesFromStream(cmdD).first[0]
        assertEquals(Protocol.KEY_SLEEP, fD.key)
        assertEquals(byteArrayOf(0x01).toList(), fD.data.toList())

        // Variant E: 0505 keyFlag 0x10, data [0x00, 0x01]
        val cmdE = Protocol.buildSleepCommandE()
        val fE = Protocol.parseFramesFromStream(cmdE).first[0]
        assertEquals(Protocol.KEY_SLEEP, fE.key)
        assertEquals(byteArrayOf(0x00, 0x01).toList(), fE.data.toList())

        // Sleep Delete Command: 0505 keyFlag 0x30, no data
        val cmdDel = Protocol.buildSleepDeleteCommand()
        val fDel = Protocol.parseFramesFromStream(cmdDel).first[0]
        assertEquals(Protocol.KEY_SLEEP, fDel.key)
        assertEquals(Protocol.KEY_FLAG_DELETE, fDel.keyFlag)
        assertEquals(0, fDel.data.size)
    }

    @Test
    fun testParseSleepFrameZeroReturnsNull() {
        // Empty or all zero data must return null (no dummy data)
        val emptyFrame = Protocol.Frame(
            flag = Protocol.FLAG_PUSH,
            key = Protocol.KEY_SLEEP,
            keyFlag = 0x10,
            data = byteArrayOf(0x00, 0x00, 0x00, 0x00),
            fullPayload = byteArrayOf(0x05, 0x05, 0x10, 0x00, 0x00, 0x00, 0x00),
            crcOk = true
        )
        assertNull(Protocol.parseSleepFrame(emptyFrame))
    }

    @Test
    fun testParseSleepFrameOvernightRecords() {
        // 23:35 -> 05:33 overnight (5h58m), anchored to the ring timestamps, never to "now"
        val data = sleepRecord(deviceSeconds(2026, 10, 7, 23, 35), 0x01) +
            sleepRecord(deviceSeconds(2026, 10, 7, 23, 48), 0x02) +
            sleepRecord(deviceSeconds(2026, 10, 8, 0, 5), 0x01) +
            sleepRecord(deviceSeconds(2026, 10, 8, 1, 0), 0x04) +
            sleepRecord(deviceSeconds(2026, 10, 8, 5, 33), 0x00)

        val sleep = Protocol.parseSleepFrame(sleepFrame(data))
        assertNotNull(sleep)
        assertEquals(358L, sleep!!.durationMinutes)

        val start = Calendar.getInstance().apply { timeInMillis = sleep.startTime }
        assertEquals(23, start.get(Calendar.HOUR_OF_DAY))
        assertEquals(35, start.get(Calendar.MINUTE))
        val end = Calendar.getInstance().apply { timeInMillis = sleep.endTime }
        assertEquals(5, end.get(Calendar.HOUR_OF_DAY))
        assertEquals(33, end.get(Calendar.MINUTE))

        assertEquals(4, sleep.stages.size)
        assertEquals(SleepStage.LIGHT, sleep.stages[0].stage)
        assertEquals(SleepStage.DEEP, sleep.stages[1].stage)
        assertEquals(SleepStage.REM, sleep.stages[3].stage)
        assertTrue(sleep.qualityScore in 0..100)
    }

    @Test
    fun testParseSleepFrameMergesPages() {
        val page1 = sleepRecord(deviceSeconds(2026, 10, 7, 23, 35), 0x01) +
            sleepRecord(deviceSeconds(2026, 10, 7, 23, 48), 0x02)
        val page2 = sleepRecord(deviceSeconds(2026, 10, 8, 0, 5), 0x01) +
            sleepRecord(deviceSeconds(2026, 10, 8, 5, 33), 0x00)

        val first = Protocol.parseSleepFrame(sleepFrame(page1))
        assertNotNull(first)
        assertEquals(13L, first!!.durationMinutes)

        // The second page completes the same night: one merged session, 23:35 -> 05:33
        val merged = Protocol.parseSleepFrame(sleepFrame(page2))
        assertNotNull(merged)
        assertEquals(358L, merged!!.durationMinutes)
        assertEquals(first.startTime, merged.startTime)
        assertEquals(3, merged.stages.size)
    }

    @Test
    fun testParseSleepFrameWithoutTimestampsReturnsNull() {
        // Bytes that are not valid 7-byte records must never become a made-up session
        val data = byteArrayOf(0x01, 0xC2.toByte(), 0x00, 0x5A, 0x00, 0x64, 0x00)
        assertNull(Protocol.parseSleepFrame(sleepFrame(data)))
    }

    @Test
    fun testBuildDisplayCommandScreenControl() {
        // Test screen off [0x00] and screen on [0x01] with KEY_SCREEN_CONTROL (0x0285)
        val cmdOff = Protocol.buildDisplayCommand(Protocol.KEY_SCREEN_CONTROL, false)
        val parsedOff = Protocol.parseFramesFromStream(cmdOff).first[0]
        assertEquals(0x0285, parsedOff.key)
        assertEquals(Protocol.KEY_FLAG_WRITE, parsedOff.keyFlag)
        assertArrayEquals(byteArrayOf(0x00), parsedOff.data)

        val cmdOn = Protocol.buildDisplayCommand(Protocol.KEY_SCREEN_CONTROL, true)
        val parsedOn = Protocol.parseFramesFromStream(cmdOn).first[0]
        assertEquals(0x0285, parsedOn.key)
        assertEquals(Protocol.KEY_FLAG_WRITE, parsedOn.keyFlag)
        assertArrayEquals(byteArrayOf(0x01), parsedOn.data)
    }

    @Test
    fun testBuildHrvAndStressCommands() {
        // HRV type 0x04 start: data should be [0x04, 0x05, 0x01]
        val hrvStart = Protocol.buildHrvStartCommand(0x04.toByte())
        val fHrvStart = Protocol.parseFramesFromStream(hrvStart).first[0]
        assertEquals(Protocol.KEY_MEASURE_CONTROL, fHrvStart.key)
        assertArrayEquals(byteArrayOf(0x04, 0x05, 0x01), fHrvStart.data)

        // HRV type 0x04 stop: data should be [0x04, 0x05, 0x00]
        val hrvStop = Protocol.buildHrvStopCommand(0x04.toByte())
        val fHrvStop = Protocol.parseFramesFromStream(hrvStop).first[0]
        assertEquals(Protocol.KEY_MEASURE_CONTROL, fHrvStop.key)
        assertArrayEquals(byteArrayOf(0x04, 0x05, 0x00), fHrvStop.data)

        // Stress type 0x07 start: data should be [0x07, 0x05, 0x01]
        val stressStart = Protocol.buildStressStartCommand(0x07.toByte())
        val fStressStart = Protocol.parseFramesFromStream(stressStart).first[0]
        assertEquals(Protocol.KEY_MEASURE_CONTROL, fStressStart.key)
        assertArrayEquals(byteArrayOf(0x07, 0x05, 0x01), fStressStart.data)
    }

    @Test
    fun testParseGenericMetricHrvAndStress() {
        // Generic push frame layout: key(2) + keyFlag(1) + timestamp(4 BE) + value(1)
        // e.g. key 0x0280, timestamp 4 bytes, value 65 ms
        val timestampBytes = byteArrayOf(0x00, 0x12, 0x34, 0x56)
        val frameHrv = Protocol.Frame(
            flag = Protocol.FLAG_PUSH,
            key = 0x0280,
            keyFlag = 0x00,
            data = timestampBytes + byteArrayOf(65.toByte()),
            fullPayload = byteArrayOf(0x02, 0x80.toByte(), 0x00) + timestampBytes + byteArrayOf(65.toByte()),
            crcOk = true
        )
        val parsedHrv = Protocol.parseGenericMetricValue(frameHrv, 5, 250)
        assertNotNull(parsedHrv)
        assertEquals(65, parsedHrv?.value)
        assertEquals(0x0280, parsedHrv?.key)

        // Stress: score 42 in 0..100
        val frameStress = Protocol.Frame(
            flag = Protocol.FLAG_PUSH,
            key = 0x0281,
            keyFlag = 0x00,
            data = timestampBytes + byteArrayOf(42.toByte()),
            fullPayload = byteArrayOf(0x02, 0x81.toByte(), 0x00) + timestampBytes + byteArrayOf(42.toByte()),
            crcOk = true
        )
        val parsedStress = Protocol.parseGenericMetricValue(frameStress, 0, 100)
        assertNotNull(parsedStress)
        assertEquals(42, parsedStress?.value)
        assertEquals(0x0281, parsedStress?.key)
    }

    @Test
    fun testCandidateProbesExclusion() {
        // HR 0x03 and SpO2 0x09 must NOT be in HRV probe types
        assertFalse(Protocol.HRV_PROBE_TYPES.contains(0x03.toByte()))
        assertFalse(Protocol.HRV_PROBE_TYPES.contains(0x09.toByte()))

        // Stress candidates with claimed HRV type 0x04 should exclude 0x04
        val stressCandidates = Protocol.getStressCandidateTypes(0x04.toByte())
        assertFalse(stressCandidates.contains(0x04.toByte()))
        assertFalse(stressCandidates.contains(0x03.toByte()))
        assertFalse(stressCandidates.contains(0x09.toByte()))
    }

    @Test
    fun testBuildHistoryReadFrames() {
        val keys = listOf(
            Protocol.KEY_HISTORY_HR,
            Protocol.KEY_HISTORY_SPO2,
            Protocol.KEY_HISTORY_HRV,
            Protocol.KEY_HISTORY_STRESS,
            Protocol.KEY_WORKOUT
        )
        for (k in keys) {
            val bytes = Protocol.buildHistoryRead(k)
            assertEquals(Protocol.FRAME_HEADER, bytes[0])
            assertEquals(Protocol.FLAG_COMMAND, bytes[1])
            val (frames, rem) = Protocol.parseFramesFromStream(bytes)
            assertEquals(1, frames.size)
            assertEquals(0, rem.size)
            val f = frames[0]
            assertEquals(k, f.key)
            assertEquals(Protocol.KEY_FLAG_READ, f.keyFlag)
            assertEquals(0, f.data.size)
            assertTrue(f.crcOk)
        }
    }

    @Test
    fun testParseHistorySamplesHrvAndStress() {
        // Sample: 4-byte BE seconds since 2000 (e.g. 2026-10-07 12:00:00) + 1-byte value + 1-byte padding
        val y2kSec = deviceSeconds(2026, 10, 7, 12, 0)
        val s1 = byteArrayOf(
            (y2kSec shr 24).toByte(),
            (y2kSec shr 16).toByte(),
            (y2kSec shr 8).toByte(),
            y2kSec.toByte(),
            55.toByte(), // HRV = 55 ms
            0x00
        )
        val y2kSec2 = deviceSeconds(2026, 10, 7, 13, 0)
        val s2 = byteArrayOf(
            (y2kSec2 shr 24).toByte(),
            (y2kSec2 shr 16).toByte(),
            (y2kSec2 shr 8).toByte(),
            y2kSec2.toByte(),
            62.toByte(), // HRV = 62 ms
            0x00
        )

        val frameHrv = Protocol.Frame(
            flag = Protocol.FLAG_COMMAND,
            key = Protocol.KEY_HISTORY_HRV,
            keyFlag = Protocol.KEY_FLAG_READ,
            data = s1 + s2,
            fullPayload = byteArrayOf(0x05, 0x0A, 0x10) + s1 + s2,
            crcOk = true
        )

        val hrvSamples = Protocol.parseHistorySamples(frameHrv, minVal = 5, maxVal = 250)
        assertEquals(2, hrvSamples.size)
        assertEquals(55, hrvSamples[0].value)
        assertEquals(62, hrvSamples[1].value)

        // Stress: 1-byte score e.g. 35
        val sStress = byteArrayOf(
            (y2kSec shr 24).toByte(),
            (y2kSec shr 16).toByte(),
            (y2kSec shr 8).toByte(),
            y2kSec.toByte(),
            35.toByte(), // Stress = 35
            0x00
        )
        val frameStress = Protocol.Frame(
            flag = Protocol.FLAG_COMMAND,
            key = Protocol.KEY_HISTORY_STRESS,
            keyFlag = Protocol.KEY_FLAG_READ,
            data = sStress,
            fullPayload = byteArrayOf(0x05, 0x0D, 0x10) + sStress,
            crcOk = true
        )
        val stressSamples = Protocol.parseHistorySamples(frameStress, minVal = 1, maxVal = 100)
        assertEquals(1, stressSamples.size)
        assertEquals(35, stressSamples[0].value)

        // Empty data returns empty list (no fake values)
        val emptyFrame = Protocol.Frame(
            flag = Protocol.FLAG_COMMAND,
            key = Protocol.KEY_HISTORY_HRV,
            keyFlag = Protocol.KEY_FLAG_READ,
            data = byteArrayOf(0, 0, 0, 0, 0, 0),
            fullPayload = byteArrayOf(0x05, 0x0A, 0x10, 0, 0, 0, 0, 0, 0),
            crcOk = true
        )
        val emptySamples = Protocol.parseHistorySamples(emptyFrame, minVal = 5, maxVal = 250)
        assertTrue(emptySamples.isEmpty())
    }

    @Test
    fun testWorkoutHistoryCommand() {
        assertEquals(0x0506, Protocol.KEY_WORKOUT)
        assertEquals(0x0506, Protocol.KEY_WORKOUT_HISTORY)
        assertEquals(0x0506, Protocol.KEY_HISTORY_WORKOUT)

        val workoutCmd = Protocol.buildWorkoutHistoryCommand()
        val readCmd = Protocol.buildHistoryRead(Protocol.KEY_WORKOUT)
        assertArrayEquals(readCmd, workoutCmd)

        val (frames, remaining) = Protocol.parseFramesFromStream(workoutCmd)
        assertEquals(1, frames.size)
        assertEquals(0, remaining.size)
        val f = frames[0]
        assertEquals(Protocol.KEY_WORKOUT, f.key)
        assertEquals(Protocol.KEY_FLAG_READ, f.keyFlag)
        assertEquals(Protocol.FLAG_COMMAND, f.flag)
        assertTrue(f.crcOk)
    }

    @Test
    fun testSportTypeMapping() {
        assertEquals("Walk", WorkoutRecordParser.mapSportType(1))
        assertEquals("Run", WorkoutRecordParser.mapSportType(2))
        assertEquals("Jog", WorkoutRecordParser.mapSportType(3))
        assertEquals("Hike", WorkoutRecordParser.mapSportType(4))
        assertEquals("Other", WorkoutRecordParser.mapSportType(0))
        assertEquals("Other", WorkoutRecordParser.mapSportType(5))
        assertEquals("Other", WorkoutRecordParser.mapSportType(6))
        assertEquals("Other", WorkoutRecordParser.mapSportType(7))
        assertEquals("Activity", WorkoutRecordParser.mapSportType(8))
        assertEquals("Activity", WorkoutRecordParser.mapSportType(99))
        assertEquals("Activity", WorkoutRecordParser.mapSportType(-1))
    }

    @Test
    fun testParseWorkoutPayloadValidSession() {
        // Construct 32-byte workout payload:
        // ts: 820_000_000L (Y2K seconds = approx 2025/2026) -> 0x30DE9980
        // sportType: 2 (Run)
        // duration: 1800s (30m) -> 0x00000708
        // distance: 4500m -> 0x00001194
        // steps: 5200 -> 0x00001450
        // cal: 320 kcal -> 0x00000140
        // avgHr: 145 -> 0x91, minHr: 110 -> 0x6E, maxHr: 172 -> 0xAC
        // pace: 400 s/km -> 0x0190
        // speed: 900 (9.00 km/h) -> 0x0384
        // cadence: 165 spm -> 0x00A5
        val payload = byteArrayOf(
            0x30, 0xDE.toByte(), 0x99.toByte(), 0x80.toByte(), // ts (820000000)
            0x02, // sportType (Run)
            0x00, 0x00, 0x07, 0x08, // duration 1800s
            0x00, 0x00, 0x11, 0x94.toByte(), // distance 4500m
            0x00, 0x00, 0x14, 0x50, // steps 5200
            0x00, 0x00, 0x01, 0x40, // cal 320
            0x91.toByte(), 0x6E, 0xAC.toByte(), // avg=145, min=110, max=172
            0x01, 0x90.toByte(), // pace 400
            0x03, 0x84.toByte(), // speed 9.00 km/h
            0x00, 0xA5.toByte() // cadence 165
        )

        val frame = Protocol.Frame(
            flag = Protocol.FLAG_PUSH,
            key = Protocol.KEY_WORKOUT,
            keyFlag = Protocol.KEY_FLAG_READ,
            data = payload,
            fullPayload = byteArrayOf(0x05, 0x06, 0x10) + payload,
            crcOk = true
        )

        val sessions = Protocol.parseWorkoutFrame(frame)
        assertEquals(1, sessions.size)

        val s = sessions[0]
        assertEquals(2, s.sportType)
        assertEquals("Run", s.sportTypeName)
        assertEquals(1800L, s.durationSeconds)
        assertEquals(4500.0, s.totalDistanceMeters, 0.1)
        assertEquals(5200L, s.totalSteps)
        assertEquals(320, s.totalCaloriesKcal)
        assertEquals(145, s.avgHr)
        assertEquals(110, s.minHr)
        assertEquals(172, s.maxHr)
        assertEquals(400.0, s.avgPaceSecondsPerKm!!, 0.1)
        assertEquals(9.0, s.avgSpeedKmH!!, 0.1)
        assertEquals(165, s.avgCadenceSpm)
        assertTrue(s.endTime > s.startTime)
    }

    @Test
    fun testParseWorkoutWithPacePhasesAndHrPoints() {
        // Base payload (21 bytes) + pacePhases count(1) + 2 phases(4) + hrCount(1) + 3 HR points(3)
        val payload = byteArrayOf(
            0x30, 0xDE.toByte(), 0x99.toByte(), 0x80.toByte(), // ts
            0x01, // sportType (Walk)
            0x00, 0x00, 0x04, 0xB0.toByte(), // duration 1200s (20m)
            0x00, 0x00, 0x07, 0xD0.toByte(), // distance 2000m
            0x00, 0x00, 0x09, 0xC4.toByte(), // steps 2500
            0x00, 0x00, 0x00, 0x64, // cal 100
            0x02, // 2 metric pace items
            0x02, 0x58, // km 1 pace: 600s
            0x02, 0x58, // km 2 pace: 600s
            0x03, // 3 HR items
            95.toByte(), 105.toByte(), 100.toByte() // 95, 105, 100 bpm
        )

        val frame = Protocol.Frame(
            flag = Protocol.FLAG_PUSH,
            key = Protocol.KEY_WORKOUT,
            keyFlag = Protocol.KEY_FLAG_READ,
            data = payload,
            fullPayload = byteArrayOf(0x05, 0x06, 0x10) + payload,
            crcOk = true
        )

        val sessions = Protocol.parseWorkoutFrame(frame)
        assertEquals(1, sessions.size)
        val s = sessions[0]
        assertEquals(1, s.sportType)
        assertEquals("Walk", s.sportTypeName)
        assertEquals(2, s.pacePhases.size)
        assertEquals(1, s.pacePhases[0].phaseIndex)
        assertEquals(600L, s.pacePhases[0].paceSeconds)
        assertEquals(2, s.pacePhases[1].phaseIndex)

        assertEquals(3, s.hrPoints.size)
        assertEquals(95, s.hrPoints[0].bpm)
        assertEquals(105, s.hrPoints[1].bpm)
        assertEquals(100, s.hrPoints[2].bpm)
    }

    @Test
    fun testEmptyAndZeroedWorkoutPayloadsNeverSynthesize() {
        val emptyFrame = Protocol.Frame(
            flag = Protocol.FLAG_PUSH,
            key = Protocol.KEY_WORKOUT,
            keyFlag = Protocol.KEY_FLAG_READ,
            data = byteArrayOf(),
            fullPayload = byteArrayOf(0x05, 0x06, 0x10),
            crcOk = true
        )
        val emptyResult = Protocol.parseWorkoutFrame(emptyFrame)
        assertTrue(emptyResult.isEmpty())

        val allZerosFrame = Protocol.Frame(
            flag = Protocol.FLAG_PUSH,
            key = Protocol.KEY_WORKOUT,
            keyFlag = Protocol.KEY_FLAG_READ,
            data = ByteArray(28) { 0 },
            fullPayload = byteArrayOf(0x05, 0x06, 0x10) + ByteArray(28) { 0 },
            crcOk = true
        )
        val allZerosResult = Protocol.parseWorkoutFrame(allZerosFrame)
        assertTrue(allZerosResult.isEmpty())

        // Non-workout key returns empty list
        val nonWorkoutFrame = allZerosFrame.copy(key = Protocol.KEY_HISTORY_HR)
        val nonWorkoutResult = Protocol.parseWorkoutFrame(nonWorkoutFrame)
        assertTrue(nonWorkoutResult.isEmpty())
    }

    @Test
    fun testWorkoutEntityConversionRoundTrip() {
        val original = WorkoutSession(
            id = 42L,
            date = "2026-10-21",
            sportType = 3,
            sportTypeName = "Jog",
            startTime = 1729468800000L,
            endTime = 1729470600000L,
            durationSeconds = 1800L,
            totalDistanceMeters = 3200.0,
            totalSteps = 4000L,
            totalCaloriesKcal = 250,
            avgHr = 138,
            minHr = 105,
            maxHr = 162,
            avgPaceSecondsPerKm = 560.0,
            avgSpeedKmH = 6.4,
            avgCadenceSpm = 150,
            pacePhases = listOf(
                PacePhase(1, 560L, 560L, 1000.0, 1300L),
                PacePhase(2, 570L, 570L, 1000.0, 1320L)
            ),
            hrPoints = listOf(
                SportHrPoint(0L, 1729468800000L, 110),
                SportHrPoint(60L, 1729468860000L, 135)
            ),
            rawPayloadHex = "05 06 10 AA BB CC"
        )

        val entity = original.toEntity()
        assertEquals(42L, entity.id)
        assertEquals("2026-10-21", entity.date)
        assertEquals("Jog", entity.sportTypeName)
        assertEquals(138, entity.avgHr)

        val restored = entity.toWorkoutSession()
        assertEquals(original.id, restored.id)
        assertEquals(original.date, restored.date)
        assertEquals(original.sportType, restored.sportType)
        assertEquals(original.sportTypeName, restored.sportTypeName)
        assertEquals(original.startTime, restored.startTime)
        assertEquals(original.endTime, restored.endTime)
        assertEquals(original.durationSeconds, restored.durationSeconds)
        assertEquals(original.totalDistanceMeters, restored.totalDistanceMeters, 0.01)
        assertEquals(original.totalSteps, restored.totalSteps)
        assertEquals(original.totalCaloriesKcal, restored.totalCaloriesKcal)
        assertEquals(original.avgHr, restored.avgHr)
        assertEquals(original.minHr, restored.minHr)
        assertEquals(original.maxHr, restored.maxHr)
        assertEquals(original.avgPaceSecondsPerKm, restored.avgPaceSecondsPerKm)
        assertEquals(original.avgSpeedKmH, restored.avgSpeedKmH)
        assertEquals(original.avgCadenceSpm, restored.avgCadenceSpm)
        assertEquals(2, restored.pacePhases.size)
        assertEquals(560L, restored.pacePhases[0].paceSeconds)
        assertEquals(2, restored.hrPoints.size)
        assertEquals(135, restored.hrPoints[1].bpm)
        assertEquals(original.rawPayloadHex, restored.rawPayloadHex)
    }
}
