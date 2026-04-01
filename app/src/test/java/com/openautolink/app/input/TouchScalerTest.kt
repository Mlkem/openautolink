package com.openautolink.app.input

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchScalerTest {

    @Test
    fun `scale maps coordinate proportionally`() {
        // Surface is 2628px wide, video is 1920px wide
        // Touch at surface x=1314 (midpoint) → video x=960 (midpoint)
        val result = TouchScaler.scale(1314f, 2628, 1920)
        assertEquals(960f, result, 0.5f)
    }

    @Test
    fun `scale maps origin to origin`() {
        val result = TouchScaler.scale(0f, 2628, 1920)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `scale maps max surface to max video`() {
        val result = TouchScaler.scale(2628f, 2628, 1920)
        assertEquals(1920f, result, 0.001f)
    }

    @Test
    fun `scale clamps negative coordinates to zero`() {
        val result = TouchScaler.scale(-10f, 800, 1920)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `scale clamps coordinates beyond surface to video max`() {
        // Touch beyond surface bounds — clamp to video max
        val result = TouchScaler.scale(900f, 800, 1920)
        assertEquals(1920f, result, 0.001f)
    }

    @Test
    fun `scale returns zero for zero surface dimension`() {
        val result = TouchScaler.scale(100f, 0, 1920)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `scale returns zero for zero video dimension`() {
        val result = TouchScaler.scale(100f, 800, 0)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `scalePoint scales both axes independently`() {
        // Surface: 2628x800, Video: 1920x1080
        val (x, y) = TouchScaler.scalePoint(
            surfaceX = 1314f, surfaceY = 400f,
            surfaceWidth = 2628, surfaceHeight = 800,
            videoWidth = 1920, videoHeight = 1080
        )
        assertEquals(960f, x, 0.5f)
        assertEquals(540f, y, 0.5f)
    }

    @Test
    fun `scalePoint handles different aspect ratios`() {
        // Surface: 800x480 (5:3), Video: 1920x1080 (16:9)
        val (x, y) = TouchScaler.scalePoint(
            surfaceX = 400f, surfaceY = 240f,
            surfaceWidth = 800, surfaceHeight = 480,
            videoWidth = 1920, videoHeight = 1080
        )
        assertEquals(960f, x, 0.5f)
        assertEquals(540f, y, 0.5f)
    }

    @Test
    fun `scalePoint with matching dimensions is identity`() {
        val (x, y) = TouchScaler.scalePoint(
            surfaceX = 500f, surfaceY = 300f,
            surfaceWidth = 1920, surfaceHeight = 1080,
            videoWidth = 1920, videoHeight = 1080
        )
        assertEquals(500f, x, 0.001f)
        assertEquals(300f, y, 0.001f)
    }
}
