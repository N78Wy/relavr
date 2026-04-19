package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.model.VideoStreamProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveVideoProfileControllerTest {
    @Test
    fun `sustained overload lowers bitrate before fps`() {
        val controller =
            AdaptiveVideoProfileController(
                initialProfile =
                    VideoStreamProfile(
                        codecPreference = CodecPreference.H264,
                        resolution = VideoResolution(width = 1920, height = 1080),
                        fps = 60,
                        bitrateKbps = 8000,
                    ),
                supportedProfiles = allProfilesFor(CodecPreference.H264),
            )

        repeat(3) { index ->
            val decision =
                controller.evaluate(
                    VideoEncoderStatsSample(
                        timestampUs = index * 1_000_000L,
                        framesEncoded = index.toLong() * 10L,
                        framesPerSecond = 18.0,
                        qualityLimitationReason = "cpu",
                    ),
                )
            if (index < 2) {
                assertEquals(null, decision)
            } else {
                val downgrade = decision as AdaptiveVideoProfileDecision.Downgrade
                assertEquals(6000, downgrade.profile.bitrateKbps)
                assertEquals(60, downgrade.profile.fps)
                assertEquals(VideoResolution(width = 1920, height = 1080), downgrade.profile.resolution)
            }
        }
    }

    @Test
    fun `the controller drops fps after bitrate floor is reached`() {
        val controller =
            AdaptiveVideoProfileController(
                initialProfile =
                    VideoStreamProfile(
                        codecPreference = CodecPreference.H264,
                        resolution = VideoResolution(width = 1600, height = 900),
                        fps = 60,
                        bitrateKbps = 2000,
                    ),
                supportedProfiles = allProfilesFor(CodecPreference.H264),
            )

        repeat(3) { index ->
            val decision =
                controller.evaluate(
                    VideoEncoderStatsSample(
                        timestampUs = index * 1_000_000L,
                        framesEncoded = index.toLong() * 5L,
                        framesPerSecond = 10.0,
                        qualityLimitationReason = "cpu",
                    ),
                )
            if (index == 2) {
                val downgrade = decision as AdaptiveVideoProfileDecision.Downgrade
                assertEquals(45, downgrade.profile.fps)
                assertEquals(2000, downgrade.profile.bitrateKbps)
                assertEquals(VideoResolution(width = 1600, height = 900), downgrade.profile.resolution)
            }
        }
    }

    @Test
    fun `the controller drops resolution after bitrate and fps floors are reached`() {
        val controller =
            AdaptiveVideoProfileController(
                initialProfile =
                    VideoStreamProfile(
                        codecPreference = CodecPreference.H264,
                        resolution = VideoResolution(width = 1600, height = 900),
                        fps = 24,
                        bitrateKbps = 2000,
                    ),
                supportedProfiles = allProfilesFor(CodecPreference.H264),
            )

        repeat(3) { index ->
            val decision =
                controller.evaluate(
                    VideoEncoderStatsSample(
                        timestampUs = index * 1_000_000L,
                        framesEncoded = index.toLong() * 5L,
                        framesPerSecond = 10.0,
                        qualityLimitationReason = "cpu",
                    ),
                )
            if (index == 2) {
                val downgrade = decision as AdaptiveVideoProfileDecision.Downgrade
                assertEquals(24, downgrade.profile.fps)
                assertEquals(2000, downgrade.profile.bitrateKbps)
                assertEquals(VideoResolution(width = 1280, height = 720), downgrade.profile.resolution)
            }
        }
    }

    @Test
    fun `minimum profile overload eventually exhausts the controller`() {
        val minimumProfile =
            VideoStreamProfile(
                codecPreference = CodecPreference.H264,
                resolution = StreamConfig.RESOLUTION_OPTIONS.first(),
                fps = StreamConfig.FPS_OPTIONS.first(),
                bitrateKbps = StreamConfig.BITRATE_OPTIONS_KBPS.last(),
            )
        val controller =
            AdaptiveVideoProfileController(
                initialProfile = minimumProfile,
                supportedProfiles = setOf(minimumProfile),
            )

        val decisions =
            (0..2).map { index ->
                controller.evaluate(
                    VideoEncoderStatsSample(
                        timestampUs = index * 1_000_000L,
                        framesEncoded = index.toLong() * 2L,
                        framesPerSecond = 5.0,
                        qualityLimitationReason = "cpu",
                    ),
                )
            }

        assertTrue(decisions.last() is AdaptiveVideoProfileDecision.Exhausted)
    }

    private fun allProfilesFor(codecPreference: CodecPreference): Set<VideoStreamProfile> =
        buildSet {
            StreamConfig.RESOLUTION_OPTIONS.forEach { resolution ->
                StreamConfig.FPS_OPTIONS.forEach { fps ->
                    StreamConfig.BITRATE_OPTIONS_KBPS.forEach { bitrateKbps ->
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
