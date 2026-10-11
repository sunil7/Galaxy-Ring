package com.galaxy.ring.ble

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    fun testBleManagerInitialState() {
        assertEquals(0, bleManager.rxCountSinceConnect)
        assertEquals("RWfit Standard (AB 01 len2 crc2)", bleManager.workingVariantName)
        assertTrue(bleManager.initState.value is GalaxyRingBLEManager.InitState.Idle)
    }

    @Test
    fun testSessionReset() {
        bleManager.resetSession()
        assertEquals(0, bleManager.rxCountSinceConnect)
        assertEquals(0, bleManager.rxCountFlow.value)
    }
}
