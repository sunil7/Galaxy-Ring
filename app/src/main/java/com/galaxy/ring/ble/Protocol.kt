package com.galaxy.ring.ble

import android.util.Log
import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.OxygenSaturationSample
import com.galaxy.ring.data.RingBattery
import com.galaxy.ring.data.RingHealthSnapshot
import com.galaxy.ring.data.SkinTemperature
import com.galaxy.ring.data.SleepPageStore
import com.galaxy.ring.data.SleepRecordParser
import com.galaxy.ring.data.SleepSegment
import com.galaxy.ring.data.SleepSession
import com.galaxy.ring.data.SleepStage
import com.galaxy.ring.data.SleepStageRecord
import com.galaxy.ring.data.StepData
import com.galaxy.ring.data.WorkoutRecordParser
import com.galaxy.ring.data.WorkoutSession
import com.galaxy.ring.data.isCompleted
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import kotlin.math.roundToInt
import com.galaxy.ring.debug.AppLog

/**
 * Protocol layer for SR16 Smart Ring using the RWfit binary protocol.
 *
 * ROOT CAUSE SUMMARY (SR16 38:00:00:00:6D:CA):
 * In previous versions, the BLE connection, MTU (185), service discovery (0xA00A),
 * write characteristic (0xB002), and notification characteristic (0xB003) all succeeded,
 * and CCCD writes succeeded with status=0. Every outgoing write was accepted by the Android
 * Bluetooth stack, but 0 RX frames were received.
 *
 * The root causes were:
 * 1. Wrong frame format: Frames were being generated with experimental variants (e.g. 1-byte length,
 *    LE CRC, missing flag byte, or raw payloads) instead of the exact RWfit binary framing.
 *    The hardware expects the strict big-endian format:
 *      [0xAB] [flag:1] [len:2 BE] [crc16:2 BE] [payload]
 *    where crc16 is CRC-16/ARC calculated over PAYLOAD ONLY.
 * 2. Incomplete init sequence: The SR16 requires a strict multi-step session handshake:
 *      (1) Session init (0x0302, flag 0x20, payload 00 00 00 01)
 *      (2) Timezone configuration (0x0202, flag 0x00)
 *      (3) Local time sync (0x0201, flag 0x00)
 *      (4) Function list query (0x0263, flag 0x10)
 *      (5) Optional password unlock (0x0304, flag 0x10, "0000") if bit 0 at byte 0x2C is set.
 * 3. Wrong measurement triggers: Measurements use start/stop key 0x0609 with payload [type, 0x05, enable]
 *    (HR = 0x03, SpO2 = 0x09) and results are pushed asynchronously via flag 0x21 (HR key 0x0224, SpO2 key 0x024E).
 * 4. Extraneous service writes: Services 0xFF00 and 0x0BC0 are OTA/vendor firmware endpoints.
 *    Only service 0xA00A (write 0xB002, notify 0xB003) should be used.
 *
 * Reference: https://sakshambhutani.xyz/PulseLoopiOS/hardware/rwfit/
 */
object Protocol {

    private const val TAG = "GalaxyRingBLE"

    // SR16 RWfit GATT UUIDs (Service 0xA00A)
    val SR16_SERVICE_UUID: UUID = UUID.fromString("0000A00A-0000-1000-8000-00805F9B34FB")
    val SR16_WRITE_CHAR_UUID: UUID = UUID.fromString("0000B002-0000-1000-8000-00805F9B34FB")
    val SR16_NOTIFY_CHAR_UUID: UUID = UUID.fromString("0000B003-0000-1000-8000-00805F9B34FB")

    // Standard Client Characteristic Configuration Descriptor (0x2902)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // Standard GATT UUIDs (fallbacks)
    val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
    val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")

    // RWfit Frame Header
    const val FRAME_HEADER: Byte = 0xAB.toByte()

    // Frame Flags
    const val FLAG_COMMAND: Byte = 0x01.toByte()   // Normal command / data
    const val FLAG_ACK: Byte = 0x11.toByte()       // ACK frame
    const val FLAG_PUSH: Byte = 0x21.toByte()      // Unsolicited push from ring

    // Key Flags
    const val KEY_FLAG_WRITE: Byte = 0x00.toByte()  // Set / write
    const val KEY_FLAG_READ: Byte = 0x10.toByte()   // Get / read
    const val KEY_FLAG_SETUP: Byte = 0x20.toByte()  // Session setup
    const val KEY_FLAG_DELETE: Byte = 0x30.toByte() // Delete

