package com.galaxy.ring.data

import java.text.NumberFormat
import java.util.Locale

/**
 * Utility functions for formatting workout sessions, distance, pace, speed, and duration.
 * Respects the distance unit preference (Kilometers vs Miles).
 */
object WorkoutFormatter {

    fun formatDistance(meters: Double, useMiles: Boolean): String {
        return if (useMiles) {
            val miles = meters / 1609.344
            String.format(Locale.US, "%.2f mi", miles)
        } else {
            val km = meters / 1000.0
            String.format(Locale.US, "%.2f km", km)
        }
    }

    fun formatDistanceValueOnly(meters: Double, useMiles: Boolean): String {
        return if (useMiles) {
            val miles = meters / 1609.344
            String.format(Locale.US, "%.2f", miles)
        } else {
            val km = meters / 1000.0
            String.format(Locale.US, "%.2f", km)
        }
    }

    fun distanceUnit(useMiles: Boolean): String = if (useMiles) "mi" else "km"

    fun formatPace(paceSecPerKm: Double, useMiles: Boolean): String {
        if (paceSecPerKm <= 0.0) return "--"
        val sec = if (useMiles) paceSecPerKm * 1.609344 else paceSecPerKm
        val m = (sec / 60).toInt()
        val s = (sec % 60).toInt()
        val unit = if (useMiles) "/mi" else "/km"
        return String.format(Locale.US, "%d'%02d\"%s", m, s, unit)
    }

    fun formatSpeed(speedKmH: Double, useMiles: Boolean): String {
        if (speedKmH <= 0.0) return "--"
        return if (useMiles) {
            String.format(Locale.US, "%.1f mph", speedKmH * 0.621371)
        } else {
            String.format(Locale.US, "%.1f km/h", speedKmH)
        }
    }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%dh %02dm %02ds", h, m, s)
        } else if (m > 0) {
            String.format(Locale.US, "%dm %02ds", m, s)
        } else {
            String.format(Locale.US, "%ds", s)
        }
    }

    fun formatSteps(steps: Long): String {
        return NumberFormat.getIntegerInstance(Locale.US).format(steps)
    }
}
