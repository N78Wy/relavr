package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot

data class StreamControlUiState(
    val title: String,
    val statusLabel: String,
    val statusDescription: String,
    val availableCodecs: List<CodecPreference>,
    val selectedCodec: CodecPreference,
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
    val availableCodecs = capabilities.toCodecOptions()

    return StreamControlUiState(
        title = "Quest 3 发送控制台",
        statusLabel = sessionSnapshot.toStatusLabel(),
        statusDescription = sessionSnapshot.toStatusDescription(config),
        availableCodecs = availableCodecs,
        selectedCodec = config.codecPreference,
        audioEnabled = config.audioEnabled,
        audioToggleEnabled = !sessionSnapshot.isStreaming,
        audioCapabilityLabel =
            if (capabilities?.audioPlaybackCaptureSupported == false) {
                "音频采集不可用"
            } else {
                "AudioPlaybackCapture 已接缝"
            },
        resolutionLabel = config.resolution.label,
        fpsLabel = "${config.fps} FPS",
        bitrateLabel = "${config.bitrateKbps} kbps",
        startEnabled =
            !sessionSnapshot.isStreaming &&
                sessionSnapshot.captureState != CaptureState.RequestingPermission &&
                sessionSnapshot.publishState != PublishState.Preparing,
        stopEnabled = sessionSnapshot.isStreaming,
        errorMessage = sessionSnapshot.error?.message,
    )
}

private fun CapabilitySnapshot?.toCodecOptions(): List<CodecPreference> =
    this
        ?.supportedCodecs
        ?.sortedBy(CodecPreference::ordinal)
        ?.ifEmpty { listOf(CodecPreference.H264) }
        ?: listOf(CodecPreference.H264)

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
    return when {
        isStreaming -> "当前输出编码：$resolvedCodec"
        error != null -> error?.message.orEmpty()
        else -> "默认配置：${config.resolution.label} / ${config.fps} FPS / $resolvedCodec"
    }
}
