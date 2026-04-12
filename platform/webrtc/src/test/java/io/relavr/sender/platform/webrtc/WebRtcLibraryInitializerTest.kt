package io.relavr.sender.platform.webrtc

import org.junit.Assert.assertEquals
import org.junit.Test

class WebRtcLibraryInitializerTest {
    @Test
    fun `ensureInitialized performs initialization only once`() {
        var initializeCount = 0
        val initializer =
            WebRtcLibraryInitializer {
                initializeCount += 1
            }

        initializer.ensureInitialized()
        initializer.ensureInitialized()

        assertEquals(1, initializeCount)
    }
}
