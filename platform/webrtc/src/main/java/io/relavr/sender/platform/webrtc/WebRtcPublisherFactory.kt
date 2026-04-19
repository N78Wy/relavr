package io.relavr.sender.platform.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Debug
import android.os.Process
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.AudioState
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.R
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.model.VideoStreamProfile
import io.relavr.sender.core.session.AudioCaptureFormat
import io.relavr.sender.core.session.PlaybackAudioCaptureSession
import io.relavr.sender.core.session.PlaybackAudioCaptureSessionFactory
import io.relavr.sender.core.session.ProjectionAccess
import io.relavr.sender.core.session.PublishStartResult
import io.relavr.sender.core.session.RtcPublishSession
import io.relavr.sender.core.session.RtcPublisherFactory
import io.relavr.sender.core.session.RtcSessionEvent
import io.relavr.sender.core.session.SenderException
import io.relavr.sender.core.session.SignalingMessage
import io.relavr.sender.core.session.SignalingSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeout
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RTCStatsReport
import org.webrtc.RtpParameters
import org.webrtc.RtpSender
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class WebRtcPublisherFactory(
    private val appContext: Context,
    private val libraryInitializer: WebRtcLibraryInitializer,
    private val playbackAudioCaptureSessionFactory: PlaybackAudioCaptureSessionFactory,
    private val logger: AppLogger,
) : RtcPublisherFactory {
    override suspend fun createSession(
        config: StreamConfig,
        capabilities: CapabilitySnapshot,
        signalingSession: SignalingSession,
    ): RtcPublishSession =
        WebRtcPublishSession(
            appContext = appContext.applicationContext,
            config = config,
            capabilities = capabilities,
            signalingSession = signalingSession,
            libraryInitializer = libraryInitializer,
            playbackAudioCaptureSessionFactory = playbackAudioCaptureSessionFactory,
            logger = logger,
        )
}

