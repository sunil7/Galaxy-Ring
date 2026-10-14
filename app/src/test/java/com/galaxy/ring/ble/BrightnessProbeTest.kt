package com.galaxy.ring.ble

import com.galaxy.ring.data.ScreenBrightness
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrightnessProbeTest {

    @Test
    fun candidateKeysFollowProbeOrder() {
        assertEquals(listOf(0x0285, 0x0208, 0x0266, 0x0306, 0x0307), BrightnessProbe.CANDIDATE_KEYS)
        val all = BrightnessProbe.candidates()
        assertEquals(10, all.size) // 5 keys x 2 payload shapes
        assertEquals(0x0285, all.first().key)
    }

    @Test
    fun payloadShapesCarryTheLevel() {
        assertArrayEquals(
            byteArrayOf(0x02),
            BrightnessProbe.payload(BrightnessProbe.SHAPE_LEVEL_ONLY, ScreenBrightness.SOFT)
        )
        assertArrayEquals(
            byteArrayOf(0x01, 0x03),
            BrightnessProbe.payload(BrightnessProbe.SHAPE_ON_PLUS_LEVEL, ScreenBrightness.STRONG)
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x00),
            BrightnessProbe.payload(BrightnessProbe.SHAPE_ON_PLUS_LEVEL, ScreenBrightness.OFF)
        )
    }

    @Test
    fun commandIsAValidRwfitFrame() {
        val candidate = BrightnessProbe.Candidate(0x0208, BrightnessProbe.SHAPE_LEVEL_ONLY)
        val bytes = BrightnessProbe.buildCommand(candidate, ScreenBrightness.DIM)
        val frame = Protocol.parseFramesFromStream(bytes).first[0]
        assertEquals(0x0208, frame.key)
        assertEquals(Protocol.KEY_FLAG_WRITE, frame.keyFlag)
        assertArrayEquals(byteArrayOf(0x01), frame.data)
        assertTrue(frame.crcOk)
        assertTrue(BrightnessProbe.isResponseFor(frame, candidate))
    }

    @Test
    fun cachedCandidateIsTriedFirstWithoutDuplicates() {
        val ordered = BrightnessProbe.orderedCandidates(0x0266, BrightnessProbe.SHAPE_ON_PLUS_LEVEL)
        assertEquals(BrightnessProbe.Candidate(0x0266, BrightnessProbe.SHAPE_ON_PLUS_LEVEL), ordered.first())
        assertEquals(10, ordered.size)
        assertEquals(10, ordered.toSet().size)
        assertEquals(BrightnessProbe.candidates(), BrightnessProbe.orderedCandidates(null, null))
    }

    @Test
    fun describeLogsKeyShapeLevelAndPayload() {
        val text = BrightnessProbe.describe(
            BrightnessProbe.Candidate(0x0285, BrightnessProbe.SHAPE_LEVEL_ONLY),
            ScreenBrightness.SOFT
        )
        assertTrue(text.contains("key=0x0285"))
        assertTrue(text.contains("level=2"))
        assertTrue(text.contains("payload=[02]"))
    }
}
