package io.relavr.sender.app

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.testing.fakes.FakeCodecCapabilityRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedCodecCapabilityRepositoryTest {
    @Test
    fun `返回 Android 与 WebRTC 编码能力交集`() =
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
                                    audioPlaybackCaptureSupported = true,
                                    defaultCodec = CodecPreference.H264,
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
            assertEquals(CodecPreference.H264, capabilities.defaultCodec)
            assertTrue(capabilities.audioPlaybackCaptureSupported)
        }

    @Test
    fun `H264 不可用时默认编码按回退顺序解析`() =
        runTest {
            val repository =
                CombinedCodecCapabilityRepository(
                    androidCapabilityRepository =
                        FakeCodecCapabilityRepository(
                            snapshot =
                                CapabilitySnapshot(
                                    supportedCodecs = setOf(CodecPreference.HEVC, CodecPreference.VP8),
                                    audioPlaybackCaptureSupported = false,
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
