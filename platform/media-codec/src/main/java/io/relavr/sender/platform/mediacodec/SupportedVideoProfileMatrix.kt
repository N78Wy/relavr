package io.relavr.sender.platform.mediacodec

import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.VideoStreamProfile

internal data class VideoProfileSupportProbe(
    val supportsSizeAndRate: (width: Int, height: Int, fps: Int) -> Boolean,
    val supportsBitrateBps: (bitrateBps: Int) -> Boolean,
)

internal fun resolveSupportedProfiles(supportByCodec: Map<CodecPreference, List<VideoProfileSupportProbe>>): Set<VideoStreamProfile> =
    buildSet {
        CodecPreference.entries.forEach { codecPreference ->
            val probes = supportByCodec[codecPreference].orEmpty()
            if (probes.isEmpty()) {
                return@forEach
            }

            StreamConfig.RESOLUTION_OPTIONS.forEach { resolution ->
                StreamConfig.FPS_OPTIONS.forEach { fps ->
                    StreamConfig.BITRATE_OPTIONS_KBPS.forEach { bitrateKbps ->
                        val bitrateBps = bitrateKbps * 1000
                        val supported =
                            probes.any { probe ->
                                probe.supportsSizeAndRate(
                                    resolution.width,
                                    resolution.height,
                                    fps,
                                ) &&
                                    probe.supportsBitrateBps(bitrateBps)
                            }
                        if (supported) {
                            add(
                                VideoStreamProfile(
                                    codecPreference = codecPreference,
                                    resolution = resolution,
                                    fps = fps,
                                    bitrateKbps = bitrateKbps,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
