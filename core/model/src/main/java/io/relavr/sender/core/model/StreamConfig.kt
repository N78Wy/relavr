package io.relavr.sender.core.model

import java.net.URI

data class StreamConfig(
    val videoEnabled: Boolean = true,
    val audioEnabled: Boolean = true,
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
        if (resolution !in RESOLUTION_OPTIONS) {
            return SenderError.InvalidConfig("分辨率不在支持列表内")
        }
        if (fps !in FPS_OPTIONS) {
            return SenderError.InvalidConfig("帧率不在支持列表内")
        }
        if (bitrateKbps !in BITRATE_OPTIONS_KBPS) {
            return SenderError.InvalidConfig("码率不在支持列表内")
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
