package com.openautolink.app.navigation

import com.openautolink.app.transport.ControlMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages navigation state received from the bridge (originally from Android Auto).
 * Provides maneuver information for cluster display and nav overlay.
 */
interface NavigationDisplay {
    /** Current maneuver state, null when no navigation active. */
    val currentManeuver: StateFlow<ManeuverState?>

    /** Process a nav_state control message from the bridge. */
    fun onNavState(state: ControlMessage.NavState)

    /** Clear navigation state (e.g., on phone disconnect). */
    fun clear()
}

/**
 * Navigation maneuver state for display in cluster or nav overlay.
 */
data class ManeuverState(
    val type: ManeuverType,
    val distanceMeters: Int?,
    val formattedDistance: String,
    val roadName: String?,
    val etaSeconds: Int?
)

/**
 * Maneuver types mapped from bridge nav_state maneuver strings.
 * These align with Android Auto navigation turn types.
 */
enum class ManeuverType {
    UNKNOWN,
    DEPART,
    STRAIGHT,
    TURN_SLIGHT_LEFT,
    TURN_LEFT,
    TURN_SHARP_LEFT,
    TURN_SLIGHT_RIGHT,
    TURN_RIGHT,
    TURN_SHARP_RIGHT,
    U_TURN_LEFT,
    U_TURN_RIGHT,
    MERGE_LEFT,
    MERGE_RIGHT,
    FORK_LEFT,
    FORK_RIGHT,
    ON_RAMP_LEFT,
    ON_RAMP_RIGHT,
    OFF_RAMP_LEFT,
    OFF_RAMP_RIGHT,
    ROUNDABOUT_ENTER,
    ROUNDABOUT_EXIT,
    DESTINATION,
    DESTINATION_LEFT,
    DESTINATION_RIGHT,
    FERRY,
    NAME_CHANGE
}
