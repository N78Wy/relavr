package io.relavr.sender.app

import android.content.pm.PackageManager
import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkSecurityPolicyTest {
    @Test
    fun `应用已声明网络访问权限`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo =
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requestedPermissions = packageInfo.requestedPermissions.orEmpty()

        assertTrue(requestedPermissions.contains(android.Manifest.permission.INTERNET))
    }

    @Test
    fun `应用允许模拟器和局域网信令地址使用明文流量`() {
        val policy = NetworkSecurityPolicy.getInstance()

        assertTrue(policy.isCleartextTrafficPermitted("10.0.2.2"))
        assertTrue(policy.isCleartextTrafficPermitted("192.168.1.20"))
    }
}
