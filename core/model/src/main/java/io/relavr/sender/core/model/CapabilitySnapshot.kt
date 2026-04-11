package io.relavr.sender.core.model

data class CapabilitySnapshot(
    val supportedCodecs: Set<CodecPreference>,
    val audioPlaybackCaptureSupported: Boolean,
    val defaultCodec: CodecPreference = CodecPreference.H264,
) {
    fun supports(codecPreference: CodecPreference): Boolean = codecPreference in supportedCodecs
}
