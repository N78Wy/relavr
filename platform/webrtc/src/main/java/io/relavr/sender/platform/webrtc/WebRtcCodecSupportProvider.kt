package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.model.CodecPreference
import org.webrtc.DefaultVideoEncoderFactory

fun interface WebRtcCodecSupportProvider {
    fun getSupportedCodecs(): Set<CodecPreference>
}

class DefaultWebRtcCodecSupportProvider : WebRtcCodecSupportProvider {
    override fun getSupportedCodecs(): Set<CodecPreference> =
        DefaultVideoEncoderFactory(null, true, true)
            .supportedCodecs
            .mapNotNull { codecInfo ->
                CodecPreference.fromWebRtcCodecName(codecInfo.name)
            }.toSet()
}
