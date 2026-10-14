package com.galaxy.ring.ble

import com.galaxy.ring.data.ScreenBrightness

/**
 * Probe helper for the 4-level ring screen brightness (Off | Dim | Soft | Strong).
 *
 * The working BLE key/payload is not known yet, so the caller tries [orderedCandidates] one by one,
 * logs every TX/RX with [describe], and caches the first candidate the ring answers
 * (MonitoringSettings.brightnessKey / brightnessShape).
 * Candidate keys: 0x0285, 0x0208, 0x0266, 0x0306, 0x0307 with level 0/1/2/3.
 * Payload shapes are probe guesses: [level] and [on(0/1), level].
 */
object BrightnessProbe {

    val CANDIDATE_KEYS: List<Int> = listOf(0x0285, 0x0208, 0x0266, 0x0306, 0x0307)

    const val SHAPE_LEVEL_ONLY = 0
    const val SHAPE_ON_PLUS_LEVEL = 1

    data class Candidate(val key: Int, val shape: Int)

    fun candidates(): List<Candidate> = CANDIDATE_KEYS.flatMap { key ->
        listOf(Candidate(key, SHAPE_LEVEL_ONLY), Candidate(key, SHAPE_ON_PLUS_LEVEL))
    }

    /** The cached working candidate is tried first, then the rest in probe order. */
    fun orderedCandidates(cachedKey: Int?, cachedShape: Int?): List<Candidate> {
        val all = candidates()
        if (cachedKey == null || cachedShape == null) return all
        val cached = Candidate(cachedKey, cachedShape)
        return listOf(cached) + all.filter { it != cached }
    }

    fun payload(shape: Int, brightness: ScreenBrightness): ByteArray = when (shape) {
        SHAPE_ON_PLUS_LEVEL -> byteArrayOf(
            (if (brightness == ScreenBrightness.OFF) 0 else 1).toByte(),
            brightness.level.toByte()
        )
        else -> byteArrayOf(brightness.level.toByte())
    }

    fun buildCommand(candidate: Candidate, brightness: ScreenBrightness): ByteArray =
        Protocol.encodeFrame(
            flag = Protocol.FLAG_COMMAND,
            key = candidate.key,
            keyFlag = Protocol.KEY_FLAG_WRITE,
            data = payload(candidate.shape, brightness)
        )

    /** True when an incoming frame carries the key that was just probed (an answer from the ring). */
    fun isResponseFor(frame: Protocol.Frame, candidate: Candidate): Boolean = frame.key == candidate.key

    /** Text for AppLog TX lines. */
    fun describe(candidate: Candidate, brightness: ScreenBrightness): String {
        val hex = payload(candidate.shape, brightness).joinToString(" ") { "%02X".format(it) }
        return "Brightness probe TX key=0x%04X shape=%d level=%d (%s) payload=[%s]".format(
            candidate.key, candidate.shape, brightness.level, brightness.label, hex
        )
    }
}
