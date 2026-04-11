package io.relavr.sender.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class NetworkSecurityConfigTest {
    @Test
    fun `网络安全配置默认允许明文流量`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = context.resources.getXml(R.xml.network_security_config)

        var foundBaseConfig = false
        var cleartextPermitted: String? = null
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "base-config") {
                foundBaseConfig = true
                cleartextPermitted = parser.getAttributeValue(null, "cleartextTrafficPermitted")
                break
            }
            parser.next()
        }

        assertTrue("network_security_config.xml 中缺少 base-config", foundBaseConfig)
        assertEquals("true", cleartextPermitted)
    }
}
