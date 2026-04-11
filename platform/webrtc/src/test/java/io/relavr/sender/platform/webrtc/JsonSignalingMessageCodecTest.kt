package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.session.SignalingMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonSignalingMessageCodecTest {
    @Test
    fun `offer 消息可以完成编解码`() {
        val encoded =
            JsonSignalingMessageCodec.encode(
                SignalingMessage.Offer(
                    sessionId = "room-1",
                    sdp = "v=0",
                ),
            )

        val decoded = JsonSignalingMessageCodec.decode(encoded)

        assertEquals(SignalingMessage.Offer(sessionId = "room-1", sdp = "v=0"), decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `缺少必要字段时解码失败`() {
        JsonSignalingMessageCodec.decode("""{"type":"answer","sessionId":"room-1"}""")
    }
}
