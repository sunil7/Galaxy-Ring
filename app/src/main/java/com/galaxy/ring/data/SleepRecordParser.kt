package com.galaxy.ring.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One contiguous range of a single sleep stage, derived ONLY from real 0x0505 ring records.
 * rawCode is the ring's stage byte (-1 when the segment was rebuilt from storage).
 */
data class SleepSegment(
    val start: Long,
    val end: Long,
    val stage: SleepStage,
    val durationMin: Long,
    val rawCode: Int = -1
)

/**
 * Parser for the 0x0505 sleep history payload (after key + keyFlag 0x10):
 * repeated 7-byte records
 *   [0..3] big-endian seconds since 2000-01-01 (device local wall clock)
 *   [4]    stage code
 *   [5..6] usually 00 00
 *
 * duration_i = ts_{i+1} - ts_i. The last record only marks the end of the session.
 * Nothing here ever uses "now" and nothing is invented.
 */
object SleepRecordParser {
    const val RECORD_SIZE = 7
    const val NIGHT_GAP_MS = 4L * 60L * 60L * 1000L
    const val COMPLETED_AFTER_END_MS = 2L * 60L * 1000L

    private const val EPOCH_2000_UTC_MS = 946_684_800_000L
    private const val MIN_VALID_SEC = 473_385_600L      // 2015-01-01 as seconds since 2000-01-01
    private const val MAX_VALID_SEC = 3_155_760_000L    // 2100-01-01 as seconds since 2000-01-01
    private val KNOWN_CODES = setOf(0x00, 0x01, 0x02, 0x04, 0x11, 0x22)

    data class RawRecord(val tsMillis: Long, val code: Int)

