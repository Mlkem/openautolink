package com.openautolink.app.video

/**
 * NAL unit parser for H.264 and H.265 bitstreams.
 * Identifies NAL types from raw codec data to detect SPS/PPS/VPS and IDR frames.
 */
object NalParser {

    // H.264 NAL types
    const val H264_NAL_SLICE = 1
    const val H264_NAL_IDR = 5
    const val H264_NAL_SEI = 6
    const val H264_NAL_SPS = 7
    const val H264_NAL_PPS = 8

    // H.265 NAL types
    const val H265_NAL_IDR_W_RADL = 19
    const val H265_NAL_IDR_N_LP = 20
    const val H265_NAL_VPS = 32
    const val H265_NAL_SPS = 33
    const val H265_NAL_PPS = 34

    /**
     * Extract the H.264 NAL type from the first byte after a start code.
     * NAL type = byte & 0x1F
     */
    fun h264NalType(nalByte: Byte): Int = nalByte.toInt() and 0x1F

    /**
     * Extract the H.265 NAL type from the first byte after a start code.
     * NAL type = (byte >> 1) & 0x3F
     */
    fun h265NalType(nalByte: Byte): Int = (nalByte.toInt() shr 1) and 0x3F

    /**
     * Find the first NAL unit start code in the data and return the NAL type.
     * Start codes: 0x00 0x00 0x00 0x01 or 0x00 0x00 0x01
     * Returns -1 if no start code found.
     */
    fun findFirstH264NalType(data: ByteArray, offset: Int = 0): Int {
        val pos = findStartCode(data, offset)
        if (pos < 0) return -1
        val nalBytePos = pos + startCodeLength(data, pos)
        if (nalBytePos >= data.size) return -1
        return h264NalType(data[nalBytePos])
    }

    /**
     * Find the first NAL unit start code in H.265 data and return the NAL type.
     * Returns -1 if no start code found.
     */
    fun findFirstH265NalType(data: ByteArray, offset: Int = 0): Int {
        val pos = findStartCode(data, offset)
        if (pos < 0) return -1
        val nalBytePos = pos + startCodeLength(data, pos)
        if (nalBytePos >= data.size) return -1
        return h265NalType(data[nalBytePos])
    }

    /**
     * Check if H.264 data contains codec config (SPS or PPS).
     */
    fun isH264CodecConfig(data: ByteArray): Boolean {
        val nalType = findFirstH264NalType(data)
        return nalType == H264_NAL_SPS || nalType == H264_NAL_PPS
    }

    /**
     * Check if H.264 data is an IDR (keyframe).
     */
    fun isH264Idr(data: ByteArray): Boolean {
        val nalType = findFirstH264NalType(data)
        return nalType == H264_NAL_IDR
    }

    /**
     * Check if H.265 data contains codec config (VPS, SPS, or PPS).
     */
    fun isH265CodecConfig(data: ByteArray): Boolean {
        val nalType = findFirstH265NalType(data)
        return nalType == H265_NAL_VPS || nalType == H265_NAL_SPS || nalType == H265_NAL_PPS
    }

    /**
     * Check if H.265 data is an IDR (keyframe).
     */
    fun isH265Idr(data: ByteArray): Boolean {
        val nalType = findFirstH265NalType(data)
        return nalType == H265_NAL_IDR_W_RADL || nalType == H265_NAL_IDR_N_LP
    }

    /**
     * Collect all NAL types present in the data (H.264).
     * Useful for determining if a buffer contains both SPS and PPS.
     */
    fun collectH264NalTypes(data: ByteArray): List<Int> {
        val types = mutableListOf<Int>()
        var offset = 0
        while (offset < data.size) {
            val pos = findStartCode(data, offset)
            if (pos < 0) break
            val nalBytePos = pos + startCodeLength(data, pos)
            if (nalBytePos >= data.size) break
            types.add(h264NalType(data[nalBytePos]))
            offset = nalBytePos + 1
        }
        return types
    }

    /**
     * Collect all NAL types present in the data (H.265).
     */
    fun collectH265NalTypes(data: ByteArray): List<Int> {
        val types = mutableListOf<Int>()
        var offset = 0
        while (offset < data.size) {
            val pos = findStartCode(data, offset)
            if (pos < 0) break
            val nalBytePos = pos + startCodeLength(data, pos)
            if (nalBytePos >= data.size) break
            types.add(h265NalType(data[nalBytePos]))
            offset = nalBytePos + 1
        }
        return types
    }

    /**
     * Find the position of the next start code (0x000001 or 0x00000001) starting from offset.
     * Returns -1 if not found.
     */
    fun findStartCode(data: ByteArray, offset: Int = 0): Int {
        if (data.size < offset + 3) return -1
        var i = offset
        while (i < data.size - 2) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) return i // 3-byte start code
                if (i < data.size - 3 && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    return i // 4-byte start code
                }
            }
            i++
        }
        return -1
    }

    /**
     * Returns the length of the start code at the given position (3 or 4 bytes).
     */
    fun startCodeLength(data: ByteArray, pos: Int): Int {
        if (pos + 3 < data.size &&
            data[pos] == 0.toByte() && data[pos + 1] == 0.toByte() &&
            data[pos + 2] == 0.toByte() && data[pos + 3] == 1.toByte()
        ) return 4
        return 3
    }
}
