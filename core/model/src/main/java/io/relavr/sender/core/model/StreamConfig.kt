package io.relavr.sender.core.model

import java.net.URI

data class StreamConfig(
    val videoEnabled: Boolean = true,
    val audioEnabled: Boolean = true,
    val codecPreference: CodecPreference = CodecPreference.H264,
    val resolution: VideoResolution = VideoResolution(width = 1280, height = 720),
    val fps: Int = 30,
    val bitrateKbps: Int = 4000,
    val signalingEndpoint: String = DEFAULT_SIGNALING_ENDPOINT,
    val sessionId: String = DEFAULT_SESSION_ID,
    val iceServers: List<String> = listOf(DEFAULT_STUN_SERVER),
) {
    val trimmedSignalingEndpoint: String = signalingEndpoint.trim()
    val trimmedSessionId: String = sessionId.trim()

    fun validationError(): SenderError.InvalidConfig? {
        if (!videoEnabled) {
            return SenderError.InvalidConfig("当前版本必须启用视频推流")
        }
        if (trimmedSignalingEndpoint.isEmpty()) {
            return SenderError.InvalidConfig("WebSocket 地址不能为空")
        }
        if (trimmedSessionId.isEmpty()) {
            return SenderError.InvalidConfig("Session ID 不能为空")
        }

        val uri =
            runCatching {
                URI(trimmedSignalingEndpoint)
            }.getOrNull()
                ?: return SenderError.InvalidConfig("WebSocket 地址格式无效")

        if (uri.scheme !in SUPPORTED_SIGNALING_SCHEMES || uri.host.isNullOrBlank()) {
            return SenderError.InvalidConfig("WebSocket 地址必须使用 ws:// 或 wss://")
        }
        return null
    }

    companion object {
        const val DEFAULT_SIGNALING_ENDPOINT = ""
        const val DEFAULT_SESSION_ID = "quest3-demo"
        const val DEFAULT_STUN_SERVER = "stun:stun.l.google.com:19302"

        private val SUPPORTED_SIGNALING_SCHEMES = setOf("ws", "wss")
    }
}
