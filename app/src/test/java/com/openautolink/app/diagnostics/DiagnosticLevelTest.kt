package com.openautolink.app.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticLevelTest {

    @Test
    fun `toWire returns uppercase level name`() {
        assertEquals("DEBUG", DiagnosticLevel.DEBUG.toWire())
        assertEquals("INFO", DiagnosticLevel.INFO.toWire())
        assertEquals("WARN", DiagnosticLevel.WARN.toWire())
        assertEquals("ERROR", DiagnosticLevel.ERROR.toWire())
    }

    @Test
    fun `fromWire parses case-insensitively`() {
        assertEquals(DiagnosticLevel.DEBUG, DiagnosticLevel.fromWire("debug"))
        assertEquals(DiagnosticLevel.INFO, DiagnosticLevel.fromWire("INFO"))
        assertEquals(DiagnosticLevel.WARN, DiagnosticLevel.fromWire("Warn"))
        assertEquals(DiagnosticLevel.ERROR, DiagnosticLevel.fromWire("error"))
    }

    @Test
    fun `fromWire returns INFO for unknown values`() {
        assertEquals(DiagnosticLevel.INFO, DiagnosticLevel.fromWire("unknown"))
        assertEquals(DiagnosticLevel.INFO, DiagnosticLevel.fromWire(""))
    }

    @Test
    fun `ordinal ordering is DEBUG less than INFO less than WARN less than ERROR`() {
        assertTrue(DiagnosticLevel.DEBUG.ordinal < DiagnosticLevel.INFO.ordinal)
        assertTrue(DiagnosticLevel.INFO.ordinal < DiagnosticLevel.WARN.ordinal)
        assertTrue(DiagnosticLevel.WARN.ordinal < DiagnosticLevel.ERROR.ordinal)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
