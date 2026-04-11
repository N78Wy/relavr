package io.relavr.sender.core.model

sealed interface SenderError {
    val message: String

    data object PermissionDenied : SenderError {
        override val message: String = "未授予屏幕采集权限"
    }

    data class UnsupportedCodec(
        private val requested: CodecPreference,
    ) : SenderError {
        override val message: String = "设备不支持所选编码格式：${requested.displayName}"
    }

    data class CapabilityUnavailable(
        override val message: String,
    ) : SenderError

    data class InvalidConfig(
        override val message: String,
    ) : SenderError

    data class AudioCaptureUnavailable(
        override val message: String,
    ) : SenderError

    data class SignalingFailed(
        override val message: String,
    ) : SenderError

    data class PeerConnectionFailed(
        override val message: String,
    ) : SenderError

    data class CaptureInterrupted(
        override val message: String,
    ) : SenderError

    data class SessionStartFailed(
        override val message: String,
    ) : SenderError

    data class SessionStopFailed(
        override val message: String,
    ) : SenderError

    data class Unexpected(
        override val message: String,
    ) : SenderError
}
