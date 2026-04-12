package io.relavr.sender.core.model

sealed interface SenderError {
    val message: String
    val uiText: UiText

    data object PermissionDenied : SenderError {
        override val message: String = "Screen capture permission was not granted."
        override val uiText: UiText = UiText.of(R.string.sender_error_permission_denied)
    }

    data class UnsupportedCodec(
        private val requested: CodecPreference,
    ) : SenderError {
        override val message: String = "The selected codec is not supported: ${requested.displayName}"
        override val uiText: UiText = UiText.of(R.string.sender_error_unsupported_codec, requested.displayName)
    }

    data class CapabilityUnavailable(
        override val message: String = "No video codec capability was reported.",
    ) : SenderError {
        override val uiText: UiText = UiText.of(R.string.sender_error_capability_unavailable)
    }

    data class InvalidConfig(
        override val message: String,
        override val uiText: UiText,
    ) : SenderError

    data class AudioCaptureUnavailable(
        override val message: String,
        override val uiText: UiText = UiText.of(R.string.sender_error_audio_capture_unavailable),
    ) : SenderError

    data class SignalingFailed(
        override val message: String,
        override val uiText: UiText = UiText.of(R.string.sender_error_signaling_failed),
    ) : SenderError

    data class PeerConnectionFailed(
        override val message: String,
        override val uiText: UiText = UiText.of(R.string.sender_error_peer_connection_failed),
    ) : SenderError

    data class CaptureInterrupted(
        override val message: String,
        override val uiText: UiText = UiText.of(R.string.sender_error_capture_interrupted),
    ) : SenderError

    data class SessionStartFailed(
        override val message: String,
        override val uiText: UiText = UiText.of(R.string.sender_error_session_start_failed),
    ) : SenderError

    data class SessionStopFailed(
        override val message: String,
        override val uiText: UiText = UiText.of(R.string.sender_error_session_stop_failed),
    ) : SenderError

    data class Unexpected(
        override val message: String,
        override val uiText: UiText = UiText.of(R.string.sender_error_unexpected),
    ) : SenderError
}
