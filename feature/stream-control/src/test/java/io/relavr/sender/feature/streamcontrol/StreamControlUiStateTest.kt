package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.model.VideoStreamProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamControlUiStateTest {
    @Test
    fun `active profile summary shows requested and current values`() {
        val uiState =
            buildStreamControlUiState(
                config =
                    StreamConfig(
                        signalingEndpoint = "ws://192.168.1.20:8080/ws",
                        resolution = VideoResolution(width = 1920, height = 1080),
                        fps = 60,
                        bitrateKbps = 8000,
                    ),
                sessionSnapshot =
                    StreamingSessionSnapshot(
                        captureState = CaptureState.Capturing,
                        publishState = PublishState.Publishing,
                        activeVideoProfile =
                            VideoStreamProfile(
                                codecPreference = CodecPreference.H264,
                                resolution = VideoResolution(width = 1280, height = 720),
                                fps = 30,
                                bitrateKbps = 8000,
                            ),
                    ),
            )

        assertEquals(
            listOf("1920x1080", 60, 8000, "1280x720", 30, 8000),
            uiState.streamProfileSummary.args,
        )
    }

    @Test
    fun `unsupported capability combinations disable start and surface the error`() {
        val supportedProfile =
            VideoStreamProfile(
                codecPreference = CodecPreference.H264,
                resolution = VideoResolution(width = 1280, height = 720),
                fps = 30,
                bitrateKbps = 4000,
            )
        val uiState =
            buildStreamControlUiState(
                config =
                    StreamConfig(
                        signalingEndpoint = "ws://192.168.1.20:8080/ws",
                        resolution = VideoResolution(width = 1920, height = 1080),
                        fps = 60,
                        bitrateKbps = 8000,
                    ),
                sessionSnapshot =
                    StreamingSessionSnapshot(
                        capabilities =
                            CapabilitySnapshot(
                                supportedCodecs = setOf(CodecPreference.H264),
                                supportedProfiles = setOf(supportedProfile),
                            ),
                    ),
            )

        assertFalse(uiState.startEnabled)
        assertNotNull(uiState.errorMessage)
    }

    @Test
    fun `manual connection draft summary reflects endpoint and session`() {
        val uiState =
            buildStreamControlUiState(
                config =
                    StreamConfig(
                        signalingEndpoint = "wss://preview.relavr.example:443/ws",
                        sessionId = "room-42",
                    ),
                signalingEndpointDraft =
                    SignalingEndpointDraft(
                        scheme = "wss",
                        host = "preview.relavr.example",
                        port = "443",
                        path = "/ws",
                    ),
                sessionSnapshot = StreamingSessionSnapshot(),
            )

        assertEquals(R.string.stream_control_settings_connection_summary, uiState.connectionSummary.resId)
        assertEquals("wss://preview.relavr.example:443/ws", uiState.connectionSummary.args[0])
        assertEquals("room-42", uiState.connectionSummary.args[1])
    }

    @Test
    fun `blank manual port disables start even when endpoint still parses`() {
        val uiState =
            buildStreamControlUiState(
                config =
                    StreamConfig(
                        signalingEndpoint = "ws://192.168.1.20/ws",
                    ),
                signalingEndpointDraft =
                    SignalingEndpointDraft(
                        scheme = "ws",
                        host = "192.168.1.20",
                        port = "",
                        path = "/ws",
                    ),
                sessionSnapshot = StreamingSessionSnapshot(),
            )

        assertFalse(uiState.startEnabled)
        assertTrue(uiState.connectionDraft.host.isNotBlank())
    }
}
