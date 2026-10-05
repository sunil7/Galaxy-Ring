package com.galaxy.ring.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        // Flags: 0x00 (8-bit heart rate format)
        // Value: 72 bpm
        val data = byteArrayOf(0x00, 72)
        val hr = Protocol.parseHeartRate(data)
        assertNotNull(hr)
        assertEquals(72, hr?.bpm)
    }

    @Test
    fun testParseHeartRate16Bit() {
        // Flags: 0x01 (16-bit format)
        // Value: 140 bpm (0x008C -> LSB 0x8C, MSB 0x00)
        val data = byteArrayOf(0x01, 0x8C.toByte(), 0x00)
        val hr = Protocol.parseHeartRate(data)
        assertNotNull(hr)
        assertEquals(140, hr?.bpm)
    }

    @Test
    fun testParseTemperature() {
        // Flags: 0x00 (Celsius)
        // Value: 36.5 deg C = 3650 with exponent -2 (0xFE)
        // 3650 = 0x000E42 -> byte 1: 0x42, byte 2: 0x0E, byte 3: 0x00, exponent: -2 (0xFE)
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
        assertEquals(0.toByte(), ping[3]) // 0 length payload

        // Verify checksum is XOR of bytes 0..3
        val expectedChecksum = (ping[0].toInt() xor ping[1].toInt() xor ping[2].toInt() xor ping[3].toInt()).toByte()
        assertEquals(expectedChecksum, ping[4])
    }

    @Test
    fun testBuildAndParseCustomRingPayload() {
        // Header: 0xAA, 0x55
        // Cmd: CMD_RING_TELEMETRY_RESPONSE (0x10)
        // Payload Length: 9 bytes
        // [4]: Battery = 80
        // [5..6]: HR = 68 (0x0044)
        // [7..10]: Steps = 5000 (0x00001388)
        // [11..12]: Temp = 3660 (36.6 C, 0x0E4C)
        val payload = byteArrayOf(
            80,                     // Battery
            0x44, 0x00,             // HR 68 bpm
            0x88.toByte(), 0x13, 0x00, 0x00, // Steps 5000
            0x4C, 0x0E              // Temp 3660 -> 36.6 C
        )

        val packet = Protocol.buildCommand(Protocol.CMD_RING_TELEMETRY_RESPONSE, payload)
        val snapshot = Protocol.parseCustomRingPayload(packet)

        assertNotNull(snapshot)
        assertEquals(80, snapshot?.battery?.level)
        assertEquals(68, snapshot?.latestHeartRate?.bpm)
        assertEquals(5000L, snapshot?.steps?.totalSteps)
        assertEquals(36.6f, snapshot?.temperature?.temperatureCelsius ?: 0f, 0.1f)
    }
}
