package com.openautolink.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MicSourcePreferenceTest {

    @Test
    fun `default mic source is car`() {
        assertEquals("car", AppPreferences.DEFAULT_MIC_SOURCE)
    }

    @Test
    fun `mic source car is valid string constant`() {
        assertEquals("car", AppPreferences.DEFAULT_MIC_SOURCE)
    }
}
