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
        val supportedProfiles =
            androidCapabilities.supportedProfiles.filterTo(linkedSetOf()) { profile ->
                profile.codecPreference in webRtcSupportedCodecs
            }
        val supportedCodecs =
            androidCapabilities
                .supportedCodecs
                .intersect(webRtcSupportedCodecs)
                .filterTo(linkedSetOf()) { codecPreference ->
                    supportedProfiles.isEmpty() || supportedProfiles.any { profile -> profile.codecPreference == codecPreference }
                }

        return CapabilitySnapshot(
            supportedCodecs = supportedCodecs,
            audioPlaybackCaptureSupported = androidCapabilities.audioPlaybackCaptureSupported,
            defaultCodec = CapabilitySnapshot.resolveDefaultCodec(supportedCodecs),
            supportedProfiles = supportedProfiles,
        )
    }
}