private class WebRtcPublishSession(
    private val appContext: Context,
    private val config: StreamConfig,
    private val capabilities: CapabilitySnapshot,
    private val signalingSession: SignalingSession,
    private val libraryInitializer: WebRtcLibraryInitializer,
    private val playbackAudioCaptureSessionFactory: PlaybackAudioCaptureSessionFactory,
    private val logger: AppLogger,
) : RtcPublishSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventFlow = MutableSharedFlow<RtcSessionEvent>(extraBufferCapacity = 32)
    private val audioBridge = if (config.audioEnabled) WebRtcPlaybackAudioBridge(eventFlow, logger) else null
    private val runtime = WebRtcRuntime(appContext, libraryInitializer, audioBridge)
    private val terminalError = CompletableDeferred<SenderError>()
    private val remoteDescriptionReady = CompletableDeferred<Unit>()
    private val peerConnected = CompletableDeferred<Unit>()
    private val closed = AtomicBoolean(false)
    private val bufferedRemoteCandidates = mutableListOf<IceCandidate>()

    private var peerConnection: PeerConnection? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoSender: RtpSender? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var audioSender: RtpSender? = null
    private var signalingJob: Job? = null
    private var videoMonitorJob: Job? = null

    override val events: Flow<RtcSessionEvent> = eventFlow.asSharedFlow()

    override suspend fun publish(projectionAccess: ProjectionAccess): PublishStartResult {
        val initialVideoProfile = config.toVideoStreamProfile()
        val adaptiveVideoProfileController =
            AdaptiveVideoProfileController(
                initialProfile = initialVideoProfile,
                supportedProfiles = capabilities.supportedProfiles,
            )
        val permission =
            projectionAccess.mediaProjectionPermission()
                ?: throw SenderException(SenderError.SessionStartFailed("Missing a valid MediaProjection permission result."))

        val sessionId = config.trimmedSessionId
        val peer =
            createPeerConnection(sessionId)
                ?: throw SenderException(SenderError.PeerConnectionFailed("Unable to create the PeerConnection."))
        peerConnection = peer

        signalingJob = observeIncomingSignaling(peer)

        eventFlow.tryEmit(RtcSessionEvent.Status(UiText.of(R.string.sender_status_connected_signaling)))
        signalingSession.send(SignalingMessage.Join(sessionId = sessionId))
        eventFlow.tryEmit(RtcSessionEvent.Status(UiText.of(R.string.sender_status_sent_join_request)))

        val capturer =
            ScreenCapturerAndroid(
                Intent(permission.resultData),
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (closed.get()) {
                            return
                        }
                        signalFailure(
                            SenderError.CaptureInterrupted("Screen capture was stopped by the system."),
                            interruptCapture = true,
                        )
                    }
                },
            )
        screenCapturer = capturer

        val textureHelper =
            SurfaceTextureHelper.create(
                "RelavrScreenCapture",
                runtime.eglBase.eglBaseContext,
            )
        surfaceTextureHelper = textureHelper

        val localVideoSource = runtime.peerConnectionFactory.createVideoSource(true)
        videoSource = localVideoSource
        localVideoSource.adaptOutputFormat(
            config.resolution.width,
            config.resolution.height,
            config.fps,
        )
        capturer.initialize(textureHelper, appContext, localVideoSource.capturerObserver)
        capturer.startCapture(
            config.resolution.width,
            config.resolution.height,
            config.fps,
        )

        val localVideoTrack =
            runtime.peerConnectionFactory.createVideoTrack(
                VIDEO_TRACK_ID,
                localVideoSource,
            )
        videoTrack = localVideoTrack
        val localVideoSender =
            peer.addTrack(localVideoTrack, listOf(sessionId))
                ?: throw SenderException(SenderError.PeerConnectionFailed("Unable to create the WebRTC video sender."))
        videoSender = localVideoSender
        applyVideoEncoderParameters(
            peer = peer,
            sender = localVideoSender,
            profile = initialVideoProfile,
        )

        val publishStartResult =
            startAudioIfRequested(
                peer = peer,
                sessionId = sessionId,
                mediaProjection = capturer.mediaProjection,
            )

        eventFlow.tryEmit(RtcSessionEvent.Status(UiText.of(R.string.sender_status_creating_offer)))
        val rawOffer = peer.awaitCreateOffer()
        val preferredOffer =
            SessionDescription(
                rawOffer.type,
                SdpCodecPreference.preferVideoCodec(
                    rawOffer.description,
                    config.codecPreference.webrtcCodecName,
                ),
            )
        peer.awaitSetLocalDescription(preferredOffer)
        signalingSession.send(
            SignalingMessage.Offer(
                sessionId = sessionId,
                sdp = preferredOffer.description,
            ),
        )
        eventFlow.tryEmit(RtcSessionEvent.Status(UiText.of(R.string.sender_status_sent_offer_waiting_answer)))

        awaitSuccessOrFailure(
            success = remoteDescriptionReady,
            failure = terminalError,
            timeoutMs = ANSWER_TIMEOUT_MS,
            timeoutError = SenderError.SignalingFailed("Timed out while waiting for the remote answer."),
        )

        eventFlow.tryEmit(RtcSessionEvent.Status(UiText.of(R.string.sender_status_checking_ice)))
        awaitSuccessOrFailure(
            success = peerConnected,
            failure = terminalError,
            timeoutMs = CONNECT_TIMEOUT_MS,
            timeoutError = SenderError.PeerConnectionFailed("Timed out while waiting for the WebRTC connection."),
        )
        videoMonitorJob =
            observeVideoEncoderHealth(
                peer = peer,
                sender = localVideoSender,
                controller = adaptiveVideoProfileController,
            )
        return publishStartResult
    }

    private fun startAudioIfRequested(
        peer: PeerConnection,
        sessionId: String,
        mediaProjection: MediaProjection?,
    ): PublishStartResult {
        if (!config.audioEnabled) {
            return PublishStartResult(
                audioState = AudioState.Disabled,
                audioDetail = UiText.of(R.string.sender_status_audio_disabled),
            )
        }

        val bridge =
            audioBridge
                ?: return PublishStartResult(
                    audioState = AudioState.VideoOnlyFallback,
                    audioDetail = UiText.of(R.string.sender_status_audio_start_fallback),
                )

        if (mediaProjection == null) {
            logger.error(TAG, "ScreenCapturerAndroid did not expose a MediaProjection for audio capture reuse.", null)
            return PublishStartResult(
                audioState = AudioState.VideoOnlyFallback,
                audioDetail = UiText.of(R.string.sender_status_audio_start_fallback),
            )
        }

        eventFlow.tryEmit(RtcSessionEvent.Status(UiText.of(R.string.sender_status_preparing_system_audio)))

        var captureSession: PlaybackAudioCaptureSession? = null
        var localAudioSource: AudioSource? = null
        var localAudioTrack: AudioTrack? = null
        var localAudioSender: RtpSender? = null

        return runCatching {
            captureSession = playbackAudioCaptureSessionFactory.create(mediaProjection)
            bridge.attachAndStart(captureSession ?: error("missing capture session"))

            localAudioSource = runtime.peerConnectionFactory.createAudioSource(MediaConstraints())
            audioSource = localAudioSource
            localAudioTrack =
                runtime.peerConnectionFactory.createAudioTrack(
                    AUDIO_TRACK_ID,
                    localAudioSource,
                )
            audioTrack = localAudioTrack
            localAudioSender =
                peer.addTrack(localAudioTrack, listOf(sessionId))
                    ?: throw SenderException(SenderError.PeerConnectionFailed("Unable to create the WebRTC audio sender."))
            audioSender = localAudioSender

            PublishStartResult(
                audioState = AudioState.Capturing,
                audioDetail = UiText.of(R.string.sender_status_audio_capture_active),
            )
        }.getOrElse { throwable ->
            logger.error(TAG, "Starting system audio capture failed: ${throwable.message}", throwable)
            bridge.clearCaptureSession()
            runCatching { localAudioSender?.dispose() }
            runCatching { localAudioTrack?.dispose() }
            runCatching { localAudioSource?.dispose() }
            audioSender = null
            audioTrack = null
            audioSource = null
            PublishStartResult(
                audioState = AudioState.VideoOnlyFallback,
                audioDetail = UiText.of(R.string.sender_status_audio_start_fallback),
            )
        }
    }

    private fun observeVideoEncoderHealth(
        peer: PeerConnection,
        sender: RtpSender,
        controller: AdaptiveVideoProfileController,
    ): Job =
        scope.launch {
            var pollsSinceLastPerformanceLog = 0
            var accumulatedAudioWrittenBytes = 0L
            var accumulatedAudioDroppedBytes = 0L
            var accumulatedAudioUnderruns = 0
            while (!closed.get()) {
                delay(VIDEO_STATS_POLL_INTERVAL_MS)

                val previousProfile = controller.activeProfile()
                val statsSample =
                    runCatching {
                        peer.awaitVideoStats(sender)
                    }.onFailure { throwable ->
                        logger.error(TAG, "Reading WebRTC video stats failed: ${throwable.message}", throwable)
                    }.getOrNull()
                        ?: continue

                val decision = controller.evaluate(statsSample)
                val assessment = controller.latestAssessment()
                val audioSnapshot = audioBridge?.snapshotAndReset()
                if (audioSnapshot != null) {
                    accumulatedAudioWrittenBytes += audioSnapshot.writtenBytesSinceLastSnapshot
                    accumulatedAudioDroppedBytes += audioSnapshot.droppedBytesSinceLastSnapshot
                    accumulatedAudioUnderruns += audioSnapshot.underrunCallbacksSinceLastSnapshot
                }
                pollsSinceLastPerformanceLog += 1

                if (
                    assessment != null &&
                    shouldLogPerformanceSnapshot(
                        pollsSinceLastPerformanceLog = pollsSinceLastPerformanceLog,
                        assessment = assessment,
                        decision = decision,
                        audioSnapshot = audioSnapshot,
                    )
                ) {
                    logger.info(
                        TAG,
                        buildPerformanceLogMessage(
                            assessment = assessment,
                            activeProfile = controller.activeProfile(),
                            audioSnapshot = audioSnapshot,
                            accumulatedAudioWrittenBytes = accumulatedAudioWrittenBytes,
                            accumulatedAudioDroppedBytes = accumulatedAudioDroppedBytes,
                            accumulatedAudioUnderruns = accumulatedAudioUnderruns,
                            memorySnapshot = captureProcessMemorySnapshot(),
                        ),
                    )
                    pollsSinceLastPerformanceLog = 0
                    accumulatedAudioWrittenBytes = 0
                    accumulatedAudioDroppedBytes = 0
                    accumulatedAudioUnderruns = 0
                }

                when (decision) {
                    null -> Unit

                    is AdaptiveVideoProfileDecision.Downgrade -> {
                        logger.error(
                            TAG,
                            "Video encoder overload detected. Applying profile ${decision.profile.summaryLabel}. ${decision.assessment.reasonSummary}",
                            null,
                        )
                        applyVideoProfile(
                            peer = peer,
                            sender = sender,
                            previousProfile = previousProfile,
                            profile = decision.profile,
                        )
                        eventFlow.tryEmit(
                            RtcSessionEvent.VideoProfileChanged(
                                activeProfile = decision.profile,
                                detail =
                                    UiText.of(
                                        R.string.sender_status_video_profile_downgraded,
                                        decision.profile.resolution.label,
                                        decision.profile.fps,
                                        decision.profile.bitrateKbps,
                                    ),
                            ),
                        )
                    }

                    is AdaptiveVideoProfileDecision.Exhausted -> {
                        signalVideoEncoderOverloaded(
                            SenderError.VideoEncoderOverloaded(
                                "The video encoder remained overloaded at ${controller.activeProfile().summaryLabel}. ${decision.assessment.reasonSummary}",
                            ),
                        )
                        return@launch
                    }
                }
            }
        }

    private fun applyVideoProfile(
        peer: PeerConnection,
        sender: RtpSender,
        previousProfile: VideoStreamProfile,
        profile: VideoStreamProfile,
    ) {
        val currentVideoSource = videoSource
        val currentCapturer = screenCapturer
        if (currentVideoSource == null || currentCapturer == null) {
            return
        }

        if (previousProfile.resolution != profile.resolution) {
            currentCapturer.changeCaptureFormat(
                profile.resolution.width,
                profile.resolution.height,
                profile.fps,
            )
        }
        currentVideoSource.adaptOutputFormat(
            profile.resolution.width,
            profile.resolution.height,
            profile.fps,
        )
        applyVideoEncoderParameters(
            peer = peer,
            sender = sender,
            profile = profile,
        )
    }

    private fun applyVideoEncoderParameters(
        peer: PeerConnection,
        sender: RtpSender,
        profile: VideoStreamProfile,
    ) {
        val parameters = sender.parameters
        parameters.degradationPreference = RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION
        val encoding = parameters.encodings.firstOrNull()
        if (encoding == null) {
            logger.error(TAG, "The WebRTC video sender reported no encodings for ${profile.summaryLabel}.", null)
        } else {
            encoding.minBitrateBps = null
            encoding.maxBitrateBps = profile.bitrateKbps * 1000
            encoding.maxFramerate = profile.fps
            encoding.scaleResolutionDownBy = null
            val applied = sender.setParameters(parameters)
            if (!applied) {
                logger.error(TAG, "Applying WebRTC video sender parameters failed for ${profile.summaryLabel}.", null)
            }
        }

        val bitrateApplied = peer.setBitrate(null, profile.bitrateKbps * 1000, profile.bitrateKbps * 1000)
        if (!bitrateApplied) {
            logger.error(TAG, "Applying peer bitrate constraints failed for ${profile.summaryLabel}.", null)
        }
    }

    private fun createPeerConnection(sessionId: String): PeerConnection? {
        val rtcConfig =
            PeerConnection.RTCConfiguration(config.iceServers.toIceServers()).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                enableCpuOveruseDetection = true
            }

        return runtime.peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    if (newState == PeerConnection.IceConnectionState.FAILED) {
                        signalFailure(SenderError.PeerConnectionFailed("The ICE connection failed."))
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit

                override fun onIceCandidate(candidate: IceCandidate) {
                    if (closed.get()) {
                        return
                    }
                    scope.launch {
                        runCatching {
                            signalingSession.send(
                                SignalingMessage.IceCandidate(
                                    sessionId = sessionId,
                                    candidate = candidate.sdp,
                                    sdpMid = candidate.sdpMid ?: "0",
                                    sdpMLineIndex = candidate.sdpMLineIndex,
                                ),
                            )
                        }.onFailure { throwable ->
                            signalFailure(
                                SenderError.SignalingFailed(
                                    throwable.message ?: "Sending the local ICE candidate failed.",
                                ),
                            )
                        }
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

                override fun onAddStream(stream: org.webrtc.MediaStream) = Unit

                override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit

                override fun onDataChannel(dataChannel: org.webrtc.DataChannel) = Unit

                override fun onRenegotiationNeeded() = Unit

                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                    if (closed.get()) {
                        return
                    }
                    when (newState) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            eventFlow.tryEmit(RtcSessionEvent.Status(UiText.of(R.string.sender_status_connected_webrtc)))
                            peerConnected.complete(Unit)
                        }

                        PeerConnection.PeerConnectionState.DISCONNECTED,
                        PeerConnection.PeerConnectionState.CLOSED,
                        -> {
                            if (peerConnected.isCompleted) {
                                eventFlow.tryEmit(RtcSessionEvent.Disconnected)
                            } else {
                                signalFailure(
                                    SenderError.PeerConnectionFailed("The WebRTC connection disconnected before it was fully established."),
                                )
                            }
                        }

                        PeerConnection.PeerConnectionState.FAILED ->
                            signalFailure(SenderError.PeerConnectionFailed("The WebRTC connection failed."))

                        else -> Unit
                    }
                }
            },
        )
    }

    private fun observeIncomingSignaling(peer: PeerConnection): Job =
        scope.launch {
            signalingSession.messages.collect { message ->
                when (message) {
                    is SignalingMessage.Answer -> {
                        eventFlow.tryEmit(RtcSessionEvent.Status(UiText.of(R.string.sender_status_applying_remote_answer)))
                        peer.awaitSetRemoteDescription(
                            SessionDescription(
                                SessionDescription.Type.ANSWER,
                                message.sdp,
                            ),
                        )
                        remoteDescriptionReady.complete(Unit)
                        flushBufferedRemoteCandidates(peer)
                    }

                    is SignalingMessage.IceCandidate -> {
                        val candidate =
                            IceCandidate(
                                message.sdpMid,
                                message.sdpMLineIndex,
                                message.candidate,
                            )
                        if (remoteDescriptionReady.isCompleted) {
                            addRemoteIceCandidate(peer, candidate)
                        } else {
                            bufferedRemoteCandidates += candidate
                        }
                    }

                    is SignalingMessage.Error ->
                        signalFailure(SenderError.SignalingFailed(message.message ?: "The signaling server returned an error."))

                    is SignalingMessage.Leave -> {
                        if (peerConnected.isCompleted) {
                            eventFlow.tryEmit(RtcSessionEvent.Disconnected)
                        } else {
                            signalFailure(SenderError.SignalingFailed("The remote side ended the session."))
                        }
                    }

                    is SignalingMessage.Join,
                    is SignalingMessage.Offer,
                    -> Unit
                }
            }
        }

    private fun flushBufferedRemoteCandidates(peer: PeerConnection) {
        bufferedRemoteCandidates.forEach { candidate ->
            addRemoteIceCandidate(peer, candidate)
        }
        bufferedRemoteCandidates.clear()
    }

    private fun addRemoteIceCandidate(
        peer: PeerConnection,
        candidate: IceCandidate,
    ) {
        val added = peer.addIceCandidate(candidate)
        if (!added) {
            signalFailure(SenderError.PeerConnectionFailed("Applying the remote ICE candidate failed."))
        }
    }

    private fun signalFailure(
        error: SenderError,
        interruptCapture: Boolean = false,
    ) {
        if (!terminalError.complete(error)) {
            return
        }

        if (interruptCapture) {
            eventFlow.tryEmit(RtcSessionEvent.CaptureInterrupted(error.message))
        } else {
            eventFlow.tryEmit(RtcSessionEvent.Failure(error))
        }
    }

    private fun signalVideoEncoderOverloaded(error: SenderError.VideoEncoderOverloaded) {
        if (!terminalError.complete(error)) {
            return
        }
        eventFlow.tryEmit(RtcSessionEvent.VideoEncoderOverloaded(error))
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        signalingJob?.cancel()
        videoMonitorJob?.cancel()
        runBlocking {
            runCatching {
                signalingSession.send(SignalingMessage.Leave(config.trimmedSessionId))
            }
        }
        runCatching {
            audioBridge?.close()
        }
        runCatching {
            screenCapturer?.stopCapture()
        }
        runCatching {
            screenCapturer?.dispose()
        }
        runCatching {
            audioTrack?.dispose()
        }
        runCatching {
            audioSender?.dispose()
        }
        runCatching {
            audioSource?.dispose()
        }
        runCatching {
            videoTrack?.dispose()
        }
        runCatching {
            videoSender?.dispose()
        }
        runCatching {
            videoSource?.dispose()
        }
        runCatching {
            surfaceTextureHelper?.dispose()
        }
        runCatching {
            peerConnection?.close()
        }
        runCatching {
            peerConnection?.dispose()
        }
        runCatching {
            runtime.close()
        }
        runCatching {
            signalingSession.close()
        }
    }

    private suspend fun awaitSuccessOrFailure(
        success: CompletableDeferred<Unit>,
        failure: CompletableDeferred<SenderError>,
        timeoutMs: Long,
        timeoutError: SenderError,
    ) {
        try {
            withTimeout(timeoutMs) {
                select<Unit> {
                    success.onAwait { }
                    failure.onAwait { error ->
                        throw SenderException(error)
                    }
                }
            }
        } catch (throwable: Throwable) {
            when (throwable) {
                is SenderException -> throw throwable
                else -> throw SenderException(timeoutError)
            }
        }
    }

    private companion object {
        const val TAG = "WebRtcPublisher"
        const val VIDEO_TRACK_ID = "relavr-video-track"
        const val AUDIO_TRACK_ID = "relavr-audio-track"
        const val ANSWER_TIMEOUT_MS = 15_000L
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val VIDEO_STATS_POLL_INTERVAL_MS = 250L
    }
}

