package io.relavr.sender.core.model

enum class CodecPreference(
    val displayName: String,
    val mimeType: String,
    val webrtcCodecName: String,
) {
    H264(displayName = "H.264 / AVC", mimeType = "video/avc", webrtcCodecName = "H264"),
    HEVC(displayName = "H.265 / HEVC", mimeType = "video/hevc", webrtcCodecName = "H265"),
    VP8(displayName = "VP8", mimeType = "video/x-vnd.on2.vp8", webrtcCodecName = "VP8"),
    VP9(displayName = "VP9", mimeType = "video/x-vnd.on2.vp9", webrtcCodecName = "VP9"),

    ;

    companion object {
        fun fromWebRtcCodecName(codecName: String): CodecPreference? =
            entries.firstOrNull { preference ->
                preference.webrtcCodecName.equals(codecName, ignoreCase = true)
            }
    }
}
