package io.relavr.sender.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiverDiscoveryPayloadCodecTest {
    @Test
    fun `发现载荷可以解析为接收端信息`() {
        val receiver =
            ReceiverDiscoveryPayloadCodec.decode(
                serviceName = "Living Room",
                host = "192.168.1.20",
                port = 17888,
                attributes =
                    mapOf(
                        "name" to "Living Room",
                        "ver" to "1",
                        "sessionId" to "quest3-demo",
                        "auth" to "none",
                    ),
            )

        assertEquals("Living Room", receiver.serviceName)
        assertEquals("Living Room", receiver.receiverName)
        assertEquals("quest3-demo", receiver.sessionId)
        assertEquals("ws://192.168.1.20:17888", receiver.webSocketUrl)
        assertFalse(receiver.authRequired)
    }

    @Test
    fun `发现载荷支持 pin 鉴权提示`() {
        val receiver =
            ReceiverDiscoveryPayloadCodec.decode(
                serviceName = "Bedroom",
                host = "192.168.1.30",
                port = 18000,
                attributes =
                    mapOf(
                        "name" to "Bedroom",
                        "ver" to "1",
                        "sessionId" to "room-1",
                        "auth" to "pin",
                    ),
            )

        assertTrue(receiver.authRequired)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `不支持的发现协议版本会报错`() {
        ReceiverDiscoveryPayloadCodec.decode(
            serviceName = "TV",
            host = "192.168.1.20",
            port = 17888,
            attributes =
                mapOf(
                    "name" to "TV",
                    "ver" to "2",
                    "sessionId" to "demo",
                    "auth" to "none",
                ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `缺少 sessionId 会报错`() {
        ReceiverDiscoveryPayloadCodec.decode(
            serviceName = "TV",
            host = "192.168.1.20",
            port = 17888,
            attributes =
                mapOf(
                    "name" to "TV",
                    "ver" to "1",
                    "auth" to "none",
                ),
        )
    }

    @Test
    fun `ipv6 地址会自动补方括号`() {
        val receiver =
            ReceiverDiscoveryPayloadCodec.decode(
                serviceName = "TV",
                host = "fe80::1234",
                port = 17888,
                attributes =
                    mapOf(
                        "name" to "TV",
                        "ver" to "1",
                        "sessionId" to "demo",
                        "auth" to "none",
                    ),
            )

        assertEquals("[fe80::1234]:17888", receiver.endpoint)
        assertEquals("ws://[fe80::1234]:17888", receiver.webSocketUrl)
    }
}
