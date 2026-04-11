package io.relavr.sender.platform.mediacodec

import android.media.MediaCodecList
import android.os.Build
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

            val supportedCodecs =
                CodecPreference.entries
                    .filter { preference ->
                        codecInfos.any { codecInfo ->
                            codecInfo.supportedTypes.any { type ->
                                type.equals(preference.mimeType, ignoreCase = true)
                            }
                        }
                    }.toSet()

            CapabilitySnapshot(
                supportedCodecs = supportedCodecs,
                audioPlaybackCaptureSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                defaultCodec = CodecPreference.H264,
            )
        }
}
