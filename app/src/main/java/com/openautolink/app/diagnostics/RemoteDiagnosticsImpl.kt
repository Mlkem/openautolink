package com.openautolink.app.diagnostics

import com.openautolink.app.transport.ControlMessage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Remote diagnostics implementation with rate limiting.
 *
 * Sends `app_log` and `app_telemetry` messages to the bridge over
 * the control channel. Rate-limited to 20 log messages/second
 * (newest wins — oldest are dropped when over limit).
 *
 * @param sendMessage function that sends a ControlMessage to the bridge via the control channel
 */
class RemoteDiagnosticsImpl(
    private val sendMessage: (ControlMessage) -> Unit
) : RemoteDiagnostics {

    private val _enabled = AtomicBoolean(false)
    override val enabled: Boolean get() = _enabled.get()

    private val _minLevel = AtomicReference(DiagnosticLevel.INFO)
    override val minLevel: DiagnosticLevel get() = _minLevel.get()

    // Rate limiter: track message count and window start
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

        val truncatedMsg = if (msg.length > 500) msg.take(500) else msg
        val message = ControlMessage.AppLog(
            ts = System.currentTimeMillis(),
            level = level.toWire(),
            tag = tag,
            msg = truncatedMsg
        )
        sendMessage(message)
    }

    override fun sendTelemetry(telemetry: TelemetrySnapshot) {
        if (!_enabled.get()) return

        val message = ControlMessage.AppTelemetry(
            ts = System.currentTimeMillis(),
            video = telemetry.video,
            audio = telemetry.audio,
            session = telemetry.session,
            cluster = telemetry.cluster
        )
        sendMessage(message)
    }

    /**
     * Rate limiter: allows up to [maxMessagesPerWindow] messages per [rateLimitWindowMs].
     * Returns true if the message can be sent, false if rate-limited (dropped).
     */
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
