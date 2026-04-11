package io.relavr.sender.app

import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.testing.fakes.FakeAppLogger
import io.relavr.sender.testing.fakes.FakeStreamingSessionController
import io.relavr.sender.testing.fakes.TestAppDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundServiceStreamingSessionControllerTest {
    @Test
    fun `start 只发送前台服务命令并写入准备状态`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val engine = FakeStreamingSessionController()
            val commandDispatcher = FakeForegroundServiceCommandDispatcher()
            val controller =
                ForegroundServiceStreamingSessionController(
                    sessionEngine = engine,
                    commandDispatcher = commandDispatcher,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            advanceUntilIdle()

            val config = StreamConfig(audioEnabled = false)
            controller.start(config)

            assertEquals(1, commandDispatcher.startCount)
            assertEquals(config, commandDispatcher.lastStartConfig)
            assertEquals(0, engine.startCount)
            assertEquals(CaptureState.RequestingPermission, controller.observeState().value.captureState)
            assertEquals(PublishState.Preparing, controller.observeState().value.publishState)
            assertNull(controller.observeState().value.error)
        }

    @Test
    fun `stop 只发送停止命令并写入停止状态`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val engine =
                FakeStreamingSessionController(
                    initialState =
                        StreamingSessionSnapshot(
                            captureState = CaptureState.Capturing,
                            publishState = PublishState.Publishing,
                            resolvedConfig = StreamConfig(),
                        ),
                )
            val commandDispatcher = FakeForegroundServiceCommandDispatcher()
            val controller =
                ForegroundServiceStreamingSessionController(
                    sessionEngine = engine,
                    commandDispatcher = commandDispatcher,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            advanceUntilIdle()

            controller.stop()

            assertEquals(1, commandDispatcher.stopCount)
            assertEquals(0, engine.stopCount)
            assertEquals(CaptureState.Stopping, controller.observeState().value.captureState)
            assertEquals(PublishState.Stopping, controller.observeState().value.publishState)
        }

    @Test
    fun `底层会话状态变化会同步到服务控制器`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val engine = FakeStreamingSessionController()
            val controller =
                ForegroundServiceStreamingSessionController(
                    sessionEngine = engine,
                    commandDispatcher = FakeForegroundServiceCommandDispatcher(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            advanceUntilIdle()

            val error = SenderError.SessionStartFailed("fake-start-failure")
            engine.updateState(
                StreamingSessionSnapshot(
                    captureState = CaptureState.Error,
                    publishState = PublishState.Error,
                    error = error,
                ),
            )
            advanceUntilIdle()

            assertEquals(CaptureState.Error, controller.observeState().value.captureState)
            assertEquals(PublishState.Error, controller.observeState().value.publishState)
            assertEquals(error, controller.observeState().value.error)
        }

    @Test
    fun `前台服务命令分发失败时写入错误状态`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val logger = FakeAppLogger()
            val commandDispatcher =
                FakeForegroundServiceCommandDispatcher().also {
                    it.startFailure = IllegalStateException("dispatcher-start-failure")
                }
            val controller =
                ForegroundServiceStreamingSessionController(
                    sessionEngine = FakeStreamingSessionController(),
                    commandDispatcher = commandDispatcher,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = logger,
                )

            advanceUntilIdle()
            controller.start(StreamConfig())

            assertEquals(CaptureState.Error, controller.observeState().value.captureState)
            assertEquals(PublishState.Error, controller.observeState().value.publishState)
            assertEquals(
                SenderError.SessionStartFailed("dispatcher-start-failure"),
                controller.observeState().value.error,
            )
            assertEquals(1, logger.errorLogs.size)
        }
}
