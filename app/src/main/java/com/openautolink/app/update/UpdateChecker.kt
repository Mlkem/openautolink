package com.openautolink.app.update

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Checks a remote JSON manifest for available updates and downloads APKs.
 */
class UpdateChecker(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    sealed class CheckResult {
        data class UpdateAvailable(val manifest: UpdateManifest) : CheckResult()
        data object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    sealed class DownloadResult {
        data class Success(val apkFile: File) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    fun checkForUpdate(manifestUrl: String): CheckResult {
        if (manifestUrl.isBlank()) {
            return CheckResult.Error("No update URL configured")
        }

        val uri = try {
            URI(manifestUrl)
        } catch (e: Exception) {
            return CheckResult.Error("Invalid URL: ${e.message}")
        }

        // Only allow HTTPS
        if (uri.scheme != "https") {
            return CheckResult.Error("Update URL must use HTTPS")
        }

        val connection = try {
            uri.toURL().openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return CheckResult.Error("Connection failed: ${e.message}")
        }

        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return CheckResult.Error("HTTP $responseCode from update server")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val manifest = json.decodeFromString<UpdateManifest>(body)

            if (manifest.minAndroidSdk > Build.VERSION.SDK_INT) {
                return CheckResult.Error(
                    "Update requires Android SDK ${manifest.minAndroidSdk}, device has ${Build.VERSION.SDK_INT}"
                )
            }

            val currentVersionCode = try {
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .longVersionCode.toInt()
            } catch (e: Exception) {
                0
            }

            if (manifest.latestVersionCode > currentVersionCode) {
                CheckResult.UpdateAvailable(manifest)
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            CheckResult.Error("Check failed: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    fun downloadApk(
        apkUrl: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): DownloadResult {
        val uri = try {
            URI(apkUrl)
        } catch (e: Exception) {
            return DownloadResult.Error("Invalid APK URL: ${e.message}")
        }

        if (uri.scheme != "https") {
            return DownloadResult.Error("APK URL must use HTTPS")
        }

        val connection = try {
            uri.toURL().openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return DownloadResult.Error("Connection failed: ${e.message}")
        }

        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return DownloadResult.Error("HTTP $responseCode downloading APK")
            }

            val totalBytes = connection.contentLengthLong
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "update.apk")

            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress(totalRead, totalBytes)
                    }
                }
            }

            DownloadResult.Success(apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "APK download failed", e)
            DownloadResult.Error("Download failed: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    /** Remove cached APK files. */
    fun clearCache() {
        File(context.cacheDir, "updates").deleteRecursively()
    }

    companion object {
        private const val TAG = "UpdateChecker"
    }
}
