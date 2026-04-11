package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.DiscoveredReceiver
import io.relavr.sender.core.model.ReceiverConnectPayloadCodec
import io.relavr.sender.core.model.ReceiverConnectionInfo
import io.relavr.sender.core.model.ReceiverDiscoveryPhase
import io.relavr.sender.core.model.ReceiverDiscoverySnapshot
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.testing.fakes.FakeReceiverDiscoveryController
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
    fun `初始化后加载能力并更新配置`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    discoveryController = FakeReceiverDiscoveryController(),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CodecPreference.H264, state.codecOptions.single { it.selected }.preference)
            assertTrue(state.codecOptions.any { it.preference == CodecPreference.HEVC && it.enabled })
            assertEquals(StreamConfig.DEFAULT_RESOLUTION, state.resolutionOptions.single { it.selected }.value)
            assertEquals(StreamConfig.DEFAULT_FPS, state.fpsOptions.single { it.selected }.value)
            assertEquals(
                StreamConfig.DEFAULT_BITRATE_KBPS,
                state.bitrateOptions.single { it.selected }.value,
            )
            assertEquals("", state.signalingEndpoint)
            assertTrue(state.sessionId.isNotBlank())
            assertTrue(state.audioEnabled)
            assertFalse(state.startEnabled)
        }

    @Test
    fun `开始推流前会带上用户填写的信令配置`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    discoveryController = FakeReceiverDiscoveryController(),
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
            assertEquals(
                VideoResolution(width = 1920, height = 1080),
                controller.lastStartConfig?.resolution,
            )
            assertEquals(60, controller.lastStartConfig?.fps)
            assertEquals(8000, controller.lastStartConfig?.bitrateKbps)
            assertTrue(controller.lastStartConfig?.audioEnabled ?: false)
        }

    @Test
    fun `扫码成功后会自动回填连接信息并立即开始推流`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    discoveryController = FakeReceiverDiscoveryController(),
                )
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
                viewModel.uiState.value.scanStatusLabel
                    .contains("Living Room"),
            )
        }

    @Test
    fun `非法二维码不会污染配置也不会触发开播`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    discoveryController = FakeReceiverDiscoveryController(),
                )

            viewModel.onSignalingEndpointChanged("ws://keep.example/ws")
            viewModel.onSessionIdChanged("keep-room")
            viewModel.onScannerPayloadReceived("not-json")
            advanceUntilIdle()

            assertEquals(0, controller.startCount)
            assertEquals("ws://keep.example/ws", viewModel.uiState.value.signalingEndpoint)
            assertEquals("keep-room", viewModel.uiState.value.sessionId)
            assertTrue(
                viewModel.uiState.value.scanStatusLabel
                    .contains("JSON"),
            )
        }

    @Test
    fun `能力刷新后会把无效编码归一到默认编码`() =
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
                    discoveryController = FakeReceiverDiscoveryController(),
                    initialConfig = StreamConfig(codecPreference = CodecPreference.VP9),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CodecPreference.HEVC, state.codecOptions.single { it.selected }.preference)
            assertEquals("当前选择为设备推荐默认：H.265 / HEVC", state.codecStatusLabel)
        }

    @Test
    fun `页面可见且配置可编辑时会自动启动发现，离开页面后会停止`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val discoveryController = FakeReceiverDiscoveryController()
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    discoveryController = discoveryController,
                )

            viewModel.onScreenStarted()
            advanceUntilIdle()
            viewModel.onScreenStopped()
            advanceUntilIdle()

            assertEquals(1, discoveryController.startCount)
            assertEquals(1, discoveryController.stopCount)
        }

    @Test
    fun `发现启动失败后不会把 discovery 错误地标记为运行中`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val discoveryController =
                FakeReceiverDiscoveryController().apply {
                    startSnapshot =
                        ReceiverDiscoverySnapshot(
                            phase = ReceiverDiscoveryPhase.Error,
                            errorMessage = "启动局域网发现失败：系统内部错误（0）",
                        )
                }
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    discoveryController = discoveryController,
                )

            viewModel.onScreenStarted()
            advanceUntilIdle()
            viewModel.onScreenStopped()
            advanceUntilIdle()

            assertEquals(1, discoveryController.startCount)
            assertEquals(0, discoveryController.stopCount)
            assertEquals(
                "启动局域网发现失败：系统内部错误（0）",
                viewModel.uiState.value.discoveryStatusLabel,
            )
        }

    @Test
    fun `发现到接收端后点击确认才会开始推流`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val discoveryController =
                FakeReceiverDiscoveryController(
                    ReceiverDiscoverySnapshot(
                        receivers =
                            listOf(
                                DiscoveredReceiver(
                                    serviceName = "living-room",
                                    receiverName = "Living Room",
                                    sessionId = "room-1",
                                    host = "192.168.1.20",
                                    port = 17888,
                                    authRequired = true,
                                ),
                            ),
                    ),
                )
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    discoveryController = discoveryController,
                )
            val receiver =
                discoveryController
                    .observeState()
                    .value.receivers
                    .single()

            viewModel.onDiscoveredReceiverClicked(receiver)
            advanceUntilIdle()

            assertEquals(0, controller.startCount)
            assertEquals(
                "连接到 Living Room",
                viewModel.uiState.value.discoveryConfirmation
                    ?.title,
            )

            viewModel.onDiscoveryConnectionConfirmed()
            advanceUntilIdle()

            assertEquals(1, controller.startCount)
            assertEquals("ws://192.168.1.20:17888", controller.lastStartConfig?.signalingEndpoint)
            assertEquals("room-1", controller.lastStartConfig?.sessionId)
        }

    @Test
    fun `取消发现连接不会污染配置也不会触发开播`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val discoveryController =
                FakeReceiverDiscoveryController(
                    ReceiverDiscoverySnapshot(
                        receivers =
                            listOf(
                                DiscoveredReceiver(
                                    serviceName = "living-room",
                                    receiverName = "Living Room",
                                    sessionId = "room-1",
                                    host = "192.168.1.20",
                                    port = 17888,
                                    authRequired = false,
                                ),
                            ),
                    ),
                )
            val viewModel =
                StreamControlViewModel(
                    sessionController = controller,
                    discoveryController = discoveryController,
                )

            viewModel.onSignalingEndpointChanged("ws://keep.example/ws")
            viewModel.onSessionIdChanged("keep-room")
            viewModel.onDiscoveredReceiverClicked(
                discoveryController
                    .observeState()
                    .value.receivers
                    .single(),
            )
            advanceUntilIdle()
            viewModel.onDiscoveryConnectionDismissed()
            advanceUntilIdle()

            assertEquals(0, controller.startCount)
            assertEquals("ws://keep.example/ws", viewModel.uiState.value.signalingEndpoint)
            assertEquals("keep-room", viewModel.uiState.value.sessionId)
            assertEquals(null, viewModel.uiState.value.discoveryConfirmation)
        }
}
