package io.relavr.sender.platform.discovery

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNsdReceiverDiscoverySourceTest {
    @Test
    fun `sender discovery 服务类型与 Android NSD 规范一致`() {
        assertEquals("_relavr-recv._tcp", RECEIVER_DISCOVERY_SERVICE_TYPE)
    }

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

    @Test
    fun `错误码文案会映射内部错误`() {
        assertEquals("系统内部错误（0）", describeDiscoveryError(0))
        assertEquals("缺少本地网络相关权限（7）", describeDiscoveryError(7))
    }
}
