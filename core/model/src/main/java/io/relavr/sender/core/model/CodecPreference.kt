package io.relavr.sender.core.model

enum class CodecPreference(
    val displayName: String,
    val mimeType: String,
) {
    H264(displayName = "H.264 / AVC", mimeType = "video/avc"),
    HEVC(displayName = "H.265 / HEVC", mimeType = "video/hevc"),
    VP8(displayName = "VP8", mimeType = "video/x-vnd.on2.vp8"),
    VP9(displayName = "VP9", mimeType = "video/x-vnd.on2.vp9"),
}
