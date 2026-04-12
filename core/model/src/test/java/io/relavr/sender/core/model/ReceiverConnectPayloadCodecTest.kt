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
                host = "192.168.0.10",
                port = 17888,
                authRequired = true,
            )

        val encoded = ReceiverConnectPayloadCodec.encode(original)
        val decoded = ReceiverConnectPayloadCodec.decode(encoded)

        assertEquals(original, decoded)
        assertEquals("ws://192.168.0.10:17888", decoded.webSocketUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown payload types throw`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"unknown","ver":1,"name":"TV","sessionId":"demo","host":"127.0.0.1","port":17888,"auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported protocol versions throw`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":2,"name":"TV","sessionId":"demo","host":"127.0.0.1","port":17888,"auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing hosts throw`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":1,"name":"TV","sessionId":"demo","host":"","port":17888,"auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid ports throw`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":1,"name":"TV","sessionId":"demo","host":"127.0.0.1","port":70000,"auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid json throws`() {
        ReceiverConnectPayloadCodec.decode("not-json")
    }
}
