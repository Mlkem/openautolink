/*
 * native_crash_handler.h — POSIX signal handler for native crashes.
 *
 * Catches SIGSEGV, SIGABRT, SIGBUS, SIGFPE, SIGILL and writes a crash
 * report to the app's files directory (oal-native-crash.txt). On next
 * launch, OalApplication reads this file and displays it in diagnostics.
 *
 * Limitations:
 * - Uses only async-signal-safe functions in the handler.
 * - Stack trace uses Android's <unwind.h> — no symbol names without addr2line,
 *   but the raw addresses + library offsets are logged for offline analysis.
 */
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Install native crash signal handlers.
 * @param crashDir  Absolute path to the app's filesDir (e.g., /data/data/com.openautolink.app/files)
 *                  Must remain valid (static storage or strdup'd).
 */
void oal_install_native_crash_handler(const char* crashDir);

#ifdef __cplusplus
}
#endif
