package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.session.SignalingMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonSignalingMessageCodecTest {
    @Test
    fun `offer messages round trip through the codec`() {
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
    fun `decoding fails when required fields are missing`() {
        JsonSignalingMessageCodec.decode("""{"type":"answer","sessionId":"room-1"}""")
    }
}
