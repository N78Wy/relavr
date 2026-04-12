package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.ReceiverConnectPayloadCodec
import io.relavr.sender.core.model.ReceiverConnectionInfo
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.testing.fakes.FakeStreamingSessionController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
    fun `initialization restores the persisted configuration`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val configStore =
                FakeStreamControlConfigStore(
                    initialConfig =
                        StreamConfig(
                            signalingEndpoint = "wss://signal.example/ws",
                            sessionId = "saved-room",
                            codecPreference = CodecPreference.HEVC,
                            resolution = VideoResolution(width = 1920, height = 1080),
                            fps = 60,
                            bitrateKbps = 8000,
                            audioEnabled = false,
                        ),
                )
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    configStore = configStore,
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CodecPreference.HEVC, state.codecOptions.single { it.selected }.preference)
            assertTrue(state.codecOptions.any { it.preference == CodecPreference.HEVC && it.enabled })
            assertEquals(VideoResolution(width = 1920, height = 1080), state.resolutionOptions.single { it.selected }.value)
            assertEquals(60, state.fpsOptions.single { it.selected }.value)
            assertEquals(8000, state.bitrateOptions.single { it.selected }.value)
            assertEquals("wss://signal.example/ws", state.signalingEndpoint)
            assertEquals("saved-room", state.sessionId)
            assertFalse(state.audioEnabled)
            assertTrue(state.startEnabled)
        }

    @Test
    fun `local edits are not overwritten when persisted config finishes loading later`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val loadGate = CompletableDeferred<Unit>()
            val configStore =
                FakeStreamControlConfigStore(
                    initialConfig =
                        StreamConfig(
                            signalingEndpoint = "ws://persisted.example/ws",
                            sessionId = "persisted-room",
                        ),
                    loadGate = loadGate,
                )
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    configStore = configStore,
                )

            viewModel.onSignalingEndpointChanged("ws://draft.example/ws")
            viewModel.onSessionIdChanged("draft-room")
            advanceUntilIdle()

            loadGate.complete(Unit)
            advanceUntilIdle()

            assertEquals("ws://draft.example/ws", viewModel.uiState.value.signalingEndpoint)
            assertEquals("draft-room", viewModel.uiState.value.sessionId)
        }

    @Test
    fun `config changes are persisted immediately`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val configStore = FakeStreamControlConfigStore()
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    configStore = configStore,
                )

            viewModel.onSignalingEndpointChanged("wss://signal.example/ws")
            viewModel.onSessionIdChanged("room-42")
            viewModel.onCodecPreferenceChanged(CodecPreference.HEVC)
            viewModel.onResolutionChanged(VideoResolution(width = 1920, height = 1080))
            viewModel.onFpsChanged(60)
            viewModel.onBitrateChanged(8000)
            viewModel.onAudioToggleRequested(false)
            advanceUntilIdle()

            assertEquals(
                StreamConfig(
                    signalingEndpoint = "wss://signal.example/ws",
                    sessionId = "room-42",
                    codecPreference = CodecPreference.HEVC,
                    resolution = VideoResolution(width = 1920, height = 1080),
                    fps = 60,
                    bitrateKbps = 8000,
                    audioEnabled = false,
                ),
                configStore.storedConfig,
            )
        }

    @Test
    fun `starting a stream forwards the user supplied signaling configuration`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    configStore = FakeStreamControlConfigStore(),
                )

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
            val configStore = FakeStreamControlConfigStore()
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    configStore = configStore,
                )
            val payload =
                ReceiverConnectPayloadCodec.encode(
                    ReceiverConnectionInfo(
                        receiverName = "Living Room",
                        sessionId = "receiver-room",
                        host = "preview.relavr.example",
                        port = 443,
                        authRequired = true,
                        scheme = "wss",
                        path = "/ws",
                    ),
                )

            viewModel.onCodecPreferenceChanged(CodecPreference.HEVC)
            viewModel.onResolutionChanged(VideoResolution(width = 1920, height = 1080))
            viewModel.onFpsChanged(60)
            viewModel.onBitrateChanged(8000)
            viewModel.onAudioToggleRequested(false)
            viewModel.onScannerPayloadReceived(payload)
            advanceUntilIdle()

            assertEquals(1, controller.startCount)
            assertEquals("wss://preview.relavr.example:443/ws", controller.lastStartConfig?.signalingEndpoint)
            assertEquals("receiver-room", controller.lastStartConfig?.sessionId)
            assertEquals(CodecPreference.HEVC, controller.lastStartConfig?.codecPreference)
            assertEquals(VideoResolution(width = 1920, height = 1080), controller.lastStartConfig?.resolution)
            assertEquals(60, controller.lastStartConfig?.fps)
            assertEquals(8000, controller.lastStartConfig?.bitrateKbps)
            assertFalse(controller.lastStartConfig?.audioEnabled ?: true)
            assertEquals("wss://preview.relavr.example:443/ws", configStore.storedConfig.signalingEndpoint)
            assertEquals("receiver-room", configStore.storedConfig.sessionId)
            assertTrue(
                viewModel.uiState.value.scanStatusLabel.args
                    .contains("Living Room"),
            )
        }

    @Test
    fun `an invalid qr code does not overwrite config or trigger streaming`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val configStore = FakeStreamControlConfigStore()
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    configStore = configStore,
                )

            viewModel.onSignalingEndpointChanged("ws://keep.example/ws")
            viewModel.onSessionIdChanged("keep-room")
            viewModel.onScannerPayloadReceived("not-json")
            advanceUntilIdle()

            assertEquals(0, controller.startCount)
            assertEquals("ws://keep.example/ws", viewModel.uiState.value.signalingEndpoint)
            assertEquals("keep-room", viewModel.uiState.value.sessionId)
            assertEquals("ws://keep.example/ws", configStore.storedConfig.signalingEndpoint)
            assertEquals("keep-room", configStore.storedConfig.sessionId)
            assertEquals(R.string.stream_control_scan_parse_failed, viewModel.uiState.value.scanStatusLabel.resId)
        }

    @Test
    fun `capability refresh normalizes unsupported codecs to the device default and persists it`() =
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
            val configStore =
                FakeStreamControlConfigStore(
                    initialConfig = StreamConfig(codecPreference = CodecPreference.VP9),
                )
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    configStore = configStore,
                    initialConfig = StreamConfig(codecPreference = CodecPreference.VP9),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CodecPreference.HEVC, state.codecOptions.single { it.selected }.preference)
            assertEquals(R.string.stream_control_codec_device_default, state.codecStatusLabel.resId)
            assertEquals(listOf(CodecPreference.HEVC.displayName), state.codecStatusLabel.args)
            assertEquals(CodecPreference.HEVC, configStore.storedConfig.codecPreference)
        }

    @Test
    fun `audio enabled config auto-requests permission after an explicit denied snapshot`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val loadGate = CompletableDeferred<Unit>()
            val configStore =
                FakeStreamControlConfigStore(
                    initialConfig = StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT),
                    loadGate = loadGate,
                )
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    configStore = configStore,
                )
            val permissionRequests = mutableListOf<Unit>()

            backgroundScope.launch(dispatcher) {
                viewModel.recordAudioPermissionRequests.collect { permissionRequests += Unit }
            }
            advanceUntilIdle()

            viewModel.onRecordAudioPermissionSnapshot(false)
            advanceUntilIdle()
            assertEquals(0, permissionRequests.size)

            loadGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, permissionRequests.size)
            assertTrue(viewModel.uiState.value.audioPermissionRequestPending)
            assertFalse(viewModel.uiState.value.startEnabled)
        }

    @Test
    fun `denied record permission turns audio off and the next manual enable retries`() =
        runTest(dispatcher.scheduler) {
            val configStore =
                FakeStreamControlConfigStore(
                    initialConfig = StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT),
                )
            val viewModel =
                StreamControlViewModel(
                    sessionController = FakeStreamingSessionController(),
                    configStore = configStore,
                )
            advanceUntilIdle()

            viewModel.onRecordAudioPermissionSnapshot(false)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.audioPermissionRequestPending)

            viewModel.onRecordAudioPermissionResolved(false)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.audioEnabled)
            assertFalse(viewModel.uiState.value.audioPermissionRequestPending)
            assertFalse(configStore.storedConfig.audioEnabled)

            viewModel.onAudioToggleRequested(true)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.audioPermissionRequestPending)
        }

    @Test
    fun `manual audio enable with granted permission persists without requesting again`() =
        runTest(dispatcher.scheduler) {
            val configStore =
                FakeStreamControlConfigStore(
                    initialConfig =
                        StreamConfig(
                            signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                            audioEnabled = false,
                        ),
                )
            val viewModel =
                StreamControlViewModel(
                    sessionController = FakeStreamingSessionController(),
                    configStore = configStore,
                )
            val permissionRequests = mutableListOf<Unit>()

            backgroundScope.launch(dispatcher) {
                viewModel.recordAudioPermissionRequests.collect { permissionRequests += Unit }
            }
            advanceUntilIdle()

            viewModel.onRecordAudioPermissionSnapshot(true)
            advanceUntilIdle()

            viewModel.onAudioToggleRequested(true)
            advanceUntilIdle()

            assertEquals(0, permissionRequests.size)
            assertTrue(viewModel.uiState.value.audioEnabled)
            assertTrue(configStore.storedConfig.audioEnabled)
        }

    private class FakeStreamControlConfigStore(
        initialConfig: StreamConfig = StreamConfig(),
        private val loadGate: CompletableDeferred<Unit>? = null,
    ) : StreamControlConfigStore {
        var storedConfig: StreamConfig = initialConfig
            private set

        override suspend fun load(): StreamConfig {
            loadGate?.await()
            return storedConfig
        }

        override suspend fun save(config: StreamConfig) {
            storedConfig = config
        }
    }

    private companion object {
        const val VALID_SIGNALING_ENDPOINT = "ws://192.168.1.20:8080/ws"
    }
}
