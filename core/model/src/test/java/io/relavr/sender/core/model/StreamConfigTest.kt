package io.relavr.sender.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamConfigTest {
    @Test
    fun `默认信令地址为空且需要用户填写`() {
        val config = StreamConfig()

        assertEquals("", config.signalingEndpoint)
        assertEquals(
            SenderError.InvalidConfig("WebSocket 地址不能为空"),
            config.validationError(),
        )
    }

    @Test
    fun `ws和wss信令地址都通过校验`() {
        assertNull(
            StreamConfig(signalingEndpoint = "ws://192.168.1.20:8080/ws").validationError(),
        )
        assertNull(
            StreamConfig(signalingEndpoint = "wss://signal.example/ws").validationError(),
        )
    }

    @Test
    fun `非法协议会返回明确错误`() {
        val config = StreamConfig(signalingEndpoint = "https://signal.example/ws")

        assertEquals(
            SenderError.InvalidConfig("WebSocket 地址必须使用 ws:// 或 wss://"),
            config.validationError(),
        )
    }
}
