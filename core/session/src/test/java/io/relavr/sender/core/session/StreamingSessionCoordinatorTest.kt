package io.relavr.sender.core.session

import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingSessionCoordinatorTest {
    @Test
    fun `start 成功后进入推流中状态`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val audioSource = FakeAudioCaptureSource()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val signalingClient = FakeSignalingClient()
            val logger = FakeAppLogger()

            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(audioSource),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = signalingClient,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = logger,
                )

            coordinator.start(
                StreamConfig(
                    codecPreference = CodecPreference.H264,
                    audioEnabled = false,
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                ),
            )
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Capturing, state.captureState)
            assertEquals(PublishState.Publishing, state.publishState)
            assertEquals(1, rtcPublisherFactory.session.publishCount)
            assertEquals(1, signalingClient.openCount)
            assertEquals("WebRTC 已连接，正在发送视频", state.statusDetail)
            assertNull(state.error)
        }

    @Test
    fun `非法配置会直接写入错误状态`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionGateway = FakeProjectionPermissionGateway()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = projectionGateway,
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = FakeRtcPublisherFactory(),
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            coordinator.start(StreamConfig(signalingEndpoint = "https://invalid.example", sessionId = ""))
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Error, state.captureState)
            assertEquals(PublishState.Error, state.publishState)
            assertEquals(SenderError.InvalidConfig("Session ID 不能为空"), state.error)
            assertEquals(0, projectionGateway.requestCount)
        }

    @Test
    fun `start 被拒绝授权时写入错误状态`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val logger = FakeAppLogger()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(shouldDeny = true),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = FakeRtcPublisherFactory(),
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = logger,
                )

            coordinator.start(
                StreamConfig(
                    audioEnabled = true,
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                ),
            )
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Error, state.captureState)
            assertEquals(PublishState.Error, state.publishState)
            assertEquals(SenderError.PermissionDenied, state.error)
            assertEquals(1, logger.errorLogs.size)
            assertTrue(
                logger.errorLogs
                    .single()
                    .message
                    .contains("请求投屏权限失败"),
            )
            assertNotNull(logger.errorLogs.single().throwable)
        }

    @Test
    fun `stop 会释放已创建资源`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val audioSource = FakeAudioCaptureSource()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val signalingClient = FakeSignalingClient()
            val logger = FakeAppLogger()

            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(audioSource),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = signalingClient,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = logger,
                )

            coordinator.start(
                StreamConfig(
                    audioEnabled = true,
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                ),
            )
            advanceUntilIdle()
            coordinator.stop()
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Idle, state.captureState)
            assertEquals(PublishState.Idle, state.publishState)
            assertTrue(projectionAccess.closed)
            assertTrue(audioSource.closed)
            assertTrue(rtcPublisherFactory.session.closed)
            assertEquals(1, signalingClient.session.closeCount)
        }

    @Test
    fun `rtc 会话断开后会写入错误状态并释放资源`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            coordinator.start(StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT))
            advanceUntilIdle()
            rtcPublisherFactory.session.emitEvent(RtcSessionEvent.Disconnected)
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Error, state.captureState)
            assertEquals(PublishState.Error, state.publishState)
            assertEquals(SenderError.PeerConnectionFailed("WebRTC 连接已断开"), state.error)
            assertTrue(projectionAccess.closed)
        }

    @Test
    fun `start 失败时写入异常日志`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val rtcPublisherFactory =
                FakeRtcPublisherFactory().also {
                    it.session.shouldFail = true
                }
            val logger = FakeAppLogger()

            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = logger,
                )

            coordinator.start(StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT))
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Error, state.captureState)
            assertEquals(PublishState.Error, state.publishState)
            assertEquals(SenderError.SessionStartFailed("fake-publish-failure"), state.error)
            assertEquals(1, logger.errorLogs.size)
            assertTrue(
                logger.errorLogs
                    .single()
                    .message
                    .contains("启动推流会话失败"),
            )
            assertTrue(
                logger.errorLogs
                    .single()
                    .message
                    .contains("fake-publish-failure"),
            )
            assertNotNull(logger.errorLogs.single().throwable)
        }

    private companion object {
        const val VALID_SIGNALING_ENDPOINT = "ws://192.168.1.20:8080/ws"
    }
}
