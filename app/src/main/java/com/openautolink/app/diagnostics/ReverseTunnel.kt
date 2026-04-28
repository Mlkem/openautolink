package com.openautolink.app.diagnostics

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Reverse TCP tunnel: connects OUTBOUND to a relay server on the laptop,
 * then bridges that connection to a local port (e.g. ADB on 127.0.0.1:5555).
 *
 * This bypasses firewall/AP-isolation that blocks inbound connections to the
 * head unit. The app initiates the connection (outgoing = allowed), and the
 * laptop relay exposes a local port for `adb connect`.
 *
 * Usage:
 *   1. On laptop: run the relay script (scripts/adb-relay.ps1)
 *      - Listens on port 15555 (for adb) and 6556 (for app tunnel)
 *   2. In app Debug tab: enter laptop IP, tap "Connect Tunnel"
 *      - App connects OUT to laptop:6556
 *      - App connects locally to 127.0.0.1:5555 (ADB)
 *      - Bridges the two sockets bidirectionally
 *   3. On laptop: adb connect localhost:15555
 *      - Traffic flows: adb → relay:15555 → tunnel → head-unit:5555
 *
 * Supports auto-reconnect if either side drops.
 */
class ReverseTunnel private constructor() {
    companion object {
        private const val TAG = "ReverseTunnel"
        private const val LOCAL_ADB_PORT = 5555
        private const val BUFFER_SIZE = 32 * 1024 // 32KB -- good for ADB traffic

        /** Application-scoped singleton -- survives screen navigation. */
        val instance: ReverseTunnel by lazy { ReverseTunnel() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class TunnelState(
        val running: Boolean = false,
        val connected: Boolean = false,
        val relayHost: String = "",
        val relayPort: Int = 6556,
        val localPort: Int = LOCAL_ADB_PORT,
        val bytesForwarded: Long = 0,
        val statusLog: List<String> = emptyList(),
        val autoReconnect: Boolean = true,
    )

    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private var tunnelJob: Job? = null
    private var relaySocket: Socket? = null
    private var localSocket: Socket? = null

    fun start(relayHost: String, relayPort: Int = 6556, localPort: Int = LOCAL_ADB_PORT) {
        if (_state.value.running) return
        _state.value = _state.value.copy(
            running = true,
            connected = false,
            relayHost = relayHost,
            relayPort = relayPort,
            localPort = localPort,
            bytesForwarded = 0,
            statusLog = emptyList(),
        )

        tunnelJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive && _state.value.running) {
                attempt++
                addLog("Attempt #$attempt: connecting to $relayHost:$relayPort...")

                try {
                    // Step 1: Connect OUTBOUND to the laptop relay
                    val relay = Socket()
                    relay.connect(InetSocketAddress(relayHost, relayPort), 5000)
                    relay.tcpNoDelay = true
                    relay.keepAlive = true
                    relaySocket = relay
                    addLog("✓ Connected to relay at $relayHost:$relayPort")

                    // Step 2: Connect locally to ADB
                    val local = Socket()
                    try {
                        local.connect(InetSocketAddress("127.0.0.1", localPort), 3000)
                        local.tcpNoDelay = true
                        localSocket = local
                        addLog("✓ Connected to local port 127.0.0.1:$localPort")
                    } catch (e: Exception) {
                        addLog("✗ Local port 127.0.0.1:$localPort not open: ${e.message}")
                        addLog("  ADB TCP may not be enabled. Try 'Enable ADB TCP' button first")
                        // Keep the relay connection alive and wait for local port to come up
                        var retries = 0
                        var localConnected = false
                        while (retries < 15 && _state.value.running) {
                            delay(2000)
                            retries++
                            try {
                                val retry = Socket()
                                retry.connect(InetSocketAddress("127.0.0.1", localPort), 1000)
                                retry.tcpNoDelay = true
                                localSocket = retry
                                addLog("✓ Local port came up after ${retries * 2}s")
                                localConnected = true
                                break
                            } catch (_: Exception) {
                                if (retries % 5 == 0) addLog("  Still waiting for 127.0.0.1:$localPort... (${retries * 2}s)")
                            }
                        }
                        if (!localConnected) {
                            addLog("✗ Gave up waiting for local port after 30s")
                            closeQuietly(relay)
                            continue
                        }
                    }

                    _state.value = _state.value.copy(connected = true)

                    // Step 3: Bridge bidirectionally
                    bridge(relay, local)

                    addLog("Tunnel closed")
                } catch (e: Exception) {
                    addLog("✗ Error: ${e.message}")
                } finally {
                    closeQuietly(relaySocket)
                    closeQuietly(localSocket)
                    relaySocket = null
                    localSocket = null
                    _state.value = _state.value.copy(connected = false)
                }

                // Auto-reconnect with backoff
                if (!_state.value.running || !_state.value.autoReconnect) break
                val delay = (attempt.coerceAtMost(6)) * 2000L
                addLog("Reconnecting in ${delay / 1000}s...")
                delay(delay)
            }

            _state.value = _state.value.copy(running = false)
        }
    }

    fun stop() {
        _state.value = _state.value.copy(running = false, autoReconnect = false)
        tunnelJob?.cancel()
        tunnelJob = null
        closeQuietly(relaySocket)
        closeQuietly(localSocket)
        relaySocket = null
        localSocket = null
        _state.value = _state.value.copy(running = false, connected = false)
        addLog("Tunnel stopped")
    }

    fun setAutoReconnect(enabled: Boolean) {
        _state.value = _state.value.copy(autoReconnect = enabled)
    }

    private suspend fun bridge(relay: Socket, local: Socket) = coroutineScope {
        // Two concurrent copy loops: relay→local and local→relay
        val job1 = launch(Dispatchers.IO) {
            copyStream("relay→adb", relay.getInputStream(), local.getOutputStream())
        }
        val job2 = launch(Dispatchers.IO) {
            copyStream("adb→relay", local.getInputStream(), relay.getOutputStream())
        }

        // When either direction ends, close both and cancel
        try {
            // Wait for either to finish (whichever socket closes first)
            kotlinx.coroutines.selects.select {
                job1.onJoin {}
                job2.onJoin {}
            }
        } finally {
            job1.cancel()
            job2.cancel()
            closeQuietly(relay)
            closeQuietly(local)
        }
    }

    private fun copyStream(label: String, input: InputStream, output: OutputStream) {
        val buf = ByteArray(BUFFER_SIZE)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                output.flush()
                _state.value = _state.value.copy(
                    bytesForwarded = _state.value.bytesForwarded + n
                )
            }
        } catch (_: Exception) {
            // Socket closed — expected on disconnect
        }
    }

    private fun addLog(msg: String) {
        OalLog.i(TAG, msg)
        val current = _state.value.statusLog
        _state.value = _state.value.copy(
            statusLog = (current + msg).takeLast(30)
        )
    }

    private fun closeQuietly(socket: Socket?) {
        try { socket?.close() } catch (_: Exception) {}
    }
}
