package io.relavr.sender.feature.streamcontrol

import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.ReceiverConnectionInfo
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.model.VideoResolution

data class CodecOptionUiState(
    val preference: CodecPreference,
    val label: String,
    val supportLabel: UiText,
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
    val title: UiText,
    val statusLabel: UiText,
    val statusDescription: UiText,
    val codecOptions: List<CodecOptionUiState>,
    val codecStatusLabel: UiText,
    val signalingEndpoint: String,
    val sessionId: String,
    val configEditable: Boolean,
    val scanButtonEnabled: Boolean,
    val scannerVisible: Boolean,
    val scanStatusLabel: UiText,
    val audioEnabled: Boolean,
    val audioPermissionRequestPending: Boolean,
    val audioToggleEnabled: Boolean,
    val audioStatusLabel: UiText,
    val audioPermissionSettingsVisible: Boolean,
    val streamProfileSummary: UiText,
    val resolutionOptions: List<SelectionOptionUiState<VideoResolution>>,
    val fpsOptions: List<SelectionOptionUiState<Int>>,
    val bitrateOptions: List<SelectionOptionUiState<Int>>,
    val startEnabled: Boolean,
    val stopEnabled: Boolean,
    val errorMessage: UiText?,
)

internal fun buildStreamControlUiState(
    config: StreamConfig,
    scannerState: QrScannerState = QrScannerState(),
    sessionSnapshot: StreamingSessionSnapshot,
    audioPermissionRequestPending: Boolean = false,
    recordAudioPermissionStatus: RecordAudioPermissionStatus? = null,
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
        title = UiText.of(R.string.stream_control_title),
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
        scanButtonEnabled = configEditable,
        scannerVisible = scannerState.visible,
        scanStatusLabel = scannerState.toStatusLabel(),
        audioEnabled = config.audioEnabled,
        audioPermissionRequestPending = audioPermissionRequestPending,
        audioToggleEnabled =
            configEditable &&
                capabilities?.audioPlaybackCaptureSupported != false &&
                !audioPermissionRequestPending &&
                recordAudioPermissionStatus != RecordAudioPermissionStatus.PermanentlyDenied,
        audioStatusLabel =
            sessionSnapshot.toAudioStatusLabel(
                config = config,
                audioPermissionRequestPending = audioPermissionRequestPending,
                recordAudioPermissionStatus = recordAudioPermissionStatus,
            ),
        audioPermissionSettingsVisible =
            configEditable &&
                capabilities?.audioPlaybackCaptureSupported != false &&
                recordAudioPermissionStatus == RecordAudioPermissionStatus.PermanentlyDenied,
        streamProfileSummary =
            UiText.of(
                R.string.stream_control_profile_summary,
                config.resolution.label,
                config.fps,
                config.bitrateKbps,
            ),
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
        startEnabled = configValid && configEditable && !audioPermissionRequestPending,
        stopEnabled =
            hasActiveSession &&
                sessionSnapshot.captureState != CaptureState.Stopping &&
                sessionSnapshot.publishState != PublishState.Stopping &&
                sessionSnapshot.error == null,
        errorMessage = sessionSnapshot.error?.uiText,
    )
}

data class QrScannerState(
    val visible: Boolean = false,
    val lastReceiver: ReceiverConnectionInfo? = null,
    val errorMessage: UiText? = null,
)

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

private fun StreamingSessionSnapshot.toStatusLabel(): UiText =
    when {
        isStreaming -> UiText.of(R.string.stream_control_status_streaming)
        captureState == CaptureState.RequestingPermission -> UiText.of(R.string.stream_control_status_waiting_permission)
        captureState == CaptureState.Starting || publishState == PublishState.Preparing ->
            UiText.of(
                R.string.stream_control_status_preparing,
            )
        captureState == CaptureState.Stopping || publishState == PublishState.Stopping -> UiText.of(R.string.stream_control_status_stopping)
        error != null -> UiText.of(R.string.stream_control_status_failed)
        else -> UiText.of(R.string.stream_control_status_idle)
    }

