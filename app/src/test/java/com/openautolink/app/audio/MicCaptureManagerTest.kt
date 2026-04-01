package com.openautolink.app.audio

import com.openautolink.app.transport.AudioPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicCaptureManagerTest {

    @Test
    fun `mic frame direction is 1`() {
        val frame = AudioFrame(
            direction = AudioFrame.DIRECTION_MIC,
            purpose = AudioPurpose.ASSISTANT,
            sampleRate = 16000,
            channels = 1,
            data = ByteArray(1024)
        )
        assertTrue(frame.isMic)
        assertFalse(frame.isPlayback)
        assertEquals(AudioFrame.DIRECTION_MIC, frame.direction)
    }

    @Test
    fun `mic frame with call purpose`() {
        val frame = AudioFrame(
            direction = AudioFrame.DIRECTION_MIC,
            purpose = AudioPurpose.PHONE_CALL,
            sampleRate = 8000,
            channels = 1,
            data = ByteArray(512)
        )
        assertTrue(frame.isMic)
        assertEquals(AudioPurpose.PHONE_CALL, frame.purpose)
        assertEquals(8000, frame.sampleRate)
        assertEquals(1, frame.channels)
    }

    @Test
    fun `mic frame purpose byte round-trips for assistant`() {
        val purposeByte = AudioFrame.purposeToByte(AudioPurpose.ASSISTANT)
        assertEquals(2, purposeByte)
        assertEquals(AudioPurpose.ASSISTANT, AudioFrame.purposeFromByte(purposeByte))
    }

    @Test
    fun `mic frame purpose byte round-trips for phone call`() {
        val purposeByte = AudioFrame.purposeToByte(AudioPurpose.PHONE_CALL)
        assertEquals(3, purposeByte)
        assertEquals(AudioPurpose.PHONE_CALL, AudioFrame.purposeFromByte(purposeByte))
    }
}
