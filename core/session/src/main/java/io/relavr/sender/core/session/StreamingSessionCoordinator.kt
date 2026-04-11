package io.relavr.sender.core.session

import io.relavr.sender.core.common.AppDispatchers
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

class StreamingSessionCoordinator(
    private val projectionPermissionGateway: ProjectionPermissionGateway,
    private val audioCaptureSourceFactory: AudioCaptureSourceFactory,
    private val codecCapabilityRepository: CodecCapabilityRepository,
    private val codecPolicy: CodecPolicy,
    private val rtcPublisherFactory: RtcPublisherFactory,
    private val signalingClient: SignalingClient,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : StreamingSessionController {
    private val state = MutableStateFlow(StreamingSessionSnapshot())
    private val sessionMutex = Mutex()
    private val sessionScope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private var activeSession: ActiveSession? = null
    private var activeSessionToken: Any? = null

    override suspend fun refreshCapabilities(): CapabilitySnapshot =
        withContext(dispatchers.io) {
            val capabilities = codecCapabilityRepository.getCapabilities()
            state.update { current ->
                current.copy(capabilities = capabilities)
            }
            capabilities
        }

    override suspend fun start(config: StreamConfig) {
        sessionMutex.withLock {
            if (activeSession != null || !config.videoEnabled) {
                return
            }

            val configError = config.validationError()
            if (configError != null) {
                handleFailure(
                    error = configError,
                    throwable = null,
                    operation = "推流配置校验失败",
                )
                return
            }

            state.update {
                it.copy(
                    captureState = CaptureState.RequestingPermission,
                    publishState = PublishState.Preparing,
                    audioState =
                        if (config.audioEnabled) {
                            AudioStreamState.Starting
                        } else {
                            AudioStreamState.Disabled
                        },
                    audioDetail = null,
                    statusDetail = "正在等待 MediaProjection 系统授权",
                    error = null,
                )
            }

            val projectionAccess =
                try {
                    projectionPermissionGateway.restoreIfAvailable()
                        ?: projectionPermissionGateway.requestPermission()
                } catch (throwable: Throwable) {
                    handleFailure(
                        error = mapError(throwable),
                        throwable = throwable,
                        operation = "请求投屏权限失败",
                    )
                    return
                }

            val resources = mutableListOf<Closeable>(projectionAccess)
            val sessionToken = Any()
            activeSessionToken = sessionToken
            var monitorJob: Job? = null

            try {
                state.update {
                    it.copy(
                        captureState = CaptureState.Starting,
                        publishState = PublishState.Preparing,
                        statusDetail = "正在探测设备编码能力",
                    )
                }

                val capabilities =
                    withContext(dispatchers.io) {
                        codecCapabilityRepository.getCapabilities()
                    }
                val selection = codecPolicy.select(config.codecPreference, capabilities)
                val resolvedConfig = config.copy(codecPreference = selection.resolved)
                var audioState =
                    if (resolvedConfig.audioEnabled) {
                        AudioStreamState.Starting
                    } else {
                        AudioStreamState.Disabled
                    }
                var audioDetail: String? = null

                val audioSource =
                    if (resolvedConfig.audioEnabled) {
                        runCatching {
                            withContext(dispatchers.io) {
                                audioCaptureSourceFactory.create(projectionAccess, resolvedConfig)
                            }
                        }.onFailure { throwable ->
                            val mappedError = mapError(throwable)
                            if (mappedError !is SenderError.AudioCaptureUnavailable) {
                                throw throwable
                            }
                            audioState = AudioStreamState.Degraded
                            audioDetail = mappedError.message
                            logger.error(
                                TAG,
                                "音频采集初始化失败，已降级为仅视频推流: ${mappedError.message}",
                                throwable,
                            )
                        }.getOrNull()
                            ?.also { resources += it }
                    } else {
                        null
                    }

                state.update {
                    it.copy(
                        audioState = audioState,
                        audioDetail = audioDetail,
                    )
                }

                state.update {
                    it.copy(statusDetail = "正在连接信令服务器")
                }
                val signalingSession =
                    withContext(dispatchers.io) {
                        signalingClient.open(resolvedConfig)
                    }
                resources += signalingSession

                val publishSession =
                    withContext(dispatchers.io) {
                        rtcPublisherFactory.createSession(resolvedConfig, signalingSession)
                    }
                resources += publishSession

                monitorJob = observeRtcEvents(sessionToken, publishSession)

                state.update {
                    it.copy(statusDetail = "正在准备 WebRTC 视频轨道")
                }
                val publishResult =
                    withContext(dispatchers.io) {
                        publishSession.publish(projectionAccess, audioSource)
                    }
                val finalAudioState =
                    if (audioState == AudioStreamState.Degraded) {
                        AudioStreamState.Degraded
                    } else {
                        publishResult.audioState
                    }
                val finalAudioDetail = audioDetail ?: publishResult.audioDetail

                activeSession =
                    ActiveSession(
                        token = sessionToken,
                        projectionAccess = projectionAccess,
                        audioSource = audioSource,
                        signalingSession = signalingSession,
                        publishSession = publishSession,
                        monitorJob = monitorJob,
                    )

                state.update {
                    it.copy(
                        captureState = CaptureState.Capturing,
                        publishState = PublishState.Publishing,
                        audioState = finalAudioState,
                        audioDetail = finalAudioDetail,
                        resolvedConfig = resolvedConfig,
                        capabilities = capabilities,
                        codecSelection = selection,
                        statusDetail =
                            if (finalAudioState == AudioStreamState.Publishing) {
                                "WebRTC 已连接，正在发送音视频"
                            } else {
                                "WebRTC 已连接，正在发送视频"
                            },
                        error = null,
                    )
                }
            } catch (throwable: Throwable) {
                monitorJob?.cancel()
                activeSessionToken = null
                resources.closeAllQuietly()
                handleFailure(
                    error = mapError(throwable),
                    throwable = throwable,
                    operation = "启动推流会话失败",
                )
            }
        }
    }

    override suspend fun stop() {
        sessionMutex.withLock {
            val currentSession =
                activeSession ?: run {
                    activeSessionToken = null
                    state.update {
                        it.copy(
                            captureState = CaptureState.Idle,
                            publishState = PublishState.Idle,
                            audioState = AudioStreamState.Disabled,
                            audioDetail = null,
                            resolvedConfig = null,
                            codecSelection = null,
                            statusDetail = null,
                            error = null,
                        )
                    }
                    return
                }

            state.update {
                it.copy(
                    captureState = CaptureState.Stopping,
                    publishState = PublishState.Stopping,
                    statusDetail = "正在释放推流资源",
                    error = null,
                )
            }

            try {
                currentSession.monitorJob.cancel()
                activeSessionToken = null
                listOfNotNull(
                    currentSession.publishSession,
                    currentSession.audioSource,
                    currentSession.signalingSession,
                    currentSession.projectionAccess,
                ).closeAllQuietly()

                activeSession = null
                state.update {
                    it.copy(
                        captureState = CaptureState.Idle,
                        publishState = PublishState.Idle,
                        audioState = AudioStreamState.Disabled,
                        audioDetail = null,
                        resolvedConfig = null,
                        codecSelection = null,
                        statusDetail = null,
                        error = null,
                    )
                }
            } catch (throwable: Throwable) {
                handleFailure(
                    error = SenderError.SessionStopFailed(throwable.message ?: "停止推流失败"),
                    throwable = throwable,
                    operation = "停止推流会话失败",
                )
            }
        }
    }

    override fun observeState(): StateFlow<StreamingSessionSnapshot> = state

    private fun observeRtcEvents(
        sessionToken: Any,
        publishSession: RtcPublishSession,
    ): Job =
        sessionScope.launch {
            publishSession.events.collect { event ->
                when (event) {
                    is RtcSessionEvent.Status -> {
                        if (activeSessionToken === sessionToken) {
                            state.update { current ->
                                current.copy(statusDetail = event.detail)
                            }
                        }
                    }

                    is RtcSessionEvent.Failure ->
                        terminateFromRtcEvent(
                            sessionToken = sessionToken,
                            error = event.error,
                        )

                    is RtcSessionEvent.CaptureInterrupted ->
                        terminateFromRtcEvent(
                            sessionToken = sessionToken,
                            error = SenderError.CaptureInterrupted(event.reason),
                        )

                    is RtcSessionEvent.AudioDegraded -> {
                        if (activeSessionToken === sessionToken) {
                            state.update { current ->
                                current.copy(
                                    audioState = AudioStreamState.Degraded,
                                    audioDetail = event.detail,
                                    statusDetail =
                                        if (
                                            current.captureState == CaptureState.Capturing &&
                                            current.publishState == PublishState.Publishing
                                        ) {
                                            "WebRTC 已连接，正在发送视频"
                                        } else {
                                            current.statusDetail
                                        },
                                )
                            }
                        }
                    }

                    RtcSessionEvent.Disconnected ->
                        terminateFromRtcEvent(
                            sessionToken = sessionToken,
                            error = SenderError.PeerConnectionFailed("WebRTC 连接已断开"),
                        )
                }
            }
        }

    private suspend fun terminateFromRtcEvent(
        sessionToken: Any,
        error: SenderError,
    ) {
        sessionMutex.withLock {
            val currentSession = activeSession ?: return
            if (currentSession.token !== sessionToken) {
                return
            }

            currentSession.monitorJob.cancel()
            activeSession = null
            activeSessionToken = null
            listOfNotNull(
                currentSession.publishSession,
                currentSession.audioSource,
                currentSession.signalingSession,
                currentSession.projectionAccess,
            ).closeAllQuietly()

            logger.error(
                TAG,
                "推流会话异常结束: ${error.message}",
                null,
            )
            state.update {
                it.copy(
                    captureState = CaptureState.Error,
                    publishState = PublishState.Error,
                    resolvedConfig = null,
                    codecSelection = null,
                    statusDetail = error.message,
                    error = error,
                )
            }
        }
    }

    private fun handleFailure(
        error: SenderError,
        throwable: Throwable? = null,
        operation: String,
    ) {
        activeSession = null
        activeSessionToken = null
        logger.error(
            TAG,
            "$operation: ${error.message}",
            throwable,
        )
        state.update {
            it.copy(
                captureState = CaptureState.Error,
                publishState = PublishState.Error,
                resolvedConfig = null,
                codecSelection = null,
                statusDetail = error.message,
                error = error,
            )
        }
    }

    private fun mapError(throwable: Throwable): SenderError =
        when (throwable) {
            is PermissionDeniedException -> SenderError.PermissionDenied
            is SenderException -> throwable.error
            is TimeoutCancellationException -> SenderError.SignalingFailed("等待远端应答超时")
            else -> SenderError.Unexpected(throwable.message ?: "发送会话出现未知错误")
        }

    private data class ActiveSession(
        val token: Any,
        val projectionAccess: ProjectionAccess,
        val audioSource: AudioCaptureSource?,
        val signalingSession: SignalingSession,
        val publishSession: RtcPublishSession,
        val monitorJob: Job,
    )

    private companion object {
        const val TAG = "StreamingSession"
    }
}

class PermissionDeniedException : IllegalStateException("用户拒绝了屏幕采集授权")

class SenderException(
    val error: SenderError,
) : IllegalStateException(error.message)

private fun Iterable<Closeable>.closeAllQuietly() {
    for (resource in this.reversed()) {
        runCatching {
            resource.close()
        }
    }
}
