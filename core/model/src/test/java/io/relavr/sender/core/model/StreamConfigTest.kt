package io.relavr.sender.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamConfigTest {
    @Test
    fun `默认规格使用受控候选值`() {
        val config = StreamConfig()

        assertEquals(StreamConfig.DEFAULT_RESOLUTION, config.resolution)
        assertEquals(StreamConfig.DEFAULT_FPS, config.fps)
        assertEquals(StreamConfig.DEFAULT_BITRATE_KBPS, config.bitrateKbps)
        assertEquals(
            listOf(
                VideoResolution(width = 1280, height = 720),
                VideoResolution(width = 1600, height = 900),
                VideoResolution(width = 1920, height = 1080),
            ),
            StreamConfig.RESOLUTION_OPTIONS,
        )
        assertEquals(listOf(24, 30, 45, 60), StreamConfig.FPS_OPTIONS)
        assertEquals(listOf(2000, 4000, 6000, 8000), StreamConfig.BITRATE_OPTIONS_KBPS)
    }

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

    @Test
    fun `非法规格会返回明确错误`() {
        assertEquals(
            SenderError.InvalidConfig("分辨率不在支持列表内"),
            StreamConfig(
                resolution = VideoResolution(width = 1024, height = 768),
                signalingEndpoint = "ws://192.168.1.20:8080/ws",
            ).validationError(),
        )
        assertEquals(
            SenderError.InvalidConfig("帧率不在支持列表内"),
            StreamConfig(
                fps = 29,
                signalingEndpoint = "ws://192.168.1.20:8080/ws",
            ).validationError(),
        )
        assertEquals(
            SenderError.InvalidConfig("码率不在支持列表内"),
            StreamConfig(
                bitrateKbps = 3500,
                signalingEndpoint = "ws://192.168.1.20:8080/ws",
            ).validationError(),
        )
    }
}
