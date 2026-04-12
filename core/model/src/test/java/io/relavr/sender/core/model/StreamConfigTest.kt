package io.relavr.sender.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamConfigTest {
    @Test
    fun `default profiles use controlled candidate values`() {
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
    fun `default signaling endpoint is empty and requires user input`() {
        val config = StreamConfig()

        assertEquals("", config.signalingEndpoint)
        assertEquals(
            SenderError.InvalidConfig(
                message = "The WebSocket endpoint is required.",
                uiText = UiText.of(R.string.sender_error_signaling_endpoint_required),
            ),
            config.validationError(),
        )
    }

    @Test
    fun `ws and wss signaling endpoints pass validation`() {
        assertNull(
            StreamConfig(signalingEndpoint = "ws://192.168.1.20:8080/ws").validationError(),
        )
        assertNull(
            StreamConfig(signalingEndpoint = "wss://signal.example/ws").validationError(),
        )
    }

    @Test
    fun `invalid schemes return a clear error`() {
        val config = StreamConfig(signalingEndpoint = "https://signal.example/ws")

        assertEquals(
            SenderError.InvalidConfig(
                message = "The WebSocket endpoint must use ws:// or wss://.",
                uiText = UiText.of(R.string.sender_error_signaling_endpoint_scheme_invalid),
            ),
            config.validationError(),
        )
    }

    @Test
    fun `invalid profiles return clear errors`() {
        assertEquals(
            SenderError.InvalidConfig(
                message = "The selected resolution is not supported.",
                uiText = UiText.of(R.string.sender_error_resolution_unsupported),
            ),
            StreamConfig(
                resolution = VideoResolution(width = 1024, height = 768),
                signalingEndpoint = "ws://192.168.1.20:8080/ws",
            ).validationError(),
        )
        assertEquals(
            SenderError.InvalidConfig(
                message = "The selected frame rate is not supported.",
                uiText = UiText.of(R.string.sender_error_fps_unsupported),
            ),
            StreamConfig(
                fps = 29,
                signalingEndpoint = "ws://192.168.1.20:8080/ws",
            ).validationError(),
        )
        assertEquals(
            SenderError.InvalidConfig(
                message = "The selected bitrate is not supported.",
                uiText = UiText.of(R.string.sender_error_bitrate_unsupported),
            ),
            StreamConfig(
                bitrateKbps = 3500,
                signalingEndpoint = "ws://192.168.1.20:8080/ws",
            ).validationError(),
        )
    }

    @Test
    fun `unsupported capability combinations return a profile error`() {
        val capabilities =
            CapabilitySnapshot(
                supportedCodecs = setOf(CodecPreference.H264),
                audioPlaybackCaptureSupported = true,
                supportedProfiles =
                    setOf(
                        VideoStreamProfile(
                            codecPreference = CodecPreference.H264,
                            resolution = VideoResolution(width = 1280, height = 720),
                            fps = 30,
                            bitrateKbps = 4000,
                        ),
                    ),
            )

        assertEquals(
            SenderError.InvalidConfig(
                message = "The selected codec and stream profile are not supported together.",
                uiText = UiText.of(R.string.sender_error_profile_unsupported),
            ),
            StreamConfig(
                signalingEndpoint = "ws://192.168.1.20:8080/ws",
                resolution = VideoResolution(width = 1920, height = 1080),
                fps = 60,
                bitrateKbps = 8000,
            ).validationError(capabilities),
        )
    }
}
