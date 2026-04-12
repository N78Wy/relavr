package io.relavr.sender.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.relavr.sender.feature.streamcontrol.StreamControlTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `the main screen shows the start streaming button after launch`() {
        composeRule.onNodeWithTag(StreamControlTestTags.START_BUTTON).fetchSemanticsNode()
    }
}
