package com.openautolink.app.diagnostics

/**
 * Global diagnostic logger — subsystems call this to emit diagnostic log events.
 *
 * This is a thin facade over [RemoteDiagnostics] that allows any subsystem
 * to log without holding a direct reference to the diagnostics instance.
 * The session manager sets the active instance; when null, calls are no-ops.
 *
 * Thread-safe: the volatile reference ensures visibility across threads.
 */
object DiagnosticLog {

    @Volatile
    var instance: RemoteDiagnostics? = null

    fun d(tag: String, msg: String) {
        instance?.log(DiagnosticLevel.DEBUG, tag, msg)
    }

    fun i(tag: String, msg: String) {
        instance?.log(DiagnosticLevel.INFO, tag, msg)
    }

    fun w(tag: String, msg: String) {
        instance?.log(DiagnosticLevel.WARN, tag, msg)
    }

    fun e(tag: String, msg: String) {
        instance?.log(DiagnosticLevel.ERROR, tag, msg)
    }
}
