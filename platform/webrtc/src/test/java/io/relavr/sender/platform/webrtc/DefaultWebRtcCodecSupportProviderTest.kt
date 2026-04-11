package io.relavr.sender.platform.webrtc

import io.relavr.sender.core.model.CodecPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultWebRtcCodecSupportProviderTest {
    @Test
    fun `getSupportedCodecs 会先初始化 native 再读取编码列表`() {
        val callOrder = mutableListOf<String>()
        val initializer =
            WebRtcLibraryInitializer {
                callOrder += "init"
            }
        val provider =
            DefaultWebRtcCodecSupportProvider(
                libraryInitializer = initializer,
                supportedCodecNamesProvider = {
                    callOrder += "codecs"
                    listOf("H264", "VP9", "AV1")
                },
            )

        val codecs = provider.getSupportedCodecs()

        assertEquals(listOf("init", "codecs"), callOrder)
        assertEquals(setOf(CodecPreference.H264, CodecPreference.VP9), codecs)
    }
}
