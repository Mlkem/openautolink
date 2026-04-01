package com.openautolink.app.audio

import com.openautolink.app.transport.AudioPurpose
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFrameTest {

    @Test
    fun `playback frame has correct direction`() {
        val frame = AudioFrame(
            direction = AudioFrame.DIRECTION_PLAYBACK,
            purpose = AudioPurpose.MEDIA,
            sampleRate = 48000,
            channels = 2,
            data = ByteArray(960)
        )
        assertTrue(frame.isPlayback)
        assertFalse(frame.isMic)
    }

    @Test
    fun `mic frame has correct direction`() {
        val frame = AudioFrame(
            direction = AudioFrame.DIRECTION_MIC,
            purpose = AudioPurpose.ASSISTANT,
            sampleRate = 16000,
            channels = 1,
            data = ByteArray(320)
        )
        assertFalse(frame.isPlayback)
        assertTrue(frame.isMic)
    }

    @Test
    fun `purposeFromByte maps all valid bytes`() {
        assertEquals(AudioPurpose.MEDIA, AudioFrame.purposeFromByte(0))
        assertEquals(AudioPurpose.NAVIGATION, AudioFrame.purposeFromByte(1))
        assertEquals(AudioPurpose.ASSISTANT, AudioFrame.purposeFromByte(2))
        assertEquals(AudioPurpose.PHONE_CALL, AudioFrame.purposeFromByte(3))
        assertEquals(AudioPurpose.ALERT, AudioFrame.purposeFromByte(4))
    }

    @Test
    fun `purposeFromByte returns null for invalid bytes`() {
        assertNull(AudioFrame.purposeFromByte(5))
        assertNull(AudioFrame.purposeFromByte(255))
        assertNull(AudioFrame.purposeFromByte(-1))
    }

    @Test
    fun `purposeToByte maps all purposes`() {
        assertEquals(0, AudioFrame.purposeToByte(AudioPurpose.MEDIA))
        assertEquals(1, AudioFrame.purposeToByte(AudioPurpose.NAVIGATION))
        assertEquals(2, AudioFrame.purposeToByte(AudioPurpose.ASSISTANT))
        assertEquals(3, AudioFrame.purposeToByte(AudioPurpose.PHONE_CALL))
        assertEquals(4, AudioFrame.purposeToByte(AudioPurpose.ALERT))
    }

    @Test
    fun `round-trip purposeToByte and purposeFromByte`() {
        for (purpose in AudioPurpose.entries) {
            val byte = AudioFrame.purposeToByte(purpose)
            val result = AudioFrame.purposeFromByte(byte)
            assertEquals(purpose, result)
        }
    }

    @Test
    fun `equality compares data by content`() {
        val a = AudioFrame(0, AudioPurpose.MEDIA, 48000, 2, byteArrayOf(1, 2, 3))
        val b = AudioFrame(0, AudioPurpose.MEDIA, 48000, 2, byteArrayOf(1, 2, 3))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different data means not equal`() {
        val a = AudioFrame(0, AudioPurpose.MEDIA, 48000, 2, byteArrayOf(1, 2, 3))
        val b = AudioFrame(0, AudioPurpose.MEDIA, 48000, 2, byteArrayOf(4, 5, 6))
        assertFalse(a == b)
    }

    @Test
    fun `header size constant is 8`() {
        assertEquals(8, AudioFrame.HEADER_SIZE)
    }
}
