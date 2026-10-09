package com.galaxy.ring.data

import android.util.Log
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Heuristic screening module for overnight breathing stability and sleep apnea informational risk indicators.
 *
 * IMPORTANT MEDICAL DISCLAIMER:
 * This algorithm evaluates informational risk indicators only and is NOT a medical diagnosis, medical device,
 * or treatment recommendation. Always consult a certified clinician or sleep physician for clinical evaluation.
 */
object SleepApneaScreener {

    private const val TAG = "GalaxyRingSleep"

    const val DISCLAIMER = "Informational risk indicators only — not a medical diagnosis. Consult a clinician."

    enum class ApneaRiskBand(val label: String) {
        LOW("Low Risk Indicator"),
        MODERATE("Moderate Risk Indicator"),
        ELEVATED("Elevated Risk Indicator"),
        INSUFFICIENT_DATA("Insufficient Data")
    }

    data class SelfReportSymptoms(
        val loudSnoring: Boolean = false,
        val daytimeSleepiness: Boolean = false,
        val observedBreathingPauses: Boolean = false
    )

    data class ApneaScreeningReport(
        val riskBand: ApneaRiskBand,
        val lowestSpo2: Float?,
        val dipCount: Int,
        val minutesUnder90: Int,
        val hrSurgesCount: Int,
        val fragmentationNote: String,
        val explanationBullets: List<String>,
        val hasInsufficientData: Boolean,
        val totalSpo2Samples: Int,
        val disclaimer: String = DISCLAIMER
    )

