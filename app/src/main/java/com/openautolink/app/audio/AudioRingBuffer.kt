package com.openautolink.app.audio

import java.util.concurrent.atomic.AtomicInteger

/**
 * Lock-free Single-Producer Single-Consumer (SPSC) ring buffer for audio PCM data.
 * 
 * Absorbs TCP jitter between the network read thread (producer) and the
 * AudioTrack write thread (consumer). Capacity is specified in bytes.
 * 
 * On overflow: drops oldest samples (advances read position).
 * On underrun: returns 0 bytes (caller fills silence).
 */
class AudioRingBuffer(val capacity: Int) {

    private val buffer = ByteArray(capacity)
    private val writePos = AtomicInteger(0)
    private val readPos = AtomicInteger(0)

    @Volatile
    var overflows: Long = 0L
        private set

    @Volatile
    var underruns: Long = 0L
        private set

    /** Number of bytes available to read. */
    val available: Int
        get() {
            val w = writePos.get()
            val r = readPos.get()
            return if (w >= r) w - r else capacity - r + w
        }

    /** Free space available to write. */
    val free: Int
        get() = capacity - available - 1 // -1 to distinguish full from empty

    /**
     * Write PCM data into the ring buffer.
     * If there isn't enough space, drops oldest data to make room.
     * Called from the TCP read thread (producer).
     */
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        if (length <= 0) return
        if (length >= capacity) {
            // Data larger than buffer — keep only the newest portion
            overflows++
            val srcOffset = offset + length - (capacity - 1)
            writeInternal(data, srcOffset, capacity - 1)
            return
        }

        // If not enough free space, advance read position (drop oldest)
        val neededSpace = length - free
        if (neededSpace > 0) {
            overflows++
            val r = readPos.get()
            readPos.set((r + neededSpace) % capacity)
        }

        writeInternal(data, offset, length)
    }

    /**
     * Read PCM data from the ring buffer into the destination array.
     * Returns the number of bytes actually read (may be less than requested on underrun).
     * Called from the AudioTrack write thread (consumer).
     */
    fun read(dest: ByteArray, offset: Int = 0, length: Int = dest.size): Int {
        val avail = available
        if (avail == 0) {
            underruns++
            return 0
        }

        val toRead = minOf(length, avail)
        val r = readPos.get()

        if (r + toRead <= capacity) {
            System.arraycopy(buffer, r, dest, offset, toRead)
        } else {
            val firstChunk = capacity - r
            System.arraycopy(buffer, r, dest, offset, firstChunk)
            System.arraycopy(buffer, 0, dest, offset + firstChunk, toRead - firstChunk)
        }

        readPos.set((r + toRead) % capacity)
        return toRead
    }

    /** Clear all buffered data. */
    fun clear() {
        writePos.set(0)
        readPos.set(0)
    }

    /** Reset all counters and clear buffer. */
    fun reset() {
        clear()
        overflows = 0
        underruns = 0
    }

    private fun writeInternal(data: ByteArray, offset: Int, length: Int) {
        val w = writePos.get()

        if (w + length <= capacity) {
            System.arraycopy(data, offset, buffer, w, length)
        } else {
            val firstChunk = capacity - w
            System.arraycopy(data, offset, buffer, w, firstChunk)
            System.arraycopy(data, offset + firstChunk, buffer, 0, length - firstChunk)
        }

        writePos.set((w + length) % capacity)
    }
}
