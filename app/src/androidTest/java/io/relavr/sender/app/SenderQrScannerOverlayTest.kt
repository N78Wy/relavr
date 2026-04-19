package io.relavr.sender.app

import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCamera2Interop
@RunWith(AndroidJUnit4::class)
class SenderQrScannerOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun permanently_denied_camera_permission_shows_settings_entry() {
        var openSettingsCount = 0

        composeRule.setContent {
            senderQrScannerOverlay(
                permissionState = HeadsetCameraPermissionState.PermanentlyDenied,
                permissionRequestPending = false,
                onOpenSettingsClicked = { openSettingsCount += 1 },
                onDismiss = {},
                onPayloadScanned = {},
                onFailure = {},
            )
        }

        composeRule.onNodeWithTag(SenderQrScannerTestTags.OPEN_SETTINGS_BUTTON).assertIsDisplayed().performClick()
        assertEquals(1, openSettingsCount)
    }
}
