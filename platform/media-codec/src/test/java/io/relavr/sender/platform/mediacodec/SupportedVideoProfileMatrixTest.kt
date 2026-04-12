package io.relavr.sender.platform.mediacodec

import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.model.VideoStreamProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedVideoProfileMatrixTest {
    @Test
    fun `supported profiles are generated from codec probes`() {
        val supportedProfiles =
            resolveSupportedProfiles(
                mapOf(
                    CodecPreference.H264 to
                        listOf(
                            VideoProfileSupportProbe(
                                supportsSizeAndRate = { width, height, fps ->
                                    width <= 1280 && height <= 720 && fps <= 30
                                },
                                supportsBitrateBps = { bitrateBps ->
                                    bitrateBps <= 4_000_000
                                },
                            ),
                        ),
                ),
            )

        assertTrue(
            VideoStreamProfile(
                codecPreference = CodecPreference.H264,
                resolution = VideoResolution(width = 1280, height = 720),
                fps = 30,
                bitrateKbps = 4000,
            ) in supportedProfiles,
        )
        assertFalse(
            VideoStreamProfile(
                codecPreference = CodecPreference.H264,
                resolution = VideoResolution(width = 1920, height = 1080),
                fps = 60,
                bitrateKbps = 8000,
            ) in supportedProfiles,
        )
    }
}
