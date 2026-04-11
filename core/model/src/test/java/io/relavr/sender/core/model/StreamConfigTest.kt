package io.relavr.sender.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamConfigTest {
    @Test
    fun `默认信令地址为空`() {
        assertEquals("", StreamConfig().signalingEndpoint)
    }

    @Test
    fun `信令地址为空时返回校验错误`() {
        val error = StreamConfig(signalingEndpoint = "").validationError()

        assertEquals(SenderError.InvalidConfig("WebSocket 地址不能为空"), error)
    }

    @Test
    fun `ws 信令地址通过校验`() {
        val error = StreamConfig(signalingEndpoint = "ws://192.168.123.182:8765").validationError()

        assertNull(error)
    }

    @Test
    fun `wss 信令地址通过校验`() {
        val error = StreamConfig(signalingEndpoint = "wss://signal.example/ws").validationError()

        assertNull(error)
    }

    @Test
    fun `非 WebSocket scheme 会被拒绝`() {
        val error = StreamConfig(signalingEndpoint = "https://signal.example/ws").validationError()

        assertEquals(SenderError.InvalidConfig("WebSocket 地址必须使用 ws:// 或 wss://"), error)
    }

    @Test
    fun `缺少 host 的 WebSocket 地址会被拒绝`() {
        val error = StreamConfig(signalingEndpoint = "ws:///ws").validationError()

        assertEquals(SenderError.InvalidConfig("WebSocket 地址必须使用 ws:// 或 wss://"), error)
    }
}
