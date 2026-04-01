package com.openautolink.app.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFrameTest {

    @Test
    fun `flags correctly identify keyframe`() {
        val frame = VideoFrame(1920, 1080, 100, VideoFrame.FLAG_KEYFRAME, byteArrayOf(1))
        assertTrue(frame.isKeyframe)
        assertFalse(frame.isCodecConfig)
        assertFalse(frame.isEndOfStream)
    }

    @Test
    fun `flags correctly identify codec config`() {
        val frame = VideoFrame(1920, 1080, 0, VideoFrame.FLAG_CODEC_CONFIG, byteArrayOf(1))
        assertFalse(frame.isKeyframe)
        assertTrue(frame.isCodecConfig)
        assertFalse(frame.isEndOfStream)
    }

    @Test
    fun `flags correctly identify end of stream`() {
        val frame = VideoFrame(0, 0, 0, VideoFrame.FLAG_END_OF_STREAM, byteArrayOf())
        assertFalse(frame.isKeyframe)
        assertFalse(frame.isCodecConfig)
        assertTrue(frame.isEndOfStream)
    }

    @Test
    fun `combined flags work correctly`() {
        // Keyframe + Codec config (first IDR sometimes carries both)
        val frame = VideoFrame(
            1920, 1080, 50,
            VideoFrame.FLAG_KEYFRAME or VideoFrame.FLAG_CODEC_CONFIG,
            byteArrayOf(1)
        )
        assertTrue(frame.isKeyframe)
        assertTrue(frame.isCodecConfig)
        assertFalse(frame.isEndOfStream)
    }

    @Test
    fun `no flags set means regular P-frame`() {
        val frame = VideoFrame(1920, 1080, 200, 0, byteArrayOf(1, 2, 3))
        assertFalse(frame.isKeyframe)
        assertFalse(frame.isCodecConfig)
        assertFalse(frame.isEndOfStream)
    }

    @Test
    fun `HEADER_SIZE is 16 bytes`() {
        assertEquals(16, VideoFrame.HEADER_SIZE)
    }

    @Test
    fun `equality includes data content`() {
        val a = VideoFrame(1920, 1080, 100, 0, byteArrayOf(1, 2, 3))
        val b = VideoFrame(1920, 1080, 100, 0, byteArrayOf(1, 2, 3))
        val c = VideoFrame(1920, 1080, 100, 0, byteArrayOf(1, 2, 4))
        assertEquals(a, b)
        assertFalse(a == c)
    }

    @Test
    fun `hashCode is consistent with equals`() {
        val a = VideoFrame(1920, 1080, 100, 0, byteArrayOf(1, 2, 3))
        val b = VideoFrame(1920, 1080, 100, 0, byteArrayOf(1, 2, 3))
        assertEquals(a.hashCode(), b.hashCode())
    }
}
