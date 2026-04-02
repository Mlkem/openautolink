package com.openautolink.app.cluster

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.openautolink.app.navigation.ManeuverIconRenderer
import com.openautolink.app.navigation.ManeuverState
import com.openautolink.app.navigation.ManeuverType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Standard AAOS cluster session — renders NavigationTemplate directly via onGetTemplate().
 *
 * Used on non-GM AAOS platforms (Volvo, Polestar, emulator) where Templates Host renders
 * the Screen's template directly on the cluster display. The NavigationTemplate shows
 * RoutingInfo with maneuver icon, distance, and road name.
 *
 * GM AAOS ignores this session's Screen — it uses OnStarTurnByTurnManager instead.
 * But both paths call NavigationManager.updateTrip() for compatibility.
 */
class OalClusterSession : Session() {

    companion object {
        private const val TAG = "OalClusterSession"
    }

    private var screen: OalClusterScreen? = null
    private var navigationManager: NavigationManager? = null
    private var scope: CoroutineScope? = null
    private var isNavigating = false
    private var hasSeenActiveNav = false

    override fun onCreateScreen(intent: Intent): Screen {
        val clusterScreen = OalClusterScreen(carContext)
        screen = clusterScreen

        try {
            navigationManager = carContext.getCarService(NavigationManager::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get NavigationManager: ${e.message}")
        }

        navigationManager?.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                isNavigating = false
            }
            override fun onAutoDriveEnabled() {}
        })

        val sessionScope = CoroutineScope(Dispatchers.Main)
        scope = sessionScope
        sessionScope.launch { collectNavigationState() }

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (isNavigating) {
                    try { navigationManager?.navigationEnded() } catch (_: Exception) {}
                    isNavigating = false
                }
                scope?.cancel()
                scope = null
                screen = null
                navigationManager = null
            }
        })

        return clusterScreen
    }

    private suspend fun collectNavigationState() {
        var debounceJob: Job? = null
        ClusterNavigationState.state.collectLatest { maneuver ->
            debounceJob?.cancel()
            debounceJob = scope?.launch {
                delay(200)
                processStateUpdate(maneuver)
            }
        }
    }

    private fun processStateUpdate(maneuver: ManeuverState?) {
        val navManager = navigationManager ?: return

        if (maneuver != null) {
            hasSeenActiveNav = true
            if (!isNavigating) {
                try {
                    navManager.navigationStarted()
                    isNavigating = true
                } catch (e: Exception) {
                    Log.e(TAG, "navigationStarted() failed: ${e.message}")
                    return
                }
            }

            try {
                val trip = buildTrip(maneuver)
                navManager.updateTrip(trip)
            } catch (e: Exception) {
                Log.e(TAG, "updateTrip() failed: ${e.message}")
            }

            screen?.updateState(maneuver)
        } else if (isNavigating && hasSeenActiveNav) {
            try { navManager.navigationEnded() } catch (_: Exception) {}
            isNavigating = false
            screen?.updateState(null)
        }
    }

    private fun buildTrip(maneuver: ManeuverState): Trip {
        val tripBuilder = Trip.Builder()
        val eta = ZonedDateTime.now().plus(
            Duration.ofSeconds((maneuver.etaSeconds ?: 0).toLong())
        )

        val maneuverObj = buildManeuver(maneuver, carContext)
        val stepBuilder = Step.Builder()
        stepBuilder.setManeuver(maneuverObj)
        maneuver.roadName?.let { stepBuilder.setCue(it) }

        val distance = toDistance(maneuver.distanceMeters ?: 0)
        val stepEstimate = TravelEstimate.Builder(distance, eta).build()
        tripBuilder.addStep(stepBuilder.build(), stepEstimate)
        tripBuilder.setLoading(false)
        return tripBuilder.build()
    }
}

/**
 * Screen rendered on the cluster for standard AAOS platforms.
 * Returns NavigationTemplate with RoutingInfo showing maneuver, distance, road.
 */
class OalClusterScreen(carContext: CarContext) : Screen(carContext) {

    private var currentManeuver: ManeuverState? = null

    private val actionStrip = ActionStrip.Builder()
        .addAction(Action.APP_ICON)
        .build()

    fun updateState(maneuver: ManeuverState?) {
        currentManeuver = maneuver
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val maneuver = currentManeuver ?: return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()

        return try {
            val maneuverObj = buildManeuver(maneuver, carContext)
            val stepBuilder = Step.Builder()
            stepBuilder.setManeuver(maneuverObj)
            maneuver.roadName?.let { stepBuilder.setCue(it) }

            val distance = toDistance(maneuver.distanceMeters ?: 0)
            val routingInfo = RoutingInfo.Builder()
                .setCurrentStep(stepBuilder.build(), distance)
                .build()

            NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .setNavigationInfo(routingInfo)
                .build()
        } catch (e: Exception) {
            Log.e("OalClusterScreen", "Failed to build template: ${e.message}")
            NavigationTemplate.Builder()
                .setActionStrip(actionStrip)
                .build()
        }
    }
}

