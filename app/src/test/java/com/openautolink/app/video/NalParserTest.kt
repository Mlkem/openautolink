package com.openautolink.app.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NalParserTest {

    // Helper to build a byte array with a 4-byte start code followed by a NAL byte
    private fun h264Nal(nalType: Int): ByteArray =
        byteArrayOf(0x00, 0x00, 0x00, 0x01, nalType.toByte())

    // H.264: NAL type = byte & 0x1F
    // So SPS (7) = 0x67 (with forbidden_bit=0, nal_ref_idc=3), or just 0x07
    private fun h264NalWithRefIdc(nalType: Int, refIdc: Int = 3): ByteArray =
        byteArrayOf(0x00, 0x00, 0x00, 0x01, ((refIdc shl 5) or nalType).toByte())

    // Helper for 3-byte start code
    private fun h264Nal3Byte(nalType: Int): ByteArray =
        byteArrayOf(0x00, 0x00, 0x01, nalType.toByte())

    @Test
    fun `h264NalType extracts type from NAL byte`() {
        assertEquals(NalParser.H264_NAL_SPS, NalParser.h264NalType(0x07.toByte()))
        assertEquals(NalParser.H264_NAL_SPS, NalParser.h264NalType(0x67.toByte())) // with nal_ref_idc=3
        assertEquals(NalParser.H264_NAL_PPS, NalParser.h264NalType(0x08.toByte()))
        assertEquals(NalParser.H264_NAL_PPS, NalParser.h264NalType(0x68.toByte()))
        assertEquals(NalParser.H264_NAL_IDR, NalParser.h264NalType(0x65.toByte()))
        assertEquals(NalParser.H264_NAL_SLICE, NalParser.h264NalType(0x41.toByte()))
    }

    @Test
    fun `h265NalType extracts type from NAL byte`() {
        // H.265: NAL type = (byte >> 1) & 0x3F
        // VPS (32) → (32 << 1) = 64 = 0x40
        assertEquals(NalParser.H265_NAL_VPS, NalParser.h265NalType(0x40.toByte()))
        // SPS (33) → (33 << 1) = 66 = 0x42
        assertEquals(NalParser.H265_NAL_SPS, NalParser.h265NalType(0x42.toByte()))
        // PPS (34) → (34 << 1) = 68 = 0x44
        assertEquals(NalParser.H265_NAL_PPS, NalParser.h265NalType(0x44.toByte()))
        // IDR_W_RADL (19) → (19 << 1) = 38 = 0x26
        assertEquals(NalParser.H265_NAL_IDR_W_RADL, NalParser.h265NalType(0x26.toByte()))
        // IDR_N_LP (20) → (20 << 1) = 40 = 0x28
        assertEquals(NalParser.H265_NAL_IDR_N_LP, NalParser.h265NalType(0x28.toByte()))
    }

    @Test
    fun `findStartCode finds 4-byte start code`() {
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67)
        assertEquals(0, NalParser.findStartCode(data))
    }

    @Test
    fun `findStartCode finds 3-byte start code`() {
        val data = byteArrayOf(0x00, 0x00, 0x01, 0x67)
        assertEquals(0, NalParser.findStartCode(data))
    }

    @Test
    fun `findStartCode finds start code with offset`() {
        val data = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0x00, 0x00, 0x00, 0x01, 0x67)
        assertEquals(2, NalParser.findStartCode(data, 0))
        assertEquals(2, NalParser.findStartCode(data, 2))
    }

    @Test
    fun `findStartCode returns -1 for no start code`() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        assertEquals(-1, NalParser.findStartCode(data))
    }

    @Test
    fun `findStartCode returns -1 for too short data`() {
        assertEquals(-1, NalParser.findStartCode(byteArrayOf(0x00, 0x00)))
        assertEquals(-1, NalParser.findStartCode(byteArrayOf()))
    }

    @Test
    fun `startCodeLength returns 4 for 4-byte start code`() {
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x67)
        assertEquals(4, NalParser.startCodeLength(data, 0))
    }

    @Test
    fun `startCodeLength returns 3 for 3-byte start code`() {
        val data = byteArrayOf(0x00, 0x00, 0x01, 0x67)
        assertEquals(3, NalParser.startCodeLength(data, 0))
    }

    @Test
    fun `findFirstH264NalType finds SPS`() {
        val data = h264Nal(NalParser.H264_NAL_SPS)
        assertEquals(NalParser.H264_NAL_SPS, NalParser.findFirstH264NalType(data))
    }

    @Test
    fun `findFirstH264NalType finds IDR`() {
        val data = h264Nal(NalParser.H264_NAL_IDR)
        assertEquals(NalParser.H264_NAL_IDR, NalParser.findFirstH264NalType(data))
    }

    @Test
    fun `findFirstH264NalType returns -1 for empty data`() {
        assertEquals(-1, NalParser.findFirstH264NalType(byteArrayOf()))
    }

    @Test
    fun `isH264CodecConfig detects SPS`() {
        assertTrue(NalParser.isH264CodecConfig(h264Nal(NalParser.H264_NAL_SPS)))
    }

    @Test
    fun `isH264CodecConfig detects PPS`() {
        assertTrue(NalParser.isH264CodecConfig(h264Nal(NalParser.H264_NAL_PPS)))
    }

    @Test
    fun `isH264CodecConfig rejects IDR`() {
        assertFalse(NalParser.isH264CodecConfig(h264Nal(NalParser.H264_NAL_IDR)))
    }

    @Test
    fun `isH264Idr detects IDR frame`() {
        assertTrue(NalParser.isH264Idr(h264Nal(NalParser.H264_NAL_IDR)))
    }

    @Test
    fun `isH264Idr rejects non-IDR`() {
        assertFalse(NalParser.isH264Idr(h264Nal(NalParser.H264_NAL_SLICE)))
    }

    @Test
    fun `isH265CodecConfig detects VPS`() {
        // VPS NAL type 32 → byte = (32 << 1) = 0x40
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x40)
        assertTrue(NalParser.isH265CodecConfig(data))
    }

    @Test
    fun `isH265CodecConfig detects SPS`() {
        // SPS NAL type 33 → byte = (33 << 1) = 0x42
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x42)
        assertTrue(NalParser.isH265CodecConfig(data))
    }

    @Test
    fun `isH265Idr detects IDR_W_RADL`() {
        // IDR_W_RADL NAL type 19 → byte = (19 << 1) = 0x26
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x26)
        assertTrue(NalParser.isH265Idr(data))
    }

    @Test
    fun `isH265Idr detects IDR_N_LP`() {
        // IDR_N_LP NAL type 20 → byte = (20 << 1) = 0x28
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x28)
        assertTrue(NalParser.isH265Idr(data))
    }

    @Test
    fun `collectH264NalTypes finds multiple NALs`() {
        // SPS + PPS in one buffer
        val sps = h264Nal(NalParser.H264_NAL_SPS)
        val pps = h264Nal(NalParser.H264_NAL_PPS)
        val combined = sps + byteArrayOf(0x01, 0x02) + pps // some data between
        val types = NalParser.collectH264NalTypes(combined)
        assertTrue(types.contains(NalParser.H264_NAL_SPS))
        assertTrue(types.contains(NalParser.H264_NAL_PPS))
    }

    @Test
    fun `collectH265NalTypes finds multiple NALs`() {
        // VPS (0x40) + SPS (0x42) + PPS (0x44)
        val data = byteArrayOf(
            0x00, 0x00, 0x00, 0x01, 0x40, 0x01, // VPS
            0x00, 0x00, 0x00, 0x01, 0x42, 0x01, // SPS
            0x00, 0x00, 0x00, 0x01, 0x44, 0x01, // PPS
        )
        val types = NalParser.collectH265NalTypes(data)
        assertEquals(3, types.size)
        assertTrue(types.contains(NalParser.H265_NAL_VPS))
        assertTrue(types.contains(NalParser.H265_NAL_SPS))
        assertTrue(types.contains(NalParser.H265_NAL_PPS))
    }

    @Test
    fun `findFirstH264NalType with real SPS byte including nal_ref_idc`() {
        // Real H.264 SPS: 0x67 = nal_ref_idc=3, type=7
        val data = h264NalWithRefIdc(NalParser.H264_NAL_SPS, 3)
        assertEquals(NalParser.H264_NAL_SPS, NalParser.findFirstH264NalType(data))
    }

    @Test
    fun `findFirstH264NalType with 3-byte start code`() {
        val data = h264Nal3Byte(NalParser.H264_NAL_SPS)
        assertEquals(NalParser.H264_NAL_SPS, NalParser.findFirstH264NalType(data))
    }
}
