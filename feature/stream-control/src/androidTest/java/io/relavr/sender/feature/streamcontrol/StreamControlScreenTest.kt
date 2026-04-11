package io.relavr.sender.feature.streamcontrol

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamControlScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `开始和停止按钮会触发对应动作`() {
        var startCount = 0
        var stopCount = 0
        var uiState by mutableStateOf(
            buildStreamControlUiState(
                config = StreamConfig(),
                sessionSnapshot = StreamingSessionSnapshot(),
            ),
        )

        composeRule.setContent {
            streamControlScreen(
                uiState = uiState,
                onCodecSelected = {},
                onAudioEnabledChanged = {},
                onStartClicked = { startCount += 1 },
                onStopClicked = { stopCount += 1 },
            )
        }

        composeRule.onNodeWithTag(StreamControlTestTags.START_BUTTON).performClick()
        assertEquals(1, startCount)

        uiState =
            buildStreamControlUiState(
                config = StreamConfig(),
                sessionSnapshot =
                    StreamingSessionSnapshot(
                        captureState = CaptureState.Capturing,
                        publishState = PublishState.Publishing,
                    ),
            )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(StreamControlTestTags.STOP_BUTTON).performClick()
        assertEquals(1, stopCount)
    }

    @Test
    fun `错误信息会显示在界面上`() {
        composeRule.setContent {
            streamControlScreen(
                uiState =
                    buildStreamControlUiState(
                        config = StreamConfig(),
                        sessionSnapshot =
                            StreamingSessionSnapshot(
                                error =
                                    io.relavr.sender.core.model.SenderError
                                        .Unexpected("mock-error"),
                            ),
                    ),
                onCodecSelected = {},
                onAudioEnabledChanged = {},
                onStartClicked = {},
                onStopClicked = {},
            )
        }

        composeRule.onNodeWithText("mock-error").fetchSemanticsNode()
    }
}
