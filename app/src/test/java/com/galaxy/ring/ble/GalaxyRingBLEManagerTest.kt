package com.galaxy.ring.ble

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.galaxy.ring.data.HeartRateSample
import com.galaxy.ring.data.OxygenSaturationSample
import com.galaxy.ring.data.StepData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GalaxyRingBLEManagerTest {

    private lateinit var context: Context
    private lateinit var bleManager: GalaxyRingBLEManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        bleManager = GalaxyRingBLEManager(context)
    }

    @Test
    fun testBuildCommandFrameStarts0xABAndCalculatesCrc16Arc() {
        val payload = byteArrayOf(0x02, 0x24) // Heart Rate command
        val frame = bleManager.buildCommandFrame(payload)

        // 1. Verify 0xAB start
        assertEquals(0xAB.toByte(), frame[0])

        // 2. Verify payload length
        assertEquals(2.toByte(), frame[1])

        // 3. Verify payload content
        assertEquals(0x02.toByte(), frame[2])
        assertEquals(0x24.toByte(), frame[3])

        // 4. Verify CRC16-ARC verification passes
        assertTrue(bleManager.verifyFrame(frame))
    }

    @Test
    fun testExtractPayloadStripsHeaderAndCrc() {
        val originalPayload = byteArrayOf(0x05, 0x1A) // Steps today command
        val frame = bleManager.buildCommandFrame(originalPayload)

        val extracted = bleManager.extractPayload(frame)
        assertArrayEquals(originalPayload, extracted)
    }

    @Test
    fun testCorruptedFrameFailsVerification() {
        val payload = byteArrayOf(0x02, 0x4E) // SpO2 command
        val frame = bleManager.buildCommandFrame(payload)

        // Corrupt CRC byte
        val corrupted = frame.clone()
        corrupted[corrupted.lastIndex] = (corrupted[corrupted.lastIndex] + 1).toByte()

        assertFalse(bleManager.verifyFrame(corrupted))
    }

    @Test
    fun testInitSequenceCommandsIntegrity() {
        assertEquals(2, Protocol.INIT_CMD_1.size)
        assertEquals(0x03.toByte(), Protocol.INIT_CMD_1[0])
        assertEquals(0x02.toByte(), Protocol.INIT_CMD_1[1])

        assertEquals(2, Protocol.INIT_CMD_2.size)
        assertEquals(0x02.toByte(), Protocol.INIT_CMD_2[0])
        assertEquals(0x02.toByte(), Protocol.INIT_CMD_2[1])

        assertEquals(2, Protocol.INIT_CMD_3.size)
        assertEquals(0x02.toByte(), Protocol.INIT_CMD_3[0])
        assertEquals(0x01.toByte(), Protocol.INIT_CMD_3[1])

        assertEquals(2, Protocol.INIT_CMD_4.size)
        assertEquals(0x02.toByte(), Protocol.INIT_CMD_4[0])
        assertEquals(0x63.toByte(), Protocol.INIT_CMD_4[1])
    }
}
