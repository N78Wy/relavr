package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.ReceiverConnectPayloadCodec
import io.relavr.sender.core.model.ReceiverConnectionInfo
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.testing.fakes.FakeStreamingSessionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamControlViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads capabilities and updates the configuration`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel = StreamControlViewModel(sessionController = controller)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CodecPreference.H264, state.codecOptions.single { it.selected }.preference)
            assertTrue(state.codecOptions.any { it.preference == CodecPreference.HEVC && it.enabled })
            assertEquals(StreamConfig.DEFAULT_RESOLUTION, state.resolutionOptions.single { it.selected }.value)
            assertEquals(StreamConfig.DEFAULT_FPS, state.fpsOptions.single { it.selected }.value)
            assertEquals(StreamConfig.DEFAULT_BITRATE_KBPS, state.bitrateOptions.single { it.selected }.value)
            assertEquals("", state.signalingEndpoint)
            assertTrue(state.sessionId.isNotBlank())
            assertTrue(state.audioEnabled)
            assertFalse(state.startEnabled)
        }

    @Test
    fun `starting a stream forwards the user supplied signaling configuration`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel = StreamControlViewModel(sessionController = controller)

            viewModel.onSignalingEndpointChanged("wss://signal.example/ws")
            viewModel.onSessionIdChanged("room-42")
            viewModel.onCodecPreferenceChanged(CodecPreference.HEVC)
            viewModel.onResolutionChanged(VideoResolution(width = 1920, height = 1080))
            viewModel.onFpsChanged(60)
            viewModel.onBitrateChanged(8000)
            viewModel.onStartClicked()
            advanceUntilIdle()

            assertEquals(1, controller.startCount)
            assertEquals("wss://signal.example/ws", controller.lastStartConfig?.signalingEndpoint)
            assertEquals("room-42", controller.lastStartConfig?.sessionId)
            assertEquals(CodecPreference.HEVC, controller.lastStartConfig?.codecPreference)
            assertEquals(VideoResolution(width = 1920, height = 1080), controller.lastStartConfig?.resolution)
            assertEquals(60, controller.lastStartConfig?.fps)
            assertEquals(8000, controller.lastStartConfig?.bitrateKbps)
            assertTrue(controller.lastStartConfig?.audioEnabled ?: false)
        }

    @Test
    fun `a successful qr scan autofills the connection info and starts streaming immediately`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel = StreamControlViewModel(sessionController = controller)
            val payload =
                ReceiverConnectPayloadCodec.encode(
                    ReceiverConnectionInfo(
                        receiverName = "Living Room",
                        sessionId = "receiver-room",
                        host = "192.168.50.20",
                        port = 17888,
                        authRequired = true,
                    ),
                )

            viewModel.onCodecPreferenceChanged(CodecPreference.HEVC)
            viewModel.onResolutionChanged(VideoResolution(width = 1920, height = 1080))
            viewModel.onFpsChanged(60)
            viewModel.onBitrateChanged(8000)
            viewModel.onAudioEnabledChanged(false)
            viewModel.onScannerPayloadReceived(payload)
            advanceUntilIdle()

            assertEquals(1, controller.startCount)
            assertEquals("ws://192.168.50.20:17888", controller.lastStartConfig?.signalingEndpoint)
            assertEquals("receiver-room", controller.lastStartConfig?.sessionId)
            assertEquals(CodecPreference.HEVC, controller.lastStartConfig?.codecPreference)
            assertEquals(VideoResolution(width = 1920, height = 1080), controller.lastStartConfig?.resolution)
            assertEquals(60, controller.lastStartConfig?.fps)
            assertEquals(8000, controller.lastStartConfig?.bitrateKbps)
            assertFalse(controller.lastStartConfig?.audioEnabled ?: true)
            assertTrue(
                viewModel.uiState.value.scanStatusLabel.args
                    .contains("Living Room"),
            )
        }

    @Test
    fun `an invalid qr code does not overwrite config or trigger streaming`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel = StreamControlViewModel(sessionController = controller)

            viewModel.onSignalingEndpointChanged("ws://keep.example/ws")
            viewModel.onSessionIdChanged("keep-room")
            viewModel.onScannerPayloadReceived("not-json")
            advanceUntilIdle()

            assertEquals(0, controller.startCount)
            assertEquals("ws://keep.example/ws", viewModel.uiState.value.signalingEndpoint)
            assertEquals("keep-room", viewModel.uiState.value.sessionId)
            assertEquals(R.string.stream_control_scan_parse_failed, viewModel.uiState.value.scanStatusLabel.resId)
        }

    @Test
    fun `capability refresh normalizes unsupported codecs to the device default`() =
        runTest(dispatcher.scheduler) {
            val controller =
                FakeStreamingSessionController(
                    capabilitySnapshot =
                        CapabilitySnapshot(
                            supportedCodecs = setOf(CodecPreference.HEVC, CodecPreference.VP8),
                            audioPlaybackCaptureSupported = true,
                            defaultCodec = CodecPreference.HEVC,
                        ),
                )
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    initialConfig = StreamConfig(codecPreference = CodecPreference.VP9),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CodecPreference.HEVC, state.codecOptions.single { it.selected }.preference)
            assertEquals(R.string.stream_control_codec_device_default, state.codecStatusLabel.resId)
            assertEquals(listOf(CodecPreference.HEVC.displayName), state.codecStatusLabel.args)
        }
}