private fun StreamingSessionSnapshot.toStatusDescription(config: StreamConfig): UiText {
    val resolvedCodec = codecSelection?.resolved?.displayName ?: config.codecPreference.displayName
    statusDetail?.let { detail ->
        return detail
    }
    return when {
        error != null -> error?.uiText ?: UiText.of(R.string.stream_control_status_failed)
        else ->
            UiText.of(
                R.string.stream_control_default_profile,
                config.resolution.label,
                config.fps,
                resolvedCodec,
                config.trimmedSessionId,
            )
    }
}

private fun StreamingSessionSnapshot.toAudioStatusLabel(
    config: StreamConfig,
    audioPermissionRequestPending: Boolean,
    recordAudioPermissionStatus: RecordAudioPermissionStatus?,
): UiText =
    when {
        audioPermissionRequestPending ->
            UiText.of(io.relavr.sender.core.model.R.string.sender_status_permission_requested)
        recordAudioPermissionStatus == RecordAudioPermissionStatus.PermanentlyDenied ->
            UiText.of(R.string.stream_control_audio_permission_permanently_denied)
        capabilities?.audioPlaybackCaptureSupported == false -> UiText.of(R.string.stream_control_audio_unsupported)
        !config.audioEnabled -> UiText.of(R.string.stream_control_audio_disabled)
        audioState == AudioStreamState.Starting -> UiText.of(R.string.stream_control_audio_starting)
        audioState == AudioStreamState.Publishing -> UiText.of(R.string.stream_control_audio_publishing)
        audioState == AudioStreamState.Degraded ->
            audioDetail
                ?: UiText.of(io.relavr.sender.core.model.R.string.sender_audio_degraded_video_only)
        else -> UiText.of(R.string.stream_control_audio_default)
    }

private fun StreamingSessionSnapshot.toCodecStatusLabel(config: StreamConfig): UiText {
    val requestedCodec = config.codecPreference.displayName
    val resolvedCodec = codecSelection?.resolved?.displayName
    val capabilitySnapshot = capabilities
    return when {
        codecSelection?.fellBack == true && resolvedCodec != null ->
            UiText.of(R.string.stream_control_codec_fallback, requestedCodec, resolvedCodec)
        resolvedCodec != null -> UiText.of(R.string.stream_control_codec_active, resolvedCodec)
        capabilitySnapshot == null ->
            UiText.of(R.string.stream_control_codec_probable_default, CodecPreference.H264.displayName)
        capabilitySnapshot.supports(config.codecPreference) &&
            capabilitySnapshot.defaultCodec == config.codecPreference ->
            UiText.of(R.string.stream_control_codec_device_default, requestedCodec)
        capabilitySnapshot.supports(config.codecPreference) ->
            UiText.of(
                R.string.stream_control_codec_device_recommended,
                requestedCodec,
                capabilitySnapshot.defaultCodec.displayName,
            )
        capabilitySnapshot.supportedCodecs.isEmpty() ->
            UiText.of(R.string.stream_control_codec_unavailable)
        else ->
            UiText.of(
                R.string.stream_control_codec_will_fallback,
                requestedCodec,
                capabilitySnapshot.defaultCodec.displayName,
            )
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
            capabilities == null && this == CodecPreference.H264 -> UiText.of(R.string.stream_control_codec_support_default)
            capabilities == null -> UiText.of(R.string.stream_control_codec_support_loading)
            isSupported && capabilities.defaultCodec == this -> UiText.of(R.string.stream_control_codec_support_device_default)
            isSupported -> UiText.of(R.string.stream_control_codec_support_available)
            else -> UiText.of(R.string.stream_control_codec_support_unavailable)
        }

    return CodecOptionUiState(
        preference = this,
        label = displayName,
        supportLabel = supportLabel,
        selected = selectedPreference == this,
        enabled = enabled,
    )
}

private fun QrScannerState.toStatusLabel(): UiText =
    when {
        visible -> UiText.of(R.string.stream_control_scan_waiting)
        errorMessage != null -> errorMessage
        lastReceiver != null && lastReceiver.authRequired ->
            UiText.of(
                R.string.stream_control_scan_recent_requires_confirmation,
                lastReceiver.receiverName,
                lastReceiver.webSocketUrl,
            )
        lastReceiver != null -> UiText.of(R.string.stream_control_scan_recent, lastReceiver.receiverName, lastReceiver.webSocketUrl)
        else -> UiText.of(R.string.stream_control_scan_default)
    }
