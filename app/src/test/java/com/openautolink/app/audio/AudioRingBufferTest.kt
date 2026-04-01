package com.openautolink.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRingBufferTest {

    @Test
    fun `empty buffer returns zero bytes on read`() {
        val ring = AudioRingBuffer(1024)
        val dest = ByteArray(100)
        val read = ring.read(dest)
        assertEquals(0, read)
        assertEquals(1, ring.underruns)
    }

    @Test
    fun `write then read returns same data`() {
        val ring = AudioRingBuffer(1024)
        val data = byteArrayOf(1, 2, 3, 4, 5)
        ring.write(data)

        assertEquals(5, ring.available)

        val dest = ByteArray(5)
        val read = ring.read(dest)
        assertEquals(5, read)
        assertArrayEquals(data, dest)
        assertEquals(0, ring.available)
    }

    @Test
    fun `partial read returns available data`() {
        val ring = AudioRingBuffer(1024)
        ring.write(byteArrayOf(10, 20, 30))

        val dest = ByteArray(10)
        val read = ring.read(dest)
        assertEquals(3, read)
        assertEquals(10.toByte(), dest[0])
        assertEquals(20.toByte(), dest[1])
        assertEquals(30.toByte(), dest[2])
    }

    @Test
    fun `wraparound write and read`() {
        val ring = AudioRingBuffer(8) // Small buffer to force wraparound

        // Fill most of the buffer
        ring.write(byteArrayOf(1, 2, 3, 4, 5))
        // Read some to advance read pointer
        val tmp = ByteArray(3)
        ring.read(tmp)
        // Now write more data that wraps around
        ring.write(byteArrayOf(6, 7, 8))

        val dest = ByteArray(5)
        val read = ring.read(dest)
        assertEquals(5, read)
        assertArrayEquals(byteArrayOf(4, 5, 6, 7, 8), dest)
    }

    @Test
    fun `overflow drops oldest data`() {
        val ring = AudioRingBuffer(8)
        ring.write(byteArrayOf(1, 2, 3, 4, 5, 6, 7)) // Fill to capacity - 1
        ring.write(byteArrayOf(8, 9)) // This overflows

        assertEquals(1, ring.overflows)

        // Should have newest data, oldest dropped
        val dest = ByteArray(7)
        val read = ring.read(dest)
        assertEquals(7, read)
        // After overflow, the newest 7 bytes should be available
        assertArrayEquals(byteArrayOf(3, 4, 5, 6, 7, 8, 9), dest)
    }

    @Test
    fun `clear resets buffer`() {
        val ring = AudioRingBuffer(1024)
        ring.write(byteArrayOf(1, 2, 3))
        assertEquals(3, ring.available)

        ring.clear()
        assertEquals(0, ring.available)

        val dest = ByteArray(10)
        val read = ring.read(dest)
        assertEquals(0, read)
    }

    @Test
    fun `reset clears buffer and counters`() {
        val ring = AudioRingBuffer(4)
        ring.write(byteArrayOf(1, 2, 3)) // Fill
        ring.write(byteArrayOf(4, 5, 6)) // Overflow

        ring.read(ByteArray(1)) // Trigger no underrun here
        val emptyRing = AudioRingBuffer(4)
        emptyRing.read(ByteArray(1)) // Force underrun

        ring.reset()
        assertEquals(0, ring.available)
        assertEquals(0, ring.overflows)
        assertEquals(0, ring.underruns)
    }

    @Test
    fun `multiple write-read cycles`() {
        val ring = AudioRingBuffer(256)

        for (i in 0 until 100) {
            val data = ByteArray(10) { (i * 10 + it).toByte() }
            ring.write(data)

            val dest = ByteArray(10)
            val read = ring.read(dest)
            assertEquals(10, read)
            assertArrayEquals(data, dest)
        }

        assertEquals(0, ring.overflows)
    }

    @Test
    fun `write larger than capacity keeps newest`() {
        val ring = AudioRingBuffer(8)
        val bigData = ByteArray(20) { it.toByte() }
        ring.write(bigData)

        assertEquals(1, ring.overflows)

        // Should keep the newest (capacity - 1) = 7 bytes
        val dest = ByteArray(7)
        val read = ring.read(dest)
        assertEquals(7, read)
        assertArrayEquals(byteArrayOf(13, 14, 15, 16, 17, 18, 19), dest)
    }

    @Test
    fun `free space tracks correctly`() {
        val ring = AudioRingBuffer(16)
        assertEquals(15, ring.free) // capacity - 1

        ring.write(ByteArray(5))
        assertEquals(10, ring.free)

        ring.read(ByteArray(3))
        assertEquals(13, ring.free)
    }

    @Test
    fun `write with offset and length`() {
        val ring = AudioRingBuffer(1024)
        val source = byteArrayOf(0, 0, 10, 20, 30, 0, 0)
        ring.write(source, offset = 2, length = 3)

        val dest = ByteArray(3)
        ring.read(dest)
        assertArrayEquals(byteArrayOf(10, 20, 30), dest)
    }

    @Test
    fun `read with offset`() {
        val ring = AudioRingBuffer(1024)
        ring.write(byteArrayOf(1, 2, 3))

        val dest = ByteArray(10)
        val read = ring.read(dest, offset = 5, length = 3)
        assertEquals(3, read)
        assertEquals(1.toByte(), dest[5])
        assertEquals(2.toByte(), dest[6])
        assertEquals(3.toByte(), dest[7])
    }

    @Test
    fun `zero length write is no-op`() {
        val ring = AudioRingBuffer(1024)
        ring.write(byteArrayOf(1, 2, 3), length = 0)
        assertEquals(0, ring.available)
    }
}
