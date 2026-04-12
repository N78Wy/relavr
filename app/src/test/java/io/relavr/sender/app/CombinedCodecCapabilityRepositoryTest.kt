package io.relavr.sender.app

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.VideoResolution
import io.relavr.sender.core.model.VideoStreamProfile
import io.relavr.sender.testing.fakes.FakeCodecCapabilityRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CombinedCodecCapabilityRepositoryTest {
    @Test
    fun `the intersection of android and webrtc codec capability is returned`() =
        runTest {
            val repository =
                CombinedCodecCapabilityRepository(
                    androidCapabilityRepository =
                        FakeCodecCapabilityRepository(
                            snapshot =
                                CapabilitySnapshot(
                                    supportedCodecs =
                                        setOf(
                                            CodecPreference.H264,
                                            CodecPreference.HEVC,
                                            CodecPreference.VP9,
                                        ),
                                    defaultCodec = CodecPreference.H264,
                                    supportedProfiles =
                                        setOf(
                                            VideoStreamProfile(
                                                codecPreference = CodecPreference.H264,
                                                resolution = VideoResolution(width = 1280, height = 720),
                                                fps = 30,
                                                bitrateKbps = 4000,
                                            ),
                                            VideoStreamProfile(
                                                codecPreference = CodecPreference.HEVC,
                                                resolution = VideoResolution(width = 1920, height = 1080),
                                                fps = 60,
                                                bitrateKbps = 8000,
                                            ),
                                            VideoStreamProfile(
                                                codecPreference = CodecPreference.VP9,
                                                resolution = VideoResolution(width = 1280, height = 720),
                                                fps = 30,
                                                bitrateKbps = 4000,
                                            ),
                                        ),
                                ),
                        ),
                    webRtcCodecSupportProvider = {
                        setOf(CodecPreference.H264, CodecPreference.VP8, CodecPreference.VP9)
                    },
                )

            val capabilities = repository.getCapabilities()

            assertEquals(
                setOf(CodecPreference.H264, CodecPreference.VP9),
                capabilities.supportedCodecs,
            )
            assertEquals(
                setOf(
                    VideoStreamProfile(
                        codecPreference = CodecPreference.H264,
                        resolution = VideoResolution(width = 1280, height = 720),
                        fps = 30,
                        bitrateKbps = 4000,
                    ),
                    VideoStreamProfile(
                        codecPreference = CodecPreference.VP9,
                        resolution = VideoResolution(width = 1280, height = 720),
                        fps = 30,
                        bitrateKbps = 4000,
                    ),
                ),
                capabilities.supportedProfiles,
            )
            assertEquals(CodecPreference.H264, capabilities.defaultCodec)
        }

    @Test
    fun `the default codec follows the fallback order when h264 is unavailable`() =
        runTest {
            val repository =
                CombinedCodecCapabilityRepository(
                    androidCapabilityRepository =
                        FakeCodecCapabilityRepository(
                            snapshot =
                                CapabilitySnapshot(
                                    supportedCodecs = setOf(CodecPreference.HEVC, CodecPreference.VP8),
                                    defaultCodec = CodecPreference.HEVC,
                                ),
                        ),
                    webRtcCodecSupportProvider = {
                        setOf(CodecPreference.HEVC, CodecPreference.VP8, CodecPreference.VP9)
                    },
                )

            val capabilities = repository.getCapabilities()

            assertEquals(
                setOf(CodecPreference.HEVC, CodecPreference.VP8),
                capabilities.supportedCodecs,
            )
            assertEquals(CodecPreference.HEVC, capabilities.defaultCodec)
        }
}
