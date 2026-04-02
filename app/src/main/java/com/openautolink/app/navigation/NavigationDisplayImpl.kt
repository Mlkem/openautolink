package com.openautolink.app.navigation

import com.openautolink.app.transport.ControlMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Processes nav_state control messages from the bridge and maintains
 * the current maneuver state for display in UI or cluster service.
 */
class NavigationDisplayImpl : NavigationDisplay {

    private val _currentManeuver = MutableStateFlow<ManeuverState?>(null)
    override val currentManeuver: StateFlow<ManeuverState?> = _currentManeuver.asStateFlow()

    override fun onNavState(state: ControlMessage.NavState) {
        val type = ManeuverMapper.fromWire(state.maneuver)
        val formattedDist = DistanceFormatter.format(state.distanceMeters)

        _currentManeuver.value = ManeuverState(
            type = type,
            distanceMeters = state.distanceMeters,
            formattedDistance = formattedDist,
            roadName = state.road,
            etaSeconds = state.etaSeconds,
            navImageBase64 = state.navImageBase64
        )
    }

    override fun clear() {
        _currentManeuver.value = null
    }
}
