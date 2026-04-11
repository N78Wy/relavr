package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot

data class StreamControlUiState(
    val title: String,
    val statusLabel: String,
    val statusDescription: String,
    val codecLabel: String,
    val signalingEndpoint: String,
    val sessionId: String,
    val configEditable: Boolean,
    val audioEnabled: Boolean,
    val audioToggleEnabled: Boolean,
    val audioCapabilityLabel: String,
    val resolutionLabel: String,
    val fpsLabel: String,
    val bitrateLabel: String,
    val startEnabled: Boolean,
    val stopEnabled: Boolean,
    val errorMessage: String?,
)

internal fun buildStreamControlUiState(
    config: StreamConfig,
    sessionSnapshot: StreamingSessionSnapshot,
): StreamControlUiState {
    val capabilities = sessionSnapshot.capabilities
    val configEditable =
        !sessionSnapshot.isStreaming &&
            sessionSnapshot.captureState != CaptureState.RequestingPermission &&
            sessionSnapshot.publishState != PublishState.Preparing
    val configValid = config.validationError() == null
    val hasActiveSession =
        sessionSnapshot.captureState != CaptureState.Idle ||
            sessionSnapshot.publishState != PublishState.Idle

    return StreamControlUiState(
        title = "Quest 3 发送控制台",
        statusLabel = sessionSnapshot.toStatusLabel(),
        statusDescription = sessionSnapshot.toStatusDescription(config),
        codecLabel = CodecPreference.H264.displayName,
        signalingEndpoint = config.signalingEndpoint,
        sessionId = config.sessionId,
        configEditable = configEditable,
        audioEnabled = config.audioEnabled,
        audioToggleEnabled = false,
        audioCapabilityLabel =
            if (capabilities?.audioPlaybackCaptureSupported == false) {
                "当前设备不支持 AudioPlaybackCapture"
            } else {
                "音频推流将在第二阶段接入"
            },
        resolutionLabel = config.resolution.label,
        fpsLabel = "${config.fps} FPS",
        bitrateLabel = "${config.bitrateKbps} kbps",
        startEnabled = configValid && configEditable,
        stopEnabled =
            hasActiveSession &&
                sessionSnapshot.captureState != CaptureState.Stopping &&
                sessionSnapshot.publishState != PublishState.Stopping &&
                sessionSnapshot.error == null,
        errorMessage = sessionSnapshot.error?.message,
    )
}

private fun StreamingSessionSnapshot.toStatusLabel(): String =
    when {
        isStreaming -> "推流中"
        captureState == CaptureState.RequestingPermission -> "等待授权"
        captureState == CaptureState.Starting || publishState == PublishState.Preparing -> "正在准备"
        captureState == CaptureState.Stopping || publishState == PublishState.Stopping -> "正在停止"
        error != null -> "已失败"
        else -> "空闲"
    }

private fun StreamingSessionSnapshot.toStatusDescription(config: StreamConfig): String {
    val resolvedCodec = codecSelection?.resolved?.displayName ?: config.codecPreference.displayName
    statusDetail?.let { detail ->
        return detail
    }
    return when {
        error != null -> error?.message.orEmpty()
        else ->
            "默认配置：${config.resolution.label} / ${config.fps} FPS / $resolvedCodec / ${config.trimmedSessionId}"
    }
}