    /**
     * Evaluates overnight telemetry data against clinical heuristic risk markers:
     *
     * 1. Oxygen Saturation Desaturations (SpO₂ dips ≥3-4% and time below 90%)
     * 2. Heart rate variability and post-apnea arousals (sudden surges of ≥15 bpm)
     * 3. Sleep architecture fragmentation (frequent awakenings and efficiency < 85%)
     * 4. Validated symptom indicators (loud snoring, daytime somnolence, observed apneas)
     */
    fun evaluate(
        overnightSpo2: List<OxygenSaturationEntity>,
        overnightHr: List<HeartRateEntity> = emptyList(),
        sleepAnalysis: SleepAnalysis? = null,
        symptoms: SelfReportSymptoms = SelfReportSymptoms()
    ): ApneaScreeningReport {
        Log.d(TAG, "Evaluating sleep apnea screening: ${overnightSpo2.size} SpO2 samples, ${overnightHr.size} HR samples")

        // Require at least 3 SpO₂ samples recorded during sleep
        if (overnightSpo2.size < 3) {
            return ApneaScreeningReport(
                riskBand = ApneaRiskBand.INSUFFICIENT_DATA,
                lowestSpo2 = overnightSpo2.minOfOrNull { it.percentage },
                dipCount = 0,
                minutesUnder90 = 0,
                hrSurgesCount = 0,
                fragmentationNote = "Insufficient overnight SpO₂ samples for evaluation.",
                explanationBullets = listOf(
                    "Insufficient SpO₂ data for screening. Ensure the Galaxy Ring has a snug fit with continuous night logging enabled."
                ),
                hasInsufficientData = true,
                totalSpo2Samples = overnightSpo2.size
            )
        }

        val sortedSpo2 = overnightSpo2.sortedBy { it.timestamp }
        val lowestSpo2 = sortedSpo2.minOf { it.percentage }

        // 1. Detect desaturation dips (drop of >= 3.0% from prior sample or rolling baseline)
        var dipCount = 0
        var samplesUnder90 = 0
        for (i in 1 until sortedSpo2.size) {
            val prev = sortedSpo2[i - 1].percentage
            val curr = sortedSpo2[i].percentage
            if (curr < 90f) {
                samplesUnder90++
            }
            if ((prev - curr) >= 3.0f) {
                dipCount++
            }
        }
        if (sortedSpo2.first().percentage < 90f) {
            samplesUnder90++
        }

        // Approximate time under 90% based on average spacing between samples (default ~10-30 min or sample count * 5m)
        val minutesUnder90 = if (sortedSpo2.size > 1) {
            val totalSpanMinutes = ((sortedSpo2.last().timestamp - sortedSpo2.first().timestamp) / 60000L).coerceAtLeast(1L)
            val avgInterval = totalSpanMinutes.toFloat() / sortedSpo2.size.toFloat()
            (samplesUnder90 * avgInterval).toInt()
        } else {
            samplesUnder90 * 10
        }

        // 2. Overnight HR surges (sudden rise of >= 15 bpm between consecutive samples, indicative of autonomic arousal)
        val sortedHr = overnightHr.sortedBy { it.timestamp }
        var hrSurges = 0
        for (i in 1 until sortedHr.size) {
            val prevBpm = sortedHr[i - 1].bpm
            val currBpm = sortedHr[i].bpm
            if ((currBpm - prevBpm) >= 15) {
                hrSurges++
            }
        }

        // 3. Sleep fragmentation notes
        val awakenings = sleepAnalysis?.awakeningsCount ?: 0
        val efficiency = sleepAnalysis?.sleepEfficiency ?: 0.90f
        val isFragmented = awakenings >= 3 || efficiency < 0.85f
        val fragmentationNote = when {
            awakenings >= 4 -> "Significant sleep fragmentation ($awakenings awakenings, ${(efficiency * 100).toInt()}% efficiency)"
            awakenings in 2..3 -> "Moderate sleep fragmentation ($awakenings awakenings)"
            else -> "Stable sleep continuity (${(efficiency * 100).toInt()}% efficiency)"
        }

        // 4. Heuristic Risk Scoring Points Calculation
        var riskScore = 0
        val bullets = mutableListOf<String>()

        // SpO2 Dip evaluation
        when {
            dipCount >= 5 -> {
                riskScore += 3
                bullets.add("Frequent overnight desaturations detected ($dipCount SpO₂ dips of ≥3%).")
            }
            dipCount in 2..4 -> {
                riskScore += 1
                bullets.add("Mild oxygen desaturations observed ($dipCount SpO₂ dips).")
            }
            else -> {
                bullets.add("Blood oxygen saturation remained largely consistent throughout sleep.")
            }
        }

        // Lowest SpO2 nadir
        when {
            lowestSpo2 < 88f -> {
                riskScore += 3
                bullets.add("Low nocturnal SpO₂ nadir recorded at ${lowestSpo2.toInt()}% (clinical concern when <88%).")
            }
            lowestSpo2 < 92f -> {
                riskScore += 2
                bullets.add("Lowest nocturnal SpO₂ reached ${lowestSpo2.toInt()}% (normal is typically ≥95%).")
            }
            lowestSpo2 < 94f -> {
                riskScore += 1
                bullets.add("Borderline minimum SpO₂ recorded at ${lowestSpo2.toInt()}%.")
            }
            else -> {
                bullets.add("Minimum SpO₂ was healthy at ${lowestSpo2.toInt()}%.")
            }
        }

        if (minutesUnder90 > 5) {
            riskScore += 2
            bullets.add("Estimated $minutesUnder90 minutes with SpO₂ below 90%.")
        }

        // Autonomic arousal (HR surges)
        if (hrSurges >= 3) {
            riskScore += 1
            bullets.add("Repeated heart rate spikes detected ($hrSurges surges ≥15 BPM), suggesting nocturnal arousals.")
        }

        // Sleep fragmentation
        if (isFragmented) {
            riskScore += 1
            bullets.add("Fragmented sleep architecture observed with reduced sleep efficiency (${(efficiency * 100).toInt()}%).")
        }

        // Self-reported symptoms
        if (symptoms.observedBreathingPauses) {
            riskScore += 2
            bullets.add("Self-reported observed pauses in breathing during sleep.")
        }
        if (symptoms.loudSnoring) {
            riskScore += 1
            bullets.add("Self-reported habitual loud snoring.")
        }
        if (symptoms.daytimeSleepiness) {
            riskScore += 1
            bullets.add("Self-reported daytime fatigue and excessive sleepiness.")
        }

        // Assign Risk Band
        val riskBand = when {
            riskScore >= 6 -> ApneaRiskBand.ELEVATED
            riskScore in 3..5 -> ApneaRiskBand.MODERATE
            else -> ApneaRiskBand.LOW
        }

        return ApneaScreeningReport(
            riskBand = riskBand,
            lowestSpo2 = lowestSpo2,
            dipCount = dipCount,
            minutesUnder90 = minutesUnder90,
            hrSurgesCount = hrSurges,
            fragmentationNote = fragmentationNote,
            explanationBullets = bullets,
            hasInsufficientData = false,
            totalSpo2Samples = overnightSpo2.size
        )
    }
}
