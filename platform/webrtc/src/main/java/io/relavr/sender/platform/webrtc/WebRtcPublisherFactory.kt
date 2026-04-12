package io.relavr.sender.platform.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.R
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.session.AudioCaptureSource
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class WebRtcPublisherFactory(
    private val appContext: Context,
    private val libraryInitializer: WebRtcLibraryInitializer,
    private val logger: AppLogger,
) : RtcPublisherFactory {
    override suspend fun createSession(
        config: StreamConfig,
        signalingSession: SignalingSession,
    ): RtcPublishSession =
        WebRtcPublishSession(
            appContext = appContext.applicationContext,
            config = config,
            signalingSession = signalingSession,
            libraryInitializer = libraryInitializer,
            logger = logger,
        )
}

private class WebRtcPublishSession(
    private val appContext: Context,
    private val config: StreamConfig,
    private val signalingSession: SignalingSession,
    private val libraryInitializer: WebRtcLibraryInitializer,
    private val logger: AppLogger,
) : RtcPublishSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventFlow = MutableSharedFlow<RtcSessionEvent>(extraBufferCapacity = 32)
    private val audioBridge =
        PlaybackAudioBufferBridge { detail ->
            eventFlow.tryEmit(RtcSessionEvent.AudioDegraded(detail))
        }
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
    private var webrtcAudioSource: AudioSource? = null
    private var webrtcAudioTrack: AudioTrack? = null
    private var signalingJob: Job? = null

    override val events: Flow<RtcSessionEvent> = eventFlow.asSharedFlow()

    override suspend fun publish(
        projectionAccess: ProjectionAccess,
        audioSource: AudioCaptureSource?,
    ): PublishStartResult {
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
        peer.addTrack(localVideoTrack, listOf(sessionId))

        val audioPublishResult = attachAudioTrack(peer, sessionId, capturer, audioSource)
        peer.setBitrate(null, config.bitrateKbps * 1000, config.bitrateKbps * 1000)

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
        return audioPublishResult
    }

    private fun attachAudioTrack(
        peer: PeerConnection,
        sessionId: String,
        capturer: ScreenCapturerAndroid,
        audioSource: AudioCaptureSource?,
    ): PublishStartResult {
        if (audioSource == null) {
            audioBridge.attachSource(null)
            return PublishStartResult(audioState = AudioStreamState.Disabled)
        }

        return runCatching {
            val mediaProjection =
                capturer.mediaProjection
                    ?: throw SenderException(SenderError.SessionStartFailed("Screen capture is not ready, so audio capture cannot start."))
            audioSource.start(mediaProjection)
            audioBridge.attachSource(audioSource)

            val localAudioSource =
                runtime.peerConnectionFactory.createAudioSource(MediaConstraints())
            webrtcAudioSource = localAudioSource
            val localAudioTrack =
                runtime.peerConnectionFactory.createAudioTrack(
                    AUDIO_TRACK_ID,
                    localAudioSource,
                )
            webrtcAudioTrack = localAudioTrack
            peer.addTrack(localAudioTrack, listOf(sessionId))
            PublishStartResult(audioState = AudioStreamState.Publishing)
        }.getOrElse { throwable ->
            audioBridge.attachSource(null)
            runCatching {
                audioSource.close()
            }
            logger.error(
                TAG,
                "Failed to initialize the WebRTC audio track. Falling back to video-only streaming: ${throwable.message}",
                throwable,
            )
            PublishStartResult(
                audioState = AudioStreamState.Degraded,
                audioDetail = UiText.of(R.string.sender_audio_degraded_video_only),
            )
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

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        audioBridge.attachSource(null)
        signalingJob?.cancel()
        runBlocking {
            runCatching {
                signalingSession.send(SignalingMessage.Leave(config.trimmedSessionId))
            }
        }
        runCatching {
            screenCapturer?.stopCapture()
        }
        runCatching {
            screenCapturer?.dispose()
        }
        runCatching {
            webrtcAudioTrack?.dispose()
        }
        runCatching {
            webrtcAudioSource?.dispose()
        }
        runCatching {
            videoTrack?.dispose()
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
    }
}

private class WebRtcRuntime(
    val appContext: Context,
    private val libraryInitializer: WebRtcLibraryInitializer,
    audioBridge: PlaybackAudioBufferBridge,
) : Closeable {
    val eglBase: EglBase = EglBase.create()
    private val audioDeviceModule: JavaAudioDeviceModule =
        JavaAudioDeviceModule
            .builder(appContext)
            .setInputSampleRate(AUDIO_SAMPLE_RATE_HZ)
            .setUseStereoInput(true)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .setEnableVolumeLogger(false)
            .setAudioBufferCallback(audioBridge)
            .createAudioDeviceModule()
            .also { module ->
                module.setAudioRecordEnabled(false)
            }

    val peerConnectionFactory: PeerConnectionFactory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        libraryInitializer.ensureInitialized()
        PeerConnectionFactory
            .builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    true,
                    true,
                ),
            ).setVideoDecoderFactory(
                DefaultVideoDecoderFactory(eglBase.eglBaseContext),
            ).createPeerConnectionFactory()
    }

    override fun close() {
        runCatching {
            peerConnectionFactory.dispose()
        }
        runCatching {
            audioDeviceModule.release()
        }
        runCatching {
            eglBase.release()
        }
    }

    private companion object {
        const val AUDIO_SAMPLE_RATE_HZ = 48_000
    }
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
