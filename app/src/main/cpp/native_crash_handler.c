/*
 * native_crash_handler.c — POSIX signal handler for native crashes.
 *
 * All code in the signal handler uses ONLY async-signal-safe functions:
 * open, write, close, _exit. No malloc, no printf, no C++ exceptions.
 */
#include "native_crash_handler.h"

#include <android/log.h>
#include <signal.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <unwind.h>
#include <inttypes.h>

#define LOG_TAG "OAL-NativeCrash"
#define MAX_FRAMES 64
#define CRASH_FILENAME "/oal-native-crash.txt"

static char sCrashFilePath[512];
static struct sigaction sOldSigsegv;
static struct sigaction sOldSigabrt;
static struct sigaction sOldSigbus;
static struct sigaction sOldSigfpe;
static struct sigaction sOldSigill;

/* Async-signal-safe integer to string (decimal). */
static int itoa_safe(int value, char* buf, int bufsize) {
    if (bufsize < 2) return 0;
    int neg = 0;
    unsigned int uval;
    if (value < 0) { neg = 1; uval = (unsigned int)(-(value + 1)) + 1; }
    else { uval = (unsigned int)value; }

    char tmp[16];
    int len = 0;
    do {
        tmp[len++] = '0' + (char)(uval % 10);
        uval /= 10;
    } while (uval > 0 && len < 15);

    int pos = 0;
    if (neg && pos < bufsize - 1) buf[pos++] = '-';
    for (int i = len - 1; i >= 0 && pos < bufsize - 1; i--)
        buf[pos++] = tmp[i];
    buf[pos] = '\0';
    return pos;
}

/* Async-signal-safe pointer to hex string. */
static int ptrtohex(uintptr_t val, char* buf, int bufsize) {
    if (bufsize < 3) return 0;
    buf[0] = '0'; buf[1] = 'x';
    int pos = 2;
    /* Find highest nibble */
    int started = 0;
    for (int i = (sizeof(uintptr_t) * 2) - 1; i >= 0; i--) {
        int nibble = (int)((val >> (i * 4)) & 0xF);
        if (nibble || started || i == 0) {
            buf[pos++] = "0123456789abcdef"[nibble];
            started = 1;
            if (pos >= bufsize - 1) break;
        }
    }
    buf[pos] = '\0';
    return pos;
}

/* Stack frame collector for _Unwind_Backtrace */
struct BacktraceState {
    uintptr_t frames[MAX_FRAMES];
    int count;
};

static _Unwind_Reason_Code unwind_callback(struct _Unwind_Context* context, void* arg) {
    struct BacktraceState* state = (struct BacktraceState*)arg;
    uintptr_t pc = _Unwind_GetIP(context);
    if (pc && state->count < MAX_FRAMES) {
        state->frames[state->count++] = pc;
    }
    return _URC_NO_REASON;
}

/* Write a string to fd (async-signal-safe). */
static void write_str(int fd, const char* s) {
    if (s) write(fd, s, strlen(s));
}

static const char* signal_name(int sig) {
    switch (sig) {
        case SIGSEGV: return "SIGSEGV";
        case SIGABRT: return "SIGABRT";
        case SIGBUS:  return "SIGBUS";
        case SIGFPE:  return "SIGFPE";
        case SIGILL:  return "SIGILL";
        default:      return "UNKNOWN";
    }
}

static const char* si_code_name(int sig, int code) {
    if (sig == SIGSEGV) {
        switch (code) {
            case SEGV_MAPERR: return "SEGV_MAPERR (address not mapped)";
            case SEGV_ACCERR: return "SEGV_ACCERR (invalid permissions)";
            default: return "unknown";
        }
    }
    if (sig == SIGBUS) {
        switch (code) {
            case BUS_ADRALN: return "BUS_ADRALN (alignment error)";
            case BUS_ADRERR: return "BUS_ADRERR (nonexistent address)";
            default: return "unknown";
        }
    }
    return "";
}

