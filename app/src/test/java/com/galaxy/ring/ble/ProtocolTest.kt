package com.galaxy.ring.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun testParseBatteryLevel() {
        val data = byteArrayOf(85)
        val battery = Protocol.parseBatteryLevel(data)
        assertNotNull(battery)
        assertEquals(85, battery?.level)
        assertEquals(false, battery?.isCharging)

        val chargingData = byteArrayOf(92, 0x01)
        val chargingBattery = Protocol.parseBatteryLevel(chargingData)
        assertNotNull(chargingBattery)
        assertEquals(92, chargingBattery?.level)
        assertEquals(true, chargingBattery?.isCharging)
    }

    @Test
    fun testParseHeartRate8Bit() {
        val data = byteArrayOf(0x00, 72)
        val hr = Protocol.parseHeartRate(data)
        assertNotNull(hr)
        assertEquals(72, hr?.bpm)
    }

    @Test
    fun testParseHeartRate16Bit() {
        val data = byteArrayOf(0x01, 0x8C.toByte(), 0x00)
        val hr = Protocol.parseHeartRate(data)
        assertNotNull(hr)
        assertEquals(140, hr?.bpm)
    }

    @Test
    fun testParseTemperature() {
        val data = byteArrayOf(0x00, 0x42, 0x0E, 0x00, (-2).toByte())
        val temp = Protocol.parseTemperature(data)
        assertNotNull(temp)
        assertEquals(36.5f, temp?.temperatureCelsius ?: 0f, 0.1f)
    }

    @Test
    fun testBuildCommandChecksum() {
        val ping = Protocol.buildPingCommand()
        assertEquals(Protocol.FRAME_HEADER_1, ping[0])
        assertEquals(Protocol.FRAME_HEADER_2, ping[1])
        assertEquals(Protocol.CMD_PING, ping[2])
        assertEquals(0.toByte(), ping[3])

        val expectedChecksum = (ping[0].toInt() xor ping[1].toInt() xor ping[2].toInt() xor ping[3].toInt()).toByte()
        assertEquals(expectedChecksum, ping[4])
    }

    @Test
    fun testBuildAndParseCustomRingPayload() {
        val payload = byteArrayOf(
            80,
            0x44, 0x00,
            0x88.toByte(), 0x13, 0x00, 0x00,
            0x4C, 0x0E
        )

        val packet = Protocol.buildCommand(Protocol.CMD_RING_TELEMETRY_RESPONSE, payload)
        val snapshot = Protocol.parseCustomRingPayload(packet)

        assertNotNull(snapshot)
        assertEquals(80, snapshot?.battery?.level)
        assertEquals(68, snapshot?.latestHeartRate?.bpm)
        assertEquals(5000L, snapshot?.steps?.totalSteps)
        assertEquals(36.6f, snapshot?.temperature?.temperatureCelsius ?: 0f, 0.1f)
    }

    @Test
    fun testCrc16Arc() {
        // Standard check vector for CRC16-ARC with "123456789": 0xBB3D
        val checkBytes = "123456789".toByteArray(Charsets.US_ASCII)
        val crc = Protocol.crc16Arc(checkBytes)
        assertEquals(0xBB3D, crc)
    }

    @Test
    fun testBuildSr16FrameAndValidation() {
        val cmd = Protocol.CMD_MEASURE_HEART_RATE // 0x02, 0x24
        val frame = Protocol.buildSr16Frame(cmd)

        assertEquals(Protocol.FRAME_HEADER_SR16, frame[0])
        assertEquals(2.toByte(), frame[1]) // Length
        assertEquals(0x02.toByte(), frame[2])
        assertEquals(0x24.toByte(), frame[3])

        // Verify framing and CRC check
        assertTrue(Protocol.isValidSr16Frame(frame))
    }

    @Test
    fun testParseSr16Biometrics() {
        // Heart rate payload: [0x02, 0x24, status=0, bpm=74]
        val hrPayload = byteArrayOf(0x02, 0x24, 0x00, 74)
        val hr = Protocol.parseSr16HeartRate(hrPayload)
        assertNotNull(hr)
        assertEquals(74, hr?.bpm)

        // SpO2 payload: [0x02, 0x4E, status=0, pct=98]
        val spo2Payload = byteArrayOf(0x02, 0x4E, 0x00, 98)
        val spo2 = Protocol.parseSr16SpO2(spo2Payload)
        assertNotNull(spo2)
        assertEquals(98f, spo2?.percentage ?: 0f, 0.1f)

        // Steps payload: [0x05, 0x1A, steps LSB..MSB] -> 8200 steps (0x00002008)
        val stepsPayload = byteArrayOf(0x05, 0x1A, 0x08, 0x20, 0x00, 0x00)
        val steps = Protocol.parseSr16Steps(stepsPayload)
        assertNotNull(steps)
        assertEquals(8200L, steps?.totalSteps)
    }

    @Test
    fun testFrameVariants() {
        val payload = byteArrayOf(0x02, 0x24)

        // Variant 1: [0xAB][len][payload][crcL][crcH]
        val v1 = Protocol.buildVariant1(payload)
        assertEquals(0xAB.toByte(), v1[0])
        assertEquals(2.toByte(), v1[1])
        assertEquals(0x02.toByte(), v1[2])
        assertEquals(0x24.toByte(), v1[3])
        assertEquals(6, v1.size)

        // Variant 2: raw payload
        val v2 = Protocol.buildVariant2(payload)
        assertEquals(2, v2.size)
        assertEquals(0x02.toByte(), v2[0])
        assertEquals(0x24.toByte(), v2[1])

        // Variant 3: [0xAB][payload][crcL][crcH]
        val v3 = Protocol.buildVariant3(payload)
        assertEquals(0xAB.toByte(), v3[0])
        assertEquals(0x02.toByte(), v3[1])
        assertEquals(0x24.toByte(), v3[2])
        assertEquals(5, v3.size)

        // Variant 4: [0xAB][len][payload][crcH][crcL] (swapped CRC)
        val v4 = Protocol.buildVariant4(payload)
        assertEquals(0xAB.toByte(), v4[0])
        assertEquals(2.toByte(), v4[1])
        assertEquals(v1[5], v4[4]) // CRC swapped
        assertEquals(v1[4], v4[5])

        // Short UUID matching
        assertTrue(Protocol.matchesShortUuid(Protocol.SR16_SERVICE_UUID, "A00A"))
        assertTrue(Protocol.matchesShortUuid(Protocol.SR16_WRITE_CHAR_UUID, "B002"))
        assertTrue(Protocol.matchesShortUuid(Protocol.SR16_NOTIFY_CHAR_UUID, "B003"))
        assertTrue(Protocol.matchesShortUuid(Protocol.SERVICE_FF00_UUID, "FF00"))
        assertTrue(Protocol.matchesShortUuid(Protocol.SERVICE_0BC0_UUID, "0BC0"))
    }
}
