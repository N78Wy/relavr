package io.relavr.sender.core.session

import io.relavr.sender.core.common.AppDispatchers
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.R
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.UiText
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
                    operation = "Streaming configuration validation failed",
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
                    statusDetail = UiText.of(R.string.sender_status_waiting_projection_permission),
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
                        operation = "Requesting the screen-capture permission failed",
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
                        statusDetail = UiText.of(R.string.sender_status_probing_codec_capabilities),
                    )
                }

                val capabilities =
                    withContext(dispatchers.io) {
                        codecCapabilityRepository.getCapabilities()
                    }
                val selection = codecPolicy.select(config.codecPreference, capabilities)
                val resolvedConfig = config.copy(codecPreference = selection.resolved)
                val resolvedConfigError = resolvedConfig.validationError(capabilities)
                if (resolvedConfigError != null) {
                    activeSessionToken = null
                    resources.closeAllQuietly()
                    handleFailure(
                        error = resolvedConfigError,
                        throwable = null,
                        operation = "Resolved streaming configuration validation failed",
                    )
                    return
                }
                var audioState =
                    if (resolvedConfig.audioEnabled) {
                        AudioStreamState.Starting
                    } else {
                        AudioStreamState.Disabled
                    }
                var audioDetail: UiText? = null

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
                            audioDetail = mappedError.uiText
                            logger.error(
                                TAG,
                                "Audio capture initialization failed. Falling back to video-only streaming: ${mappedError.message}",
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
                    it.copy(statusDetail = UiText.of(R.string.sender_status_connecting_signaling))
                }
                val signalingSession =
                    withContext(dispatchers.io) {
                        signalingClient.open(resolvedConfig)
                    }
                resources += signalingSession

                val publishSession =
                    withContext(dispatchers.io) {
                        rtcPublisherFactory.createSession(
                            config = resolvedConfig,
                            capabilities = capabilities,
                            signalingSession = signalingSession,
                        )
                    }
                resources += publishSession

                monitorJob = observeRtcEvents(sessionToken, publishSession)

                state.update {
                    it.copy(statusDetail = UiText.of(R.string.sender_status_preparing_video_track))
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
                        activeVideoProfile = resolvedConfig.toVideoStreamProfile(),
                        capabilities = capabilities,
                        codecSelection = selection,
                        statusDetail =
                            if (finalAudioState == AudioStreamState.Publishing) {
                                UiText.of(R.string.sender_status_streaming_audio_video)
                            } else {
                                UiText.of(R.string.sender_status_streaming_video_only)
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
                    operation = "Starting the streaming session failed",
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
                            activeVideoProfile = null,
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
                    statusDetail = UiText.of(R.string.sender_status_releasing_resources),
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
                        activeVideoProfile = null,
                        codecSelection = null,
                        statusDetail = null,
                        error = null,
                    )
                }
            } catch (throwable: Throwable) {
                handleFailure(
                    error = SenderError.SessionStopFailed(throwable.message ?: "Stopping the streaming session failed."),
                    throwable = throwable,
                    operation = "Stopping the streaming session failed",
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
                                            UiText.of(R.string.sender_status_streaming_video_only)
                                        } else {
                                            current.statusDetail
                                        },
                                )
                            }
                        }
                    }

                    is RtcSessionEvent.VideoProfileChanged -> {
                        if (activeSessionToken === sessionToken) {
                            state.update { current ->
                                current.copy(
                                    activeVideoProfile = event.activeProfile,
                                    statusDetail = event.detail,
                                )
                            }
                        }
                    }

                    is RtcSessionEvent.VideoEncoderOverloaded ->
                        terminateFromRtcEvent(
                            sessionToken = sessionToken,
                            error = event.error,
                        )

                    RtcSessionEvent.Disconnected ->
                        terminateFromRtcEvent(
                            sessionToken = sessionToken,
                            error = SenderError.PeerConnectionFailed("The WebRTC connection was disconnected."),
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
                "The streaming session ended unexpectedly: ${error.message}",
                null,
            )
            state.update {
                it.copy(
                    captureState = CaptureState.Error,
                    publishState = PublishState.Error,
                    resolvedConfig = null,
                    activeVideoProfile = null,
                    codecSelection = null,
                    statusDetail = error.uiText,
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
                activeVideoProfile = null,
                codecSelection = null,
                statusDetail = error.uiText,
                error = error,
            )
        }
    }

    private fun mapError(throwable: Throwable): SenderError =
        when (throwable) {
            is PermissionDeniedException -> SenderError.PermissionDenied
            is SenderException -> throwable.error
            is TimeoutCancellationException -> SenderError.SignalingFailed("Timed out while waiting for the remote answer.")
            else -> SenderError.Unexpected(throwable.message ?: "An unexpected sender error occurred.")
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

class PermissionDeniedException : IllegalStateException("The user denied the screen capture permission request.")

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