static void crash_handler(int sig, siginfo_t* info, void* ucontext) {
    (void)ucontext;

    /* Write to logcat first (most likely to succeed) */
    __android_log_print(ANDROID_LOG_FATAL, LOG_TAG,
        "NATIVE CRASH: signal=%d (%s) code=%d addr=%p",
        sig, signal_name(sig), info ? info->si_code : -1,
        info ? info->si_addr : NULL);

    /* Collect stack trace */
    struct BacktraceState bt;
    bt.count = 0;
    _Unwind_Backtrace(unwind_callback, &bt);

    /* Log stack to logcat */
    for (int i = 0; i < bt.count; i++) {
        Dl_info dl;
        if (dladdr((void*)bt.frames[i], &dl) && dl.dli_fname) {
            uintptr_t offset = bt.frames[i] - (uintptr_t)dl.dli_fbase;
            __android_log_print(ANDROID_LOG_FATAL, LOG_TAG,
                "  #%02d pc %p  %s (+%p) %s",
                i, (void*)bt.frames[i], dl.dli_fname,
                (void*)offset,
                dl.dli_sname ? dl.dli_sname : "");
        } else {
            __android_log_print(ANDROID_LOG_FATAL, LOG_TAG,
                "  #%02d pc %p", i, (void*)bt.frames[i]);
        }
    }

    /* Write crash file (async-signal-safe — no malloc, no printf) */
    int fd = open(sCrashFilePath, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) {
        char numbuf[32];

        write_str(fd, "\n=== OpenAutoLink NATIVE Crash ===\n");
        write_str(fd, "Signal: ");
        write_str(fd, signal_name(sig));
        write_str(fd, " (");
        itoa_safe(sig, numbuf, sizeof(numbuf));
        write_str(fd, numbuf);
        write_str(fd, ")\n");

        if (info) {
            write_str(fd, "Code: ");
            itoa_safe(info->si_code, numbuf, sizeof(numbuf));
            write_str(fd, numbuf);
            write_str(fd, " ");
            write_str(fd, si_code_name(sig, info->si_code));
            write_str(fd, "\n");

            write_str(fd, "Fault addr: ");
            ptrtohex((uintptr_t)info->si_addr, numbuf, sizeof(numbuf));
            write_str(fd, numbuf);
            write_str(fd, "\n");
        }

        write_str(fd, "Backtrace:\n");
        for (int i = 0; i < bt.count; i++) {
            write_str(fd, "  #");
            itoa_safe(i, numbuf, sizeof(numbuf));
            write_str(fd, numbuf);
            write_str(fd, " pc ");
            ptrtohex(bt.frames[i], numbuf, sizeof(numbuf));
            write_str(fd, numbuf);

            Dl_info dl;
            if (dladdr((void*)bt.frames[i], &dl) && dl.dli_fname) {
                write_str(fd, "  ");
                write_str(fd, dl.dli_fname);
                write_str(fd, " (+");
                ptrtohex(bt.frames[i] - (uintptr_t)dl.dli_fbase, numbuf, sizeof(numbuf));
                write_str(fd, numbuf);
                write_str(fd, ")");
                if (dl.dli_sname) {
                    write_str(fd, " ");
                    write_str(fd, dl.dli_sname);
                }
            }
            write_str(fd, "\n");
        }
        write_str(fd, "=== End native crash ===\n");

        close(fd);
    }

    /* Re-raise to let Android's default handler produce tombstone */
    struct sigaction* old = NULL;
    switch (sig) {
        case SIGSEGV: old = &sOldSigsegv; break;
        case SIGABRT: old = &sOldSigabrt; break;
        case SIGBUS:  old = &sOldSigbus; break;
        case SIGFPE:  old = &sOldSigfpe; break;
        case SIGILL:  old = &sOldSigill; break;
    }
    if (old && old->sa_sigaction) {
        old->sa_sigaction(sig, info, ucontext);
    } else {
        /* Reset to default and re-raise */
        struct sigaction dfl;
        memset(&dfl, 0, sizeof(dfl));
        dfl.sa_handler = SIG_DFL;
        sigaction(sig, &dfl, NULL);
        raise(sig);
    }
}

void oal_install_native_crash_handler(const char* crashDir) {
    if (!crashDir) return;

    /* Build crash file path */
    size_t dirLen = strlen(crashDir);
    size_t fnLen = strlen(CRASH_FILENAME);
    if (dirLen + fnLen >= sizeof(sCrashFilePath)) return;
    memcpy(sCrashFilePath, crashDir, dirLen);
    memcpy(sCrashFilePath + dirLen, CRASH_FILENAME, fnLen + 1);

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
        "Installing native crash handler, crash file: %s", sCrashFilePath);

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = crash_handler;
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);

    sigaction(SIGSEGV, &sa, &sOldSigsegv);
    sigaction(SIGABRT, &sa, &sOldSigabrt);
    sigaction(SIGBUS,  &sa, &sOldSigbus);
    sigaction(SIGFPE,  &sa, &sOldSigfpe);
    sigaction(SIGILL,  &sa, &sOldSigill);
}
