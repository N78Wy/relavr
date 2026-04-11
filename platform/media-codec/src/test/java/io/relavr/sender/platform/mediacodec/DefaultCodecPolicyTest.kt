package io.relavr.sender.platform.mediacodec

import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.session.SenderException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCodecPolicyTest {
    private val policy = DefaultCodecPolicy()

    @Test
    fun `优先返回用户选择的编码`() {
        val selection =
            policy.select(
                preference = CodecPreference.HEVC,
                capabilities =
                    CapabilitySnapshot(
                        supportedCodecs = setOf(CodecPreference.H264, CodecPreference.HEVC),
                        audioPlaybackCaptureSupported = true,
                        defaultCodec = CodecPreference.H264,
                    ),
            )

        assertEquals(CodecPreference.HEVC, selection.resolved)
        assertTrue(!selection.fellBack)
    }

    @Test
    fun `用户选择不支持时回退到 H264`() {
        val selection =
            policy.select(
                preference = CodecPreference.VP9,
                capabilities =
                    CapabilitySnapshot(
                        supportedCodecs = setOf(CodecPreference.H264, CodecPreference.HEVC),
                        audioPlaybackCaptureSupported = true,
                        defaultCodec = CodecPreference.H264,
                    ),
            )

        assertEquals(CodecPreference.H264, selection.resolved)
        assertTrue(selection.fellBack)
    }

    @Test
    fun `H264 不可用时回退到设备默认编码`() {
        val selection =
            policy.select(
                preference = CodecPreference.H264,
                capabilities =
                    CapabilitySnapshot(
                        supportedCodecs = setOf(CodecPreference.HEVC, CodecPreference.VP8),
                        audioPlaybackCaptureSupported = true,
                        defaultCodec = CodecPreference.HEVC,
                    ),
            )

        assertEquals(CodecPreference.HEVC, selection.resolved)
        assertTrue(selection.fellBack)
    }

    @Test(expected = SenderException::class)
    fun `设备没有任何编码能力时抛错`() {
        policy.select(
            preference = CodecPreference.H264,
            capabilities =
                CapabilitySnapshot(
                    supportedCodecs = emptySet(),
                    audioPlaybackCaptureSupported = false,
                ),
        )
    }
}