    /** Converts device wall-clock seconds since 2000-01-01 into epoch millis in the phone's local zone. */
    fun deviceSecondsToLocalMillis(sec: Long): Long {
        val wall = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = EPOCH_2000_UTC_MS + sec * 1000L
        }
        return Calendar.getInstance().apply {
            clear()
            set(
                wall.get(Calendar.YEAR),
                wall.get(Calendar.MONTH),
                wall.get(Calendar.DAY_OF_MONTH),
                wall.get(Calendar.HOUR_OF_DAY),
                wall.get(Calendar.MINUTE),
                wall.get(Calendar.SECOND)
            )
        }.timeInMillis
    }

    /** 0x01 Light, 0x02 Deep, 0x04 REM, 0x00/0x11/0x22 Awake. Unknown codes are treated as Awake (callers log them). */
    fun stageForCode(code: Int): SleepStage = when (code and 0xFF) {
        0x01 -> SleepStage.LIGHT
        0x02 -> SleepStage.DEEP
        0x04 -> SleepStage.REM
        else -> SleepStage.AWAKE
    }

    fun isKnownCode(code: Int): Boolean = (code and 0xFF) in KNOWN_CODES

    /** Parses every complete 7-byte record; records with implausible timestamps are skipped. */
    fun parseRecords(data: ByteArray): List<RawRecord> {
        val out = ArrayList<RawRecord>(data.size / RECORD_SIZE)
        var off = 0
        while (off + RECORD_SIZE <= data.size) {
            val sec = ((data[off].toLong() and 0xFF) shl 24) or
                ((data[off + 1].toLong() and 0xFF) shl 16) or
                ((data[off + 2].toLong() and 0xFF) shl 8) or
                (data[off + 3].toLong() and 0xFF)
            if (sec in MIN_VALID_SEC..MAX_VALID_SEC) {
                out.add(RawRecord(deviceSecondsToLocalMillis(sec), data[off + 4].toInt() and 0xFF))
            }
            off += RECORD_SIZE
        }
        return out
    }

    /** Sorts records and splits them into nights wherever two records are more than 4 hours apart. */
    fun groupRecords(records: Collection<RawRecord>): List<List<RawRecord>> {
        val sorted = records.sortedBy { it.tsMillis }
        val groups = ArrayList<List<RawRecord>>()
        var current = ArrayList<RawRecord>()
        for (r in sorted) {
            val prev = current.lastOrNull()
            val shouldSplit = if (prev != null) {
                val gap = r.tsMillis - prev.tsMillis
                val prevAwake = stageForCode(prev.code) == SleepStage.AWAKE
                (prevAwake && gap > NIGHT_GAP_MS) || gap > 12L * 60L * 60L * 1000L
            } else {
                false
            }
            if (shouldSplit) {
                groups.add(current)
                current = ArrayList()
            }
            current.add(r)
        }
        if (current.isNotEmpty()) groups.add(current)
        return groups
    }

    /** duration_i = ts_{i+1} - ts_i; zero-length gaps are skipped. */
    fun segmentsFromRecords(sorted: List<RawRecord>): List<SleepSegment> {
        val out = ArrayList<SleepSegment>()
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            val dur = b.tsMillis - a.tsMillis
            if (dur <= 0L) continue
            out.add(SleepSegment(a.tsMillis, b.tsMillis, stageForCode(a.code), dur / 60_000L, a.code))
        }
        return out
    }

    /** sessionStart = first timestamp, sessionEnd = last timestamp. */
    fun sessionFromSegments(segments: List<SleepSegment>): SleepSession = SleepSession(
        startTime = segments.first().start,
        endTime = segments.last().end,
        stages = segments.map { SleepStageRecord(it.stage, it.start, it.end) }
    )

    fun sessionFromGroup(group: List<RawRecord>): SleepSession? {
        val segments = segmentsFromRecords(group)
        return if (segments.isEmpty()) null else sessionFromSegments(segments)
    }

    fun buildSessions(records: Collection<RawRecord>): List<SleepSession> =
        groupRecords(records).mapNotNull { sessionFromGroup(it) }

    // ---- Persistence (stored in SleepSessionEntity.stagesJson as "start,end,STAGE;start,end,STAGE") ----

    fun encodeStages(stages: List<SleepStageRecord>): String =
        stages.filter { it.endTime > it.startTime }
            .joinToString(";") { "${it.startTime},${it.endTime},${it.stage.name}" }

    fun decodeSegments(encoded: String): List<SleepSegment> {
        if (encoded.isBlank()) return emptyList()
        return encoded.split(';').mapNotNull { part ->
            val f = part.split(',')
            if (f.size != 3) return@mapNotNull null
            val s = f[0].toLongOrNull() ?: return@mapNotNull null
            val e = f[1].toLongOrNull() ?: return@mapNotNull null
            val st = runCatching { SleepStage.valueOf(f[2]) }.getOrNull() ?: return@mapNotNull null
            if (e <= s) null else SleepSegment(s, e, st, (e - s) / 60_000L)
        }
    }

    fun segmentsFromStages(stages: List<SleepStageRecord>): List<SleepSegment> =
        stages.filter { it.endTime > it.startTime }
            .sortedBy { it.startTime }
            .map { SleepSegment(it.startTime, it.endTime, it.stage, (it.endTime - it.startTime) / 60_000L) }

    // ---- Display helpers ----

    fun stageLabel(stage: SleepStage): String = when (stage) {
        SleepStage.DEEP -> "Deep sleep"
        SleepStage.LIGHT -> "Light sleep"
        SleepStage.REM -> "REM sleep"
        SleepStage.AWAKE -> "Awake"
    }

    fun stageLogName(stage: SleepStage): String = when (stage) {
        SleepStage.DEEP -> "Deep"
        SleepStage.LIGHT -> "Light"
        SleepStage.REM -> "REM"
        SleepStage.AWAKE -> "Awake"
    }

    fun formatClock(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    /** e.g. "Deep sleep  23:48 – 00:05  (17 min)" */
    fun formatSegment(seg: SleepSegment): String =
        "${stageLabel(seg.stage)}  ${formatClock(seg.start)} – ${formatClock(seg.end)}  (${seg.durationMin} min)"

    fun formatDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /** "Mid-Night" is shown only when the real range crosses local midnight. */
    fun crossesLocalMidnight(startMs: Long, endMs: Long): Boolean {
        val a = Calendar.getInstance().apply { timeInMillis = startMs }
        val b = Calendar.getInstance().apply { timeInMillis = endMs }
        return a.get(Calendar.YEAR) != b.get(Calendar.YEAR) ||
            a.get(Calendar.DAY_OF_YEAR) != b.get(Calendar.DAY_OF_YEAR)
    }
}

/** Sleep that ended more than 2 minutes ago is COMPLETED, not "currently sleeping". */
fun SleepSession.isCompleted(nowMs: Long = System.currentTimeMillis()): Boolean =
    endTime < nowMs - SleepRecordParser.COMPLETED_AFTER_END_MS

/** Real segments saved for this night (empty when the night was saved before segments were stored). */
fun SleepSessionEntity.toSegments(): List<SleepSegment> = SleepRecordParser.decodeSegments(stagesJson)

/**
 * Accumulates records from every 0x0505 page so pages merge into one session per night.
 * Records are keyed by timestamp, so re-reading the same page is harmless.
 */
object SleepPageStore {
    private const val KEEP_MS = 14L * 24L * 60L * 60L * 1000L
    private val lock = Any()
    private val codes = HashMap<Long, Int>()

    fun addPage(page: List<SleepRecordParser.RawRecord>) {
        if (page.isEmpty()) return
        synchronized(lock) {
            for (r in page) codes[r.tsMillis] = r.code
            val newest = codes.keys.maxOrNull() ?: return
            val cutoff = newest - KEEP_MS
            codes.keys.removeAll { it < cutoff }
        }
    }

    fun snapshot(): List<SleepRecordParser.RawRecord> = synchronized(lock) {
        codes.map { SleepRecordParser.RawRecord(it.key, it.value) }.sortedBy { it.tsMillis }
    }

    fun clear() {
        synchronized(lock) { codes.clear() }
    }
}
