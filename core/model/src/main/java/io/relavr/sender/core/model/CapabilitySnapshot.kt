package io.relavr.sender.core.model

data class CapabilitySnapshot(
    val supportedCodecs: Set<CodecPreference>,
    val audioPlaybackCaptureSupported: Boolean,
    val defaultCodec: CodecPreference = CodecPreference.H264,
) {
    fun supports(codecPreference: CodecPreference): Boolean = codecPreference in supportedCodecs

    companion object {
        fun resolveDefaultCodec(supportedCodecs: Set<CodecPreference>): CodecPreference =
            when {
                CodecPreference.H264 in supportedCodecs -> CodecPreference.H264
                CodecPreference.HEVC in supportedCodecs -> CodecPreference.HEVC
                CodecPreference.VP8 in supportedCodecs -> CodecPreference.VP8
                CodecPreference.VP9 in supportedCodecs -> CodecPreference.VP9
                else -> CodecPreference.H264
            }
    }
}
