package com.openautolink.app.diagnostics

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Local diagnostics implementation.
 * Logs to [DiagnosticLog]'s ring buffer for the in-app diagnostics screen.
 */
class RemoteDiagnosticsImpl : RemoteDiagnostics {

    private val _enabled = AtomicBoolean(true)
    override val enabled: Boolean get() = _enabled.get()

    private val _minLevel = AtomicReference(DiagnosticLevel.INFO)
    override val minLevel: DiagnosticLevel get() = _minLevel.get()

    private val rateLimitWindowMs = 1000L
    private val maxMessagesPerWindow = 20
    @Volatile private var windowStart = 0L
    private val windowCount = AtomicInteger(0)

    override fun setEnabled(enabled: Boolean) {
        _enabled.set(enabled)
    }

    override fun setMinLevel(level: DiagnosticLevel) {
        _minLevel.set(level)
    }

    override fun log(level: DiagnosticLevel, tag: String, msg: String) {
        if (!_enabled.get()) return
        if (level.ordinal < _minLevel.get().ordinal) return
        if (!tryAcquireRate()) return

        when (level) {
            DiagnosticLevel.DEBUG -> DiagnosticLog.d(tag, msg)
            DiagnosticLevel.INFO -> DiagnosticLog.i(tag, msg)
            DiagnosticLevel.WARN -> DiagnosticLog.w(tag, msg)
            DiagnosticLevel.ERROR -> DiagnosticLog.e(tag, msg)
        }
    }

    override fun sendTelemetry(telemetry: TelemetrySnapshot) {
        // Local-only — no remote destination
    }

    private fun tryAcquireRate(): Boolean {
        val now = System.currentTimeMillis()
        if (now - windowStart >= rateLimitWindowMs) {
            windowStart = now
            windowCount.set(1)
            return true
        }
        return windowCount.incrementAndGet() <= maxMessagesPerWindow
    }
}
