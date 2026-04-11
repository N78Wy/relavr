package io.relavr.sender.platform.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
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
            logger = logger,
        )
}

private class WebRtcPublishSession(
    private val appContext: Context,
    private val config: StreamConfig,
    private val signalingSession: SignalingSession,
    private val logger: AppLogger,
) : RtcPublishSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventFlow = MutableSharedFlow<RtcSessionEvent>(extraBufferCapacity = 32)
    private val audioBridge =
        PlaybackAudioBufferBridge { detail ->
            eventFlow.tryEmit(RtcSessionEvent.AudioDegraded(detail))
        }
    private val runtime = WebRtcRuntime(appContext, audioBridge)
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
                ?: throw SenderException(SenderError.SessionStartFailed("缺少有效的 MediaProjection 授权结果"))

        val sessionId = config.trimmedSessionId
        val peer =
            createPeerConnection(sessionId)
                ?: throw SenderException(SenderError.PeerConnectionFailed("无法创建 PeerConnection"))
        peerConnection = peer

        signalingJob = observeIncomingSignaling(peer)

        eventFlow.tryEmit(RtcSessionEvent.Status("已连接信令服务器"))
        signalingSession.send(SignalingMessage.Join(sessionId = sessionId))
        eventFlow.tryEmit(RtcSessionEvent.Status("已发送会话加入请求"))

        val capturer =
            ScreenCapturerAndroid(
                Intent(permission.resultData),
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        if (closed.get()) {
                            return
                        }
                        signalFailure(
                            SenderError.CaptureInterrupted("屏幕采集已被系统停止"),
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

        eventFlow.tryEmit(RtcSessionEvent.Status("正在创建 WebRTC Offer"))
        val rawOffer = peer.awaitCreateOffer()
        val preferredOffer =
            SessionDescription(
                rawOffer.type,
                if (config.codecPreference == CodecPreference.H264) {
                    SdpCodecPreference.preferVideoCodec(rawOffer.description, "H264")
                } else {
                    rawOffer.description
                },
            )
        peer.awaitSetLocalDescription(preferredOffer)
        signalingSession.send(
            SignalingMessage.Offer(
                sessionId = sessionId,
                sdp = preferredOffer.description,
            ),
        )
        eventFlow.tryEmit(RtcSessionEvent.Status("已发送 Offer，等待 Answer"))

        awaitSuccessOrFailure(
            success = remoteDescriptionReady,
            failure = terminalError,
            timeoutMs = ANSWER_TIMEOUT_MS,
            timeoutError = SenderError.SignalingFailed("等待远端 Answer 超时"),
        )

        eventFlow.tryEmit(RtcSessionEvent.Status("ICE 连接建立中"))
        awaitSuccessOrFailure(
            success = peerConnected,
            failure = terminalError,
            timeoutMs = CONNECT_TIMEOUT_MS,
            timeoutError = SenderError.PeerConnectionFailed("等待 WebRTC 连接建立超时"),
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
                    ?: throw SenderException(SenderError.SessionStartFailed("屏幕采集尚未就绪，无法启动音频采集"))
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
                "初始化 WebRTC 音轨失败，已降级为仅视频推流: ${throwable.message}",
                throwable,
            )
            PublishStartResult(
                audioState = AudioStreamState.Degraded,
                audioDetail = throwable.message ?: "音频已降级为静音/仅视频",
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
                        signalFailure(SenderError.PeerConnectionFailed("ICE 连接失败"))
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
                                    throwable.message ?: "发送本地 ICE Candidate 失败",
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
                            eventFlow.tryEmit(RtcSessionEvent.Status("WebRTC 已连接"))
                            peerConnected.complete(Unit)
                        }

                        PeerConnection.PeerConnectionState.DISCONNECTED,
                        PeerConnection.PeerConnectionState.CLOSED,
                        -> {
                            if (peerConnected.isCompleted) {
                                eventFlow.tryEmit(RtcSessionEvent.Disconnected)
                            } else {
                                signalFailure(SenderError.PeerConnectionFailed("WebRTC 连接在建连阶段断开"))
                            }
                        }

                        PeerConnection.PeerConnectionState.FAILED ->
                            signalFailure(SenderError.PeerConnectionFailed("WebRTC 连接失败"))

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
                        eventFlow.tryEmit(RtcSessionEvent.Status("正在应用远端 Answer"))
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
                        signalFailure(SenderError.SignalingFailed(message.message))

                    is SignalingMessage.Leave -> {
                        if (peerConnected.isCompleted) {
                            eventFlow.tryEmit(RtcSessionEvent.Disconnected)
                        } else {
                            signalFailure(SenderError.SignalingFailed("远端已结束会话"))
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
            signalFailure(SenderError.PeerConnectionFailed("应用远端 ICE Candidate 失败"))
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
        initializePeerConnectionFactory(appContext)
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

    private fun initializePeerConnectionFactory(context: Context) {
        if (initialized.compareAndSet(false, true)) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(context)
                    .createInitializationOptions(),
            )
        }
    }

    private companion object {
        const val AUDIO_SAMPLE_RATE_HZ = 48_000
        val initialized = AtomicBoolean(false)
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
                                    SenderError.PeerConnectionFailed(error.ifBlank { "创建 SDP 失败" }),
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
                                    SenderError.PeerConnectionFailed(error.ifBlank { "设置 SDP 失败" }),
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
