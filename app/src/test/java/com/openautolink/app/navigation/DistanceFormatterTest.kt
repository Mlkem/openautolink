package com.openautolink.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class DistanceFormatterTest {

    // --- Metric ---

    @Test
    fun `metric - null returns empty string`() {
        assertEquals("", DistanceFormatter.format(null, Locale.GERMANY))
    }

    @Test
    fun `metric - short distance in meters`() {
        assertEquals("50 m", DistanceFormatter.formatMetric(50))
        assertEquals("99 m", DistanceFormatter.formatMetric(99))
    }

    @Test
    fun `metric - medium distance rounds to 50m`() {
        assertEquals("100 m", DistanceFormatter.formatMetric(100))
        assertEquals("150 m", DistanceFormatter.formatMetric(175))
        assertEquals("200 m", DistanceFormatter.formatMetric(230))
        assertEquals("900 m", DistanceFormatter.formatMetric(940))
    }

    @Test
    fun `metric - km with one decimal`() {
        assertEquals("1.0 km", DistanceFormatter.formatMetric(1000))
        assertEquals("1.5 km", DistanceFormatter.formatMetric(1500))
        assertEquals("5.0 km", DistanceFormatter.formatMetric(5000))
        assertEquals("9.9 km", DistanceFormatter.formatMetric(9900))
    }

    @Test
    fun `metric - large km no decimal`() {
        assertEquals("10 km", DistanceFormatter.formatMetric(10_000))
        assertEquals("15 km", DistanceFormatter.formatMetric(15_000))
        assertEquals("100 km", DistanceFormatter.formatMetric(100_000))
    }

    // --- Imperial ---

    @Test
    fun `imperial - null returns empty string`() {
        assertEquals("", DistanceFormatter.format(null, Locale.US))
    }

    @Test
    fun `imperial - short distance in feet`() {
        assertEquals("49 ft", DistanceFormatter.formatImperial(15))
        assertEquals("0 ft", DistanceFormatter.formatImperial(0))
    }

    @Test
    fun `imperial - medium distance rounds to 50 feet`() {
        assertEquals("100 ft", DistanceFormatter.formatImperial(33))
        assertEquals("300 ft", DistanceFormatter.formatImperial(96))
    }

    @Test
    fun `imperial - miles with one decimal`() {
        assertEquals("0.3 mi", DistanceFormatter.formatImperial(500))
        assertEquals("0.6 mi", DistanceFormatter.formatImperial(1000))
        assertEquals("1.0 mi", DistanceFormatter.formatImperial(1609))
    }

    @Test
    fun `imperial - large miles no decimal`() {
        assertEquals("10 mi", DistanceFormatter.formatImperial(16_094))
        assertEquals("62 mi", DistanceFormatter.formatImperial(100_000))
    }

    // --- Locale selection ---

    @Test
    fun `US locale uses imperial`() {
        val result = DistanceFormatter.format(1609, Locale.US)
        assert(result.contains("mi")) { "US should use miles: $result" }
    }

    @Test
    fun `German locale uses metric`() {
        val result = DistanceFormatter.format(1000, Locale.GERMANY)
        assert(result.contains("km")) { "Germany should use km: $result" }
    }

    @Test
    fun `UK locale uses metric`() {
        val result = DistanceFormatter.format(1000, Locale.UK)
        assert(result.contains("km")) { "UK should use km: $result" }
    }
}
