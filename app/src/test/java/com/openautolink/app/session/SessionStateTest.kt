package com.openautolink.app.session

import com.openautolink.app.transport.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStateTest {

    @Test
    fun `ConnectionState DISCONNECTED maps to SessionState IDLE`() {
        assertEquals(SessionState.IDLE, ConnectionState.DISCONNECTED.toSessionState())
    }

    @Test
    fun `ConnectionState CONNECTING maps to SessionState CONNECTING`() {
        assertEquals(SessionState.CONNECTING, ConnectionState.CONNECTING.toSessionState())
    }

    @Test
    fun `ConnectionState CONNECTED maps to SessionState CONNECTED`() {
        assertEquals(SessionState.CONNECTED, ConnectionState.CONNECTED.toSessionState())
    }

    @Test
    fun `ConnectionState PHONE_CONNECTED maps to SessionState STREAMING`() {
        assertEquals(SessionState.STREAMING, ConnectionState.PHONE_CONNECTED.toSessionState())
    }

    @Test
    fun `ConnectionState STREAMING maps to SessionState STREAMING`() {
        assertEquals(SessionState.STREAMING, ConnectionState.STREAMING.toSessionState())
    }

    @Test
    fun `all ConnectionState values have a mapping`() {
        ConnectionState.entries.forEach { state ->
            // Should not throw
            state.toSessionState()
        }
    }
}
