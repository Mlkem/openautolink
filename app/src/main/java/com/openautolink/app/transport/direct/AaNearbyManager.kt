package com.openautolink.app.transport.direct

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import com.openautolink.app.diagnostics.OalLog
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch

/**
 * Google Nearby Connections manager for Direct Mode.
 *
 * The car acts as a DISCOVERER — it finds the phone's companion app
 * (which advertises with our service ID). On connection, both sides
 * exchange STREAM payloads to create a bidirectional pipe. The AA
 * protocol runs over this pipe.
 *
 * This eliminates the need for phone hotspot or any WiFi configuration.
 * Nearby handles transport internally (BT → WiFi Direct upgrade).
 */
class AaNearbyManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onSocketReady: (Socket) -> Unit,
) {
    companion object {
        private const val TAG = "AaNearby"
        // Use the same service ID as HURev's companion app for compatibility
        private const val SERVICE_ID = "com.andrerinas.hurev"
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT

        private val _discoveredEndpoints = MutableStateFlow<List<DiscoveredEndpoint>>(emptyList())
        val discoveredEndpoints: StateFlow<List<DiscoveredEndpoint>> = _discoveredEndpoints

        private val _status = MutableStateFlow("Not started")
        val status: StateFlow<String> = _status

        private val _wifiFrequencyMhz = MutableStateFlow(0)
        /** WiFi Direct frequency in MHz (0 = unknown). >4000 = 5GHz, >0 = 2.4GHz. */
        val wifiFrequencyMhz: StateFlow<Int> = _wifiFrequencyMhz
    }

    data class DiscoveredEndpoint(val id: String, val name: String)

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private var isRunning = false
    private var isConnecting = false
    private var activeEndpointId: String? = null
    private var activeSocket: NearbySocket? = null
    private var activePipes: Array<android.os.ParcelFileDescriptor>? = null

    /** Auto-connect to first discovered endpoint. Set false for manual selection. */
    var autoConnect = true

    /** Last connected device name for auto-reconnect matching. */
    var lastDeviceName: String = ""

    fun start() {
        if (isRunning) return
        isRunning = true
        _discoveredEndpoints.value = emptyList()
        _status.value = "Starting discovery..."

        val options = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        OalLog.i(TAG, "Starting Nearby discovery (service=$SERVICE_ID)")
        try {
            connectionsClient.startDiscovery(SERVICE_ID, discoveryCallback, options)
                .addOnSuccessListener {
                    OalLog.i(TAG, "Discovery started")
                    _status.value = "Discovering..."
                }
                .addOnFailureListener { e ->
                    OalLog.e(TAG, "Discovery failed: ${e.message}")
                    _status.value = "Discovery FAILED: ${e.message}"
                    isRunning = false
                }
        } catch (e: Exception) {
            OalLog.e(TAG, "Nearby init error: ${e.message}")
            _status.value = "Init ERROR: ${e.message}"
            isRunning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun detectWifiFrequency() {
        // Delay slightly — Nearby may still be upgrading BT→WiFi Direct
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val p2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
                if (p2pManager == null) {
                    OalLog.w(TAG, "WiFi P2P not available — cannot detect frequency")
                    return@postDelayed
                }
                val p2pChannel = p2pManager.initialize(context, context.mainLooper, null)
                p2pManager.requestGroupInfo(p2pChannel) { group ->
                    if (group != null) {
                        var freq = 0
                        // Try public API first (API 30+)
                        try {
                            val getFreq = group.javaClass.getMethod("getFrequency")
                            freq = getFreq.invoke(group) as? Int ?: 0
                        } catch (_: Exception) {}
                        // Fallback: try reflection on hidden fields
                        if (freq <= 0) {
                            try {
                                for (name in arrayOf("frequency", "mFrequency")) {
                                    try {
                                        val field = group.javaClass.getDeclaredField(name)
                                        field.isAccessible = true
                                        freq = field.getInt(group)
                                        if (freq > 0) break
                                    } catch (_: Exception) {}
                                }
                            } catch (_: Exception) {}
                        }
                        val band = if (freq > 4000) "5GHz" else if (freq > 0) "2.4GHz" else "unknown"
                        OalLog.i(TAG, "WiFi Direct frequency: ${freq}MHz ($band)")
                        _wifiFrequencyMhz.value = freq
                    } else {
                        OalLog.d(TAG, "No P2P group info available (Nearby manages transport internally)")
                    }
                }
            } catch (e: Exception) {
                OalLog.w(TAG, "Could not detect WiFi frequency: ${e.message}")
            }
        }, 2000) // 2s delay for transport upgrade
    }

    fun stop() {
        OalLog.i(TAG, "Stopping")
        isRunning = false
        _wifiFrequencyMhz.value = 0
        isConnecting = false
        connectionsClient.stopDiscovery()
        activeEndpointId?.let {
            connectionsClient.disconnectFromEndpoint(it)
            activeEndpointId = null
        }
        activeSocket?.close()
        activeSocket = null
        activePipes?.forEach { try { it.close() } catch (_: Exception) {} }
        activePipes = null
        _discoveredEndpoints.value = emptyList()
    }

    /** Manually connect to a discovered endpoint (for UI-driven selection). */
    fun connectToEndpoint(endpointId: String) {
        if (isConnecting) return
        isConnecting = true
        OalLog.i(TAG, "Requesting connection to $endpointId")
        connectionsClient.requestConnection(android.os.Build.MODEL, endpointId, connectionCallback)
            .addOnFailureListener { e ->
                OalLog.e(TAG, "Connection request failed: ${e.message}")
                isConnecting = false
            }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            OalLog.i(TAG, "Found: ${info.endpointName} ($endpointId)")
            _status.value = "Found: ${info.endpointName}"
            val current = _discoveredEndpoints.value.toMutableList()
            if (current.none { it.id == endpointId }) {
                current.add(DiscoveredEndpoint(endpointId, info.endpointName))
                _discoveredEndpoints.value = current
            }

            // Auto-connect
            if (autoConnect && !isConnecting && activeEndpointId == null) {
                if (lastDeviceName.isEmpty() || lastDeviceName == info.endpointName) {
                    OalLog.i(TAG, "Auto-connecting to ${info.endpointName}")
                    connectToEndpoint(endpointId)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            OalLog.i(TAG, "Lost: $endpointId")
            val current = _discoveredEndpoints.value.toMutableList()
            current.removeAll { it.id == endpointId }
            _discoveredEndpoints.value = current
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            OalLog.i(TAG, "Connection initiated with ${info.endpointName}, accepting")
            lastDeviceName = info.endpointName
            isRunning = false
            connectionsClient.stopDiscovery()
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener { OalLog.i(TAG, "acceptConnection succeeded") }
                .addOnFailureListener { e -> OalLog.e(TAG, "acceptConnection failed: ${e.message}") }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            isConnecting = false
            if (result.status.statusCode != ConnectionsStatusCodes.STATUS_OK) {
                OalLog.e(TAG, "Connection failed: ${result.status.statusMessage}")
                return
            }

            OalLog.i(TAG, "Connected to $endpointId")
            activeEndpointId = endpointId
            val socket = NearbySocket()
            activeSocket = socket

            // Don't start the AA session yet — wait for the phone's incoming stream
            // to arrive (onPayloadReceived) AND our outgoing stream to be accepted.
            // Starting too early causes "Payload transfer FAILED" because Nearby's
            // internal transport (BT→WiFi Direct upgrade) isn't ready.
            scope.launch(Dispatchers.IO) {
                // Match HUR's timing: 800ms delay after connection, then send stream
                // and start AA immediately. Do NOT wait for phone's stream first —
                // that causes a deadlock because Nearby won't transmit our stream
                // until we write data into the pipe, and we don't write until AA starts.
                OalLog.i(TAG, "Waiting 800ms for Nearby transport to stabilize...")
                delay(800)

                // Create outgoing pipe (car → phone)
                val pipes = android.os.ParcelFileDescriptor.createPipe()
                activePipes = pipes
                socket.outputStreamWrapper = android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1])

                // Send stream payload to phone
                val outPayload = Payload.fromStream(pipes[0])
                OalLog.i(TAG, "Sending stream payload to $endpointId")
                connectionsClient.sendPayload(endpointId, outPayload)
                    .addOnSuccessListener { OalLog.i(TAG, "Stream payload registered with Nearby") }
                    .addOnFailureListener { e -> OalLog.e(TAG, "Stream send failed: ${e.message}") }

                // Start AA handshake IMMEDIATELY — don't wait for phone's stream.
                // NearbySocket.getInputStream() blocks via CountDownLatch until the
                // phone's stream arrives via onPayloadReceived. This matches HUR's
                // approach and avoids the deadlock where both sides wait for each other.
                OalLog.i(TAG, "Starting AA session — input will block until phone stream arrives")
                _status.value = "Connected — starting AA"
                detectWifiFrequency()
                onSocketReady(socket)
            }
        }

        override fun onDisconnected(endpointId: String) {
            OalLog.i(TAG, "Disconnected from $endpointId")
            if (activeEndpointId == endpointId) {
                activeEndpointId = null
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            OalLog.i(TAG, "Payload received from $endpointId, type=${payload.type}")
            if (payload.type == Payload.Type.STREAM) {
                val stream = payload.asStream()?.asInputStream()
                if (stream != null) {
                    activeSocket?.inputStreamWrapper = stream
                    OalLog.i(TAG, "Phone stream received — input ready")
                } else {
                    OalLog.e(TAG, "Stream payload was null!")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.FAILURE -> {
                    OalLog.e(TAG, "Payload transfer FAILED: id=${update.payloadId} bytes=${update.bytesTransferred}")
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    OalLog.d(TAG, "Payload transfer complete: id=${update.payloadId} bytes=${update.bytesTransferred}")
                }
                // IN_PROGRESS — don't log, too noisy
                else -> {}
            }
        }
    }
}

