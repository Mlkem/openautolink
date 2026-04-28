package com.openautolink.app.diagnostics

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * TCP server that streams OalLog/DiagnosticLog entries to connected clients.
 *
 * Usage from laptop (all on the same WiFi/hotspot):
 *   nc <car-ip> 6555
 *   # or: ncat <car-ip> 6555
 *   # or: telnet <car-ip> 6555
 *
 * Streams all buffered logs on connect, then live-tails new entries.
 * Supports multiple simultaneous clients.
 */
class RemoteLogServer(
    private val port: Int = DEFAULT_PORT,
) {
    companion object {
        const val DEFAULT_PORT = 6555
        private const val TAG = "RemoteLogServer"

        /** Application-scoped singleton — survives screen navigation. */
        val instance: RemoteLogServer by lazy { RemoteLogServer() }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val clients = CopyOnWriteArrayList<ClientConnection>()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _clientCount = MutableStateFlow(0)
    val clientCount: StateFlow<Int> = _clientCount.asStateFlow()

    private val _statusLog = MutableStateFlow<List<String>>(emptyList())
    val statusLog: StateFlow<List<String>> = _statusLog.asStateFlow()

    private val dateFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun start() {
        if (_isRunning.value) return
        _isRunning.value = true
        addStatus("Starting log server on port $port...")

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(port).apply { reuseAddress = true }
                serverSocket = server

                val bindAddr = getLocalIp() ?: "0.0.0.0"
                addStatus("Listening on $bindAddr:$port")
                addStatus("Connect from laptop: nc $bindAddr $port")

                while (isActive) {
                    val socket = server.accept()
                    handleClient(socket)
                }
            } catch (e: Exception) {
                if (_isRunning.value) {
                    addStatus("Server error: ${e.message}")
                }
            } finally {
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        outboundJob?.cancel()
        outboundJob = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        clients.forEach { it.close() }
        clients.clear()
        _clientCount.value = 0
        _isRunning.value = false
        addStatus("Server stopped")
    }

    // ── Outbound (reverse) mode ─────────────────────────────────────

    private var outboundJob: Job? = null

    /**
     * Connect OUTBOUND to a listener on the laptop and stream logs there.
     * Use when the hotspot blocks inbound connections to the car.
     *
     * Laptop side: just run a TCP listener to receive the stream:
     *   PowerShell: .\scripts\log-listener.ps1
     *   WSL/Linux:  nc -l -p 6555
     */
    fun connectOutbound(host: String, port: Int = DEFAULT_PORT) {
        if (_isRunning.value) return
        _isRunning.value = true
        addStatus("Connecting outbound to $host:$port...")

        outboundJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive && _isRunning.value) {
                attempt++
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 5000)
                    socket.tcpNoDelay = true
                    socket.keepAlive = true
                    addStatus("Connected to $host:$port")

                    val client = ClientConnection(socket)
                    clients.add(client)
                    _clientCount.value = clients.size

                    // Send header + buffered logs
                    client.send("=== OpenAutoLink Remote Log Server (outbound) ===")
                    client.send("=== Connected: ${Date()} ===")
                    client.send("=== Dumping ${DiagnosticLog.localLogs.value.size} buffered entries ===")
                    client.send("")

                    for (entry in DiagnosticLog.localLogs.value) {
                        if (!client.send(formatEntry(entry))) break
                    }

                    client.send("")
                    client.send("=== Live tail (new entries streamed in real-time) ===")
                    client.send("")

                    // Keep alive — check socket health periodically
                    attempt = 0  // reset on successful connect
                    while (isActive && _isRunning.value) {
                        delay(5000)
                        // Send a keepalive comment
                        if (!client.send("# keepalive ${dateFmt.format(Date())}")) {
                            addStatus("Connection lost to $host:$port")
                            break
                        }
                    }
                } catch (e: Exception) {
                    addStatus("Outbound error: ${e.message}")
                } finally {
                    clients.clear()
                    _clientCount.value = 0
                }

                if (!_isRunning.value) break
                val delay = (attempt.coerceAtMost(6)) * 2000L
                addStatus("Reconnecting in ${delay / 1000}s...")
                delay(delay)
            }
            _isRunning.value = false
        }
    }

    /**
     * Called by OalLog/DiagnosticLog when a new entry is added.
     * Broadcasts to all connected clients.
     */
    fun broadcast(entry: LocalLogEntry) {
        if (clients.isEmpty()) return
        val line = formatEntry(entry)
        val dead = mutableListOf<ClientConnection>()
        for (client in clients) {
            if (!client.send(line)) {
                dead.add(client)
            }
        }
        if (dead.isNotEmpty()) {
            clients.removeAll(dead.toSet())
            _clientCount.value = clients.size
        }
    }

    private fun handleClient(socket: Socket) {
        val remote = socket.inetAddress?.hostAddress ?: "unknown"
        addStatus("Client connected: $remote")
        OalLog.i(TAG, "Remote log client connected: $remote")

        val client = ClientConnection(socket)
        clients.add(client)
        _clientCount.value = clients.size

        // Send header + buffered logs
        scope.launch(Dispatchers.IO) {
            try {
                client.send("=== OpenAutoLink Remote Log Server ===")
                client.send("=== Connected: ${Date()} ===")
                client.send("=== Dumping ${DiagnosticLog.localLogs.value.size} buffered entries ===")
                client.send("")

                // Dump existing buffer
                for (entry in DiagnosticLog.localLogs.value) {
                    if (!client.send(formatEntry(entry))) break
                }

                client.send("")
                client.send("=== Live tail (new entries streamed in real-time) ===")
                client.send("")
            } catch (e: Exception) {
                addStatus("Error sending to $remote: ${e.message}")
                client.close()
                clients.remove(client)
                _clientCount.value = clients.size
            }
        }
    }

    private fun formatEntry(entry: LocalLogEntry): String {
        val ts = dateFmt.format(Date(entry.timestamp))
        val lvl = when (entry.level) {
            DiagnosticLevel.DEBUG -> "D"
            DiagnosticLevel.INFO -> "I"
            DiagnosticLevel.WARN -> "W"
            DiagnosticLevel.ERROR -> "E"
        }
        return "$ts $lvl/${entry.tag}: ${entry.message}"
    }

    private fun addStatus(msg: String) {
        val current = _statusLog.value
        _statusLog.value = (current + msg).takeLast(30)
    }

    private fun getLocalIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    private class ClientConnection(private val socket: Socket) {
        private val writer: BufferedWriter? = try {
            BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
        } catch (_: Exception) { null }

        fun send(line: String): Boolean {
            return try {
                writer?.write(line)
                writer?.write("\n")
                writer?.flush()
                true
            } catch (_: Exception) {
                close()
                false
            }
        }

        fun close() {
            try { writer?.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
