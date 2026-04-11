package io.relavr.sender.platform.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNsdReceiverDiscoverySourceTest {
    @Test
    fun `TXT 字段会按 utf8 解码`() {
        val attributes =
            mapOf(
                "name" to "Living Room".toByteArray(Charsets.UTF_8),
                "sessionId" to "quest3-demo".toByteArray(Charsets.UTF_8),
            )

        assertEquals(
            mapOf(
                "name" to "Living Room",
                "sessionId" to "quest3-demo",
            ),
            attributes.toUtf8Map(),
        )
    }
}
