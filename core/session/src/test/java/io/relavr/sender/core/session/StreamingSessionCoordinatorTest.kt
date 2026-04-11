package io.relavr.sender.core.session

import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.testing.fakes.FakeAudioCaptureSource
import io.relavr.sender.testing.fakes.FakeAudioCaptureSourceFactory
import io.relavr.sender.testing.fakes.FakeCodecCapabilityRepository
import io.relavr.sender.testing.fakes.FakeCodecPolicy
import io.relavr.sender.testing.fakes.FakeProjectionAccess
import io.relavr.sender.testing.fakes.FakeProjectionPermissionGateway
import io.relavr.sender.testing.fakes.FakeRtcPublisherFactory
import io.relavr.sender.testing.fakes.FakeSignalingClient
import io.relavr.sender.testing.fakes.FakeVideoCaptureSource
import io.relavr.sender.testing.fakes.FakeVideoCaptureSourceFactory
import io.relavr.sender.testing.fakes.TestAppDispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingSessionCoordinatorTest {
    @Test
    fun `start 成功后进入推流中状态`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val videoSource = FakeVideoCaptureSource()
            val audioSource = FakeAudioCaptureSource()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val signalingClient = FakeSignalingClient()

            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    videoCaptureSourceFactory = FakeVideoCaptureSourceFactory(videoSource),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(audioSource),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = signalingClient,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                )

            coordinator.start(StreamConfig(codecPreference = CodecPreference.H264))

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Capturing, state.captureState)
            assertEquals(PublishState.Publishing, state.publishState)
            assertEquals(1, rtcPublisherFactory.session.publishCount)
            assertEquals(1, signalingClient.openCount)
            assertNull(state.error)
        }

    @Test
    fun `start 被拒绝授权时写入错误状态`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(shouldDeny = true),
                    videoCaptureSourceFactory = FakeVideoCaptureSourceFactory(),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = FakeRtcPublisherFactory(),
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                )

            coordinator.start(StreamConfig())

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Error, state.captureState)
            assertEquals(PublishState.Error, state.publishState)
            assertEquals(SenderError.PermissionDenied, state.error)
        }

    @Test
    fun `stop 会释放已创建资源`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val videoSource = FakeVideoCaptureSource()
            val audioSource = FakeAudioCaptureSource()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val signalingClient = FakeSignalingClient()

            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    videoCaptureSourceFactory = FakeVideoCaptureSourceFactory(videoSource),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(audioSource),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = signalingClient,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                )

            coordinator.start(StreamConfig())
            coordinator.stop()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Idle, state.captureState)
            assertEquals(PublishState.Idle, state.publishState)
            assertTrue(projectionAccess.closed)
            assertTrue(videoSource.closed)
            assertTrue(audioSource.closed)
            assertTrue(rtcPublisherFactory.session.closed)
            assertEquals(1, signalingClient.closeCount)
        }
}
