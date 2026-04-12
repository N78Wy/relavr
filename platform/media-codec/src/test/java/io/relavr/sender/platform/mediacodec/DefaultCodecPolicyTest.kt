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
    fun `the selected codec is returned first when supported`() {
        val selection =
            policy.select(
                preference = CodecPreference.HEVC,
                capabilities =
                    CapabilitySnapshot(
                        supportedCodecs = setOf(CodecPreference.H264, CodecPreference.HEVC),
                        defaultCodec = CodecPreference.H264,
                    ),
            )

        assertEquals(CodecPreference.HEVC, selection.resolved)
        assertTrue(!selection.fellBack)
    }

    @Test
    fun `unsupported selections fall back to h264`() {
        val selection =
            policy.select(
                preference = CodecPreference.VP9,
                capabilities =
                    CapabilitySnapshot(
                        supportedCodecs = setOf(CodecPreference.H264, CodecPreference.HEVC),
                        defaultCodec = CodecPreference.H264,
                    ),
            )

        assertEquals(CodecPreference.H264, selection.resolved)
        assertTrue(selection.fellBack)
    }

    @Test
    fun `the device default is used when h264 is unavailable`() {
        val selection =
            policy.select(
                preference = CodecPreference.H264,
                capabilities =
                    CapabilitySnapshot(
                        supportedCodecs = setOf(CodecPreference.HEVC, CodecPreference.VP8),
                        defaultCodec = CodecPreference.HEVC,
                    ),
            )

        assertEquals(CodecPreference.HEVC, selection.resolved)
        assertTrue(selection.fellBack)
    }

    @Test(expected = SenderException::class)
    fun `an exception is thrown when the device has no codec capability`() {
        policy.select(
            preference = CodecPreference.H264,
            capabilities =
                CapabilitySnapshot(
                    supportedCodecs = emptySet(),
                ),
        )
    }
}