private class WebRtcRuntime(
    val appContext: Context,
    private val libraryInitializer: WebRtcLibraryInitializer,
    private val audioBridge: WebRtcPlaybackAudioBridge?,
) : Closeable {
    val eglBase: EglBase = EglBase.create()

    private var audioDeviceModule: JavaAudioDeviceModule? = null

    val peerConnectionFactory: PeerConnectionFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        libraryInitializer.ensureInitialized()
        val builder =
            PeerConnectionFactory
                .builder()
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(
                        eglBase.eglBaseContext,
                        true,
                        true,
                    ),
                ).setVideoDecoderFactory(
                    DefaultVideoDecoderFactory(eglBase.eglBaseContext),
                )
        if (audioBridge != null) {
            val javaAudioDeviceModule =
                JavaAudioDeviceModule
                    .builder(appContext)
                    .setInputSampleRate(WebRtcPlaybackAudioBridge.OUTPUT_SAMPLE_RATE_HZ)
                    .setOutputSampleRate(WebRtcPlaybackAudioBridge.OUTPUT_SAMPLE_RATE_HZ)
                    .setUseStereoInput(true)
                    .setUseStereoOutput(false)
                    .setEnableVolumeLogger(false)
                    .setAudioBufferCallback(audioBridge)
                    .createAudioDeviceModule()
            javaAudioDeviceModule.setAudioRecordEnabled(false)
            javaAudioDeviceModule.setMicrophoneMute(true)
            audioDeviceModule = javaAudioDeviceModule
            builder.setAudioDeviceModule(javaAudioDeviceModule)
        }
        builder.createPeerConnectionFactory()
    }

    override fun close() {
        runCatching {
            peerConnectionFactory.dispose()
        }
        runCatching {
            audioDeviceModule?.release()
        }
        runCatching {
            eglBase.release()
        }
    }
}

