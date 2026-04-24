package com.openautolink.app.transport.direct

import com.openautolink.app.diagnostics.OalLog
import com.openautolink.app.video.NalParser
import com.openautolink.app.video.VideoFrame
import java.nio.ByteBuffer

/**
 * Reassembles fragmented AA video frames.
 *
 * AA sends video data in potentially multiple fragments with flag bits:
 * - Flag 11 (0x0b): Single complete frame
 * - Flag 9 (0x09): First fragment of multi-part frame
 * - Flag 8 (0x08): Middle fragment
 * - Flag 10 (0x0a): Last fragment → triggers decode
 *
 * The first fragment has a timestamp indication (10-byte prefix before NAL start code)
 * or a media indication (2-byte prefix). We detect and strip these prefixes.
 */
class AaVideoAssembler(
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    private val onFrameCorrupted: () -> Unit,
) {
    companion object {
        private const val TAG = "AaVideoAssembler"
        const val DEFAULT_BUFFER_SIZE = 2 * 1024 * 1024  // 2MB for H.264
        const val H265_BUFFER_SIZE = 8 * 1024 * 1024      // 8MB for H.265
    }

    private val messageBuffer = ByteBuffer.allocate(bufferSize)
    private var isFrameCorrupt = false
    private var lastKeyframeRequestMs = 0L

    /**
     * Process an incoming video message and return a complete frame if ready.
     * @return VideoFrame if a complete frame was assembled, null otherwise
     */
    fun process(msg: AaMessage): VideoFrame? {
        val flags = msg.flags
        val data = msg.payload
        val offset = msg.payloadOffset
        val length = msg.payloadLength

        return when (flags) {
            0x0b -> { // Single complete frame
                isFrameCorrupt = false
                messageBuffer.clear()
                extractAndCreateFrame(data, offset, length)
            }

            0x09 -> { // First fragment
                isFrameCorrupt = false
                messageBuffer.clear()
                val nalOffset = findNalOffset(data, offset, length)
                if (nalOffset >= 0) {
                    val start = offset + nalOffset
                    val remaining = length - nalOffset
                    if (remaining > 0 && messageBuffer.remaining() >= remaining) {
                        messageBuffer.put(data, start, remaining)
                    }
                }
                null // Not complete yet
            }

            0x08 -> { // Middle fragment
                if (isFrameCorrupt) return null
                if (messageBuffer.remaining() >= length) {
                    messageBuffer.put(data, offset, length)
                } else {
                    OalLog.e(TAG, "Fragment overflow (mid): ${length}B > ${messageBuffer.remaining()}B remaining")
                    requestKeyframe()
                    isFrameCorrupt = true
                    messageBuffer.clear()
                }
                null
            }

            0x0a -> { // Last fragment → assemble and decode
                if (isFrameCorrupt) return null
                if (messageBuffer.remaining() >= length) {
                    messageBuffer.put(data, offset, length)
                } else {
                    OalLog.e(TAG, "Fragment overflow (last): ${length}B > ${messageBuffer.remaining()}B remaining")
                    requestKeyframe()
                    messageBuffer.clear()
                    return null
                }
                messageBuffer.flip()
                val frameSize = messageBuffer.limit()
                val frameData = ByteArray(frameSize)
                messageBuffer.get(frameData)
                messageBuffer.clear()
                val frameFlags = if (NalParser.containsIdr(frameData)) VideoFrame.FLAG_KEYFRAME else 0
                VideoFrame(width = 0, height = 0, ptsMs = System.currentTimeMillis(), flags = frameFlags, data = frameData)
            }

            else -> {
                OalLog.w(TAG, "Unknown video flag: 0x${flags.toString(16)}")
                null
            }
        }
    }

    /**
     * Find the NAL start code offset within the payload.
     * After the 2-byte message type is stripped by the readLoop, the payload is:
     *   [8B timestamp (uint64)] [NAL data with 00 00 00 01 start code]
     * Some phones may use a 10-byte prefix or 2-byte media indication instead.
     */
    private fun findNalOffset(data: ByteArray, offset: Int, length: Int): Int {
        // Check common offsets in order of likelihood
        if (length > 12 && hasStartCode(data, offset + 8)) return 8   // 8-byte timestamp (standard)
        if (length > 14 && hasStartCode(data, offset + 10)) return 10 // 10-byte timestamp (some phones)
        if (length > 6 && hasStartCode(data, offset + 2)) return 2    // 2-byte media indication
        if (length > 4 && hasStartCode(data, offset)) return 0        // raw NAL (no prefix)
        // Scan first 16 bytes as fallback
        for (i in 1..minOf(16, length - 4)) {
            if (i != 2 && i != 8 && i != 10 && hasStartCode(data, offset + i)) return i
        }
        return -1
    }

    private fun hasStartCode(data: ByteArray, pos: Int): Boolean {
        if (pos + 3 > data.size) return false
        if (data[pos].toInt() == 0 && data[pos + 1].toInt() == 0) {
            if (data[pos + 2].toInt() == 1) return true // 3-byte start code
            if (pos + 4 <= data.size && data[pos + 2].toInt() == 0 && data[pos + 3].toInt() == 1) return true
        }
        return false
    }

    private fun extractAndCreateFrame(data: ByteArray, offset: Int, length: Int): VideoFrame? {
        val nalOffset = findNalOffset(data, offset, length)
        if (nalOffset < 0) {
            OalLog.w(TAG, "No NAL start code found in single frame (${length}B)")
            return null
        }
        val start = offset + nalOffset
        val frameLength = length - nalOffset
        val frameData = data.copyOfRange(start, start + frameLength)
        // Detect IDR keyframes from NAL type so the decoder knows to start rendering
        val isIdr = NalParser.containsIdr(frameData)
        val flags = if (isIdr) VideoFrame.FLAG_KEYFRAME else 0
        if (isIdr) OalLog.i(TAG, "IDR keyframe detected (${frameLength}B)")
        return VideoFrame(width = 0, height = 0, ptsMs = System.currentTimeMillis(), flags = flags, data = frameData)
    }

    private fun requestKeyframe() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastKeyframeRequestMs > 1000) {
            lastKeyframeRequestMs = now
            OalLog.w(TAG, "Requesting keyframe due to frame corruption")
            onFrameCorrupted()
        }
    }
}
