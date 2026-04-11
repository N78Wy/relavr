package io.relavr.sender.app

import io.relavr.sender.core.common.AppDispatchers
import io.relavr.sender.core.common.AppLogger
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.SenderError
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
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
                statusDetail = "正在启动前台推流服务",
                error = null,
            )
        }
        runCatching {
            commandDispatcher.startSession(config)
        }.onFailure { throwable ->
            reportFailure(
                error = SenderError.SessionStartFailed(throwable.message ?: "无法启动前台推流服务"),
                throwable = throwable,
                operation = "启动前台推流服务失败",
            )
        }
    }

    override suspend fun stop() {
        state.update { current ->
            current.copy(
                captureState = CaptureState.Stopping,
                publishState = PublishState.Stopping,
                statusDetail = "正在发送停止命令",
                error = null,
            )
        }
        runCatching {
            commandDispatcher.stopSession()
        }.onFailure { throwable ->
            reportFailure(
                error = SenderError.SessionStopFailed(throwable.message ?: "无法发送停止前台服务命令"),
                throwable = throwable,
                operation = "发送停止前台服务命令失败",
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
                statusDetail = error.message,
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
