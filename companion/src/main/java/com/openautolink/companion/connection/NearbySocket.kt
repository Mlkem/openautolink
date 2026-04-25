package com.openautolink.companion.connection

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch

/**
 * Socket wrapper that bridges Google Nearby Connections stream payloads
 * to standard blocking Socket I/O. Android Auto expects a TCP socket;
 * Nearby uses callback-based payload delivery. This adapter uses
 * CountDownLatches to block read/write until the corresponding Nearby
 * stream arrives.
 */
class NearbySocket : Socket() {

    private var internalInput: InputStream? = null
    private var internalOutput: OutputStream? = null
    private val inputLatch = CountDownLatch(1)
    private val outputLatch = CountDownLatch(1)

    var inputStreamWrapper: InputStream?
        get() = internalInput
        set(value) {
            internalInput = value
            if (value != null) inputLatch.countDown()
        }

    var outputStreamWrapper: OutputStream?
        get() = internalOutput
        set(value) {
            internalOutput = value
            if (value != null) outputLatch.countDown()
        }

    override fun isConnected() = true
    override fun getInetAddress(): InetAddress = InetAddress.getLoopbackAddress()

    override fun getInputStream(): InputStream = object : InputStream() {
        private fun stream(): InputStream {
            inputLatch.await()
            return internalInput!!
        }

        override fun read(): Int = stream().read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream().read(b, off, len)
        override fun available(): Int =
            if (inputLatch.count == 0L) internalInput!!.available() else 0

        override fun close() {
            if (inputLatch.count == 0L) internalInput?.close()
        }
    }

    override fun getOutputStream(): OutputStream = object : OutputStream() {
        private fun stream(): OutputStream {
            outputLatch.await()
            return internalOutput!!
        }

        override fun write(b: Int) {
            stream().write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            stream().write(b, off, len)
            stream().flush()
        }

        override fun flush() {
            if (outputLatch.count == 0L) internalOutput?.flush()
        }

        override fun close() {
            if (outputLatch.count == 0L) internalOutput?.close()
        }
    }
}
