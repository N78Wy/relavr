package io.relavr.sender.app

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.session.CodecCapabilityRepository
import io.relavr.sender.platform.webrtc.WebRtcCodecSupportProvider

internal class CombinedCodecCapabilityRepository(
    private val androidCapabilityRepository: CodecCapabilityRepository,
    private val webRtcCodecSupportProvider: WebRtcCodecSupportProvider,
) : CodecCapabilityRepository {
    override suspend fun getCapabilities(): CapabilitySnapshot {
        val androidCapabilities = androidCapabilityRepository.getCapabilities()
        val webRtcSupportedCodecs = webRtcCodecSupportProvider.getSupportedCodecs()
        val supportedCodecs =
            androidCapabilities.supportedCodecs.intersect(webRtcSupportedCodecs)

        return CapabilitySnapshot(
            supportedCodecs = supportedCodecs,
            audioPlaybackCaptureSupported = androidCapabilities.audioPlaybackCaptureSupported,
            defaultCodec = CapabilitySnapshot.resolveDefaultCodec(supportedCodecs),
        )
    }
}
