package io.relavr.sender.platform.webrtc

import org.junit.Assert.assertTrue
import org.junit.Test

class SdpCodecPreferenceTest {
    @Test
    fun `preferVideoCodec 会把 H264 payload 提前到 video m line 前部`() {
        val original =
            """
            v=0
            o=- 0 0 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99
            a=rtpmap:96 VP8/90000
            a=rtpmap:97 rtx/90000
            a=fmtp:97 apt=96
            a=rtpmap:98 H264/90000
            a=rtpmap:99 rtx/90000
            a=fmtp:99 apt=98
            """.trimIndent().replace("\n", "\r\n")

        val preferred = SdpCodecPreference.preferVideoCodec(original, "H264")

        assertTrue(preferred.contains("m=video 9 UDP/TLS/RTP/SAVPF 98 99 96 97"))
    }
}