private class WebRtcPlaybackAudioBridge(
    private val eventFlow: MutableSharedFlow<RtcSessionEvent>,
    private val logger: AppLogger,
) : JavaAudioDeviceModule.AudioBufferCallback,
    Closeable {
    private val lock = Any()
    private val callbackBuffer = ByteArray(OUTPUT_BYTES_PER_10MS)
    private val stereoReadBuffer = ByteArray(OUTPUT_BYTES_PER_10MS)
    private val monoReadBuffer = ByteArray(MONO_BYTES_PER_10MS)
    private val monoToStereoBuffer = ByteArray(OUTPUT_BYTES_PER_10MS)
    private val ringBuffer = BoundedPcmRingBuffer(OUTPUT_BYTES_PER_10MS * MAX_BUFFERED_FRAMES)
    private val captureDispatcher: ExecutorCoroutineDispatcher =
        Executors
            .newSingleThreadExecutor { runnable ->
                Thread(runnable, "RelavrAudioCapture")
            }.asCoroutineDispatcher()
    private val captureScope = CoroutineScope(SupervisorJob() + captureDispatcher)
    private var captureSession: PlaybackAudioCaptureSession? = null
    private var captureJob: Job? = null
    private var activeCaptureFormat: AudioCaptureFormat? = null
    private var underrunCallbacksSinceLastSnapshot: Int = 0
    private val degraded = AtomicBoolean(false)

    fun attachAndStart(session: PlaybackAudioCaptureSession) {
        clearCaptureSession()
        degraded.set(false)
        captureSession = session
        activeCaptureFormat = session.format
        session.start()
        captureJob =
            captureScope.launch {
                readLoop(session)
            }
    }

    fun clearCaptureSession() {
        captureJob?.cancel()
        captureJob = null
        val session = captureSession
        captureSession = null
        activeCaptureFormat = null
        runCatching {
            session?.close()
        }
        clearBuffer()
    }

    override fun onBuffer(
        byteBuffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        bytesRead: Int,
        captureTimeNs: Long,
    ): Long {
        val targetSize = minOf(byteBuffer.capacity(), callbackBuffer.size)
        synchronized(lock) {
            val copiedBytes = ringBuffer.read(callbackBuffer, targetSize)
            if (copiedBytes < targetSize) {
                underrunCallbacksSinceLastSnapshot += 1
                callbackBuffer.fill(0, copiedBytes, targetSize)
            }
        }
        byteBuffer.clear()
        byteBuffer.put(callbackBuffer, 0, targetSize)
        return System.nanoTime()
    }

    fun snapshotAndReset(): AudioBridgePerformanceSnapshot =
        synchronized(lock) {
            val ringSnapshot = ringBuffer.snapshotAndReset()
            val snapshot =
                AudioBridgePerformanceSnapshot(
                    captureFormat = activeCaptureFormat,
                    bufferedBytes = ringSnapshot.bufferedBytes,
                    writtenBytesSinceLastSnapshot = ringSnapshot.writtenBytesSinceLastSnapshot,
                    droppedBytesSinceLastSnapshot = ringSnapshot.droppedBytesSinceLastSnapshot,
                    underrunCallbacksSinceLastSnapshot = underrunCallbacksSinceLastSnapshot,
                )
            underrunCallbacksSinceLastSnapshot = 0
            snapshot
        }

    override fun close() {
        clearCaptureSession()
        captureScope.cancel()
        captureDispatcher.close()
    }

    private suspend fun readLoop(session: PlaybackAudioCaptureSession) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val captureFormat = session.format
        val readBuffer =
            if (captureFormat.channelCount == 2) {
                stereoReadBuffer
            } else {
                monoReadBuffer
            }

        try {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                val bytesRead = session.read(readBuffer, 0, readBuffer.size)
                when {
                    bytesRead > 0 -> {
                        if (captureFormat.channelCount == 2) {
                            writeToRing(readBuffer, bytesRead)
                        } else {
                            val stereoBytes = duplicateMonoSamples(readBuffer, bytesRead)
                            writeToRing(monoToStereoBuffer, stereoBytes)
                        }
                    }

                    bytesRead == 0 -> Unit

                    else ->
                        throw IllegalStateException("AudioPlaybackCapture read failed with code $bytesRead.")
                }
            }
        } catch (throwable: Throwable) {
            if (throwable is kotlinx.coroutines.CancellationException) {
                return
            }
            if (kotlinx.coroutines.currentCoroutineContext().isActive) {
                handleRuntimeFailure(throwable)
            }
        }
    }

    private fun handleRuntimeFailure(throwable: Throwable) {
        clearCaptureSession()
        if (!degraded.compareAndSet(false, true)) {
            logger.error(TAG, "System audio capture degraded again: ${throwable.message}", throwable)
            return
        }
        logger.error(TAG, "System audio capture degraded: ${throwable.message}", throwable)
        eventFlow.tryEmit(
            RtcSessionEvent.AudioDegraded(
                detail = UiText.of(R.string.sender_status_audio_runtime_degraded),
                reason = throwable.message ?: "The system audio capture loop failed.",
            ),
        )
    }

    private fun duplicateMonoSamples(
        source: ByteArray,
        bytesRead: Int,
    ): Int {
        val safeBytes = bytesRead - (bytesRead % BYTES_PER_SAMPLE)
        var destinationIndex = 0
        var sourceIndex = 0
        while (sourceIndex < safeBytes) {
            val lowByte = source[sourceIndex]
            val highByte = source[sourceIndex + 1]
            monoToStereoBuffer[destinationIndex] = lowByte
            monoToStereoBuffer[destinationIndex + 1] = highByte
            monoToStereoBuffer[destinationIndex + 2] = lowByte
            monoToStereoBuffer[destinationIndex + 3] = highByte
            sourceIndex += BYTES_PER_SAMPLE
            destinationIndex += OUTPUT_BYTES_PER_SAMPLE_FRAME
        }
        return destinationIndex
    }

    private fun writeToRing(
        source: ByteArray,
        length: Int,
    ) {
        if (length <= 0) {
            return
        }
        synchronized(lock) {
            ringBuffer.write(source, length)
        }
    }

    private fun clearBuffer() {
        synchronized(lock) {
            ringBuffer.clear()
            underrunCallbacksSinceLastSnapshot = 0
        }
    }

    companion object {
        const val TAG = "WebRtcAudioBridge"
        const val OUTPUT_SAMPLE_RATE_HZ = 48_000
        const val OUTPUT_CHANNEL_COUNT = 2
        const val BYTES_PER_SAMPLE = 2
        const val OUTPUT_BYTES_PER_SAMPLE_FRAME = OUTPUT_CHANNEL_COUNT * BYTES_PER_SAMPLE
        const val OUTPUT_BYTES_PER_10MS = OUTPUT_SAMPLE_RATE_HZ / 100 * OUTPUT_BYTES_PER_SAMPLE_FRAME
        const val MONO_BYTES_PER_10MS = OUTPUT_SAMPLE_RATE_HZ / 100 * BYTES_PER_SAMPLE
        const val MAX_BUFFERED_FRAMES = 4
    }
}

