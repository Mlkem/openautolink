package com.openautolink.app.diagnostics

import com.openautolink.app.transport.ControlMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteDiagnosticsImplTest {

    private val sentMessages = mutableListOf<ControlMessage>()
    private lateinit var diagnostics: RemoteDiagnosticsImpl

    @Before
    fun setUp() {
        sentMessages.clear()
        diagnostics = RemoteDiagnosticsImpl { sentMessages.add(it) }
    }

    @Test
    fun `log does nothing when disabled`() {
        diagnostics.setEnabled(false)
        diagnostics.log(DiagnosticLevel.INFO, "test", "hello")
        assertTrue(sentMessages.isEmpty())
    }

    @Test
    fun `log sends when enabled`() {
        diagnostics.setEnabled(true)
        diagnostics.log(DiagnosticLevel.INFO, "video", "codec selected")
        assertEquals(1, sentMessages.size)
        val msg = sentMessages[0] as ControlMessage.AppLog
        assertEquals("INFO", msg.level)
        assertEquals("video", msg.tag)
        assertEquals("codec selected", msg.msg)
    }

    @Test
    fun `log filters by minimum level`() {
        diagnostics.setEnabled(true)
        diagnostics.setMinLevel(DiagnosticLevel.WARN)
        diagnostics.log(DiagnosticLevel.DEBUG, "test", "debug msg")
        diagnostics.log(DiagnosticLevel.INFO, "test", "info msg")
        diagnostics.log(DiagnosticLevel.WARN, "test", "warn msg")
        diagnostics.log(DiagnosticLevel.ERROR, "test", "error msg")
        assertEquals(2, sentMessages.size)
        assertEquals("warn msg", (sentMessages[0] as ControlMessage.AppLog).msg)
        assertEquals("error msg", (sentMessages[1] as ControlMessage.AppLog).msg)
    }

    @Test
    fun `log truncates messages over 500 chars`() {
        diagnostics.setEnabled(true)
        val longMsg = "x".repeat(600)
        diagnostics.log(DiagnosticLevel.INFO, "test", longMsg)
        assertEquals(1, sentMessages.size)
        val msg = sentMessages[0] as ControlMessage.AppLog
        assertEquals(500, msg.msg.length)
    }

    @Test
    fun `telemetry does nothing when disabled`() {
        diagnostics.setEnabled(false)
        diagnostics.sendTelemetry(TelemetrySnapshot())
        assertTrue(sentMessages.isEmpty())
    }

    @Test
    fun `telemetry sends when enabled`() {
        diagnostics.setEnabled(true)
        val snapshot = TelemetrySnapshot(
            video = VideoTelemetry(fps = 58.2f, decoded = 1000, dropped = 5, codec = "h264", width = 1920, height = 1080),
            session = SessionTelemetry(state = "STREAMING", uptimeMs = 30000)
        )
        diagnostics.sendTelemetry(snapshot)
        assertEquals(1, sentMessages.size)
        val msg = sentMessages[0] as ControlMessage.AppTelemetry
        assertEquals(58.2f, msg.video!!.fps, 0.01f)
        assertEquals("STREAMING", msg.session!!.state)
    }

    @Test
    fun `rate limiter allows up to 20 messages per second`() {
        diagnostics.setEnabled(true)
        for (i in 1..25) {
            diagnostics.log(DiagnosticLevel.INFO, "test", "msg $i")
        }
        assertEquals(20, sentMessages.size)
    }

    @Test
    fun `enabled defaults to false`() {
        assertFalse(diagnostics.enabled)
    }

    @Test
    fun `minLevel defaults to INFO`() {
        assertEquals(DiagnosticLevel.INFO, diagnostics.minLevel)
    }
}
