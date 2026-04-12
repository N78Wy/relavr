package io.relavr.sender.app

import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.VideoResolution
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
    fun `start sends only the foreground service command and writes the preparing state`() =
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

            val config =
                StreamConfig(
                    codecPreference = CodecPreference.HEVC,
                    resolution = VideoResolution(width = 1920, height = 1080),
                    fps = 60,
                    bitrateKbps = 8000,
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                )
            controller.start(config)

            assertEquals(1, commandDispatcher.startCount)
            assertEquals(config, commandDispatcher.lastStartConfig)
            assertEquals(0, engine.startCount)
            assertEquals(CaptureState.RequestingPermission, controller.observeState().value.captureState)
            assertEquals(PublishState.Preparing, controller.observeState().value.publishState)
            assertNull(controller.observeState().value.error)
        }

    @Test
    fun `stop sends only the stop command and writes the stopping state`() =
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
    fun `session engine state changes are mirrored into the service controller`() =
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
    fun `foreground service command dispatch failures write an error state`() =
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
            controller.start(StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT))

            assertEquals(CaptureState.Error, controller.observeState().value.captureState)
            assertEquals(PublishState.Error, controller.observeState().value.publishState)
            assertEquals(
                SenderError.SessionStartFailed("dispatcher-start-failure"),
                controller.observeState().value.error,
            )
            assertEquals(1, logger.errorLogs.size)
        }

    private companion object {
        const val VALID_SIGNALING_ENDPOINT = "ws://192.168.1.20:8080/ws"
    }
}
