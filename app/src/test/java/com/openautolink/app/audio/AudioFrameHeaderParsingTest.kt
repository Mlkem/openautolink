package com.openautolink.app.audio

import com.openautolink.app.transport.AudioPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AudioFrameHeaderParsingTest {

    /**
     * Build a raw 8-byte OAL audio header for testing.
     * Format: direction(u8) + purpose(u8) + sample_rate(u16le) + channels(u8) + payload_length(u24le)
     */
    private fun buildHeader(
        direction: Int,
        purpose: Int,
        sampleRate: Int,
        channels: Int,
        payloadLength: Int
    ): ByteArray {
        val buf = ByteArray(8)
        buf[0] = direction.toByte()
        buf[1] = purpose.toByte()
        buf[2] = (sampleRate and 0xFF).toByte()
        buf[3] = ((sampleRate shr 8) and 0xFF).toByte()
        buf[4] = channels.toByte()
        buf[5] = (payloadLength and 0xFF).toByte()
        buf[6] = ((payloadLength shr 8) and 0xFF).toByte()
        buf[7] = ((payloadLength shr 16) and 0xFF).toByte()
        return buf
    }

    @Test
    fun `parse media playback header`() {
        val header = buildHeader(
            direction = 0,
            purpose = 0, // MEDIA
            sampleRate = 48000,
            channels = 2,
            payloadLength = 3840 // 20ms @ 48kHz stereo 16-bit
        )

        assertEquals(0, header[0].toInt() and 0xFF) // direction
        assertEquals(0, header[1].toInt() and 0xFF) // purpose (media)

        // Sample rate 48000 = 0xBB80
        val sr = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
        assertEquals(48000, sr)

        assertEquals(2, header[4].toInt() and 0xFF) // channels

        // Payload length 3840 = 0x000F00
        val pl = (header[5].toInt() and 0xFF) or
                ((header[6].toInt() and 0xFF) shl 8) or
                ((header[7].toInt() and 0xFF) shl 16)
        assertEquals(3840, pl)

        // Purpose byte maps correctly
        assertNotNull(AudioFrame.purposeFromByte(header[1].toInt() and 0xFF))
        assertEquals(AudioPurpose.MEDIA, AudioFrame.purposeFromByte(header[1].toInt() and 0xFF))
    }

    @Test
    fun `parse navigation mono header`() {
        val header = buildHeader(
            direction = 0,
            purpose = 1, // NAVIGATION
            sampleRate = 16000,
            channels = 1,
            payloadLength = 640 // 20ms @ 16kHz mono 16-bit
        )

        val sr = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
        assertEquals(16000, sr)
        assertEquals(1, header[4].toInt() and 0xFF)
        assertEquals(AudioPurpose.NAVIGATION, AudioFrame.purposeFromByte(header[1].toInt() and 0xFF))
    }

    @Test
    fun `parse phone call header`() {
        val header = buildHeader(
            direction = 0,
            purpose = 3, // PHONE_CALL
            sampleRate = 8000,
            channels = 1,
            payloadLength = 320 // 20ms @ 8kHz mono 16-bit
        )

        val sr = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
        assertEquals(8000, sr)
        assertEquals(AudioPurpose.PHONE_CALL, AudioFrame.purposeFromByte(header[1].toInt() and 0xFF))
    }

    @Test
    fun `parse mic capture header (direction 1)`() {
        val header = buildHeader(
            direction = 1,
            purpose = 2, // ASSISTANT
            sampleRate = 16000,
            channels = 1,
            payloadLength = 640
        )

        assertEquals(1, header[0].toInt() and 0xFF)
        assertEquals(AudioPurpose.ASSISTANT, AudioFrame.purposeFromByte(header[1].toInt() and 0xFF))
    }

    @Test
    fun `large payload length encodes correctly in u24le`() {
        val largePayload = 0x0F_FF_FF // ~1MB
        val header = buildHeader(0, 0, 48000, 2, largePayload)

        val pl = (header[5].toInt() and 0xFF) or
                ((header[6].toInt() and 0xFF) shl 8) or
                ((header[7].toInt() and 0xFF) shl 16)
        assertEquals(largePayload, pl)
    }

    @Test
    fun `alert purpose header`() {
        val header = buildHeader(
            direction = 0,
            purpose = 4, // ALERT
            sampleRate = 24000,
            channels = 1,
            payloadLength = 960
        )

        assertEquals(AudioPurpose.ALERT, AudioFrame.purposeFromByte(header[1].toInt() and 0xFF))
        val sr = (header[2].toInt() and 0xFF) or ((header[3].toInt() and 0xFF) shl 8)
        assertEquals(24000, sr)
    }
}
