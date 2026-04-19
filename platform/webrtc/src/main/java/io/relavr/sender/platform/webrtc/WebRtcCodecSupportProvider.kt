package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.model.CodecPreference
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase

fun interface WebRtcCodecSupportProvider {
    fun getSupportedCodecs(): Set<CodecPreference>
}

class DefaultWebRtcCodecSupportProvider(
    private val libraryInitializer: WebRtcLibraryInitializer,
    private val supportedCodecNamesProvider: (EglBase.Context?) -> List<String> = { eglContext ->
        DefaultVideoEncoderFactory(checkNotNull(eglContext), true, true)
            .supportedCodecs
            .map { codecInfo -> codecInfo.name }
    },
) : WebRtcCodecSupportProvider {
    override fun getSupportedCodecs(): Set<CodecPreference> = supportedCodecNames()

    private fun supportedCodecNames(): Set<CodecPreference> {
        libraryInitializer.ensureInitialized()
        val eglBase = runCatching { EglBase.create() }.getOrNull()
        return try {
            supportedCodecNamesProvider(eglBase?.eglBaseContext)
                .mapNotNull { codecName ->
                    CodecPreference.fromWebRtcCodecName(codecName)
                }.toSet()
        } finally {
            eglBase?.release()
        }
    }
}
