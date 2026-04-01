package com.openautolink.app.navigation

/**
 * Maps bridge maneuver strings to ManeuverType enum values.
 * Bridge sends maneuver names from aasdk's NavigationStatusMessage,
 * which originate from the phone's Android Auto navigation app.
 */
object ManeuverMapper {

    /**
     * Map a maneuver string from the bridge to a ManeuverType.
     * Handles various formats: snake_case from OAL protocol and
     * numeric codes from aasdk's TurnEvent.
     */
    fun fromWire(maneuver: String?): ManeuverType {
        if (maneuver == null) return ManeuverType.UNKNOWN

        return when (maneuver.lowercase().trim()) {
            // Standard direction-based names (OAL protocol)
            "depart" -> ManeuverType.DEPART
            "straight", "head", "continue" -> ManeuverType.STRAIGHT
            "turn_slight_left", "slight_left" -> ManeuverType.TURN_SLIGHT_LEFT
            "turn_left", "left" -> ManeuverType.TURN_LEFT
            "turn_sharp_left", "sharp_left" -> ManeuverType.TURN_SHARP_LEFT
            "turn_slight_right", "slight_right" -> ManeuverType.TURN_SLIGHT_RIGHT
            "turn_right", "right" -> ManeuverType.TURN_RIGHT
            "turn_sharp_right", "sharp_right" -> ManeuverType.TURN_SHARP_RIGHT
            "u_turn_left", "uturn_left" -> ManeuverType.U_TURN_LEFT
            "u_turn_right", "uturn_right" -> ManeuverType.U_TURN_RIGHT
            "merge_left" -> ManeuverType.MERGE_LEFT
            "merge_right" -> ManeuverType.MERGE_RIGHT
            "fork_left" -> ManeuverType.FORK_LEFT
            "fork_right" -> ManeuverType.FORK_RIGHT
            "on_ramp_left", "ramp_left" -> ManeuverType.ON_RAMP_LEFT
            "on_ramp_right", "ramp_right" -> ManeuverType.ON_RAMP_RIGHT
            "off_ramp_left" -> ManeuverType.OFF_RAMP_LEFT
            "off_ramp_right" -> ManeuverType.OFF_RAMP_RIGHT
            "roundabout_enter", "roundabout" -> ManeuverType.ROUNDABOUT_ENTER
            "roundabout_exit" -> ManeuverType.ROUNDABOUT_EXIT
            "destination", "arrive" -> ManeuverType.DESTINATION
            "destination_left" -> ManeuverType.DESTINATION_LEFT
            "destination_right" -> ManeuverType.DESTINATION_RIGHT
            "ferry", "ferry_boat", "ferry_train" -> ManeuverType.FERRY
            "name_change", "new_name" -> ManeuverType.NAME_CHANGE
            else -> ManeuverType.UNKNOWN
        }
    }

    /**
     * Get a Unicode arrow character for the maneuver type,
     * useful for simple text-based display.
     */
    fun toArrow(type: ManeuverType): String = when (type) {
        ManeuverType.STRAIGHT -> "↑"
        ManeuverType.TURN_SLIGHT_LEFT -> "↖"
        ManeuverType.TURN_LEFT -> "←"
        ManeuverType.TURN_SHARP_LEFT -> "↙"
        ManeuverType.TURN_SLIGHT_RIGHT -> "↗"
        ManeuverType.TURN_RIGHT -> "→"
        ManeuverType.TURN_SHARP_RIGHT -> "↘"
        ManeuverType.U_TURN_LEFT -> "⤺"
        ManeuverType.U_TURN_RIGHT -> "⤻"
        ManeuverType.MERGE_LEFT -> "↰"
        ManeuverType.MERGE_RIGHT -> "↱"
        ManeuverType.FORK_LEFT -> "⑂"
        ManeuverType.FORK_RIGHT -> "⑂"
        ManeuverType.ROUNDABOUT_ENTER, ManeuverType.ROUNDABOUT_EXIT -> "↻"
        ManeuverType.DESTINATION, ManeuverType.DESTINATION_LEFT, ManeuverType.DESTINATION_RIGHT -> "⚑"
        ManeuverType.DEPART -> "▶"
        ManeuverType.FERRY -> "⛴"
        else -> "•"
    }
}
