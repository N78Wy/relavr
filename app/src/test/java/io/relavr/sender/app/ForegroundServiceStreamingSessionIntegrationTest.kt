package io.relavr.sender.app

import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.session.StreamingSessionCoordinator
import io.relavr.sender.testing.fakes.FakeAppLogger
import io.relavr.sender.testing.fakes.FakeAudioCaptureSource
import io.relavr.sender.testing.fakes.FakeAudioCaptureSourceFactory
import io.relavr.sender.testing.fakes.FakeCodecCapabilityRepository
import io.relavr.sender.testing.fakes.FakeCodecPolicy
import io.relavr.sender.testing.fakes.FakeProjectionAccess
import io.relavr.sender.testing.fakes.FakeProjectionPermissionGateway
import io.relavr.sender.testing.fakes.FakeRtcPublisherFactory
import io.relavr.sender.testing.fakes.FakeSignalingClient
import io.relavr.sender.testing.fakes.TestAppDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundServiceStreamingSessionIntegrationTest {
    @Test
    fun `服务控制器会透传底层会话引擎的推流状态`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val signalingClient = FakeSignalingClient()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(FakeAudioCaptureSource()),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = signalingClient,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )
            val commandDispatcher = FakeForegroundServiceCommandDispatcher()
            val controller =
                ForegroundServiceStreamingSessionController(
                    sessionEngine = coordinator,
                    commandDispatcher = commandDispatcher,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            advanceUntilIdle()

            val config =
                StreamConfig(
                    codecPreference = CodecPreference.H264,
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                )
            controller.start(config)

            assertEquals(1, commandDispatcher.startCount)
            assertEquals(CaptureState.RequestingPermission, controller.observeState().value.captureState)
            assertEquals(PublishState.Preparing, controller.observeState().value.publishState)

            coordinator.start(config)
            advanceUntilIdle()

            assertTrue(controller.observeState().value.isStreaming)
            assertEquals(CaptureState.Capturing, controller.observeState().value.captureState)
            assertEquals(PublishState.Publishing, controller.observeState().value.publishState)
            assertEquals("WebRTC 已连接，正在发送视频", controller.observeState().value.statusDetail)
            assertEquals(1, rtcPublisherFactory.session.publishCount)
            assertEquals(1, signalingClient.openCount)
        }

    private companion object {
        const val VALID_SIGNALING_ENDPOINT = "ws://192.168.1.20:8080/ws"
    }
}
