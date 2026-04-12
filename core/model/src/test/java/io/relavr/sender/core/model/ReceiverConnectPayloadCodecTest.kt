package io.relavr.sender.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiverConnectPayloadCodecTest {
    @Test
    fun `connection info encodes and decodes through the qr payload codec`() {
        val original =
            ReceiverConnectionInfo(
                receiverName = "Living Room",
                sessionId = "quest3-demo",
                host = "preview.relavr.example",
                port = 443,
                authRequired = true,
                scheme = "wss",
                path = "/ws",
            )

        val encoded = ReceiverConnectPayloadCodec.encode(original)
        val decoded = ReceiverConnectPayloadCodec.decode(encoded)

        assertEquals(original, decoded)
        assertEquals("wss://preview.relavr.example:443/ws", decoded.webSocketUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown payload types throw`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"unknown","ver":2,"name":"TV","sessionId":"demo","scheme":"ws","host":"127.0.0.1","port":17888,"path":"/","auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported protocol versions throw`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":1,"name":"TV","sessionId":"demo","scheme":"ws","host":"127.0.0.1","port":17888,"path":"/","auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing hosts throw`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":2,"name":"TV","sessionId":"demo","scheme":"ws","host":"","port":17888,"path":"/","auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid ports throw`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":2,"name":"TV","sessionId":"demo","scheme":"ws","host":"127.0.0.1","port":70000,"path":"/","auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid paths throw`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":2,"name":"TV","sessionId":"demo","scheme":"wss","host":"preview.relavr.example","port":443,"path":"ws","auth":"none"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid json throws`() {
        ReceiverConnectPayloadCodec.decode("not-json")
    }
}
