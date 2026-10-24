package com.galaxy.ring.data

import com.galaxy.ring.debug.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parser for 0x0506 workout history payloads.
 * Conforms to RWFit SportItemBean & SportResultBean data layout on the AB frame stack.
 *
 * Mappings:
 *   1 -> Walk
 *   2 -> Run
 *   3 -> Jog
 *   4 -> Hike
 *   0 -> Other (also 5..7 known sports)
 *   unknown -> Activity
 *
 * Never invents synthetic sessions.
 */
object WorkoutRecordParser {

    private const val TAG = "WorkoutRecordParser"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Maps known sportType integers to Walk/Run/Jog/Hike/Other; unknown -> "Activity".
     */
    fun mapSportType(type: Int): String = when (type) {
        1 -> "Walk"
        2 -> "Run"
        3 -> "Jog"
        4 -> "Hike"
        0 -> "Other"
        5 -> "Other" // Cycling / Ride
        6 -> "Other" // Indoor / Gym
        7 -> "Other" // Free training
        else -> "Activity"
    }

    // ---- Encoding & Decoding helpers for Room persistence ----

    fun encodePacePhases(phases: List<PacePhase>): String =
        phases.joinToString(";") {
            "${it.phaseIndex},${it.durationSeconds},${it.paceSeconds},${it.distanceMeters},${it.steps}"
        }

