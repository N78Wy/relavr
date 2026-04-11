package io.relavr.sender.core.session

import io.relavr.sender.core.common.AppDispatchers
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

class StreamingSessionCoordinator(
    private val projectionPermissionGateway: ProjectionPermissionGateway,
    private val videoCaptureSourceFactory: VideoCaptureSourceFactory,
    private val audioCaptureSourceFactory: AudioCaptureSourceFactory,
    private val codecCapabilityRepository: CodecCapabilityRepository,
    private val codecPolicy: CodecPolicy,
    private val rtcPublisherFactory: RtcPublisherFactory,
    private val signalingClient: SignalingClient,
    private val dispatchers: AppDispatchers,
) : StreamingSessionController {
    private val state = MutableStateFlow(StreamingSessionSnapshot())
    private val sessionMutex = Mutex()

    private var activeSession: ActiveSession? = null

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

            state.update {
                it.copy(
                    captureState = CaptureState.RequestingPermission,
                    publishState = PublishState.Preparing,
                    error = null,
                )
            }

            val projectionAccess =
                try {
                    projectionPermissionGateway.restoreIfAvailable()
                        ?: projectionPermissionGateway.requestPermission()
                } catch (throwable: Throwable) {
                    handleFailure(mapError(throwable))
                    return
                }

            val resources = mutableListOf<Closeable>(projectionAccess)
            try {
                state.update {
                    it.copy(captureState = CaptureState.Starting)
                }

                val capabilities =
                    withContext(dispatchers.io) {
                        codecCapabilityRepository.getCapabilities()
                    }
                val selection = codecPolicy.select(config.codecPreference, capabilities)
                val resolvedConfig = config.copy(codecPreference = selection.resolved)

                val videoSource =
                    withContext(dispatchers.io) {
                        videoCaptureSourceFactory.create(projectionAccess, resolvedConfig)
                    }
                resources += videoSource

                val audioSource =
                    if (resolvedConfig.audioEnabled) {
                        withContext(dispatchers.io) {
                            audioCaptureSourceFactory.create(projectionAccess, resolvedConfig)
                        }?.also { resources += it }
                    } else {
                        null
                    }

                withContext(dispatchers.io) {
                    signalingClient.open(resolvedConfig)
                }

                val publishSession =
                    withContext(dispatchers.io) {
                        rtcPublisherFactory.createSession(resolvedConfig)
                    }
                resources += publishSession

                withContext(dispatchers.io) {
                    publishSession.publish(videoSource, audioSource)
                }

                activeSession =
                    ActiveSession(
                        projectionAccess = projectionAccess,
                        videoSource = videoSource,
                        audioSource = audioSource,
                        publishSession = publishSession,
                    )

                state.update {
                    it.copy(
                        captureState = CaptureState.Capturing,
                        publishState = PublishState.Publishing,
                        resolvedConfig = resolvedConfig,
                        capabilities = capabilities,
                        codecSelection = selection,
                        error = null,
                    )
                }
            } catch (throwable: Throwable) {
                resources.closeAllQuietly()
                withContext(dispatchers.io) {
                    signalingClient.closeSession()
                }
                handleFailure(mapError(throwable))
            }
        }
    }

    override suspend fun stop() {
        sessionMutex.withLock {
            val currentSession =
                activeSession ?: run {
                    state.update {
                        it.copy(
                            captureState = CaptureState.Idle,
                            publishState = PublishState.Idle,
                            error = null,
                        )
                    }
                    return
                }

            state.update {
                it.copy(
                    captureState = CaptureState.Stopping,
                    publishState = PublishState.Stopping,
                )
            }

            try {
                listOfNotNull(
                    currentSession.publishSession,
                    currentSession.audioSource,
                    currentSession.videoSource,
                    currentSession.projectionAccess,
                ).closeAllQuietly()

                withContext(dispatchers.io) {
                    signalingClient.closeSession()
                }

                activeSession = null
                state.update {
                    it.copy(
                        captureState = CaptureState.Idle,
                        publishState = PublishState.Idle,
                        resolvedConfig = null,
                        codecSelection = null,
                        error = null,
                    )
                }
            } catch (throwable: Throwable) {
                handleFailure(SenderError.SessionStopFailed(throwable.message ?: "停止推流失败"))
            }
        }
    }

    override fun observeState(): StateFlow<StreamingSessionSnapshot> = state

    private fun handleFailure(error: SenderError) {
        activeSession = null
        state.update {
            it.copy(
                captureState = CaptureState.Error,
                publishState = PublishState.Error,
                resolvedConfig = null,
                codecSelection = null,
                error = error,
            )
        }
    }

    private fun mapError(throwable: Throwable): SenderError =
        when (throwable) {
            is PermissionDeniedException -> SenderError.PermissionDenied
            is SenderException -> throwable.error
            else -> SenderError.Unexpected(throwable.message ?: "发送会话出现未知错误")
        }

    private data class ActiveSession(
        val projectionAccess: ProjectionAccess,
        val videoSource: VideoCaptureSource,
        val audioSource: AudioCaptureSource?,
        val publishSession: RtcPublishSession,
    )
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
