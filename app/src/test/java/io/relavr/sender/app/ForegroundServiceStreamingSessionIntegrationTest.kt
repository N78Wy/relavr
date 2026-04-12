package io.relavr.sender.app

import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.model.VideoResolution
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
    fun `the service controller forwards the streaming state from the session engine`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val signalingClient = FakeSignalingClient()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(FakeAudioCaptureSource()),
                    codecCapabilityRepository =
                        FakeCodecCapabilityRepository(
                            snapshot =
                                CapabilitySnapshot(
                                    supportedCodecs = setOf(CodecPreference.H264, CodecPreference.HEVC),
                                    audioPlaybackCaptureSupported = true,
                                    defaultCodec = CodecPreference.H264,
                                ),
                        ),
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
                    recordAudioPermissionGateway = FakeRecordAudioPermissionGateway(),
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
            assertEquals(CaptureState.RequestingPermission, controller.observeState().value.captureState)
            assertEquals(PublishState.Preparing, controller.observeState().value.publishState)

            coordinator.start(commandDispatcher.lastStartConfig ?: error("missing forwarded config"))
            advanceUntilIdle()

            assertTrue(controller.observeState().value.isStreaming)
            assertEquals(CaptureState.Capturing, controller.observeState().value.captureState)
            assertEquals(PublishState.Publishing, controller.observeState().value.publishState)
            assertEquals(AudioStreamState.Publishing, controller.observeState().value.audioState)
            assertEquals(
                UiText.of(io.relavr.sender.core.model.R.string.sender_status_streaming_audio_video),
                controller.observeState().value.statusDetail,
            )
            assertEquals(CodecPreference.HEVC, signalingClient.lastOpenedConfig?.codecPreference)
            assertEquals(VideoResolution(width = 1920, height = 1080), signalingClient.lastOpenedConfig?.resolution)
            assertEquals(60, signalingClient.lastOpenedConfig?.fps)
            assertEquals(8000, signalingClient.lastOpenedConfig?.bitrateKbps)
            assertEquals(VideoResolution(width = 1920, height = 1080), rtcPublisherFactory.lastConfig?.resolution)
            assertEquals(60, rtcPublisherFactory.lastConfig?.fps)
            assertEquals(8000, rtcPublisherFactory.lastConfig?.bitrateKbps)
            assertEquals(1, rtcPublisherFactory.session.publishCount)
            assertEquals(1, signalingClient.openCount)
        }

    @Test
    fun `permission denial before service start keeps the integrated session in video-only mode`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val projectionAccess = FakeProjectionAccess()
            val audioCaptureSource = FakeAudioCaptureSource()
            val rtcPublisherFactory = FakeRtcPublisherFactory()
            val signalingClient = FakeSignalingClient()
            val coordinator =
                StreamingSessionCoordinator(
                    projectionPermissionGateway = FakeProjectionPermissionGateway(nextAccess = projectionAccess),
                    audioCaptureSourceFactory = FakeAudioCaptureSourceFactory(audioCaptureSource),
                    codecCapabilityRepository =
                        FakeCodecCapabilityRepository(
                            snapshot =
                                CapabilitySnapshot(
                                    supportedCodecs = setOf(CodecPreference.H264, CodecPreference.HEVC),
                                    audioPlaybackCaptureSupported = true,
                                    defaultCodec = CodecPreference.H264,
                                ),
                        ),
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
                    recordAudioPermissionGateway =
                        FakeRecordAudioPermissionGateway().also {
                            it.nextGranted = false
                        },
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

            val forwardedConfig = commandDispatcher.lastStartConfig ?: error("missing forwarded config")
            assertEquals(false, forwardedConfig.audioEnabled)

            coordinator.start(forwardedConfig)
            advanceUntilIdle()

            assertTrue(controller.observeState().value.isStreaming)
            assertEquals(AudioStreamState.Disabled, controller.observeState().value.audioState)
            assertEquals(
                UiText.of(io.relavr.sender.core.model.R.string.sender_status_streaming_video_only),
                controller.observeState().value.statusDetail,
            )
            assertEquals(null, rtcPublisherFactory.session.lastAudioSource)
            assertEquals(false, audioCaptureSource.started)
        }

    private companion object {
        const val VALID_SIGNALING_ENDPOINT = "ws://192.168.1.20:8080/ws"
    }
}
