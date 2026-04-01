package com.openautolink.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import java.io.File

/**
 * Installs an APK using the PackageInstaller session API.
 * Falls back to a user-friendly error if the device blocks installation.
 */
class AppInstaller(private val context: Context) {

    sealed class InstallResult {
        data object SessionCreated : InstallResult()
        data class Error(val message: String) : InstallResult()
    }

    /**
     * Creates a PackageInstaller session and writes the APK into it.
     * The system will show an install confirmation prompt to the user.
     */
    fun installApk(apkFile: File): InstallResult {
        if (!apkFile.exists()) {
            return InstallResult.Error("APK file not found")
        }

        return try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            params.setSize(apkFile.length())

            val sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)

            // Write APK into the session
            session.openWrite("update.apk", 0, apkFile.length()).use { output ->
                apkFile.inputStream().use { input ->
                    input.copyTo(output)
                }
                session.fsync(output)
            }

            // Create a status receiver intent
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = ACTION_INSTALL_STATUS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )

            // Commit — this triggers the system install prompt
            session.commit(pendingIntent.intentSender)
            Log.i(TAG, "Install session $sessionId committed")

            InstallResult.SessionCreated
        } catch (e: SecurityException) {
            Log.e(TAG, "Install blocked by system policy", e)
            InstallResult.Error(
                "Installation not permitted on this device. " +
                    "AAOS may block app installation from non-system sources."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            InstallResult.Error("Installation failed: ${e.message}")
        }
    }

    /**
     * Check if this device allows installing packages from this app.
     */
    fun canInstallPackages(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    companion object {
        private const val TAG = "AppInstaller"
        const val ACTION_INSTALL_STATUS = "com.openautolink.app.INSTALL_STATUS"
    }
}
