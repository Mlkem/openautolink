package com.openautolink.app.ui.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsModelTest {

    @Test
    fun `LogSeverity ordering allows filtering`() {
        // Entries should be ordered: DEBUG < INFO < WARN < ERROR
        val entries = LogSeverity.entries
        assertEquals(LogSeverity.DEBUG, entries[0])
        assertEquals(LogSeverity.INFO, entries[1])
        assertEquals(LogSeverity.WARN, entries[2])
        assertEquals(LogSeverity.ERROR, entries[3])
    }

    @Test
    fun `LogSeverity filtering by ordinal comparison`() {
        val allLogs = listOf(
            LogEntry(1000, LogSeverity.DEBUG, "Test", "debug msg"),
            LogEntry(2000, LogSeverity.INFO, "Test", "info msg"),
            LogEntry(3000, LogSeverity.WARN, "Test", "warn msg"),
            LogEntry(4000, LogSeverity.ERROR, "Test", "error msg"),
        )

        // Filter by WARN — should include WARN and ERROR
        val filter = LogSeverity.WARN
        val filtered = allLogs.filter { it.severity >= filter }
        assertEquals(2, filtered.size)
        assertEquals(LogSeverity.WARN, filtered[0].severity)
        assertEquals(LogSeverity.ERROR, filtered[1].severity)
    }

    @Test
    fun `LogSeverity DEBUG filter includes all logs`() {
        val allLogs = listOf(
            LogEntry(1000, LogSeverity.DEBUG, "Test", "debug msg"),
            LogEntry(2000, LogSeverity.INFO, "Test", "info msg"),
            LogEntry(3000, LogSeverity.ERROR, "Test", "error msg"),
        )

        val filter = LogSeverity.DEBUG
        val filtered = allLogs.filter { it.severity >= filter }
        assertEquals(3, filtered.size)
    }

    @Test
    fun `LogSeverity ERROR filter includes only errors`() {
        val allLogs = listOf(
            LogEntry(1000, LogSeverity.DEBUG, "Test", "debug msg"),
            LogEntry(2000, LogSeverity.INFO, "Test", "info msg"),
            LogEntry(3000, LogSeverity.WARN, "Test", "warn msg"),
            LogEntry(4000, LogSeverity.ERROR, "Test", "error msg"),
        )

        val filter = LogSeverity.ERROR
        val filtered = allLogs.filter { it.severity >= filter }
        assertEquals(1, filtered.size)
        assertEquals("error msg", filtered[0].message)
    }

    @Test
    fun `CodecInfo parses HW flag correctly`() {
        val hwCodec = CodecInfo("c2.qti.avc.decoder", hwAccelerated = true)
        val swCodec = CodecInfo("c2.android.avc.decoder", hwAccelerated = false)

        assertTrue(hwCodec.hwAccelerated)
        assertTrue(!swCodec.hwAccelerated)
    }

    @Test
    fun `DiagnosticsUiState default values`() {
        val state = DiagnosticsUiState()
        assertEquals(LogSeverity.DEBUG, state.logFilter)
        assertTrue(state.logs.isEmpty())
        assertEquals("", state.system.androidVersion)
        assertEquals(0, state.network.bridgePort)
    }

    @Test
    fun `NetworkInfo default session state is IDLE`() {
        val state = DiagnosticsUiState()
        assertEquals(com.openautolink.app.session.SessionState.IDLE, state.network.sessionState)
    }

    @Test
    fun `BridgeStats default has no bridge name`() {
        val state = DiagnosticsUiState()
        assertEquals(null, state.bridge.bridgeName)
        assertEquals(null, state.bridge.bridgeVersion)
        assertTrue(state.bridge.capabilities.isEmpty())
    }

    @Test
    fun `logs takeLast limits to 500 entries`() {
        val logs = (1..600).map { i ->
            LogEntry(i.toLong(), LogSeverity.INFO, "Test", "message $i")
        }.takeLast(500)
        assertEquals(500, logs.size)
        assertEquals("message 101", logs.first().message)
        assertEquals("message 600", logs.last().message)
    }
}
