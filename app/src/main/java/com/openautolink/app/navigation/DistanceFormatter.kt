package com.openautolink.app.navigation

import java.util.Locale

/**
 * Formats navigation distances for display.
 * Uses imperial (miles/feet) for US locale, metric (km/m) otherwise.
 */
object DistanceFormatter {

    private const val METERS_PER_MILE = 1609.344
    private const val METERS_PER_FOOT = 0.3048
    private const val FEET_PER_MILE = 5280

    /**
     * Format a distance in meters for display.
     *
     * @param meters Distance in meters, or null
     * @param locale Locale for unit selection (defaults to system locale)
     * @return Formatted distance string, or empty string if null
     */
    fun format(meters: Int?, locale: Locale = Locale.getDefault()): String {
        if (meters == null) return ""

        return if (isImperial(locale)) {
            formatImperial(meters)
        } else {
            formatMetric(meters)
        }
    }

    internal fun formatMetric(meters: Int): String {
        return when {
            meters >= 10_000 -> "${meters / 1000} km"
            meters >= 1_000 -> {
                val km = meters / 1000.0
                String.format(Locale.US, "%.1f km", km)
            }
            meters >= 100 -> "${(meters / 50) * 50} m" // Round to nearest 50m
            else -> "$meters m"
        }
    }

    internal fun formatImperial(meters: Int): String {
        val feet = (meters / METERS_PER_FOOT).toInt()
        val miles = meters / METERS_PER_MILE

        return when {
            miles >= 10 -> "${miles.toInt()} mi"
            miles >= 0.2 -> String.format(Locale.US, "%.1f mi", miles)
            feet >= 100 -> "${(feet / 50) * 50} ft" // Round to nearest 50ft
            else -> "$feet ft"
        }
    }

    private fun isImperial(locale: Locale): Boolean {
        return locale.country in setOf("US", "LR", "MM")
    }
}
