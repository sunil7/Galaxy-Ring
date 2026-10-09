package com.galaxy.ring.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SleepApneaScreenerTest {

    @Test
    fun testInsufficientDataWhenLessThanThreeSamples() {
        val samples = listOf(
            OxygenSaturationEntity(id = 1, timestamp = 1000L, percentage = 98f)
        )
        val report = SleepApneaScreener.evaluate(samples)
        assertTrue(report.hasInsufficientData)
        assertEquals(SleepApneaScreener.ApneaRiskBand.INSUFFICIENT_DATA, report.riskBand)
    }

    @Test
    fun testLowRiskWithHealthyStableSaturation() {
        val samples = listOf(
            OxygenSaturationEntity(id = 1, timestamp = 1000L, percentage = 98f),
            OxygenSaturationEntity(id = 2, timestamp = 2000L, percentage = 97f),
            OxygenSaturationEntity(id = 3, timestamp = 3000L, percentage = 98f),
            OxygenSaturationEntity(id = 4, timestamp = 4000L, percentage = 97f)
        )
        val report = SleepApneaScreener.evaluate(samples)
        assertFalse(report.hasInsufficientData)
        assertEquals(SleepApneaScreener.ApneaRiskBand.LOW, report.riskBand)
        assertEquals(0, report.dipCount)
        assertEquals(97f, report.lowestSpo2 ?: 0f, 0.1f)
    }

    @Test
    fun testElevatedRiskWithMultipleDipsAndLowNadir() {
        val samples = listOf(
            OxygenSaturationEntity(id = 1, timestamp = 1000L, percentage = 96f),
            OxygenSaturationEntity(id = 2, timestamp = 2000L, percentage = 87f),
            OxygenSaturationEntity(id = 3, timestamp = 3000L, percentage = 94f),
            OxygenSaturationEntity(id = 4, timestamp = 4000L, percentage = 88f),
            OxygenSaturationEntity(id = 5, timestamp = 5000L, percentage = 95f),
            OxygenSaturationEntity(id = 6, timestamp = 6000L, percentage = 86f)
        )
        val symptoms = SleepApneaScreener.SelfReportSymptoms(
            observedBreathingPauses = true,
            loudSnoring = true
        )
        val report = SleepApneaScreener.evaluate(samples, symptoms = symptoms)
        assertFalse(report.hasInsufficientData)
        assertEquals(SleepApneaScreener.ApneaRiskBand.ELEVATED, report.riskBand)
        assertTrue(report.dipCount >= 3)
        assertEquals(86f, report.lowestSpo2 ?: 0f, 0.1f)
    }

    @Test
    fun testDisclaimerTextIsPresent() {
        val report = SleepApneaScreener.evaluate(emptyList())
        assertNotNull(report.disclaimer)
        assertTrue(report.disclaimer.contains("not a medical diagnosis"))
    }
}
