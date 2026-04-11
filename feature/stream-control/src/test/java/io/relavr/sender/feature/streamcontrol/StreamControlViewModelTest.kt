package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.StreamConfig
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
            val viewModel = StreamControlViewModel(sessionController = controller)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CodecPreference.H264, state.codecOptions.single { it.selected }.preference)
            assertTrue(state.codecOptions.any { it.preference == CodecPreference.HEVC && it.enabled })
            assertEquals("", state.signalingEndpoint)
            assertTrue(state.sessionId.isNotBlank())
            assertTrue(state.audioEnabled)
            assertFalse(state.startEnabled)
        }

    @Test
    fun `开始推流前会带上用户填写的信令配置`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel = StreamControlViewModel(sessionController = controller)

            viewModel.onSignalingEndpointChanged("wss://signal.example/ws")
            viewModel.onSessionIdChanged("room-42")
            viewModel.onCodecPreferenceChanged(CodecPreference.HEVC)
            viewModel.onStartClicked()
            advanceUntilIdle()

            assertEquals(1, controller.startCount)
            assertEquals("wss://signal.example/ws", controller.lastStartConfig?.signalingEndpoint)
            assertEquals("room-42", controller.lastStartConfig?.sessionId)
            assertEquals(CodecPreference.HEVC, controller.lastStartConfig?.codecPreference)
            assertTrue(controller.lastStartConfig?.audioEnabled ?: false)
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
                    initialConfig = StreamConfig(codecPreference = CodecPreference.VP9),
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(CodecPreference.HEVC, state.codecOptions.single { it.selected }.preference)
            assertEquals("当前选择为设备推荐默认：H.265 / HEVC", state.codecStatusLabel)
        }
}
