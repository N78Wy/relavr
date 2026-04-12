package io.relavr.sender.core.session

import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.model.VideoStreamProfile
import io.relavr.sender.testing.fakes.FakeAppLogger
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
    fun `start enters the streaming state on success`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val signalingClient = FakeSignalingClient()
            val logger = FakeAppLogger()

            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
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
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                ),
            )
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Capturing, state.captureState)
            assertEquals(PublishState.Publishing, state.publishState)
            assertEquals(1, rtcPublisherFactory.session.publishCount)
            assertEquals(1, signalingClient.openCount)
            assertEquals(
                UiText.of(io.relavr.sender.core.model.R.string.sender_status_streaming_video_only),
                state.statusDetail,
            )
            assertNull(state.error)
        }

    @Test
    fun `invalid config writes an error state immediately`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionGateway = FakeProjectionPermissionGateway()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = projectionGateway,
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
            assertEquals(
                SenderError.InvalidConfig(
                    message = "The session ID is required.",
                    uiText = UiText.of(io.relavr.sender.core.model.R.string.sender_error_session_id_required),
                ),
                state.error,
            )
            assertEquals(0, projectionGateway.requestCount)
        }

    @Test
    fun `permission denial during start writes an error state`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val logger = FakeAppLogger()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(shouldDeny = true),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = FakeRtcPublisherFactory(),
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = logger,
                )

            coordinator.start(
                StreamConfig(
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
                    .contains("Requesting the screen-capture permission failed"),
            )
            assertNotNull(logger.errorLogs.single().throwable)
        }

    @Test
    fun `stop releases created resources`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val signalingClient = FakeSignalingClient()
            val logger = FakeAppLogger()

            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = signalingClient,
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = logger,
                )

            coordinator.start(
                StreamConfig(
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
            assertTrue(rtcPublisherFactory.session.closed)
            assertEquals(1, signalingClient.session.closeCount)
        }

    @Test
    fun `an rtc disconnect writes an error state and releases resources`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
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
            assertEquals(SenderError.PeerConnectionFailed("The WebRTC connection was disconnected."), state.error)
            assertTrue(projectionAccess.closed)
        }

    @Test
    fun `video profile changes update the active profile without stopping the session`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            coordinator.start(StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT))
            advanceUntilIdle()

            val activeProfile =
                VideoStreamProfile(
                    codecPreference = CodecPreference.H264,
                    resolution = VideoResolution(width = 1280, height = 720),
                    fps = 30,
                    bitrateKbps = 4000,
                )
            rtcPublisherFactory.session.emitEvent(
                RtcSessionEvent.VideoProfileChanged(
                    activeProfile = activeProfile,
                    detail =
                        UiText.of(
                            io.relavr.sender.core.model.R.string.sender_status_video_profile_downgraded,
                            activeProfile.resolution.label,
                            activeProfile.fps,
                            activeProfile.bitrateKbps,
                        ),
                ),
            )
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Capturing, state.captureState)
            assertEquals(PublishState.Publishing, state.publishState)
            assertEquals(activeProfile, state.activeVideoProfile)
            assertEquals(
                UiText.of(
                    io.relavr.sender.core.model.R.string.sender_status_video_profile_downgraded,
                    activeProfile.resolution.label,
                    activeProfile.fps,
                    activeProfile.bitrateKbps,
                ),
                state.statusDetail,
            )
        }

    @Test
    fun `encoder overload ends the session with a dedicated error`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            coordinator.start(StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT))
            advanceUntilIdle()
            rtcPublisherFactory.session.emitEvent(
                RtcSessionEvent.VideoEncoderOverloaded(
                    SenderError.VideoEncoderOverloaded("video-overloaded"),
                ),
            )
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Error, state.captureState)
            assertEquals(PublishState.Error, state.publishState)
            assertEquals(SenderError.VideoEncoderOverloaded("video-overloaded"), state.error)
            assertTrue(projectionAccess.closed)
        }

    @Test
    fun `start failures write error logs`() =
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
                    .contains("Starting the streaming session failed"),
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