    fun decodePacePhases(encoded: String): List<PacePhase> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(';').mapNotNull { part ->
            val f = part.split(',')
            if (f.size != 5) return@mapNotNull null
            val idx = f[0].toIntOrNull() ?: return@mapNotNull null
            val dur = f[1].toLongOrNull() ?: return@mapNotNull null
            val pace = f[2].toLongOrNull() ?: return@mapNotNull null
            val dist = f[3].toDoubleOrNull() ?: return@mapNotNull null
            val steps = f[4].toLongOrNull() ?: return@mapNotNull null
            if (dist <= 0.0 || steps <= 0L) return@mapNotNull null
            PacePhase(idx, dur, pace, dist, steps)
        }
    }

    fun encodeHrPoints(points: List<SportHrPoint>): String =
        points.joinToString(";") {
            "${it.offsetSeconds},${it.timestamp},${it.bpm}"
        }

    fun decodeHrPoints(encoded: String): List<SportHrPoint> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(';').mapNotNull { part ->
            val f = part.split(',')
            if (f.size != 3) return@mapNotNull null
            val off = f[0].toLongOrNull() ?: return@mapNotNull null
            val ts = f[1].toLongOrNull() ?: return@mapNotNull null
            val bpm = f[2].toIntOrNull()?.takeIf { it in 30..240 } ?: return@mapNotNull null
            SportHrPoint(off, ts, bpm)
        }
    }

    // ---- Byte reading helpers ----

    private fun u8(data: ByteArray, idx: Int): Long = (data[idx].toLong() and 0xFF)
    private fun u16BE(data: ByteArray, idx: Int): Long = (u8(data, idx) shl 8) or u8(data, idx + 1)
    private fun u16LE(data: ByteArray, idx: Int): Long = u8(data, idx) or (u8(data, idx + 1) shl 8)
    private fun u32BE(data: ByteArray, idx: Int): Long =
        (u8(data, idx) shl 24) or (u8(data, idx + 1) shl 16) or (u8(data, idx + 2) shl 8) or u8(data, idx + 3)
    private fun u32LE(data: ByteArray, idx: Int): Long =
        u8(data, idx) or (u8(data, idx + 1) shl 8) or (u8(data, idx + 2) shl 16) or (u8(data, idx + 3) shl 24)

    /**
     * Validates and converts 4-byte raw timestamp to phone epoch millis.
     * Supports both device Y2K seconds (2000-01-01 epoch) and standard Unix seconds.
     */
    private fun resolveTimestampMillis(sec: Long): Long? {
        return when (sec) {
            in 300_000_000L..2_500_000_000L -> SleepRecordParser.deviceSecondsToLocalMillis(sec)
            in 1_500_000_000L..2_500_000_000L -> sec * 1000L
            else -> null
        }
    }

    /**
     * Parses a single session chunk starting at [startOffset] within [data].
     * Returns Pair(WorkoutSession, nextOffset), or null if not a valid session.
     */
    private fun parseSingleSessionChunk(
        data: ByteArray,
        startOffset: Int,
        rawPayloadHex: String
    ): Pair<WorkoutSession, Int>? {
        val remaining = data.size - startOffset
        if (remaining < 17) {
            return null
        }

        // Check layout 1: Timestamp at offset 0 (4 bytes), sportType at offset 4 (1 byte)
        // [ts(4)] [sportType(1)] [duration(4)] [distance(4)] [steps(4)] [cal(4)]...
        var tsSec = u32BE(data, startOffset)
        var startTime = resolveTimestampMillis(tsSec)
        var sportType = data[startOffset + 4].toInt() and 0xFF
        var headerOffset = startOffset

        // Check layout 2: sportType at offset 0 (1 byte), timestamp at offset 1 (4 bytes)
        // [sportType(1)] [ts(4)] [duration(4)] [distance(4)] [steps(4)] [cal(4)]...
        if (startTime == null && remaining >= 18) {
            val altTs = u32BE(data, startOffset + 1)
            val altStart = resolveTimestampMillis(altTs)
            if (altStart != null) {
                sportType = data[startOffset].toInt() and 0xFF
                startTime = altStart
                headerOffset = startOffset + 1
            }
        }

        // Check LE timestamp as fallback
        if (startTime == null) {
            val tsLE = u32LE(data, startOffset)
            val startLE = resolveTimestampMillis(tsLE)
            if (startLE != null) {
                startTime = startLE
                sportType = data[startOffset + 4].toInt() and 0xFF
                headerOffset = startOffset
            }
        }

        if (startTime == null) {
            return null
        }

        val sportTypeName = mapSportType(sportType)

        // Read totalSportTime (duration in seconds)
        var durationSec: Long = 0L
        var distMeters: Double = 0.0
        var totalSteps: Long = 0L
        var totalCal: Int = 0
        var currentIdx = headerOffset + 5

        // Read duration (4 bytes BE or 2 bytes BE if short)
        if (currentIdx + 4 <= data.size) {
            val d32 = u32BE(data, currentIdx)
            if (d32 in 1L..86400L) {
                durationSec = d32
                currentIdx += 4
            } else {
                val d16 = u16BE(data, currentIdx)
                if (d16 in 1L..86400L) {
                    durationSec = d16
                    currentIdx += 2
                }
            }
        }

        // If duration is 0 or implausible, cannot form a real session
        if (durationSec <= 0L || durationSec > 86400L) {
            AppLog.w(TAG, "Implausible duration $durationSec at offset $startOffset; skipping chunk")
            return null
        }

        val endTime = startTime + durationSec * 1000L
        val dateStr = synchronized(dateFormat) { dateFormat.format(Date(startTime)) }

        // Distance (4 bytes BE, meters)
        if (currentIdx + 4 <= data.size) {
            val dist = u32BE(data, currentIdx).toDouble()
            if (dist in 0.0..500_000.0) {
                distMeters = dist
                currentIdx += 4
            }
        }

        // Steps (4 bytes BE)
        if (currentIdx + 4 <= data.size) {
            val st = u32BE(data, currentIdx)
            if (st in 0L..200_000L) {
                totalSteps = st
                currentIdx += 4
            }
        }

        // Calories (4 or 2 bytes BE, kcal)
        if (currentIdx + 4 <= data.size) {
            val cal = u32BE(data, currentIdx).toInt()
            if (cal in 0..20_000) {
                totalCal = cal
                currentIdx += 4
            } else if (currentIdx + 2 <= data.size) {
                val cal16 = u16BE(data, currentIdx).toInt()
                if (cal16 in 0..20_000) {
                    totalCal = cal16
                    currentIdx += 2
                }
            }
        }

        // Optional SportResultBean metrics: avgHr(1), minHr(1), maxHr(1), pace(2), speed(2), cadence(2)
        var avgHr: Int? = null
        var minHr: Int? = null
        var maxHr: Int? = null
        var avgPaceSecondsPerKm: Double? = null
        var avgSpeedKmH: Double? = null
        var avgCadenceSpm: Int? = null

        if (currentIdx + 3 <= data.size) {
            val hrAvg = data[currentIdx].toInt() and 0xFF
            val hrMin = data[currentIdx + 1].toInt() and 0xFF
            val hrMax = data[currentIdx + 2].toInt() and 0xFF
            if (hrAvg in 30..240 && hrMin in 30..240 && hrMax in 30..240) {
                avgHr = hrAvg
                minHr = hrMin
                maxHr = hrMax
                currentIdx += 3

                if (currentIdx + 2 <= data.size) {
                    val pace = u16BE(data, currentIdx)
                    if (pace in 60L..3600L) {
                        avgPaceSecondsPerKm = pace.toDouble()
                        currentIdx += 2
                    }
                }

                if (currentIdx + 2 <= data.size) {
                    val speedRaw = u16BE(data, currentIdx)
                    // Stored either in 0.01 km/h or direct km/h
                    if (speedRaw in 1L..10000L) {
                        avgSpeedKmH = if (speedRaw > 100) speedRaw / 100.0 else speedRaw.toDouble()
                        currentIdx += 2
                    }
                }

                if (currentIdx + 2 <= data.size) {
                    val cad = u16BE(data, currentIdx).toInt()
                    if (cad in 20..300) {
                        avgCadenceSpm = cad
                        currentIdx += 2
                    }
                }
            }
        }

        // Embedded metricPaceItems (if count byte / length is present)
        val pacePhases = mutableListOf<PacePhase>()
        if (currentIdx + 1 <= data.size) {
            val count = data[currentIdx].toInt() and 0xFF
            if (count in 1..50 && currentIdx + 1 + count * 2 <= data.size) {
                currentIdx += 1
                val phaseDist = if (distMeters > 0.0) distMeters / count else 0.0
                val phaseSteps = if (totalSteps > 0L) totalSteps / count else 0L
                for (i in 0 until count) {
                    val paceVal = u16BE(data, currentIdx)
                    // Omit split phase when its distance or steps are unknown. No fabricated default.
                    if (phaseDist > 0.0 && phaseSteps > 0L) {
                        pacePhases.add(
                            PacePhase(
                                phaseIndex = i + 1,
                                durationSeconds = paceVal,
                                paceSeconds = paceVal,
                                distanceMeters = phaseDist,
                                steps = phaseSteps
                            )
                        )
                    }
                    currentIdx += 2
                }
            }
        }

        // Embedded sportHrItems (if count byte / length is present or sequential HR stream)
        val hrPoints = mutableListOf<SportHrPoint>()
        if (currentIdx + 1 <= data.size) {
            val hrCount = data[currentIdx].toInt() and 0xFF
            if (hrCount in 1..250 && currentIdx + 1 + hrCount <= data.size) {
                currentIdx += 1
                val intervalSec = if (durationSec > 0 && hrCount > 0) durationSec / hrCount else 60L
                var lastValidBpm: Int? = null
                var lastValidOffsetSec: Long = 0L
                for (i in 0 until hrCount) {
                    val bpm = data[currentIdx + i].toInt() and 0xFF
                    if (bpm in 30..240) {
                        val offsetSec = i * intervalSec
                        val deltaSec = offsetSec - lastValidOffsetSec
                        val prevBpm = lastValidBpm
                        if (prevBpm != null && kotlin.math.abs(bpm - prevBpm) > 40 && deltaSec in 0 until 2L) {
                            AppLog.w(TAG, "Dropping workout HR spike: $bpm bpm (prev: $prevBpm, interval: ${deltaSec}s)")
                        } else {
                            lastValidBpm = bpm
                            lastValidOffsetSec = offsetSec
                            hrPoints.add(
                                SportHrPoint(
                                    offsetSeconds = offsetSec,
                                    timestamp = startTime + offsetSec * 1000L,
                                    bpm = bpm
                                )
                            )
                        }
                    }
                }
                currentIdx += hrCount
            }
        }

        val session = WorkoutSession(
            id = 0L,
            date = dateStr,
            sportType = sportType,
            sportTypeName = sportTypeName,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSec,
            totalDistanceMeters = distMeters,
            totalSteps = totalSteps,
            totalCaloriesKcal = totalCal,
            avgHr = avgHr,
            minHr = minHr,
            maxHr = maxHr,
            avgPaceSecondsPerKm = avgPaceSecondsPerKm,
            avgSpeedKmH = avgSpeedKmH,
            avgCadenceSpm = avgCadenceSpm,
            pacePhases = pacePhases,
            hrPoints = hrPoints,
            rawPayloadHex = rawPayloadHex
        )

        return Pair(session, currentIdx)
    }

    /**
     * Parses workout records from the raw 0x0506 payload.
     * Safely extracts known fields, maps sport types, and logs any uncertain remainder.
     * Never invents synthetic sessions.
     */
    fun parseRecords(data: ByteArray, rawPayloadHex: String = ""): List<WorkoutSession> {
        if (data.isEmpty() || data.all { it.toInt() == 0 }) {
            AppLog.i(TAG, "Payload empty or all zeroes; leaving workout sessions empty")
            return emptyList()
        }

        val sessions = mutableListOf<WorkoutSession>()
        var offset = 0
        val size = data.size

        while (offset < size) {
            val result = parseSingleSessionChunk(data, offset, rawPayloadHex)
            if (result != null) {
                val (session, nextOffset) = result
                sessions.add(session)
                AppLog.i(
                    TAG,
                    "Parsed WorkoutSession: type=${session.sportTypeName} (${session.sportType}), " +
                        "start=${Date(session.startTime)}, dur=${session.durationSeconds}s, " +
                        "dist=${session.totalDistanceMeters}m, steps=${session.totalSteps}, cal=${session.totalCaloriesKcal}kcal"
                )
                if (nextOffset <= offset) {
                    // Prevent infinite loop if offset doesn't advance
                    break
                }
                offset = nextOffset
            } else {
                // If we cannot parse a full chunk starting at offset:
                // Check if remaining bytes are simply zero padding
                val remaining = data.copyOfRange(offset, size)
                if (remaining.all { it.toInt() == 0 }) {
                    // Just trailing zero padding
                    break
                }
                val unparsedHex = remaining.joinToString(" ") { "%02X".format(it) }
                AppLog.w(TAG, "Unparsed trailing bytes (${remaining.size} bytes): $unparsedHex")
                break
            }
        }

        return sessions
    }
}