private data class AudioBridgePerformanceSnapshot(
    val captureFormat: AudioCaptureFormat?,
    val bufferedBytes: Int,
    val writtenBytesSinceLastSnapshot: Long,
    val droppedBytesSinceLastSnapshot: Long,
    val underrunCallbacksSinceLastSnapshot: Int,
) {
    val hasPressure: Boolean =
        droppedBytesSinceLastSnapshot > 0L ||
            underrunCallbacksSinceLastSnapshot > 0 ||
            bufferedBytes >= WebRtcPlaybackAudioBridge.OUTPUT_BYTES_PER_10MS * 3
}

private data class ProcessMemorySnapshot(
    val javaHeapUsedBytes: Long,
    val javaHeapMaxBytes: Long,
    val nativeHeapAllocatedBytes: Long,
    val nativeHeapFreeBytes: Long,
    val totalPssKb: Int,
)

private fun shouldLogPerformanceSnapshot(
    pollsSinceLastPerformanceLog: Int,
    assessment: VideoEncoderAssessment,
    decision: AdaptiveVideoProfileDecision?,
    audioSnapshot: AudioBridgePerformanceSnapshot?,
): Boolean =
    pollsSinceLastPerformanceLog >= PERFORMANCE_LOG_WINDOWS ||
        assessment.overloaded ||
        decision != null ||
        audioSnapshot?.hasPressure == true

