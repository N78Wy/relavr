package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.model.CodecPreference
import org.webrtc.DefaultVideoEncoderFactory

fun interface WebRtcCodecSupportProvider {
    fun getSupportedCodecs(): Set<CodecPreference>
}

class DefaultWebRtcCodecSupportProvider(
    private val libraryInitializer: WebRtcLibraryInitializer,
    private val supportedCodecNamesProvider: () -> List<String> = {
        DefaultVideoEncoderFactory(null, true, true)
            .supportedCodecs
            .map { codecInfo -> codecInfo.name }
    },
) : WebRtcCodecSupportProvider {
    override fun getSupportedCodecs(): Set<CodecPreference> = supportedCodecNames()

    private fun supportedCodecNames(): Set<CodecPreference> {
        libraryInitializer.ensureInitialized()
        return supportedCodecNamesProvider()
            .mapNotNull { codecName ->
                CodecPreference.fromWebRtcCodecName(codecName)
            }.toSet()
    }
}
