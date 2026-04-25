package com.openautolink.companion.trigger

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Transparent activity that surfaces the app to the foreground before
 * launching Android Auto. Required to bypass Background Activity Launch
 * (BAL) restrictions on Android 14+.
 */
class TransparentTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        val targetIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("intent", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("intent")
        }

        if (targetIntent != null) {
            Log.i(TAG, "Launching AA via Activity...")
            try {
                startActivity(targetIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Activity launch failed: ${e.message}. Trying broadcast fallback...")
                try {
                    val port = targetIntent.getIntExtra("PARAM_SERVICE_PORT", 5288)
                    val receiverIntent = Intent().apply {
                        setClassName(
                            "com.google.android.projection.gearhead",
                            "com.google.android.apps.auto.wireless.setup.receiver.WirelessStartupReceiver"
                        )
                        action = "com.google.android.apps.auto.wireless.setup.receiver.wirelessstartup.START"
                        putExtra("ip_address", "127.0.0.1")
                        putExtra("projection_port", port)
                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    }
                    sendBroadcast(receiverIntent)
                    Log.i(TAG, "Broadcast fallback sent")
                } catch (e2: Exception) {
                    Log.e(TAG, "Both triggers failed: ${e.message} / ${e2.message}")
                }
            }
        }
        finish()
    }

    companion object {
        private const val TAG = "OAL_Trigger"
    }
}
