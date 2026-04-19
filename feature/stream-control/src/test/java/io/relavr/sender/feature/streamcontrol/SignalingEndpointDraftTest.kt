package io.relavr.sender.feature.streamcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalingEndpointDraftTest {
    @Test
    fun `parse restores scheme host port and path from full endpoint`() {
        val draft = parseSignalingEndpointDraft("wss://preview.relavr.example:443/ws")

        assertEquals("wss", draft.scheme)
        assertEquals("preview.relavr.example", draft.host)
        assertEquals("443", draft.port)
        assertEquals("/ws", draft.path)
    }

    @Test
    fun `draft builds persisted endpoint from manual fields`() {
        val endpoint =
            SignalingEndpointDraft(
                scheme = "wss",
                host = "preview.relavr.example",
                port = "9443",
                path = "relay",
            ).toPersistedEndpoint()

        assertEquals("wss://preview.relavr.example:9443/relay", endpoint)
    }

    @Test
    fun `blank or non numeric port is not ready to connect`() {
        assertFalse(SignalingEndpointDraft(host = "relay.local", port = "").isReadyToConnect())
        assertFalse(SignalingEndpointDraft(host = "relay.local", port = "port").isReadyToConnect())
        assertTrue(SignalingEndpointDraft(host = "relay.local", port = "8080").isReadyToConnect())
    }
}
