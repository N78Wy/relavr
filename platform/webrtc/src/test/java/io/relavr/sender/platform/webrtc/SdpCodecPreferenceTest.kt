package io.relavr.sender.platform.webrtc

import org.junit.Assert.assertTrue
import org.junit.Test

class SdpCodecPreferenceTest {
    @Test
    fun `preferVideoCodec 会把 H264 payload 提前到 video m line 前部`() {
        assertCodecReordered(
            codecName = "H264",
            mediaLinePayloads = "96 97 100 101 98 99 102 103",
            expectedMediaLinePrefix = "m=video 9 UDP/TLS/RTP/SAVPF 98 99",
        )
    }

    @Test
    fun `preferVideoCodec 会把 H265 payload 提前到 video m line 前部`() {
        assertCodecReordered(
            codecName = "H265",
            mediaLinePayloads = "96 97 98 99 102 103 100 101",
            expectedMediaLinePrefix = "m=video 9 UDP/TLS/RTP/SAVPF 100 101",
        )
    }

    @Test
    fun `preferVideoCodec 会把 VP8 payload 提前到 video m line 前部`() {
        assertCodecReordered(
            codecName = "VP8",
            mediaLinePayloads = "98 99 100 101 102 103 96 97",
            expectedMediaLinePrefix = "m=video 9 UDP/TLS/RTP/SAVPF 96 97",
        )
    }

    @Test
    fun `preferVideoCodec 会把 VP9 payload 提前到 video m line 前部`() {
        assertCodecReordered(
            codecName = "VP9",
            mediaLinePayloads = "96 97 98 99 100 101 102 103",
            expectedMediaLinePrefix = "m=video 9 UDP/TLS/RTP/SAVPF 102 103",
        )
    }

    private fun assertCodecReordered(
        codecName: String,
        mediaLinePayloads: String,
        expectedMediaLinePrefix: String,
    ) {
        val original =
            """
            v=0
            o=- 0 0 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=video 9 UDP/TLS/RTP/SAVPF $mediaLinePayloads
            a=rtpmap:96 VP8/90000
            a=rtpmap:97 rtx/90000
            a=fmtp:97 apt=96
            a=rtpmap:98 H264/90000
            a=rtpmap:99 rtx/90000
            a=fmtp:99 apt=98
            a=rtpmap:100 H265/90000
            a=rtpmap:101 rtx/90000
            a=fmtp:101 apt=100
            a=rtpmap:102 VP9/90000
            a=rtpmap:103 rtx/90000
            a=fmtp:103 apt=102
            """.trimIndent().replace("\n", "\r\n")

        val preferred = SdpCodecPreference.preferVideoCodec(original, codecName)

        assertTrue(preferred.contains(expectedMediaLinePrefix))
    }
}
