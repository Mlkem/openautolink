package com.openautolink.app.ui.diagnostics

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openautolink.app.data.AppPreferences
import com.openautolink.app.session.BridgeInfo
import com.openautolink.app.session.SessionManager
import com.openautolink.app.session.SessionState
import com.openautolink.app.transport.ConnectionState
import com.openautolink.app.transport.ControlMessage
import com.openautolink.app.video.CodecSelector
import com.openautolink.app.video.VideoStats
import com.openautolink.app.audio.AudioStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CodecInfo(
    val name: String,
    val hwAccelerated: Boolean,
)

data class SystemInfo(
    val androidVersion: String,
    val sdkLevel: Int,
    val device: String,
    val manufacturer: String,
    val model: String,
    val soc: String,
    val displayWidth: Int,
    val displayHeight: Int,
    val displayDpi: Int,
    val h264Decoders: List<CodecInfo>,
    val h265Decoders: List<CodecInfo>,
    val vp9Decoders: List<CodecInfo>,
)

data class NetworkInfo(
    val bridgeHost: String,
    val bridgePort: Int,
    val controlState: String,
    val videoState: String,
    val audioState: String,
    val sessionState: SessionState,
)

data class BridgeStats(
    val bridgeName: String?,
    val bridgeVersion: Int?,
    val capabilities: List<String>,
    val videoFramesSent: Long,
    val audioFramesSent: Long,
    val uptimeSeconds: Long,
    val videoStats: VideoStats,
    val audioStats: AudioStats,
)

data class LogEntry(
    val timestamp: Long,
    val severity: LogSeverity,
    val tag: String,
    val message: String,
)

enum class LogSeverity { DEBUG, INFO, WARN, ERROR }

