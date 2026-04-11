package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.CodecPreference
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
            assertEquals(CodecPreference.H264.displayName, state.codecLabel)
            assertEquals("", state.signalingEndpoint)
            assertTrue(state.sessionId.isNotBlank())
            assertFalse(state.startEnabled)
            assertEquals("请先填写 Quest 可访问的 WebSocket 地址后再开始推流", state.startHint)
        }

    @Test
    fun `开始推流前会带上用户填写的信令配置`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel = StreamControlViewModel(sessionController = controller)

            viewModel.onSignalingEndpointChanged("ws://192.168.123.182:8765")
            viewModel.onSessionIdChanged("room-42")
            viewModel.onStartClicked()
            advanceUntilIdle()

            assertEquals(1, controller.startCount)
            assertEquals("ws://192.168.123.182:8765", controller.lastStartConfig?.signalingEndpoint)
            assertEquals("room-42", controller.lastStartConfig?.sessionId)
            assertFalse(controller.lastStartConfig?.audioEnabled ?: true)
            assertTrue(viewModel.uiState.value.startEnabled)
        }
}
