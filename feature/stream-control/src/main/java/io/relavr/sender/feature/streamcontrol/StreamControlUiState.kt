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
    val signalingEndpointHint: String,
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
    val startHint: String?,
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
    val validationError = config.validationError()
    val configValid = validationError == null
    val hasActiveSession =
        sessionSnapshot.captureState != CaptureState.Idle ||
            sessionSnapshot.publishState != PublishState.Idle
    val sessionError = sessionSnapshot.error
    val signalingEndpointHint = "Quest 真机请填写宿主机局域网地址，例如 ws://192.168.123.182:8765"
    val startHint =
        when {
            !configEditable || configValid -> null
            config.trimmedSignalingEndpoint.isEmpty() -> "请先填写 Quest 可访问的 WebSocket 地址后再开始推流"
            else -> validationError?.message
        }
    val statusDescription =
        sessionSnapshot.statusDetail?.takeIf { it.isNotBlank() }
            ?: when {
                sessionError != null -> sessionError.message
                !hasActiveSession && startHint != null -> startHint
                else ->
                    "默认配置：${config.resolution.label} / ${config.fps} FPS / ${config.codecPreference.displayName} / ${config.trimmedSessionId}"
            }

    return StreamControlUiState(
        title = "Quest 3 发送控制台",
        statusLabel = sessionSnapshot.toStatusLabel(),
        statusDescription = statusDescription,
        codecLabel = CodecPreference.H264.displayName,
        signalingEndpoint = config.signalingEndpoint,
        signalingEndpointHint = signalingEndpointHint,
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
                sessionError == null,
        startHint = startHint,
        errorMessage = sessionError?.message,
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