/**
 * Socket wrapper for Nearby Connections stream payloads.
 * Wraps bidirectional Nearby streams as a standard Socket so the AA
 * protocol layer can use it transparently.
 */
class NearbySocket : Socket() {
    private var internalInput: InputStream? = null
    private var internalOutput: OutputStream? = null
    private val inputLatch = CountDownLatch(1)
    private val outputLatch = CountDownLatch(1)

    var inputStreamWrapper: InputStream?
        get() = internalInput
        set(value) {
            internalInput = value
            if (value != null) inputLatch.countDown()
        }

    var outputStreamWrapper: OutputStream?
        get() = internalOutput
        set(value) {
            internalOutput = value
            if (value != null) outputLatch.countDown()
        }

    override fun isConnected() = true
    override fun getInetAddress(): InetAddress = InetAddress.getLoopbackAddress()

    override fun getInputStream(): InputStream = object : InputStream() {
        private fun stream(): InputStream { inputLatch.await(); return internalInput!! }
        override fun read(): Int = stream().read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = stream().read(b, off, len)
        override fun available(): Int = if (inputLatch.count == 0L) internalInput!!.available() else 0
        override fun close() { if (inputLatch.count == 0L) internalInput?.close() }
    }

    override fun getOutputStream(): OutputStream = object : OutputStream() {
        private fun stream(): OutputStream { outputLatch.await(); return internalOutput!! }
        override fun write(b: Int) { stream().write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { stream().write(b, off, len); stream().flush() }
        override fun flush() { if (outputLatch.count == 0L) internalOutput?.flush() }
        override fun close() { if (outputLatch.count == 0L) internalOutput?.close() }
    }
}