private fun buildPerformanceLogMessage(
    assessment: VideoEncoderAssessment,
    activeProfile: VideoStreamProfile,
    audioSnapshot: AudioBridgePerformanceSnapshot?,
    accumulatedAudioWrittenBytes: Long,
    accumulatedAudioDroppedBytes: Long,
    accumulatedAudioUnderruns: Int,
    memorySnapshot: ProcessMemorySnapshot,
): String =
    buildString {
        append("perf")
        append(" activeProfile=")
        append(activeProfile.summaryLabel)
        append(" overloaded=")
        append(assessment.overloaded)
        append(" encodedFps=")
        append(String.format("%.2f", assessment.encodedFps))
        append(" reportedFps=")
        append(assessment.reportedFps?.let { value -> String.format("%.2f", value) } ?: "n/a")
        append(" qualityLimitationReason=")
        append(assessment.qualityLimitationReason ?: "n/a")
        append(" audioBufferedMs=")
        append(audioSnapshot?.bufferedBytes?.toAudioMilliseconds() ?: 0)
        append(" audioWrittenMs=")
        append(accumulatedAudioWrittenBytes.toAudioMilliseconds())
        append(" audioDroppedMs=")
        append(accumulatedAudioDroppedBytes.toAudioMilliseconds())
        append(" audioUnderruns=")
        append(accumulatedAudioUnderruns)
        append(" audioFormat=")
        append(
            audioSnapshot?.captureFormat?.let { format ->
                "${format.sampleRateHz}/${format.channelCount}ch"
            } ?: "disabled",
        )
        append(" javaHeapMb=")
        append(memorySnapshot.javaHeapUsedBytes.toMegabytes())
        append("/")
        append(memorySnapshot.javaHeapMaxBytes.toMegabytes())
        append(" nativeHeapMb=")
        append(memorySnapshot.nativeHeapAllocatedBytes.toMegabytes())
        append(" nativeHeapFreeMb=")
        append(memorySnapshot.nativeHeapFreeBytes.toMegabytes())
        append(" totalPssMb=")
        append(memorySnapshot.totalPssKb / 1024)
        append(" assessment=")
        append(assessment.reasonSummary)
    }

