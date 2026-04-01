package com.openautolink.app.input

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.location.OnNmeaMessageListener
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.openautolink.app.transport.ControlMessage

/**
 * Forwards NMEA sentences from Android LocationManager to the bridge via the control channel.
 * The bridge relays these to aasdk's SensorService → phone's Google Maps.
 *
 * Requires ACCESS_FINE_LOCATION permission — caller must ensure permission is granted
 * before calling start(). If permission is missing, start() logs a warning and returns.
 */
class GnssForwarderImpl(
    private val context: Context,
    private val sendMessage: (ControlMessage.Gnss) -> Unit
) : GnssForwarder {

    companion object {
        private const val TAG = "GnssForwarder"
    }

    private var nmeaListener: OnNmeaMessageListener? = null
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override var isActive: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    override fun start() {
        if (isActive) return

        val lm = locationManager
        if (lm == null) {
            Log.w(TAG, "LocationManager not available")
            return
        }

        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.w(TAG, "GPS provider not enabled")
            return
        }

        val listener = OnNmeaMessageListener { nmea, _ ->
            // Only forward standard GPS sentences
            if (nmea.startsWith("\$GP") || nmea.startsWith("\$GN") || nmea.startsWith("\$GL")) {
                val trimmed = nmea.trimEnd('\r', '\n')
                if (trimmed.isNotEmpty()) {
                    sendMessage(ControlMessage.Gnss(trimmed))
                }
            }
        }

        try {
            lm.addNmeaListener(listener, Handler(Looper.getMainLooper()))
            nmeaListener = listener
            isActive = true
            Log.i(TAG, "GNSS forwarding started")
        } catch (e: SecurityException) {
            Log.w(TAG, "GNSS permission denied: ${e.message}")
        }
    }

    override fun stop() {
        if (!isActive) return

        nmeaListener?.let { listener ->
            locationManager?.removeNmeaListener(listener)
        }
        nmeaListener = null
        isActive = false
        Log.i(TAG, "GNSS forwarding stopped")
    }
}
