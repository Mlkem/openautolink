package com.openautolink.app.transport

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for OAL video frame header parsing.
 * Verifies the 16-byte header format: payload_length(u32le) + width(u16le) + height(u16le) + pts_ms(u32le) + flags(u16le) + reserved(u16le)
 */
class VideoFrameHeaderTest {

    // Mirror the private parsing methods from TcpVideoChannel for unit testing
    private fun readU16LE(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xFF) or
                ((buf[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readU32LE(buf: ByteArray, offset: Int): Long {
        return (buf[offset].toInt() and 0xFF).toLong() or
                ((buf[offset + 1].toInt() and 0xFF).toLong() shl 8) or
                ((buf[offset + 2].toInt() and 0xFF).toLong() shl 16) or
                ((buf[offset + 3].toInt() and 0xFF).toLong() shl 24)
    }

    private fun writeU16LE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeU32LE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    @Test
    fun `readU16LE parses little-endian 16-bit value`() {
        // 0x0380 = 896 in LE → bytes: 0x80, 0x03
        val buf = byteArrayOf(0x80.toByte(), 0x03)
        assertEquals(896, readU16LE(buf, 0))
    }

    @Test
    fun `readU16LE parses 1920 correctly`() {
        // 1920 = 0x0780 → LE bytes: 0x80, 0x07
        val buf = byteArrayOf(0x80.toByte(), 0x07)
        assertEquals(1920, readU16LE(buf, 0))
    }

    @Test
    fun `readU16LE parses 1080 correctly`() {
        // 1080 = 0x0438 → LE bytes: 0x38, 0x04
        val buf = byteArrayOf(0x38, 0x04)
        assertEquals(1080, readU16LE(buf, 0))
    }

    @Test
    fun `readU32LE parses little-endian 32-bit value`() {
        // 100000 = 0x000186A0 → LE bytes: 0xA0, 0x86, 0x01, 0x00
        val buf = byteArrayOf(0xA0.toByte(), 0x86.toByte(), 0x01, 0x00)
        assertEquals(100000L, readU32LE(buf, 0))
    }

    @Test
    fun `readU32LE parses max u32 correctly`() {
        // 0xFFFFFFFF = 4294967295
        val buf = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals(4294967295L, readU32LE(buf, 0))
    }

    @Test
    fun `full 16-byte header round-trips correctly`() {
        val header = ByteArray(16)
        val payloadLength = 50000L
        val width = 1920
        val height = 1080
        val ptsMs = 12345678L
        val flags = 0x0001 // keyframe

        writeU32LE(header, 0, payloadLength)
        writeU16LE(header, 4, width)
        writeU16LE(header, 6, height)
        writeU32LE(header, 8, ptsMs)
        writeU16LE(header, 12, flags)
        writeU16LE(header, 14, 0) // reserved

        assertEquals(payloadLength, readU32LE(header, 0))
        assertEquals(width, readU16LE(header, 4))
        assertEquals(height, readU16LE(header, 6))
        assertEquals(ptsMs, readU32LE(header, 8))
        assertEquals(flags, readU16LE(header, 12))
        assertEquals(0, readU16LE(header, 14))
    }

    @Test
    fun `header with codec config flag`() {
        val header = ByteArray(16)
        writeU32LE(header, 0, 256)    // payload_length
        writeU16LE(header, 4, 1920)   // width
        writeU16LE(header, 6, 1080)   // height
        writeU32LE(header, 8, 0)      // pts_ms
        writeU16LE(header, 12, 0x0002) // codec config flag
        writeU16LE(header, 14, 0)

        val flags = readU16LE(header, 12)
        assertEquals(0x0002, flags)
        assertTrue(flags and 0x0002 != 0)  // codec config
        assertFalse(flags and 0x0001 != 0) // not keyframe
    }

    @Test
    fun `header with combined keyframe and codec config flags`() {
        val flags = 0x0003 // keyframe | codec_config
        val header = ByteArray(16)
        writeU16LE(header, 12, flags)

        val parsed = readU16LE(header, 12)
        assertTrue(parsed and 0x0001 != 0) // keyframe
        assertTrue(parsed and 0x0002 != 0) // codec config
        assertFalse(parsed and 0x0004 != 0) // not EOS
    }

    @Test
    fun `zero payload length header`() {
        val header = ByteArray(16)
        writeU32LE(header, 0, 0)
        writeU16LE(header, 4, 0)
        writeU16LE(header, 6, 0)
        writeU32LE(header, 8, 0)
        writeU16LE(header, 12, 0x0004) // EOS
        writeU16LE(header, 14, 0)

        assertEquals(0L, readU32LE(header, 0))
        val flags = readU16LE(header, 12)
        assertTrue(flags and 0x0004 != 0) // EOS
    }

    private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)
    private fun assertFalse(condition: Boolean) = org.junit.Assert.assertFalse(condition)
}