data class DiagnosticsUiState(
    val system: SystemInfo = SystemInfo(
        androidVersion = "", sdkLevel = 0, device = "", manufacturer = "", model = "", soc = "",
        displayWidth = 0, displayHeight = 0, displayDpi = 0,
        h264Decoders = emptyList(), h265Decoders = emptyList(), vp9Decoders = emptyList(),
    ),
    val network: NetworkInfo = NetworkInfo(
        bridgeHost = "", bridgePort = 0,
        controlState = "DISCONNECTED", videoState = "DISCONNECTED",
        audioState = "DISCONNECTED", sessionState = SessionState.IDLE,
    ),
    val bridge: BridgeStats = BridgeStats(
        bridgeName = null, bridgeVersion = null, capabilities = emptyList(),
        videoFramesSent = 0, audioFramesSent = 0, uptimeSeconds = 0,
        videoStats = VideoStats(), audioStats = AudioStats(),
    ),
    val logs: List<LogEntry> = emptyList(),
    val logFilter: LogSeverity = LogSeverity.DEBUG,
)

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences.getInstance(application)

    // Shared SessionManager — retrieved from ProjectionViewModel via singleton pattern
    // For diagnostics, we observe the same session manager instance
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sessionManager = SessionManager(viewModelScope, application, audioManager)

    private val _system = MutableStateFlow(gatherSystemInfo(application))
    private val _network = MutableStateFlow(DiagnosticsUiState().network)
    private val _bridge = MutableStateFlow(DiagnosticsUiState().bridge)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    private val _logFilter = MutableStateFlow(LogSeverity.DEBUG)

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        _system,
        _network,
        _bridge,
        _logs,
        _logFilter,
    ) { system, network, bridge, logs, filter ->
        val filtered = if (filter == LogSeverity.DEBUG) logs
        else logs.filter { it.severity >= filter }
        DiagnosticsUiState(
            system = system,
            network = network,
            bridge = bridge,
            logs = filtered,
            logFilter = filter,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DiagnosticsUiState(system = _system.value)
    )

    init {
        // Observe session state for network tab
        viewModelScope.launch {
            combine(
                sessionManager.sessionState,
                sessionManager.bridgeInfo,
                preferences.bridgeHost,
                preferences.bridgePort,
            ) { state, info, host, port ->
                val connState = when (state) {
                    SessionState.IDLE -> "DISCONNECTED"
                    SessionState.CONNECTING -> "CONNECTING"
                    else -> "CONNECTED"
                }
                val videoState = when (state) {
                    SessionState.STREAMING -> "STREAMING"
                    SessionState.PHONE_CONNECTED -> "CONNECTED"
                    else -> "DISCONNECTED"
                }
                val audioState = when (state) {
                    SessionState.STREAMING -> "STREAMING"
                    SessionState.PHONE_CONNECTED -> "CONNECTED"
                    else -> "DISCONNECTED"
                }
                NetworkInfo(
                    bridgeHost = host,
                    bridgePort = port,
                    controlState = connState,
                    videoState = videoState,
                    audioState = audioState,
                    sessionState = state,
                )
            }.collect { _network.value = it }
        }

        // Observe bridge info + stats
        viewModelScope.launch {
            sessionManager.bridgeInfo.collect { info ->
                _bridge.value = _bridge.value.copy(
                    bridgeName = info?.name,
                    bridgeVersion = info?.version,
                    capabilities = info?.capabilities ?: emptyList(),
                )
            }
        }

        // Observe control messages for Stats messages
        viewModelScope.launch {
            sessionManager.controlMessages.collect { msg ->
                when (msg) {
                    is ControlMessage.Stats -> {
                        _bridge.value = _bridge.value.copy(
                            videoFramesSent = msg.videoFramesSent,
                            audioFramesSent = msg.audioFramesSent,
                            uptimeSeconds = msg.uptimeSeconds,
                        )
                    }
                    is ControlMessage.Error -> {
                        addLog(LogSeverity.ERROR, "Bridge", "Error ${msg.code}: ${msg.message}")
                    }
                    is ControlMessage.Hello -> {
                        addLog(LogSeverity.INFO, "Bridge", "Hello from ${msg.name} v${msg.version}")
                    }
                    is ControlMessage.PhoneConnected -> {
                        addLog(LogSeverity.INFO, "Session", "Phone connected: ${msg.phoneName}")
                    }
                    is ControlMessage.PhoneDisconnected -> {
                        addLog(LogSeverity.INFO, "Session", "Phone disconnected: ${msg.reason}")
                    }
                    else -> {}
                }
            }
        }

        // Observe video/audio stats
        viewModelScope.launch {
            sessionManager.sessionState.collect { state ->
                if (state == SessionState.STREAMING) {
                    sessionManager.videoStats?.let { flow ->
                        launch { flow.collect { stats -> _bridge.value = _bridge.value.copy(videoStats = stats) } }
                    }
                    sessionManager.audioStats?.let { flow ->
                        launch { flow.collect { stats -> _bridge.value = _bridge.value.copy(audioStats = stats) } }
                    }
                }
            }
        }

        addLog(LogSeverity.INFO, "Diagnostics", "Diagnostics screen opened")
    }

    fun setLogFilter(severity: LogSeverity) {
        _logFilter.value = severity
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun addLog(severity: LogSeverity, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            severity = severity,
            tag = tag,
            message = message,
        )
        _logs.value = (_logs.value + entry).takeLast(500)
    }

    companion object {
        private fun gatherSystemInfo(app: Application): SystemInfo {
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val h264Decoders = parseDecoderList(CodecSelector.listDecoders(CodecSelector.MIME_H264))
            val h265Decoders = parseDecoderList(CodecSelector.listDecoders(CodecSelector.MIME_H265))
            val vp9Decoders = parseDecoderList(CodecSelector.listDecoders(CodecSelector.MIME_VP9))

            return SystemInfo(
                androidVersion = Build.VERSION.RELEASE,
                sdkLevel = Build.VERSION.SDK_INT,
                device = Build.DEVICE,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                soc = Build.SOC_MODEL,
                displayWidth = metrics.widthPixels,
                displayHeight = metrics.heightPixels,
                displayDpi = metrics.densityDpi,
                h264Decoders = h264Decoders,
                h265Decoders = h265Decoders,
                vp9Decoders = vp9Decoders,
            )
        }

        private fun parseDecoderList(decoders: List<String>): List<CodecInfo> {
            return decoders.map { entry ->
                val hw = entry.endsWith("[HW]")
                val name = entry.removeSuffix(" [HW]").removeSuffix(" [SW]").trim()
                CodecInfo(name = name, hwAccelerated = hw)
            }
        }
    }
}
