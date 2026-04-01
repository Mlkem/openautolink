package com.openautolink.app.input

/**
 * Forwards vehicle data from AAOS VHAL (Vehicle HAL) to the bridge.
 * Uses Car API via reflection — graceful fallback when android.car is unavailable.
 * The bridge relays sensor data to the phone via aasdk sensor channel.
 */
interface VehicleDataForwarder {
    /** Start monitoring VHAL properties and forwarding to bridge. */
    fun start()

    /** Stop monitoring and unregister all property listeners. */
    fun stop()

    /** Whether vehicle data monitoring is currently active. */
    val isActive: Boolean
}
