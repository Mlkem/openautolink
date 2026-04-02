package com.openautolink.app.navigation

import androidx.annotation.DrawableRes
import com.openautolink.app.R

/**
 * Maps ManeuverType to VectorDrawable resource IDs for cluster display.
 *
 * These drawables are the fallback path when the bridge doesn't provide
 * pre-rendered maneuver icons from the phone (IMAGE mode). The icons
 * originate from the CarPlay CPManeuverType icon set.
 */
object ManeuverIconRenderer {

    /**
     * Get the drawable resource for a maneuver type.
     * Returns the most appropriate icon for the given maneuver.
     */
    @DrawableRes
    fun drawableForManeuver(type: ManeuverType): Int = when (type) {
        ManeuverType.UNKNOWN -> R.drawable.cp_maneuver_00_no_turn
        ManeuverType.DEPART -> R.drawable.cp_maneuver_11_start_route
        ManeuverType.STRAIGHT -> R.drawable.cp_maneuver_03_straight_ahead
        ManeuverType.TURN_SLIGHT_LEFT -> R.drawable.cp_maneuver_49_slight_left_turn
        ManeuverType.TURN_LEFT -> R.drawable.cp_maneuver_01_left_turn
        ManeuverType.TURN_SHARP_LEFT -> R.drawable.cp_maneuver_47_sharp_left_turn
        ManeuverType.TURN_SLIGHT_RIGHT -> R.drawable.cp_maneuver_50_slight_right_turn
        ManeuverType.TURN_RIGHT -> R.drawable.cp_maneuver_02_right_turn
        ManeuverType.TURN_SHARP_RIGHT -> R.drawable.cp_maneuver_48_sharp_right_turn
        ManeuverType.U_TURN_LEFT -> R.drawable.cp_maneuver_04_uturn
        ManeuverType.U_TURN_RIGHT -> R.drawable.cp_maneuver_04_uturn
        ManeuverType.MERGE_LEFT -> R.drawable.cp_maneuver_base_merge_left
        ManeuverType.MERGE_RIGHT -> R.drawable.cp_maneuver_base_merge_right
        ManeuverType.FORK_LEFT -> R.drawable.cp_maneuver_52_change_highway_left
        ManeuverType.FORK_RIGHT -> R.drawable.cp_maneuver_53_change_highway_right
        ManeuverType.ON_RAMP_LEFT -> R.drawable.cp_maneuver_09_on_ramp
        ManeuverType.ON_RAMP_RIGHT -> R.drawable.cp_maneuver_09_on_ramp
        ManeuverType.OFF_RAMP_LEFT -> R.drawable.cp_maneuver_22_highway_off_ramp_left
        ManeuverType.OFF_RAMP_RIGHT -> R.drawable.cp_maneuver_23_highway_off_ramp_right
        ManeuverType.ROUNDABOUT_ENTER -> R.drawable.cp_maneuver_06_enter_roundabout
        ManeuverType.ROUNDABOUT_EXIT -> R.drawable.cp_maneuver_07_exit_roundabout
        ManeuverType.DESTINATION -> R.drawable.cp_maneuver_12_arrive_at_destination
        ManeuverType.DESTINATION_LEFT -> R.drawable.cp_maneuver_24_arrive_at_destination_left
        ManeuverType.DESTINATION_RIGHT -> R.drawable.cp_maneuver_25_arrive_at_destination_right
        ManeuverType.FERRY -> R.drawable.cp_maneuver_15_enter_ferry
        ManeuverType.NAME_CHANGE -> R.drawable.cp_maneuver_03_straight_ahead
    }
}
