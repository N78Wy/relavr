package io.relavr.sender.core.session

import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.UiText
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
    fun `start enters the streaming state on success`() =
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
            assertEquals(AudioStreamState.Disabled, state.audioState)
            assertEquals(1, rtcPublisherFactory.session.publishCount)
            assertEquals(1, signalingClient.openCount)
            assertEquals(UiText.of(io.relavr.sender.core.model.R.string.sender_status_streaming_video_only), state.statusDetail)
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
                    .contains("Requesting the screen-capture permission failed"),
            )
            assertNotNull(logger.errorLogs.single().throwable)
        }

    @Test
    fun `stop releases created resources`() =
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
            assertEquals(AudioStreamState.Disabled, state.audioState)
            assertTrue(projectionAccess.closed)
            assertTrue(audioSource.closed)
            assertTrue(rtcPublisherFactory.session.closed)
            assertEquals(1, signalingClient.session.closeCount)
        }

    @Test
    fun `audio capture initialization failures degrade to video-only streaming`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val audioCaptureSourceFactory =
                FakeAudioCaptureSourceFactory().also {
                    it.shouldFail = true
                }
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(),
                    audioCaptureSourceFactory = audioCaptureSourceFactory,
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = FakeRtcPublisherFactory(),
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            coordinator.start(
                StreamConfig(
                    audioEnabled = true,
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                ),
            )
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Capturing, state.captureState)
            assertEquals(PublishState.Publishing, state.publishState)
            assertEquals(AudioStreamState.Degraded, state.audioState)
            assertEquals(
                UiText.of(io.relavr.sender.core.model.R.string.sender_error_audio_capture_unavailable),
                state.audioDetail,
            )
            assertEquals(UiText.of(io.relavr.sender.core.model.R.string.sender_status_streaming_video_only), state.statusDetail)
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
            assertEquals(SenderError.PeerConnectionFailed("The WebRTC connection was disconnected."), state.error)
            assertTrue(projectionAccess.closed)
        }

    @Test
    fun `audio degradation during streaming does not stop video publishing`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(FakeAudioCaptureSource()),
                    codecCapabilityRepository = FakeCodecCapabilityRepository(),
                    codecPolicy = FakeCodecPolicy(),
                    rtcPublisherFactory = rtcPublisherFactory,
                    signalingClient = FakeSignalingClient(),
                    dispatchers = TestAppDispatchers(dispatcher, dispatcher, dispatcher),
                    logger = FakeAppLogger(),
                )

            coordinator.start(
                StreamConfig(
                    audioEnabled = true,
                    signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                ),
            )
            advanceUntilIdle()

            rtcPublisherFactory.session.emitEvent(
                RtcSessionEvent.AudioDegraded(
                    UiText.of(io.relavr.sender.core.model.R.string.sender_audio_degraded_video_only),
                ),
            )
            advanceUntilIdle()

            val state = coordinator.observeState().value
            assertEquals(CaptureState.Capturing, state.captureState)
            assertEquals(PublishState.Publishing, state.publishState)
            assertEquals(AudioStreamState.Degraded, state.audioState)
            assertEquals(
                UiText.of(io.relavr.sender.core.model.R.string.sender_audio_degraded_video_only),
                state.audioDetail,
            )
            assertEquals(UiText.of(io.relavr.sender.core.model.R.string.sender_status_streaming_video_only), state.statusDetail)
            assertNull(state.error)
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
