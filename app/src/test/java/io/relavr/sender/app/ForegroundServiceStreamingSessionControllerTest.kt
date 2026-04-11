package io.relavr.sender.app

import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
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
            val recordAudioPermissionGateway = FakeRecordAudioPermissionGateway()
            val controller =
                ForegroundServiceStreamingSessionController(
                    sessionEngine = engine,
                    commandDispatcher = commandDispatcher,
                    recordAudioPermissionGateway = recordAudioPermissionGateway,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            advanceUntilIdle()

            val config =
                StreamConfig(
                    audioEnabled = false,
                    codecPreference = CodecPreference.HEVC,
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                )
            controller.start(config)

            assertEquals(1, commandDispatcher.startCount)
            assertEquals(config, commandDispatcher.lastStartConfig)
            assertEquals(0, engine.startCount)
            assertEquals(0, recordAudioPermissionGateway.requestCount)
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
                            resolvedConfig =
                                StreamConfig(
                                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                                ),
                        ),
                )
            val commandDispatcher = FakeForegroundServiceCommandDispatcher()
            val controller =
                ForegroundServiceStreamingSessionController(
                    sessionEngine = engine,
                    commandDispatcher = commandDispatcher,
                    recordAudioPermissionGateway = FakeRecordAudioPermissionGateway(),
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
                    recordAudioPermissionGateway = FakeRecordAudioPermissionGateway(),
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
                    recordAudioPermissionGateway = FakeRecordAudioPermissionGateway(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = logger,
                )

            advanceUntilIdle()
            controller.start(StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT))

            assertEquals(CaptureState.Error, controller.observeState().value.captureState)
            assertEquals(PublishState.Error, controller.observeState().value.publishState)
            assertEquals(
                SenderError.SessionStartFailed("dispatcher-start-failure"),
                controller.observeState().value.error,
            )
            assertEquals(1, logger.errorLogs.size)
        }

    @Test
    fun `音频开启时会先请求录音权限`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val recordAudioPermissionGateway = FakeRecordAudioPermissionGateway()
            val controller =
                ForegroundServiceStreamingSessionController(
                    sessionEngine = FakeStreamingSessionController(),
                    commandDispatcher = FakeForegroundServiceCommandDispatcher(),
                    recordAudioPermissionGateway = recordAudioPermissionGateway,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            advanceUntilIdle()
            controller.start(
                StreamConfig(
                    audioEnabled = true,
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                ),
            )

            assertEquals(1, recordAudioPermissionGateway.requestCount)
        }

    private companion object {
        const val VALID_SIGNALING_ENDPOINT = "ws://192.168.1.20:8080/ws"
    }
}
