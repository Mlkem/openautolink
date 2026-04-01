package com.openautolink.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManeuverMapperTest {

    @Test
    fun `standard turn directions map correctly`() {
        assertEquals(ManeuverType.TURN_LEFT, ManeuverMapper.fromWire("turn_left"))
        assertEquals(ManeuverType.TURN_RIGHT, ManeuverMapper.fromWire("turn_right"))
        assertEquals(ManeuverType.TURN_SLIGHT_LEFT, ManeuverMapper.fromWire("turn_slight_left"))
        assertEquals(ManeuverType.TURN_SLIGHT_RIGHT, ManeuverMapper.fromWire("turn_slight_right"))
        assertEquals(ManeuverType.TURN_SHARP_LEFT, ManeuverMapper.fromWire("turn_sharp_left"))
        assertEquals(ManeuverType.TURN_SHARP_RIGHT, ManeuverMapper.fromWire("turn_sharp_right"))
    }

    @Test
    fun `short form aliases map correctly`() {
        assertEquals(ManeuverType.TURN_LEFT, ManeuverMapper.fromWire("left"))
        assertEquals(ManeuverType.TURN_RIGHT, ManeuverMapper.fromWire("right"))
        assertEquals(ManeuverType.TURN_SLIGHT_LEFT, ManeuverMapper.fromWire("slight_left"))
        assertEquals(ManeuverType.TURN_SLIGHT_RIGHT, ManeuverMapper.fromWire("slight_right"))
        assertEquals(ManeuverType.TURN_SHARP_LEFT, ManeuverMapper.fromWire("sharp_left"))
        assertEquals(ManeuverType.TURN_SHARP_RIGHT, ManeuverMapper.fromWire("sharp_right"))
    }

    @Test
    fun `u-turn variants map correctly`() {
        assertEquals(ManeuverType.U_TURN_LEFT, ManeuverMapper.fromWire("u_turn_left"))
        assertEquals(ManeuverType.U_TURN_RIGHT, ManeuverMapper.fromWire("u_turn_right"))
        assertEquals(ManeuverType.U_TURN_LEFT, ManeuverMapper.fromWire("uturn_left"))
        assertEquals(ManeuverType.U_TURN_RIGHT, ManeuverMapper.fromWire("uturn_right"))
    }

    @Test
    fun `merge and fork map correctly`() {
        assertEquals(ManeuverType.MERGE_LEFT, ManeuverMapper.fromWire("merge_left"))
        assertEquals(ManeuverType.MERGE_RIGHT, ManeuverMapper.fromWire("merge_right"))
        assertEquals(ManeuverType.FORK_LEFT, ManeuverMapper.fromWire("fork_left"))
        assertEquals(ManeuverType.FORK_RIGHT, ManeuverMapper.fromWire("fork_right"))
    }

    @Test
    fun `ramp variants map correctly`() {
        assertEquals(ManeuverType.ON_RAMP_LEFT, ManeuverMapper.fromWire("on_ramp_left"))
        assertEquals(ManeuverType.ON_RAMP_RIGHT, ManeuverMapper.fromWire("on_ramp_right"))
        assertEquals(ManeuverType.ON_RAMP_LEFT, ManeuverMapper.fromWire("ramp_left"))
        assertEquals(ManeuverType.ON_RAMP_RIGHT, ManeuverMapper.fromWire("ramp_right"))
        assertEquals(ManeuverType.OFF_RAMP_LEFT, ManeuverMapper.fromWire("off_ramp_left"))
        assertEquals(ManeuverType.OFF_RAMP_RIGHT, ManeuverMapper.fromWire("off_ramp_right"))
    }

    @Test
    fun `roundabout maps correctly`() {
        assertEquals(ManeuverType.ROUNDABOUT_ENTER, ManeuverMapper.fromWire("roundabout_enter"))
        assertEquals(ManeuverType.ROUNDABOUT_ENTER, ManeuverMapper.fromWire("roundabout"))
        assertEquals(ManeuverType.ROUNDABOUT_EXIT, ManeuverMapper.fromWire("roundabout_exit"))
    }

    @Test
    fun `destination variants map correctly`() {
        assertEquals(ManeuverType.DESTINATION, ManeuverMapper.fromWire("destination"))
        assertEquals(ManeuverType.DESTINATION, ManeuverMapper.fromWire("arrive"))
        assertEquals(ManeuverType.DESTINATION_LEFT, ManeuverMapper.fromWire("destination_left"))
        assertEquals(ManeuverType.DESTINATION_RIGHT, ManeuverMapper.fromWire("destination_right"))
    }

    @Test
    fun `special types map correctly`() {
        assertEquals(ManeuverType.STRAIGHT, ManeuverMapper.fromWire("straight"))
        assertEquals(ManeuverType.STRAIGHT, ManeuverMapper.fromWire("continue"))
        assertEquals(ManeuverType.DEPART, ManeuverMapper.fromWire("depart"))
        assertEquals(ManeuverType.FERRY, ManeuverMapper.fromWire("ferry"))
        assertEquals(ManeuverType.NAME_CHANGE, ManeuverMapper.fromWire("name_change"))
    }

    @Test
    fun `case insensitivity`() {
        assertEquals(ManeuverType.TURN_LEFT, ManeuverMapper.fromWire("TURN_LEFT"))
        assertEquals(ManeuverType.TURN_RIGHT, ManeuverMapper.fromWire("Turn_Right"))
        assertEquals(ManeuverType.DESTINATION, ManeuverMapper.fromWire("DESTINATION"))
    }

    @Test
    fun `null input returns UNKNOWN`() {
        assertEquals(ManeuverType.UNKNOWN, ManeuverMapper.fromWire(null))
    }

    @Test
    fun `unknown string returns UNKNOWN`() {
        assertEquals(ManeuverType.UNKNOWN, ManeuverMapper.fromWire("some_future_type"))
        assertEquals(ManeuverType.UNKNOWN, ManeuverMapper.fromWire(""))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals(ManeuverType.TURN_LEFT, ManeuverMapper.fromWire("  turn_left  "))
    }

    @Test
    fun `toArrow returns non-empty for all types`() {
        ManeuverType.entries.forEach { type ->
            val arrow = ManeuverMapper.toArrow(type)
            assert(arrow.isNotEmpty()) { "Arrow for $type should not be empty" }
        }
    }

    @Test
    fun `toArrow returns distinct arrows for basic turns`() {
        val left = ManeuverMapper.toArrow(ManeuverType.TURN_LEFT)
        val right = ManeuverMapper.toArrow(ManeuverType.TURN_RIGHT)
        val straight = ManeuverMapper.toArrow(ManeuverType.STRAIGHT)
        assert(left != right) { "Left and right should have different arrows" }
        assert(left != straight) { "Left and straight should have different arrows" }
        assert(right != straight) { "Right and straight should have different arrows" }
    }
}