// ── Shared helpers for cluster sessions ──

internal fun buildManeuver(maneuver: ManeuverState, carContext: CarContext): Maneuver {
    // Path 1: AA bitmap icon from bridge (pre-rendered by Google Maps)
    val imageBase64 = maneuver.navImageBase64
    if (imageBase64 != null) {
        try {
            val bytes = Base64.decode(imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                val builder = Maneuver.Builder(Maneuver.TYPE_UNKNOWN)
                builder.setIcon(CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build())
                return builder.build()
            }
        } catch (_: Exception) {}
    }

    // Path 2: VectorDrawable fallback
    val type = mapManeuverTypeToCarApp(maneuver.type)
    val builder = Maneuver.Builder(type)
    val resId = ManeuverIconRenderer.drawableForManeuver(maneuver.type)
    builder.setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build())
    return builder.build()
}

internal fun mapManeuverTypeToCarApp(type: ManeuverType): Int = when (type) {
    ManeuverType.DEPART -> Maneuver.TYPE_DEPART
    ManeuverType.STRAIGHT -> Maneuver.TYPE_STRAIGHT
    ManeuverType.TURN_SLIGHT_LEFT -> Maneuver.TYPE_TURN_SLIGHT_LEFT
    ManeuverType.TURN_LEFT -> Maneuver.TYPE_TURN_NORMAL_LEFT
    ManeuverType.TURN_SHARP_LEFT -> Maneuver.TYPE_TURN_SHARP_LEFT
    ManeuverType.TURN_SLIGHT_RIGHT -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
    ManeuverType.TURN_RIGHT -> Maneuver.TYPE_TURN_NORMAL_RIGHT
    ManeuverType.TURN_SHARP_RIGHT -> Maneuver.TYPE_TURN_SHARP_RIGHT
    ManeuverType.U_TURN_LEFT -> Maneuver.TYPE_U_TURN_LEFT
    ManeuverType.U_TURN_RIGHT -> Maneuver.TYPE_U_TURN_RIGHT
    ManeuverType.MERGE_LEFT -> Maneuver.TYPE_MERGE_LEFT
    ManeuverType.MERGE_RIGHT -> Maneuver.TYPE_MERGE_RIGHT
    ManeuverType.FORK_LEFT -> Maneuver.TYPE_FORK_LEFT
    ManeuverType.FORK_RIGHT -> Maneuver.TYPE_FORK_RIGHT
    ManeuverType.ON_RAMP_LEFT -> Maneuver.TYPE_ON_RAMP_NORMAL_LEFT
    ManeuverType.ON_RAMP_RIGHT -> Maneuver.TYPE_ON_RAMP_NORMAL_RIGHT
    ManeuverType.OFF_RAMP_LEFT -> Maneuver.TYPE_OFF_RAMP_NORMAL_LEFT
    ManeuverType.OFF_RAMP_RIGHT -> Maneuver.TYPE_OFF_RAMP_NORMAL_RIGHT
    ManeuverType.ROUNDABOUT_ENTER -> Maneuver.TYPE_ROUNDABOUT_ENTER_CCW
    ManeuverType.ROUNDABOUT_EXIT -> Maneuver.TYPE_ROUNDABOUT_EXIT_CCW
    ManeuverType.DESTINATION -> Maneuver.TYPE_DESTINATION
    ManeuverType.DESTINATION_LEFT -> Maneuver.TYPE_DESTINATION_LEFT
    ManeuverType.DESTINATION_RIGHT -> Maneuver.TYPE_DESTINATION_RIGHT
    ManeuverType.FERRY -> Maneuver.TYPE_FERRY_BOAT
    ManeuverType.NAME_CHANGE -> Maneuver.TYPE_NAME_CHANGE
    ManeuverType.UNKNOWN -> Maneuver.TYPE_STRAIGHT
}

internal fun toDistance(meters: Int): androidx.car.app.model.Distance {
    return if (meters >= 1000) {
        androidx.car.app.model.Distance.create(
            meters / 1000.0, androidx.car.app.model.Distance.UNIT_KILOMETERS
        )
    } else {
        androidx.car.app.model.Distance.create(
            meters.toDouble(), androidx.car.app.model.Distance.UNIT_METERS
        )
    }
}
