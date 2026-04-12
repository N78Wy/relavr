package io.relavr.sender.core.model

import java.net.URI

data class StreamConfig(
    val videoEnabled: Boolean = true,
    val codecPreference: CodecPreference = CodecPreference.H264,
    val resolution: VideoResolution = DEFAULT_RESOLUTION,
    val fps: Int = DEFAULT_FPS,
    val bitrateKbps: Int = DEFAULT_BITRATE_KBPS,
    val signalingEndpoint: String = DEFAULT_SIGNALING_ENDPOINT,
    val sessionId: String = DEFAULT_SESSION_ID,
    val iceServers: List<String> = listOf(DEFAULT_STUN_SERVER),
) {
    val trimmedSignalingEndpoint: String = signalingEndpoint.trim()
    val trimmedSessionId: String = sessionId.trim()

    fun validationError(capabilities: CapabilitySnapshot? = null): SenderError.InvalidConfig? {
        if (!videoEnabled) {
            return SenderError.InvalidConfig(
                message = "Video streaming must remain enabled in this version.",
                uiText = UiText.of(R.string.sender_error_video_required),
            )
        }
        if (trimmedSignalingEndpoint.isEmpty()) {
            return SenderError.InvalidConfig(
                message = "The WebSocket endpoint is required.",
                uiText = UiText.of(R.string.sender_error_signaling_endpoint_required),
            )
        }
        if (trimmedSessionId.isEmpty()) {
            return SenderError.InvalidConfig(
                message = "The session ID is required.",
                uiText = UiText.of(R.string.sender_error_session_id_required),
            )
        }
        if (resolution !in RESOLUTION_OPTIONS) {
            return SenderError.InvalidConfig(
                message = "The selected resolution is not supported.",
                uiText = UiText.of(R.string.sender_error_resolution_unsupported),
            )
        }
        if (fps !in FPS_OPTIONS) {
            return SenderError.InvalidConfig(
                message = "The selected frame rate is not supported.",
                uiText = UiText.of(R.string.sender_error_fps_unsupported),
            )
        }
        if (bitrateKbps !in BITRATE_OPTIONS_KBPS) {
            return SenderError.InvalidConfig(
                message = "The selected bitrate is not supported.",
                uiText = UiText.of(R.string.sender_error_bitrate_unsupported),
            )
        }

        val uri =
            runCatching {
                URI(trimmedSignalingEndpoint)
            }.getOrNull()
                ?: return SenderError.InvalidConfig(
                    message = "The WebSocket endpoint format is invalid.",
                    uiText = UiText.of(R.string.sender_error_signaling_endpoint_invalid),
                )

        if (uri.scheme !in SUPPORTED_SIGNALING_SCHEMES || uri.host.isNullOrBlank()) {
            return SenderError.InvalidConfig(
                message = "The WebSocket endpoint must use ws:// or wss://.",
                uiText = UiText.of(R.string.sender_error_signaling_endpoint_scheme_invalid),
            )
        }
        if (capabilities != null && !capabilities.supports(toVideoStreamProfile())) {
            return SenderError.InvalidConfig(
                message = "The selected codec and stream profile are not supported together.",
                uiText = UiText.of(R.string.sender_error_profile_unsupported),
            )
        }
        return null
    }

    fun toVideoStreamProfile(): VideoStreamProfile = VideoStreamProfile.from(this)

    companion object {
        val DEFAULT_RESOLUTION = VideoResolution(width = 1280, height = 720)
        const val DEFAULT_FPS = 30
        const val DEFAULT_BITRATE_KBPS = 4000
        const val DEFAULT_SIGNALING_ENDPOINT = ""
        const val DEFAULT_SESSION_ID = "quest3-demo"
        const val DEFAULT_STUN_SERVER = "stun:stun.l.google.com:19302"
        val RESOLUTION_OPTIONS =
            listOf(
                VideoResolution(width = 1280, height = 720),
                VideoResolution(width = 1600, height = 900),
                VideoResolution(width = 1920, height = 1080),
            )
        val FPS_OPTIONS = listOf(24, 30, 45, 60)
        val BITRATE_OPTIONS_KBPS = listOf(2000, 4000, 6000, 8000)

        private val SUPPORTED_SIGNALING_SCHEMES = setOf("ws", "wss")
    }
}