    // Known Keys
    const val KEY_SESSION_INIT: Int = 0x0302
    const val KEY_TIMEZONE: Int = 0x0202
    const val KEY_TIME: Int = 0x0201
    const val KEY_FUNCTION_LIST: Int = 0x0263
    const val KEY_PASSWORD: Int = 0x0304
    const val KEY_MEASURE_CONTROL: Int = 0x0609
    const val KEY_HR_PUSH: Int = 0x0224
    const val KEY_SPO2_PUSH: Int = 0x024E
    const val KEY_STEPS_PUSH: Int = 0x051A
    const val KEY_SLEEP: Int = 0x0505
    const val KEY_SLEEP_PUSH: Int = 0x0505
    const val KEY_HISTORY_HR: Int = 0x0503
    const val KEY_HISTORY_SPO2: Int = 0x0509
    const val KEY_HISTORY_HRV: Int = 0x050A
    const val KEY_HISTORY_STRESS: Int = 0x050D
    const val KEY_WORKOUT: Int = 0x0506
    const val KEY_HISTORY_WORKOUT: Int = KEY_WORKOUT
    const val KEY_WORKOUT_HISTORY: Int = KEY_WORKOUT
    const val KEY_HR_HISTORY: Int = KEY_HISTORY_HR
    const val KEY_SPO2_HISTORY: Int = KEY_HISTORY_SPO2
    const val KEY_HRV_HISTORY: Int = KEY_HISTORY_HRV
    const val KEY_STRESS_HISTORY: Int = KEY_HISTORY_STRESS
    const val KEY_SMOKING_DETAIL_DATA: Int = 0x051B // Official SDK DHBleCommandEnums.h: smoking data, NOT sleep
    const val KEY_SCREEN_CONTROL: Int = 0x0285     // Official SDK DHBleCommandEnums.h: BLE_KEY_SCREEN_CONTROL [0x00]=off, [0x01]=on

    // Measurement Types for Key 0x0609
    const val MEASURE_TYPE_HR: Byte = 0x03.toByte()
    const val MEASURE_TYPE_SPO2: Byte = 0x09.toByte()

    /**
     * Checks if a UUID matches a 16-bit short hexadecimal representation.
     */
    fun matchesShortUuid(uuid: UUID, shortHex: String): Boolean {
        val s = uuid.toString().lowercase()
        val target = shortHex.lowercase().padStart(4, '0')
        return s.startsWith("0000$target") || s.contains(target)
    }

    /**
     * Calculates CRC-16/ARC (reflected polynomial 0xA001, initial 0x0000, xorout 0x0000).
     * Standard check vector: CRC-16/ARC of ASCII "123456789" is 0xBB3D.
     */
    fun crc16Arc(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Int {
        var crc = 0x0000
        val end = (offset + length).coerceAtMost(bytes.size)
        for (i in offset until end) {
            val b = bytes[i].toInt() and 0xFF
            crc = crc xor b
            for (j in 0 until 8) {
                crc = if ((crc and 1) != 0) {
                    (crc ushr 1) xor 0xA001
                } else {
                    crc ushr 1
                }
            }
        }
        return crc and 0xFFFF
    }

    /**
     * Represents a parsed RWfit binary frame.
     * Frame format:
     *   AB | flag | len(2 BE) | crc16(2 BE) | payload
     * payload:
     *   key(2 BE) + keyFlag(1) + data
     */
    data class Frame(
        val flag: Byte,
        val key: Int,
        val keyFlag: Byte,
        val data: ByteArray,
        val fullPayload: ByteArray,
        val crcOk: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Frame
            return flag == other.flag &&
                    key == other.key &&
                    keyFlag == other.keyFlag &&
                    data.contentEquals(other.data) &&
                    fullPayload.contentEquals(other.fullPayload) &&
                    crcOk == other.crcOk
        }

        override fun hashCode(): Int {
            var result = flag.toInt()
            result = 31 * result + key
            result = 31 * result + keyFlag.toInt()
            result = 31 * result + data.contentHashCode()
            result = 31 * result + fullPayload.contentHashCode()
            result = 31 * result + crcOk.hashCode()
            return result
        }
    }

    /**
     * Encodes a complete RWfit frame.
     * Header: [0xAB] [flag] [len_hi] [len_lo] [crc_hi] [crc_lo]
     * Payload: [key_hi] [key_lo] [keyFlag] [data...]
     */
    fun encodeFrame(
        flag: Byte,
        key: Int,
        keyFlag: Byte,
        data: ByteArray = byteArrayOf()
    ): ByteArray {
        val payloadSize = 3 + data.size
        val payload = ByteArray(payloadSize)
        payload[0] = ((key ushr 8) and 0xFF).toByte()
        payload[1] = (key and 0xFF).toByte()
        payload[2] = keyFlag
        if (data.isNotEmpty()) {
            System.arraycopy(data, 0, payload, 3, data.size)
        }

        val crc = crc16Arc(payload)
        val frame = ByteArray(6 + payloadSize)
        frame[0] = FRAME_HEADER
        frame[1] = flag
        frame[2] = ((payloadSize ushr 8) and 0xFF).toByte()
        frame[3] = (payloadSize and 0xFF).toByte()
        frame[4] = ((crc ushr 8) and 0xFF).toByte()
        frame[5] = (crc and 0xFF).toByte()
        System.arraycopy(payload, 0, frame, 6, payloadSize)
        return frame
    }

    /**
     * Generates an ACK frame for an incoming command frame (flag 0x01).
     * ACK format: AB 11 00 03 [crc16] [key(2)] [keyFlag]
     */
    fun buildAckFrame(key: Int, keyFlag: Byte): ByteArray {
        return encodeFrame(
            flag = FLAG_ACK,
            key = key,
            keyFlag = keyFlag,
            data = byteArrayOf()
        )
    }

