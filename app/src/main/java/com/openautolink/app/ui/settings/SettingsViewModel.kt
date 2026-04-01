package com.openautolink.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openautolink.app.data.AppPreferences
import com.openautolink.app.update.AppInstaller
import com.openautolink.app.update.UpdateChecker
import com.openautolink.app.update.UpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val bridgeHost: String = AppPreferences.DEFAULT_BRIDGE_HOST,
    val bridgePort: Int = AppPreferences.DEFAULT_BRIDGE_PORT,
    val videoCodec: String = AppPreferences.DEFAULT_VIDEO_CODEC,
    val videoFps: Int = AppPreferences.DEFAULT_VIDEO_FPS,
    val displayMode: String = AppPreferences.DEFAULT_DISPLAY_MODE,
    val micSource: String = AppPreferences.DEFAULT_MIC_SOURCE,
    val selfUpdateEnabled: String = AppPreferences.DEFAULT_SELF_UPDATE_ENABLED,
    val updateManifestUrl: String = AppPreferences.DEFAULT_UPDATE_MANIFEST_URL,
)

sealed class UpdateStatus {
    data object Idle : UpdateStatus()
    data object Checking : UpdateStatus()
    data class UpdateAvailable(val manifest: UpdateManifest) : UpdateStatus()
    data object UpToDate : UpdateStatus()
    data class Downloading(val progress: Float) : UpdateStatus()
    data object Installing : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences.getInstance(application)
    private val updateChecker = UpdateChecker(application)
    private val appInstaller = AppInstaller(application)

    val updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.bridgeHost,
        preferences.bridgePort,
        preferences.videoCodec,
        preferences.videoFps,
        preferences.displayMode,
        preferences.micSource,
        preferences.selfUpdateEnabled,
        preferences.updateManifestUrl,
    ) { values: Array<Any> ->
        SettingsUiState(
            bridgeHost = values[0] as String,
            bridgePort = values[1] as Int,
            videoCodec = values[2] as String,
            videoFps = values[3] as Int,
            displayMode = values[4] as String,
            micSource = values[5] as String,
            selfUpdateEnabled = values[6] as String,
            updateManifestUrl = values[7] as String,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        SettingsUiState()
    )

    fun updateBridgeHost(host: String) {
        viewModelScope.launch { preferences.setBridgeHost(host) }
    }

    fun updateBridgePort(port: Int) {
        viewModelScope.launch { preferences.setBridgePort(port) }
    }

    fun updateVideoCodec(codec: String) {
        viewModelScope.launch { preferences.setVideoCodec(codec) }
    }

    fun updateVideoFps(fps: Int) {
        viewModelScope.launch { preferences.setVideoFps(fps) }
    }

    fun updateDisplayMode(mode: String) {
        viewModelScope.launch { preferences.setDisplayMode(mode) }
    }

    fun updateMicSource(source: String) {
        viewModelScope.launch { preferences.setMicSource(source) }
    }

    fun updateSelfUpdateEnabled(enabled: String) {
        viewModelScope.launch { preferences.setSelfUpdateEnabled(enabled) }
    }

    fun updateManifestUrl(url: String) {
        viewModelScope.launch { preferences.setUpdateManifestUrl(url) }
    }

    fun checkForUpdate(manifestUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updateStatus.value = UpdateStatus.Checking
            when (val result = updateChecker.checkForUpdate(manifestUrl)) {
                is UpdateChecker.CheckResult.UpdateAvailable -> {
                    updateStatus.value = UpdateStatus.UpdateAvailable(result.manifest)
                }
                is UpdateChecker.CheckResult.UpToDate -> {
                    updateStatus.value = UpdateStatus.UpToDate
                }
                is UpdateChecker.CheckResult.Error -> {
                    updateStatus.value = UpdateStatus.Error(result.message)
                }
            }
        }
    }

    fun downloadAndInstall(apkUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updateStatus.value = UpdateStatus.Downloading(0f)

            when (val result = updateChecker.downloadApk(apkUrl) { downloaded, total ->
                val progress = if (total > 0) downloaded.toFloat() / total else 0f
                updateStatus.value = UpdateStatus.Downloading(progress)
            }) {
                is UpdateChecker.DownloadResult.Success -> {
                    updateStatus.value = UpdateStatus.Installing
                    when (val installResult = appInstaller.installApk(result.apkFile)) {
                        is AppInstaller.InstallResult.SessionCreated -> {
                            // System takes over with install prompt
                            updateStatus.value = UpdateStatus.Idle
                        }
                        is AppInstaller.InstallResult.Error -> {
                            updateStatus.value = UpdateStatus.Error(installResult.message)
                        }
                    }
                }
                is UpdateChecker.DownloadResult.Error -> {
                    updateStatus.value = UpdateStatus.Error(result.message)
                }
            }
        }
    }

    fun dismissUpdateStatus() {
        updateStatus.value = UpdateStatus.Idle
    }

    fun canInstallPackages(): Boolean = appInstaller.canInstallPackages()
}
