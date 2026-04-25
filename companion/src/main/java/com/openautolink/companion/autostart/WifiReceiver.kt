package com.openautolink.companion.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives WiFi state change broadcasts from ConnectivityManager's
 * PendingIntent-based NetworkCallback. Delegates to WifiJobService
 * to check if the current SSID matches auto-start targets.
 */
class WifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "WiFi state change received")
        WifiJobService.checkWifiAndStart(context)
    }

    companion object {
        private const val TAG = "OAL_WifiRx"
    }
}