    /**
     * Session init command (Key 0x0302, KeyFlag 0x20, Data: 00 00 00 01).
     * Expected byte sequence: AB 01 00 07 E5 72 03 02 20 00 00 00 01
     */
    fun buildSessionInit(): ByteArray {
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_SESSION_INIT,
            keyFlag = KEY_FLAG_SETUP,
            data = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        )
    }

    /**
     * Timezone configuration (Key 0x0202, KeyFlag 0x00).
     * Data: [signed int8 quarter-hours from UTC] [platform 0x02]
     */
    fun buildTimezoneCommand(tz: TimeZone = TimeZone.getDefault()): ByteArray {
        val rawOffsetMillis = tz.rawOffset
        val quarterHours = (rawOffsetMillis / (15 * 60 * 1000)).coerceIn(-128, 127).toByte()
        val data = byteArrayOf(quarterHours, 0x02.toByte())
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_TIMEZONE,
            keyFlag = KEY_FLAG_WRITE,
            data = data
        )
    }

    /**
     * Local time sync (Key 0x0201, KeyFlag 0x00).
     * Data: [year-2000, month, day, hour, minute, second] local, 6 bytes.
     */
    fun buildTimeCommand(calendar: Calendar = Calendar.getInstance()): ByteArray {
        val year = (calendar.get(Calendar.YEAR) - 2000).coerceIn(0, 255).toByte()
        val month = (calendar.get(Calendar.MONTH) + 1).toByte() // 1-indexed
        val day = calendar.get(Calendar.DAY_OF_MONTH).toByte()
        val hour = calendar.get(Calendar.HOUR_OF_DAY).toByte()
        val minute = calendar.get(Calendar.MINUTE).toByte()
        val second = calendar.get(Calendar.SECOND).toByte()
        val data = byteArrayOf(year, month, day, hour, minute, second)
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_TIME,
            keyFlag = KEY_FLAG_WRITE,
            data = data
        )
    }

    /**
     * Function list query (Key 0x0263, KeyFlag 0x10, no data).
     * Expected byte sequence: AB 01 00 03 FC 88 02 63 10
     */
    fun buildFunctionListCommand(): ByteArray {
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_FUNCTION_LIST,
            keyFlag = KEY_FLAG_READ,
            data = byteArrayOf()
        )
    }

    /**
     * Password unlock (Key 0x0304, KeyFlag 0x10, Data ASCII "0000" = 30 30 30 30).
     * Expected byte sequence: AB 01 00 07 5C E8 03 04 10 30 30 30 30
     */
    fun buildPasswordCommand(passwordAscii: String = "0000"): ByteArray {
        val data = passwordAscii.toByteArray(Charsets.US_ASCII)
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_PASSWORD,
            keyFlag = KEY_FLAG_READ,
            data = data
        )
    }

    /**
     * Measurement start / stop command (Key 0x0609, KeyFlag 0x00, Data [type, 0x05, enable]).
     * HR start:   AB 01 00 06 F7 EE 06 09 00 03 05 01
     * HR stop:    AB 01 00 06 37 2F 06 09 00 03 05 00
     * SpO2 start: AB 01 00 06 F5 CE 06 09 00 09 05 01
     */
    fun buildMeasurementCommand(type: Byte, enable: Boolean): ByteArray {
        val data = byteArrayOf(type, 0x05.toByte(), if (enable) 0x01.toByte() else 0x00.toByte())
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_MEASURE_CONTROL,
            keyFlag = KEY_FLAG_WRITE,
            data = data
        )
    }

    fun buildHeartRateStartCommand(): ByteArray = buildMeasurementCommand(MEASURE_TYPE_HR, true)
    fun buildHeartRateStopCommand(): ByteArray = buildMeasurementCommand(MEASURE_TYPE_HR, false)

    fun buildSpO2StartCommand(): ByteArray = buildMeasurementCommand(MEASURE_TYPE_SPO2, true)
    fun buildSpO2StopCommand(): ByteArray = buildMeasurementCommand(MEASURE_TYPE_SPO2, false)

    // Probe order for HRV (0x03=HR and 0x09=SpO2 are excluded)
    val HRV_PROBE_TYPES: List<Byte> = listOf(
        0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x0A.toByte(), 0x0B.toByte()
    )

    // Probe order for Stress (0x03=HR, 0x09=SpO2, and HRV type excluded)
    val STRESS_ALL_CANDIDATE_TYPES: List<Byte> = listOf(
        0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x0A.toByte(), 0x0B.toByte(),
        0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte()
    )

    fun getStressCandidateTypes(claimedHrvType: Byte?): List<Byte> {
        return STRESS_ALL_CANDIDATE_TYPES.filter { it != claimedHrvType }
    }

    fun buildHrvStartCommand(type: Byte): ByteArray = buildMeasurementCommand(type, true)
    fun buildHrvStopCommand(type: Byte): ByteArray = buildMeasurementCommand(type, false)

    fun buildStressStartCommand(type: Byte): ByteArray = buildMeasurementCommand(type, true)
    fun buildStressStopCommand(type: Byte): ByteArray = buildMeasurementCommand(type, false)

    // Display ON / OFF probe candidate keys (A..E) - 0x0285 (BLE_KEY_SCREEN_CONTROL) tried first
    val DISPLAY_CANDIDATE_KEYS = listOf(KEY_SCREEN_CONTROL, 0x0306, 0x0307, 0x0203, 0x0204, 0x0264)

    fun buildDisplayCommand(key: Int, enabled: Boolean): ByteArray {
        val data = byteArrayOf(if (enabled) 0x01.toByte() else 0x00.toByte())
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = key,
            keyFlag = KEY_FLAG_WRITE,
            data = data
        )
    }

    data class GenericMetricParsed(
        val key: Int,
        val value: Int,
        val timestamp: Long
    )

    /**
     * Parses generic biometric value from an incoming frame with unknown push key:
     * layout: key(2) + keyFlag(1) + data
     * data: timestamp(4 BE) + value(1) + ...
     * Accepts plausible value within [minVal..maxVal].
     */
    fun parseGenericMetricValue(frame: Frame, minVal: Int, maxVal: Int): GenericMetricParsed? {
        val data = frame.data
        val now = System.currentTimeMillis()
        if (data.size >= 5) {
            val recordCount = (data.size / 6).coerceAtLeast(1)
            for (i in 0 until recordCount) {
                val offset = i * 6
                if (offset + 4 >= data.size) break
                val v8 = data[offset + 4].toInt() and 0xFF
                if (v8 in minVal..maxVal) {
                    return GenericMetricParsed(frame.key, v8, now)
                }
                if (offset + 5 < data.size) {
                    val v16 = ((v8 shl 8) or (data[offset + 5].toInt() and 0xFF))
                    if (v16 in minVal..maxVal) {
                        return GenericMetricParsed(frame.key, v16, now)
                    }
                }
            }
        }
        for (b in data) {
            val v = b.toInt() and 0xFF
            if (v in minVal..maxVal && v != 0) {
                return GenericMetricParsed(frame.key, v, now)
            }
        }
        return null
    }

    const val KEY_FIND_RING: Int = 0x0305

    fun buildFindRingCommand(): ByteArray {
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_FIND_RING,
            keyFlag = KEY_FLAG_WRITE,
            data = byteArrayOf(0x01)
        )
    }

    fun buildStepsCommand(): ByteArray {
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_STEPS_PUSH,
            keyFlag = KEY_FLAG_READ,
            data = byteArrayOf()
        )
    }

    // Default sleep history command: key 0x0505, keyFlag 0x10 (READ), no extra data
    fun buildSleepCommand(): ByteArray = buildSleepCommand(KEY_FLAG_READ, byteArrayOf())

    fun buildSleepCommand(keyFlag: Byte = KEY_FLAG_READ, data: ByteArray = byteArrayOf()): ByteArray {
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_SLEEP,
            keyFlag = keyFlag,
            data = data
        )
    }

    // 0x0505 Sleep History Query Variant A: keyFlag 0x10, data empty (AB 01 00 03 AD 44 05 05 10)
    fun buildSleepCommandA(): ByteArray = buildSleepCommand(KEY_FLAG_READ, byteArrayOf())

    // 0x0505 Sleep Query Variant B: keyFlag 0x00, data empty
    fun buildSleepCommandB(): ByteArray = buildSleepCommand(KEY_FLAG_WRITE, byteArrayOf())

    // 0x0505 Sleep Query Variant C: keyFlag 0x10 + data 0x00
    fun buildSleepCommandC(): ByteArray = buildSleepCommand(KEY_FLAG_READ, byteArrayOf(0x00))

    // 0x0505 Sleep Query Variant D: keyFlag 0x10 + data 0x01
    fun buildSleepCommandD(): ByteArray = buildSleepCommand(KEY_FLAG_READ, byteArrayOf(0x01))

    // 0x0505 Sleep Query Variant E: keyFlag 0x10 + data 0x00 0x01
    fun buildSleepCommandE(): ByteArray = buildSleepCommand(KEY_FLAG_READ, byteArrayOf(0x00, 0x01))

    // 0x0505 Sleep History Delete (keyFlag 0x30): signals ring that current page was imported so ring advances to next page
    fun buildSleepDeleteCommand(): ByteArray {
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = KEY_SLEEP,
            keyFlag = KEY_FLAG_DELETE,
            data = byteArrayOf()
        )
    }

    /**
     * History read command for 0x0503 (HR), 0x0509 (SpO2), 0x050A (HRV), 0x050D (Stress), 0x0506 (Workout).
     * Uses encodeFrame with FLAG_COMMAND (0x01) and KEY_FLAG_READ (0x10) with empty data.
     */
    fun buildHistoryRead(key: Int): ByteArray {
        return encodeFrame(
            flag = FLAG_COMMAND,
            key = key,
            keyFlag = KEY_FLAG_READ,
            data = byteArrayOf()
        )
    }

    /**
     * Workout history read command (Key 0x0506, KeyFlag 0x10 READ, empty data).
     * Expected format: AB 01 00 03 [crc16] 05 06 10
     */
    fun buildWorkoutHistoryCommand(): ByteArray = buildHistoryRead(KEY_WORKOUT)

    data class HistorySample(
        val timestamp: Long,
        val value: Int
    )

    /**
     * Parses real samples from history frames (0x0503, 0x0509, 0x050A, 0x050D).
     * Format: ~6B samples: Y2K timestamp (4B BE seconds since 2000-01-01) + 1B value (+ 1B status/pad).
     * Returns empty list if payload is empty, all zeroes, or contains no valid samples. Never invents fake samples.
     */
    fun parseHistorySamples(frame: Frame, minVal: Int, maxVal: Int): List<HistorySample> {
        val data = frame.data
        if (data.isEmpty() || data.all { it.toInt() == 0 }) {
            return emptyList()
        }

        val samples = mutableListOf<HistorySample>()
        val stride = if (data.size >= 6 && data.size % 6 == 0) 6 else if (data.size >= 5 && data.size % 5 == 0) 5 else 6
        val count = data.size / stride

        for (i in 0 until count) {
            val offset = i * stride
            if (offset + 4 >= data.size) break
            val sec = ((data[offset].toLong() and 0xFF) shl 24) or
                    ((data[offset + 1].toLong() and 0xFF) shl 16) or
                    ((data[offset + 2].toLong() and 0xFF) shl 8) or
                    (data[offset + 3].toLong() and 0xFF)

            val tsMillis = if (sec in 300_000_000L..2_500_000_000L) {
                SleepRecordParser.deviceSecondsToLocalMillis(sec)
            } else if (sec in 1_500_000_000L..2_500_000_000L) {
                sec * 1000L
            } else {
                continue
            }

            val value = data[offset + 4].toInt() and 0xFF
            if (value in minVal..maxVal && value != 0) {
                samples.add(HistorySample(timestamp = tsMillis, value = value))
            }
        }
        return samples
    }


    /**
     * Parses an incoming buffer of bytes from characteristic 0xB003.
     * Supports split and coalesced notifications, buffers up to 8 KiB, extracts valid
     * 0xAB frames, and drops corrupt bytes with logging.
     *
     * @return Pair of list of parsed Frames and remaining unconsumed bytes.
     */
    fun parseFramesFromStream(
        stream: ByteArray,
        onDroppedBytes: ((ByteArray) -> Unit)? = null,
        onCrcFailure: ((expectedCrc: Int, actualCrc: Int, frameBytes: ByteArray) -> Unit)? = null
    ): Pair<List<Frame>, ByteArray> {
        val frames = mutableListOf<Frame>()
        var offset = 0
        val size = stream.size

        while (offset < size) {
            // Find 0xAB header
            var headerIdx = -1
            for (i in offset until size) {
                if (stream[i] == FRAME_HEADER) {
                    headerIdx = i
                    break
                }
            }

            if (headerIdx == -1) {
                // No header found in remaining buffer
                val dropped = stream.copyOfRange(offset, size)
                if (dropped.isNotEmpty()) onDroppedBytes?.invoke(dropped)
                return Pair(frames, byteArrayOf())
            }

            if (headerIdx > offset) {
                // Bytes before 0xAB are garbage/dropped
                val dropped = stream.copyOfRange(offset, headerIdx)
                onDroppedBytes?.invoke(dropped)
                offset = headerIdx
            }

            // Need at least 6 bytes for the header (AB, flag, len(2), crc(2))
            if (size - offset < 6) {
                // Incomplete frame header, keep remainder in buffer
                return Pair(frames, stream.copyOfRange(offset, size))
            }

            val flag = stream[offset + 1]
            val payloadLen = ((stream[offset + 2].toInt() and 0xFF) shl 8) or
                    (stream[offset + 3].toInt() and 0xFF)
            val expectedCrc = ((stream[offset + 4].toInt() and 0xFF) shl 8) or
                    (stream[offset + 5].toInt() and 0xFF)

            // Sanity check length: protocol limit 8192 bytes
            if (payloadLen < 3 || payloadLen > 8192) {
                // Invalid length, drop this 0xAB byte and continue search
                onDroppedBytes?.invoke(byteArrayOf(stream[offset]))
                offset += 1
                continue
            }

            val totalFrameLen = 6 + payloadLen
            if (size - offset < totalFrameLen) {
                // Incomplete frame, wait for more data from subsequent BLE chunks
                return Pair(frames, stream.copyOfRange(offset, size))
            }

            // Full frame arrived
            val payload = ByteArray(payloadLen)
            System.arraycopy(stream, offset + 6, payload, 0, payloadLen)

            val calculatedCrc = crc16Arc(payload)
            val crcOk = (calculatedCrc == expectedCrc)

            if (!crcOk) {
                val badFrame = stream.copyOfRange(offset, offset + totalFrameLen)
                onCrcFailure?.invoke(expectedCrc, calculatedCrc, badFrame)
                // Skip past this corrupt frame or try next byte
                offset += 1
                continue
            }

            val key = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
            val keyFlag = payload[2]
            val data = if (payloadLen > 3) {
                ByteArray(payloadLen - 3).also {
                    System.arraycopy(payload, 3, it, 0, payloadLen - 3)
                }
            } else {
                byteArrayOf()
            }

            frames.add(
                Frame(
                    flag = flag,
                    key = key,
                    keyFlag = keyFlag,
                    data = data,
                    fullPayload = payload,
                    crcOk = true
                )
            )

            offset += totalFrameLen
        }

        return Pair(frames, byteArrayOf())
    }

    /**
     * Parses biometric readings from ANY frame with key 0x0224 (HR) or 0x024E (SpO2),
     * regardless of frame flag (0x01, 0x11, 0x21).
     *
     * Payload layout after frame header:
     *   key(2 BE) + keyFlag(1) + data
     *   data: timestamp(4 BE) + value(1) + optional trailing byte
     *   value = data[4] (the byte immediately after the 4-byte timestamp)
     *
     * Value ranges:
     *   HR (0x0224): 30..240 BPM (value 0 ignored)
     *   SpO2 (0x024E): 70..100% (value 0 ignored)
     */
    fun parseBiometricFrame(frame: Frame): Pair<HeartRateSample?, OxygenSaturationSample?> {
        val key = frame.key
        if (key != KEY_HR_PUSH && key != KEY_SPO2_PUSH) {
            return Pair(null, null)
        }

        val data = frame.data
        if (data.size < 5) {
            return Pair(null, null)
        }

        val now = System.currentTimeMillis()
        var latestHr: HeartRateSample? = null
        var latestSpo2: OxygenSaturationSample? = null

        // Support both single 5-6 byte record and multiple 6-byte records
        val recordCount = (data.size / 6).coerceAtLeast(1)
        for (i in 0 until recordCount) {
            val offset = i * 6
            if (offset + 4 >= data.size) break
            val value = data[offset + 4].toInt() and 0xFF

            if (value == 0) {
                // value 0 = ignore (no reading yet)
                continue
            }

            if (key == KEY_HR_PUSH) {
                if (value in 30..240) {
                    latestHr = HeartRateSample(bpm = value, confidence = 100, timestamp = now)
                }
            } else if (key == KEY_SPO2_PUSH) {
                if (value in 70..100) {
                    latestSpo2 = OxygenSaturationSample(percentage = value.toFloat(), timestamp = now)
                }
            }
        }

        return Pair(latestHr, latestSpo2)
    }

    /**
     * Backward-compatible alias for parseBiometricFrame.
     */
    fun parseBiometricPush(frame: Frame): Pair<HeartRateSample?, OxygenSaturationSample?> {
        return parseBiometricFrame(frame)
    }

    /**
     * Direct parser for a biometric payload byte array (key(2) + keyFlag(1) + data).
     * e.g. "02 24 00 32 5D 6F 59 3F 00" -> bpm=63
     */
    fun parseBiometricPayload(payload: ByteArray): Pair<HeartRateSample?, OxygenSaturationSample?> {
        if (payload.size < 8) return Pair(null, null)
        val key = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val keyFlag = payload[2]
        val data = payload.copyOfRange(3, payload.size)
        val frame = Frame(
            flag = FLAG_ACK,
            key = key,
            keyFlag = keyFlag,
            data = data,
            fullPayload = payload,
            crcOk = true
        )
        return parseBiometricFrame(frame)
    }

    /**
     * Checks if key 0x0609 frame indicates measurement complete.
     * Measurement status complete received from ring:
     *   06 09 00 03 05 00  (HR done, type=0x03)
     *   06 09 00 09 05 00  (SpO2 done, type=0x09)
     * Payload ends with [type, 0x05, 0x00].
     */
    fun isMeasurementCompletedStatus(frame: Frame, expectedType: Byte? = null): Boolean {
        if (frame.key != KEY_MEASURE_CONTROL) return false
        val p = frame.fullPayload
        if (p.size >= 5) {
            val len = p.size
            val has0500 = (p[len - 2].toInt() and 0xFF == 0x05) && (p[len - 1].toInt() and 0xFF == 0x00)
            if (!has0500) return false
            if (expectedType != null) {
                return len >= 3 && p[len - 3] == expectedType
            }
            return true
        }
        return false
    }

    /**
     * Returns the measurement type (0x03 HR or 0x09 SpO2) if frame is a 0609 completion frame.
     */
    fun getCompletedMeasurementType(frame: Frame): Byte? {
        if (frame.key != KEY_MEASURE_CONTROL) return null
        val p = frame.fullPayload
        if (p.size >= 5) {
            val len = p.size
            if ((p[len - 2].toInt() and 0xFF == 0x05) && (p[len - 1].toInt() and 0xFF == 0x00)) {
                return p[len - 3]
            }
        }
        return null
    }

    /**
     * Checks function list reply payload (from Key 0x0263) to determine if password authentication is required.
     * "If payload byte at index 0x2C has bit0 set -> password required"
     */
    fun isPasswordRequired(functionListPayload: ByteArray): Boolean {
        return if (functionListPayload.size > 0x2C) {
            (functionListPayload[0x2C].toInt() and 0x01) != 0
        } else {
            false
        }
    }

    /**
     * Standard GATT battery fallback parser.
     */
    fun parseBatteryLevel(data: ByteArray): RingBattery? {
        if (data.isEmpty()) return null
        val level = (data[0].toInt() and 0xFF).coerceIn(0, 100)
        val isCharging = if (data.size > 1) (data[1].toInt() and 0x01) != 0 else false
        return RingBattery(level = level, isCharging = isCharging)
    }

    /**
     * Best-effort parser for real steps today payload from key 0x051A.
     * Expects plausible range 0..100000.
     * Logs payload hex and returns null if uncertain (no fake steps).
     */
    fun parseStepsFrame(frame: Frame): StepData? {
        if (frame.key != KEY_STEPS_PUSH) return null
        val data = frame.data
        val now = System.currentTimeMillis()

        if (data.size < 4) {
            return null
        }

        fun u8(idx: Int): Long = (data[idx].toLong() and 0xFF)
        fun u16BE(idx: Int): Long = (u8(idx) shl 8) or u8(idx + 1)
        fun u16LE(idx: Int): Long = u8(idx) or (u8(idx + 1) shl 8)
        fun u24BE(idx: Int): Long = (u8(idx) shl 16) or (u8(idx + 1) shl 8) or u8(idx + 2)
        fun u24LE(idx: Int): Long = u8(idx) or (u8(idx + 1) shl 8) or (u8(idx + 2) shl 16)
        fun u32BE(idx: Int): Long = (u8(idx) shl 24) or (u8(idx + 1) shl 16) or (u8(idx + 2) shl 8) or u8(idx + 3)
        fun u32LE(idx: Int): Long = u8(idx) or (u8(idx + 1) shl 8) or (u8(idx + 2) shl 16) or (u8(idx + 3) shl 24)

        // RWfit common step offsets:
        // Case 1: 3-byte Date prefix (Year, Month, Day) followed by 4-byte steps (BE or LE)
        // e.g. [year, month, day, steps(4), calories(4), distance(4)]
        if (data.size >= 7 && data[0].toInt() in 20..35 && data[1].toInt() in 1..12 && data[2].toInt() in 1..31) {
            val stepsBE = u32BE(3)
            val stepsLE = u32LE(3)
            val chosenSteps = if (stepsBE in 0L..100000L) stepsBE else if (stepsLE in 0L..100000L) stepsLE else null
            if (chosenSteps != null) {
                var cal = (chosenSteps * 0.04).toInt()
                var dist = chosenSteps * 0.75
                if (data.size >= 11) {
                    val calVal = u32BE(7).toInt()
                    if (calVal in 0..5000) cal = calVal
                }
                if (data.size >= 15) {
                    val distVal = u32BE(11).toDouble()
                    if (distVal in 0.0..100000.0) dist = distVal
                }
                return StepData(totalSteps = chosenSteps, caloriesKcal = cal, distanceMeters = dist, timestamp = now)
            }
        }

        // Case 2: 4-byte Unix timestamp or header, followed by 4-byte steps
        if (data.size >= 8) {
            val stepsBE = u32BE(4)
            val stepsLE = u32LE(4)
            val chosenSteps = if (stepsBE in 0L..100000L) stepsBE else if (stepsLE in 0L..100000L) stepsLE else null
            if (chosenSteps != null) {
                val cal = (chosenSteps * 0.04).toInt()
                val dist = chosenSteps * 0.75
                return StepData(totalSteps = chosenSteps, caloriesKcal = cal, distanceMeters = dist, timestamp = now)
            }
        }

        // Case 3: Steps starts at offset 0 (4 bytes BE or LE, or 3 bytes, or 2 bytes)
        val s0_32BE = u32BE(0)
        val s0_32LE = u32LE(0)
        val s0_24BE = u24BE(0)
        val s0_24LE = u24LE(0)

        val candidate = listOf(s0_32BE, s0_32LE, s0_24BE, s0_24LE).firstOrNull { it in 0L..100000L && it != 0L }
            ?: if (data.all { it.toInt() == 0 }) 0L else null

        if (candidate != null) {
            var cal = (candidate * 0.04).toInt()
            var dist = candidate * 0.75
            if (data.size >= 8) {
                val calCandidate = u32BE(4).toInt()
                if (calCandidate in 0..5000) cal = calCandidate
            }
            return StepData(totalSteps = candidate, caloriesKcal = cal, distanceMeters = dist, timestamp = now)
        }

        // If uncertain, return null (caller will log payload hex)
        return null
    }

    /** Segments already written to AppLog (so merged pages do not log the same segment twice). */
    private val loggedSleepSegments = HashSet<String>()

    private fun logNewSleepSegments(segments: List<SleepSegment>) {
        for (seg in segments) {
            val key = "${seg.start}-${seg.end}-${seg.rawCode}"
            val isNew = synchronized(loggedSleepSegments) { loggedSleepSegments.add(key) }
            if (isNew) {
                AppLog.i(
                    "SleepSeg",
                    "SleepSeg stage=${SleepRecordParser.stageLogName(seg.stage)} " +
                        "start=${SleepRecordParser.formatClock(seg.start)} " +
                        "end=${SleepRecordParser.formatClock(seg.end)} " +
                        "dur=${seg.durationMin}m raw=0x${"%02X".format(seg.rawCode)}"
                )
            }
        }
    }

    /**
     * Parses a real 0x0505 sleep history page (BLE_KEY_SLEEP).
     *
     * Payload after key + keyFlag = repeated 7-byte records:
     *   [0..3] big-endian seconds since 2000-01-01 (device local wall clock)
     *   [4]    stage code (0x01 Deep, 0x02 Light, 0x04 REM, 0x11 Awake, 0x00/0x22 Awake)
     *   [5..6] usually 00 00
     *
     * Records from every page are merged (SleepPageStore) and sorted, then split into nights.
     * Returns the night that contains this page's newest record, so the last page of a sync
     * returns the full merged night. sessionStart = first timestamp, sessionEnd = last timestamp;
     * nothing is ever anchored to "now" and no stages are invented.
     * Returns null if the payload is empty, all zeroes, or holds no valid records.
     */
    fun parseSleepFrame(frame: Frame): SleepSession? {
        if (frame.key != KEY_SLEEP && frame.key != KEY_SLEEP_PUSH) return null
        val fullHex = frame.fullPayload.joinToString(" ") { "%02X".format(it) }
        val data = frame.data
        AppLog.i("Protocol", "parseSleepFrame: key=0x0505, flag=0x${"%02X".format(frame.flag)}, fullPayload hex: $fullHex (data bytes: ${data.size})")

        if (data.isEmpty() || data.all { it.toInt() == 0 }) {
            AppLog.i("Protocol", "parseSleepFrame: payload is empty or all zeroes; leaving sleep record empty")
            return null
        }

        val trailing = data.size % SleepRecordParser.RECORD_SIZE
        if (trailing != 0) {
            AppLog.w("Protocol", "parseSleepFrame: payload length ${data.size} is not a multiple of ${SleepRecordParser.RECORD_SIZE}; ignoring $trailing trailing byte(s)")
        }

        val page = SleepRecordParser.parseRecords(data)
        if (page.isEmpty()) {
            AppLog.w("Protocol", "parseSleepFrame: no valid 7-byte records in payload")
            return null
        }
        for (r in page) {
            if (!SleepRecordParser.isKnownCode(r.code)) {
                AppLog.w("Protocol", "parseSleepFrame: unknown stage code raw=0x${"%02X".format(r.code)} at ${SleepRecordParser.formatClock(r.tsMillis)}; treated as Awake")
            }
        }

        SleepPageStore.addPage(page)
        val groups = SleepRecordParser.groupRecords(SleepPageStore.snapshot())
        val pageTimestamps = page.map { it.tsMillis }.toSet()
        val touched = groups.count { g -> g.any { it.tsMillis in pageTimestamps } }
        if (touched > 1) {
            AppLog.w("Protocol", "parseSleepFrame: page spans $touched separate nights; returning the latest one")
        }

        val pageNewest = page.maxOf { it.tsMillis }
        val group = groups.firstOrNull { g -> pageNewest >= g.first().tsMillis && pageNewest <= g.last().tsMillis }
        if (group == null) {
            AppLog.w("Protocol", "parseSleepFrame: could not place page records into a night")
            return null
        }

        val segments = SleepRecordParser.segmentsFromRecords(group)
        if (segments.isEmpty()) {
            AppLog.i("Protocol", "parseSleepFrame: night has fewer than 2 distinct records; no stage segments yet")
            return null
        }
        logNewSleepSegments(segments)

        // A1-S9: score hidden until matched; qualityScore stays null.
        val session = SleepRecordParser.sessionFromSegments(segments)
        val status = if (session.isCompleted()) "COMPLETED" else "IN_PROGRESS"

        AppLog.i(
            "Protocol",
            "parseSleepFrame: session start=${SleepRecordParser.formatClock(session.startTime)} " +
                "end=${SleepRecordParser.formatClock(session.endTime)} " +
                "dur=${session.durationMinutes}m segments=${segments.size} status=$status"
        )
        return session
    }

    /**
     * Parses workout history frames (Key 0x0506, KEY_WORKOUT).
     * Format uses the same AB frame stack as sleep/HR history (READ 0x10).
     * Parses real workout sessions into WorkoutSession models.
     * Never invents synthetic sessions.
     */
    fun parseWorkoutFrame(frame: Frame): List<WorkoutSession> {
        if (frame.key != KEY_WORKOUT) return emptyList()
        val fullHex = frame.fullPayload.joinToString(" ") { "%02X".format(it) }
        val data = frame.data
        val dataHex = if (data.isNotEmpty()) data.joinToString(" ") { "%02X".format(it) } else "<empty>"
        AppLog.i(
            "Protocol",
            "parseWorkoutFrame: key=0x0506, flag=0x${"%02X".format(frame.flag)}, keyFlag=0x${"%02X".format(frame.keyFlag)}, " +
                "data bytes=${data.size}, fullPayload hex: $fullHex, data hex: $dataHex"
        )
        if (data.isEmpty() || data.all { it.toInt() == 0 }) {
            AppLog.i("Protocol", "parseWorkoutFrame: payload empty or all zeroes; leaving workout records empty")
            return emptyList()
        }
        val sessions = WorkoutRecordParser.parseRecords(data, fullHex)
        if (sessions.isEmpty()) {
            AppLog.i("Protocol", "parseWorkoutFrame: no valid workout sessions extracted from payload")
        } else {
            AppLog.i("Protocol", "parseWorkoutFrame: extracted ${sessions.size} workout session(s)")
        }
        return sessions
    }
}
