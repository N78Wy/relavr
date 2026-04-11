package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.VideoResolution

data class CodecOptionUiState(
    val preference: CodecPreference,
    val label: String,
    val supportLabel: String,
    val selected: Boolean,
    val enabled: Boolean,
)

data class SelectionOptionUiState<T>(
    val value: T,
    val label: String,
    val selected: Boolean,
    val enabled: Boolean,
)

data class StreamControlUiState(
    val title: String,
    val statusLabel: String,
    val statusDescription: String,
    val codecOptions: List<CodecOptionUiState>,
    val codecStatusLabel: String,
    val signalingEndpoint: String,
    val sessionId: String,
    val configEditable: Boolean,
    val audioEnabled: Boolean,
    val audioToggleEnabled: Boolean,
    val audioStatusLabel: String,
    val streamProfileSummary: String,
    val resolutionOptions: List<SelectionOptionUiState<VideoResolution>>,
    val fpsOptions: List<SelectionOptionUiState<Int>>,
    val bitrateOptions: List<SelectionOptionUiState<Int>>,
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
        codecOptions =
            CodecPreference.entries.map { preference ->
                preference.toCodecOptionUiState(
                    selectedPreference = config.codecPreference,
                    capabilities = capabilities,
                    configEditable = configEditable,
                )
            },
        codecStatusLabel = sessionSnapshot.toCodecStatusLabel(config),
        signalingEndpoint = config.signalingEndpoint,
        sessionId = config.sessionId,
        configEditable = configEditable,
        audioEnabled = config.audioEnabled,
        audioToggleEnabled =
            configEditable &&
                capabilities?.audioPlaybackCaptureSupported != false,
        audioStatusLabel = sessionSnapshot.toAudioStatusLabel(config),
        streamProfileSummary = "${config.resolution.label} / ${config.fps} FPS / ${config.bitrateKbps} kbps",
        resolutionOptions =
            buildSelectionOptions(
                options = StreamConfig.RESOLUTION_OPTIONS,
                selectedValue = config.resolution,
                enabled = configEditable,
            ) { option -> option.label },
        fpsOptions =
            buildSelectionOptions(
                options = StreamConfig.FPS_OPTIONS,
                selectedValue = config.fps,
                enabled = configEditable,
            ) { option -> "$option FPS" },
        bitrateOptions =
            buildSelectionOptions(
                options = StreamConfig.BITRATE_OPTIONS_KBPS,
                selectedValue = config.bitrateKbps,
                enabled = configEditable,
            ) { option -> "$option kbps" },
        startEnabled = configValid && configEditable,
        stopEnabled =
            hasActiveSession &&
                sessionSnapshot.captureState != CaptureState.Stopping &&
                sessionSnapshot.publishState != PublishState.Stopping &&
                sessionSnapshot.error == null,
        errorMessage = sessionSnapshot.error?.message,
    )
}

private fun <T> buildSelectionOptions(
    options: List<T>,
    selectedValue: T,
    enabled: Boolean,
    labelOf: (T) -> String,
): List<SelectionOptionUiState<T>> =
    options.map { option ->
        SelectionOptionUiState(
            value = option,
            label = labelOf(option),
            selected = option == selectedValue,
            enabled = enabled,
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

private fun StreamingSessionSnapshot.toAudioStatusLabel(config: StreamConfig): String =
    when {
        capabilities?.audioPlaybackCaptureSupported == false -> "当前设备不支持 AudioPlaybackCapture"
        !config.audioEnabled -> "音频已关闭"
        audioState == AudioStreamState.Starting -> "音频准备中"
        audioState == AudioStreamState.Publishing -> "音频推流中"
        audioState == AudioStreamState.Degraded -> audioDetail ?: "音频已降级为静音/仅视频"
        else -> "开始推流后会采集系统播放音频"
    }

private fun StreamingSessionSnapshot.toCodecStatusLabel(config: StreamConfig): String {
    val requestedCodec = config.codecPreference.displayName
    val resolvedCodec = codecSelection?.resolved?.displayName
    val capabilitySnapshot = capabilities
    return when {
        codecSelection?.fellBack == true && resolvedCodec != null ->
            "本次请求 $requestedCodec，实际使用 $resolvedCodec"
        resolvedCodec != null -> "本次会话使用 $resolvedCodec"
        capabilitySnapshot == null ->
            "默认优先 ${CodecPreference.H264.displayName}，正在探测设备与 WebRTC 编码能力"
        capabilitySnapshot.supports(config.codecPreference) &&
            capabilitySnapshot.defaultCodec == config.codecPreference ->
            "当前选择为设备推荐默认：$requestedCodec"
        capabilitySnapshot.supports(config.codecPreference) ->
            "当前选择 $requestedCodec；设备推荐默认 ${capabilitySnapshot.defaultCodec.displayName}"
        capabilitySnapshot.supportedCodecs.isEmpty() ->
            "当前设备没有可用的视频编码能力"
        else ->
            "当前设备不支持 $requestedCodec，开始推流时会回退到 ${capabilitySnapshot.defaultCodec.displayName}"
    }
}

private fun CodecPreference.toCodecOptionUiState(
    selectedPreference: CodecPreference,
    capabilities: io.relavr.sender.core.model.CapabilitySnapshot?,
    configEditable: Boolean,
): CodecOptionUiState {
    val isSupported = capabilities?.supports(this) == true
    val enabled =
        configEditable &&
            when {
                capabilities == null -> this == CodecPreference.H264
                else -> isSupported
            }
    val supportLabel =
        when {
            capabilities == null && this == CodecPreference.H264 -> "默认优先"
            capabilities == null -> "等待能力探测"
            isSupported && capabilities.defaultCodec == this -> "设备推荐默认"
            isSupported -> "设备支持"
            else -> "设备或 WebRTC 不支持"
        }

    return CodecOptionUiState(
        preference = this,
        label = displayName,
        supportLabel = supportLabel,
        selected = selectedPreference == this,
        enabled = enabled,
    )
}
