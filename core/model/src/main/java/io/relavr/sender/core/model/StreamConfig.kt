package io.relavr.sender.core.model

data class StreamConfig(
    val videoEnabled: Boolean = true,
    val audioEnabled: Boolean = true,
    val codecPreference: CodecPreference = CodecPreference.H264,
    val resolution: VideoResolution = VideoResolution(width = 1280, height = 720),
    val fps: Int = 30,
    val bitrateKbps: Int = 4000,
    val signalingEndpoint: String = "wss://placeholder.invalid/session",
    val iceServers: List<String> = emptyList(),
)
