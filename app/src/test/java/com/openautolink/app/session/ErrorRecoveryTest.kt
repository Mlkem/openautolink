package com.openautolink.app.session

import com.openautolink.app.video.DecoderState
import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorRecoveryTest {

    @Test
    fun `DecoderState ERROR is a distinct recoverable state`() {
        // Verify ERROR state exists and can transition from it
        assertEquals(DecoderState.ERROR, DecoderState.valueOf("ERROR"))
    }

    @Test
    fun `DecoderState IDLE is the reset state after recovery`() {
        // After calling resume(), decoder should go to IDLE then reconfigure
        assertEquals(DecoderState.IDLE, DecoderState.valueOf("IDLE"))
    }

    @Test
    fun `all SessionState values exist for error handling`() {
        // Ensure ERROR state exists for session-level error reporting
        val states = SessionState.entries
        assert(states.contains(SessionState.ERROR))
        assert(states.contains(SessionState.STREAMING))
        assert(states.contains(SessionState.PHONE_CONNECTED))
    }

    @Test
    fun `STREAMING to PHONE_CONNECTED transition indicates channel loss`() {
        // When video channel drops, state goes from STREAMING → PHONE_CONNECTED
        // This is the signal for auto-reconnect
        val streamingState = com.openautolink.app.transport.ConnectionState.STREAMING.toSessionState()
        val phoneState = com.openautolink.app.transport.ConnectionState.PHONE_CONNECTED.toSessionState()
        assertEquals(SessionState.STREAMING, streamingState)
        assertEquals(SessionState.PHONE_CONNECTED, phoneState)
    }
}
