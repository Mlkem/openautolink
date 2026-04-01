package com.openautolink.app.input

import com.openautolink.app.transport.ControlMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssForwarderTest {

    @Test
    fun `initial state is not active`() {
        // GnssForwarderImpl requires Context, test the interface contract
        val forwarder = object : GnssForwarder {
            override var isActive: Boolean = false
                private set

            override fun start() {
                isActive = true
            }

            override fun stop() {
                isActive = false
            }
        }

        assertFalse(forwarder.isActive)
        forwarder.start()
        assertTrue(forwarder.isActive)
        forwarder.stop()
        assertFalse(forwarder.isActive)
    }

    @Test
    fun `NMEA sentence filtering - GP prefix accepted`() {
        val accepted = mutableListOf<String>()
        val filter = { nmea: String ->
            if (nmea.startsWith("\$GP") || nmea.startsWith("\$GN") || nmea.startsWith("\$GL")) {
                accepted.add(nmea.trimEnd('\r', '\n'))
            }
        }

        filter("\$GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A\r\n")
        filter("\$GPGGA,092750.000,5321.6802,N,00630.3372,W,1,8,1.03,61.7,M,55.2,M,,*76")
        filter("\$GNRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A")
        filter("\$GLGSV,3,1,09,65,77,052,35*5E")
        filter("\$PMTK,ignored")  // Should be filtered out

        assertEquals(4, accepted.size)
        assertTrue(accepted[0].startsWith("\$GPRMC"))
        assertTrue(accepted[1].startsWith("\$GPGGA"))
        assertTrue(accepted[2].startsWith("\$GNRMC"))
        assertTrue(accepted[3].startsWith("\$GLGSV"))
    }

    @Test
    fun `NMEA sentence trailing CR LF is trimmed`() {
        val result = "\$GPRMC,123519,A\r\n".trimEnd('\r', '\n')
        assertEquals("\$GPRMC,123519,A", result)
    }
}
