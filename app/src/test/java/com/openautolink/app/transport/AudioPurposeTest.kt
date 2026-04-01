package com.openautolink.app.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPurposeTest {

    @Test
    fun `toWire returns correct string for each purpose`() {
        assertEquals("media", AudioPurpose.MEDIA.toWire())
        assertEquals("navigation", AudioPurpose.NAVIGATION.toWire())
        assertEquals("assistant", AudioPurpose.ASSISTANT.toWire())
        assertEquals("phone_call", AudioPurpose.PHONE_CALL.toWire())
        assertEquals("alert", AudioPurpose.ALERT.toWire())
    }

    @Test
    fun `fromWire returns correct enum for each string`() {
        assertEquals(AudioPurpose.MEDIA, AudioPurpose.fromWire("media"))
        assertEquals(AudioPurpose.NAVIGATION, AudioPurpose.fromWire("navigation"))
        assertEquals(AudioPurpose.ASSISTANT, AudioPurpose.fromWire("assistant"))
        assertEquals(AudioPurpose.PHONE_CALL, AudioPurpose.fromWire("phone_call"))
        assertEquals(AudioPurpose.ALERT, AudioPurpose.fromWire("alert"))
    }

    @Test
    fun `fromWire returns null for unknown purpose`() {
        assertNull(AudioPurpose.fromWire("unknown"))
        assertNull(AudioPurpose.fromWire(""))
        assertNull(AudioPurpose.fromWire("MEDIA"))
    }

    @Test
    fun `round-trip through toWire and fromWire`() {
        AudioPurpose.entries.forEach { purpose ->
            assertEquals(purpose, AudioPurpose.fromWire(purpose.toWire()))
        }
    }
}
