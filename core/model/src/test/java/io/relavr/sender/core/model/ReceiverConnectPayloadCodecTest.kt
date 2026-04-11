package io.relavr.sender.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiverConnectPayloadCodecTest {
    @Test
    fun `连接信息可以完成二维码载荷编解码`() {
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
    fun `未知载荷类型会抛出异常`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"unknown","ver":1,"name":"TV","sessionId":"demo","host":"127.0.0.1","port":17888,"auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `不支持的协议版本会抛出异常`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":2,"name":"TV","sessionId":"demo","host":"127.0.0.1","port":17888,"auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `缺少主机地址会抛出异常`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":1,"name":"TV","sessionId":"demo","host":"","port":17888,"auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法端口会抛出异常`() {
        ReceiverConnectPayloadCodec.decode(
            """{"type":"receiver-connect","ver":1,"name":"TV","sessionId":"demo","host":"127.0.0.1","port":70000,"auth":"pin"}""",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法json会抛出异常`() {
        ReceiverConnectPayloadCodec.decode("not-json")
    }
}
