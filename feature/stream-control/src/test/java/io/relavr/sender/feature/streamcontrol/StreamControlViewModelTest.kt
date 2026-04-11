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
            assertTrue(state.availableCodecs.contains(CodecPreference.H264))
            assertTrue(state.availableCodecs.contains(CodecPreference.HEVC))
        }

    @Test
    fun `开始推流前会带上用户选择的配置`() =
        runTest(dispatcher.scheduler) {
            val controller = FakeStreamingSessionController()
            val viewModel = StreamControlViewModel(sessionController = controller)

            viewModel.onCodecSelected(CodecPreference.HEVC)
            viewModel.onAudioEnabledChanged(false)
            viewModel.onStartClicked()
            advanceUntilIdle()

            assertEquals(1, controller.startCount)
            assertEquals(CodecPreference.HEVC, controller.lastStartConfig?.codecPreference)
            assertFalse(controller.lastStartConfig?.audioEnabled ?: true)
        }
}
