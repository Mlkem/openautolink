package com.openautolink.app.input

/**
 * Forwards GNSS/location data from the car's LocationManager to the bridge.
 * The bridge relays NMEA sentences to the phone via aasdk sensor channel,
 * enabling Google Maps on the phone to use the car's GPS.
 */
interface GnssForwarder {
    /** Start GNSS forwarding. Requires ACCESS_FINE_LOCATION permission. */
    fun start()

    /** Stop GNSS forwarding and unregister listeners. */
    fun stop()

    /** Whether GNSS forwarding is currently active. */
    val isActive: Boolean
}
