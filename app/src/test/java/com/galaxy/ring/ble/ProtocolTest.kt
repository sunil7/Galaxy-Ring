package com.galaxy.ring.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

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
}
