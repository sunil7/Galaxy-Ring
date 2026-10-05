package com.galaxy.ring.ble

import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.RingBattery
import com.galaxy.ring.data.RingHealthSnapshot
import com.galaxy.ring.data.SkinTemperature
import com.galaxy.ring.data.StepData
import java.util.UUID

object Protocol {
    // Standard GATT Bluetooth SIG UUIDs
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")

    val HEALTH_THERMOMETER_SERVICE_UUID: UUID = UUID.fromString("00001809-0000-1000-8000-00805F9B34FB")
    val TEMPERATURE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002A1C-0000-1000-8000-00805F9B34FB")

    // Galaxy Ring Custom Telemetry & Command UUIDs
    val GALAXY_RING_SERVICE_UUID: UUID = UUID.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")
    val GALAXY_RING_TELEMETRY_CHAR_UUID: UUID = UUID.fromString("0000FD5B-0000-1000-8000-00805F9B34FB")
    val GALAXY_RING_COMMAND_CHAR_UUID: UUID = UUID.fromString("0000FD5C-0000-1000-8000-00805F9B34FB")

    // Client Characteristic Configuration Descriptor
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // Frame Header Markers
    const val FRAME_HEADER_1: Byte = 0xAA.toByte()
    const val FRAME_HEADER_2: Byte = 0x55.toByte()

    // Command IDs
    const val CMD_PING: Byte = 0x01
    const val CMD_REQUEST_SYNC: Byte = 0x02
    const val CMD_FIND_MY_RING: Byte = 0x03
    const val CMD_SET_TIME: Byte = 0x04
    const val CMD_RING_TELEMETRY_RESPONSE: Byte = 0x10

    /**
     * Parses standard Bluetooth GATT Battery Level characteristic (0x2A19).
     */
    fun parseBatteryLevel(data: ByteArray): RingBattery? {
        if (data.isEmpty()) return null
        val level = (data[0].toInt() and 0xFF).coerceIn(0, 100)
        val isCharging = if (data.size > 1) (data[1].toInt() and 0x01) != 0 else false
        return RingBattery(level = level, isCharging = isCharging)
    }

    /**
     * Parses standard Bluetooth GATT Heart Rate Measurement characteristic (0x2A37).
     */
    fun parseHeartRate(data: ByteArray): HeartRateSample? {
        if (data.isEmpty()) return null
        val flags = data[0].toInt()
        val is16Bit = (flags and 0x01) != 0
        val bpm = if (is16Bit) {
            if (data.size < 3) return null
            ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        } else {
            if (data.size < 2) return null
            data[1].toInt() and 0xFF
        }
        return HeartRateSample(bpm = bpm)
    }

    /**
     * Parses standard GATT Temperature Measurement (0x2A1C).
     */
    fun parseTemperature(data: ByteArray): SkinTemperature? {
        if (data.size < 5) return null
        // Format is flags (1 byte) + IEEE-11073 FLOAT (4 bytes: 8-bit exponent, 24-bit mantissa)
        val mantissa = ((data[3].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or
                (data[1].toInt() and 0xFF)
        val exponent = data[4].toInt()

        // Handle sign extension on 24-bit mantissa
        val signedMantissa = if ((mantissa and 0x800000) != 0) {
            mantissa or -0x1000000
        } else {
            mantissa
        }

        val rawCelsius = (signedMantissa.toFloat() * Math.pow(10.0, exponent.toDouble()).toFloat())
        val rounded = (rawCelsius * 10f).toInt() / 10f
        val delta = ((rounded - 36.5f) * 10f).toInt() / 10f
        return SkinTemperature(temperatureCelsius = rounded, baselineDelta = delta)
    }

    /**
     * Parses Galaxy Ring custom composite telemetry packet.
     * Expected layout:
     * [0] 0xAA, [1] 0x55, [2] CMD_ID (0x10), [3] payload length
     * [4] Battery %
     * [5..6] Heart Rate BPM (uint16 little endian)
     * [7..10] Steps (uint32 little endian)
     * [11..12] Skin Temp (temp * 100 as uint16)
     * [13] Checksum (XOR)
     */
    fun parseCustomRingPayload(data: ByteArray): RingHealthSnapshot? {
        if (data.size < 14) return null
        if (data[0] != FRAME_HEADER_1 || data[1] != FRAME_HEADER_2) return null
        if (data[2] != CMD_RING_TELEMETRY_RESPONSE) return null

        val payloadLength = data[3].toInt() and 0xFF
        if (data.size < 4 + payloadLength + 1) return null

        // Checksum verification
        var checksum: Byte = 0
        for (i in 0 until (4 + payloadLength)) {
            checksum = (checksum.toInt() xor data[i].toInt()).toByte()
        }
        val packetChecksum = data[4 + payloadLength]
        if (checksum != packetChecksum) {
            return null
        }

        val batteryLevel = (data[4].toInt() and 0xFF).coerceIn(0, 100)
        val bpm = ((data[6].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val steps = ((data[10].toInt() and 0xFF).toLong() shl 24) or
                ((data[9].toInt() and 0xFF).toLong() shl 16) or
                ((data[8].toInt() and 0xFF).toLong() shl 8) or
                (data[7].toInt() and 0xFF).toLong()
        val tempRaw = ((data[12].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)
        val tempCelsius = tempRaw / 100f

        return RingHealthSnapshot(
            battery = RingBattery(level = batteryLevel),
            latestHeartRate = HeartRateSample(bpm = bpm),
            steps = StepData(totalSteps = steps),
            temperature = SkinTemperature(
                temperatureCelsius = tempCelsius,
                baselineDelta = ((tempCelsius - 36.5f) * 10f).toInt() / 10f
            ),
            lastSyncTimestamp = System.currentTimeMillis()
        )
    }

    /**
     * Builds a framed command packet for transmission over BLE GATT.
     */
    fun buildCommand(cmdId: Byte, payload: ByteArray = byteArrayOf()): ByteArray {
        val length = payload.size
        val packet = ByteArray(4 + length + 1)
        packet[0] = FRAME_HEADER_1
        packet[1] = FRAME_HEADER_2
        packet[2] = cmdId
        packet[3] = length.toByte()

        for (i in payload.indices) {
            packet[4 + i] = payload[i]
        }

        var checksum: Byte = 0
        for (i in 0 until (4 + length)) {
            checksum = (checksum.toInt() xor packet[i].toInt()).toByte()
        }
        packet[4 + length] = checksum
        return packet
    }

    fun buildFindMyRingCommand(): ByteArray = buildCommand(CMD_FIND_MY_RING, byteArrayOf(0x05)) // 5 seconds light pulse
    fun buildRequestSyncCommand(): ByteArray = buildCommand(CMD_REQUEST_SYNC)
    fun buildPingCommand(): ByteArray = buildCommand(CMD_PING)
}