private fun captureProcessMemorySnapshot(): ProcessMemorySnapshot {
    val runtime = Runtime.getRuntime()
    val memoryInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(memoryInfo)
    return ProcessMemorySnapshot(
        javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
        javaHeapMaxBytes = runtime.maxMemory(),
        nativeHeapAllocatedBytes = Debug.getNativeHeapAllocatedSize(),
        nativeHeapFreeBytes = Debug.getNativeHeapFreeSize(),
        totalPssKb = memoryInfo.totalPss,
    )
}

private fun Int.toAudioMilliseconds(): Int = ((toDouble() / WebRtcPlaybackAudioBridge.OUTPUT_BYTES_PER_10MS) * 10.0).toInt()

private fun Long.toAudioMilliseconds(): Long = ((toDouble() / WebRtcPlaybackAudioBridge.OUTPUT_BYTES_PER_10MS) * 10.0).toLong()

private fun Long.toMegabytes(): Long = this / (1024L * 1024L)

private const val PERFORMANCE_LOG_WINDOWS = 4

private suspend fun PeerConnection.awaitVideoStats(sender: RtpSender): VideoEncoderStatsSample? =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        getStats(
            sender,
            object : org.webrtc.RTCStatsCollectorCallback {
                override fun onStatsDelivered(report: RTCStatsReport) {
                    if (continuation.isActive) {
                        continuation.resume(report.toVideoEncoderStatsSample())
                    }
                }
            },
        )
    }

