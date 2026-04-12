package io.relavr.sender.app

import io.relavr.sender.core.common.AppDispatchers
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.session.StreamingSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ForegroundServiceStreamingSessionController(
    private val sessionEngine: StreamingSessionController,
    private val commandDispatcher: ForegroundServiceCommandDispatcher,
    private val recordAudioPermissionGateway: RecordAudioPermissionGateway,
    dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : StreamingSessionController {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val state = MutableStateFlow(sessionEngine.observeState().value)

    init {
        scope.launch {
            sessionEngine.observeState().collect { snapshot ->
                state.value = snapshot
            }
        }
    }

    override suspend fun refreshCapabilities(): CapabilitySnapshot = sessionEngine.refreshCapabilities()

    override suspend fun start(config: StreamConfig) {
        state.update { current ->
            current.copy(
                captureState = CaptureState.RequestingPermission,
                publishState = PublishState.Preparing,
                statusDetail = UiText.of(io.relavr.sender.core.model.R.string.sender_status_starting_foreground_service),
                error = null,
            )
        }
        if (config.audioEnabled) {
            runCatching {
                recordAudioPermissionGateway.requestPermissionIfNeeded()
            }.onFailure { throwable ->
                reportFailure(
                    error = SenderError.SessionStartFailed(throwable.message ?: "Unable to request the audio-record permission."),
                    throwable = throwable,
                    operation = "Requesting the audio-record permission failed",
                )
                return
            }
        }
        runCatching {
            commandDispatcher.startSession(config)
        }.onFailure { throwable ->
            reportFailure(
                error = SenderError.SessionStartFailed(throwable.message ?: "Unable to start the foreground streaming service."),
                throwable = throwable,
                operation = "Starting the foreground streaming service failed",
            )
        }
    }

    override suspend fun stop() {
        state.update { current ->
            current.copy(
                captureState = CaptureState.Stopping,
                publishState = PublishState.Stopping,
                statusDetail = UiText.of(io.relavr.sender.core.model.R.string.sender_status_sending_stop_command),
                error = null,
            )
        }
        runCatching {
            commandDispatcher.stopSession()
        }.onFailure { throwable ->
            reportFailure(
                error = SenderError.SessionStopFailed(throwable.message ?: "Unable to send the foreground service stop command."),
                throwable = throwable,
                operation = "Sending the foreground service stop command failed",
            )
        }
    }

    override fun observeState(): StateFlow<StreamingSessionSnapshot> = state

    private fun reportFailure(
        error: SenderError,
        throwable: Throwable,
        operation: String,
    ) {
        logger.error(
            TAG,
            "$operation: ${error.message}",
            throwable,
        )
        state.update { current ->
            current.copy(
                captureState = CaptureState.Error,
                publishState = PublishState.Error,
                resolvedConfig = null,
                codecSelection = null,
                statusDetail = error.uiText,
                error = error,
            )
        }
    }

    private companion object {
        const val TAG = "StreamingSession"
    }
}

internal interface ForegroundServiceCommandDispatcher {
    fun startSession(config: StreamConfig)

    fun stopSession()
}
