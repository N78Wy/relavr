package io.relavr.sender.platform.mediacodec

import android.media.MediaCodecList
import io.relavr.sender.core.common.AppDispatchers
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.session.CodecCapabilityRepository
import kotlinx.coroutines.withContext

class AndroidMediaCodecCapabilityRepository(
    private val dispatchers: AppDispatchers,
) : CodecCapabilityRepository {
    override suspend fun getCapabilities(): CapabilitySnapshot =
        withContext(dispatchers.default) {
            val codecInfos =
                MediaCodecList(MediaCodecList.ALL_CODECS)
                    .codecInfos
                    .filter { it.isEncoder }

            val supportByCodec =
                CodecPreference.entries.associateWith { preference ->
                    codecInfos
                        .filter { codecInfo ->
                            codecInfo.supportedTypes.any { type ->
                                type.equals(preference.mimeType, ignoreCase = true)
                            }
                        }.mapNotNull { codecInfo ->
                            runCatching {
                                val videoCapabilities = codecInfo.getCapabilitiesForType(preference.mimeType).videoCapabilities
                                VideoProfileSupportProbe(
                                    supportsSizeAndRate = { width, height, fps ->
                                        videoCapabilities.areSizeAndRateSupported(
                                            width,
                                            height,
                                            fps.toDouble(),
                                        )
                                    },
                                    supportsBitrateBps = { bitrateBps ->
                                        videoCapabilities.bitrateRange.contains(bitrateBps)
                                    },
                                )
                            }.getOrNull()
                        }
                }
            val supportedProfiles = resolveSupportedProfiles(supportByCodec)
            val supportedCodecs = supportedProfiles.mapTo(linkedSetOf()) { profile -> profile.codecPreference }

            CapabilitySnapshot(
                supportedCodecs = supportedCodecs,
                defaultCodec = CapabilitySnapshot.resolveDefaultCodec(supportedCodecs),
                supportedProfiles = supportedProfiles,
            )
        }
}