private suspend fun PeerConnection.awaitCreateOffer(): SessionDescription {
    val constraints =
        MediaConstraints().apply {
            mandatory += MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false")
            mandatory += MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false")
        }
    return awaitCreateDescription { observer ->
        createOffer(observer, constraints)
    }
}

private suspend fun PeerConnection.awaitSetLocalDescription(description: SessionDescription) {
    awaitSetDescription { observer ->
        setLocalDescription(observer, description)
    }
}

private suspend fun PeerConnection.awaitSetRemoteDescription(description: SessionDescription) {
    awaitSetDescription { observer ->
        setRemoteDescription(observer, description)
    }
}

private suspend fun PeerConnection.awaitCreateDescription(createBlock: (org.webrtc.SdpObserver) -> Unit): SessionDescription =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        createBlock(
            object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) {
                    if (continuation.isActive) {
                        continuation.resume(description)
                    }
                }

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String) {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(
                                SenderException(
                                    SenderError.PeerConnectionFailed(error.ifBlank { "Creating the SDP failed." }),
                                ),
                            ),
                        )
                    }
                }

                override fun onSetFailure(error: String) = Unit
            },
        )
    }

private suspend fun PeerConnection.awaitSetDescription(setBlock: (org.webrtc.SdpObserver) -> Unit) {
    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
        setBlock(
            object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) = Unit

                override fun onSetSuccess() {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }

                override fun onCreateFailure(error: String) = Unit

                override fun onSetFailure(error: String) {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(
                                SenderException(
                                    SenderError.PeerConnectionFailed(error.ifBlank { "Applying the SDP failed." }),
                                ),
                            ),
                        )
                    }
                }
            },
        )
    }
}

private fun List<String>.toIceServers(): List<PeerConnection.IceServer> =
    map { url ->
        PeerConnection.IceServer.builder(url).createIceServer()
    }

private fun RTCStatsReport.toVideoEncoderStatsSample(): VideoEncoderStatsSample? {
    val outboundVideoStat =
        statsMap.values.firstOrNull { stats ->
            stats.type == "outbound-rtp" && stats.isVideoStat()
        } ?: return null
    val members = outboundVideoStat.members
    return VideoEncoderStatsSample(
        timestampUs = outboundVideoStat.timestampUs.toLong(),
        framesEncoded = members["framesEncoded"].asLong(),
        framesPerSecond = members["framesPerSecond"].asDouble(),
        qualityLimitationReason = members["qualityLimitationReason"].asString(),
    )
}

private fun org.webrtc.RTCStats.isVideoStat(): Boolean {
    val kind = members["kind"].asString()
    val mediaType = members["mediaType"].asString()
    return kind.equals("video", ignoreCase = true) || mediaType.equals("video", ignoreCase = true)
}

private fun Any?.asLong(): Long? =
    when (this) {
        is Long -> this
        is Int -> toLong()
        is Double -> toLong()
        is Float -> toLong()
        is Number -> toLong()
        is String -> toLongOrNull()
        else -> null
    }

private fun Any?.asDouble(): Double? =
    when (this) {
        is Double -> this
        is Float -> toDouble()
        is Long -> toDouble()
        is Int -> toDouble()
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }

private fun Any?.asString(): String? =
    when (this) {
        is String -> this
        else -> null
    }
