package io.relavr.sender.feature.streamcontrol

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.CodecSelection
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
                config = validConfig(),
                sessionSnapshot = StreamingSessionSnapshot(),
            ),
        )

        composeRule.setContent {
            streamControlScreen(
                uiState = uiState,
                onSignalingEndpointChanged = {},
                onSessionIdChanged = {},
                onCodecPreferenceChanged = {},
                onAudioEnabledChanged = {},
                onStartClicked = { startCount += 1 },
                onStopClicked = { stopCount += 1 },
            )
        }

        composeRule.onNodeWithTag(StreamControlTestTags.START_BUTTON).performClick()
        assertEquals(1, startCount)

        uiState =
            buildStreamControlUiState(
                config = validConfig(),
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
                onSignalingEndpointChanged = {},
                onSessionIdChanged = {},
                onCodecPreferenceChanged = {},
                onAudioEnabledChanged = {},
                onStartClicked = {},
                onStopClicked = {},
            )
        }

        composeRule.onNodeWithText("mock-error").fetchSemanticsNode()
    }

    @Test
    fun `会显示Quest3实机局域网地址提示`() {
        composeRule.setContent {
            streamControlScreen(
                uiState =
                    buildStreamControlUiState(
                        config = StreamConfig(),
                        sessionSnapshot = StreamingSessionSnapshot(),
                    ),
                onSignalingEndpointChanged = {},
                onSessionIdChanged = {},
                onCodecPreferenceChanged = {},
                onAudioEnabledChanged = {},
                onStartClicked = {},
                onStopClicked = {},
            )
        }

        composeRule
            .onNodeWithText("Quest 3 实机请填写开发机局域网地址，例如 ws://192.168.1.20:8080/ws；10.0.2.2 仅适用于 Android 模拟器。")
            .fetchSemanticsNode()
        composeRule.onNodeWithText("例如 ws://192.168.1.20:8080/ws").fetchSemanticsNode()
    }

    @Test
    fun `非法配置时开始按钮禁用且输入会透传回调`() {
        var lastEndpoint = ""
        var lastSessionId = ""
        var uiState by mutableStateOf(
            buildStreamControlUiState(
                config = StreamConfig(signalingEndpoint = "invalid", sessionId = ""),
                sessionSnapshot = StreamingSessionSnapshot(),
            ),
        )

        composeRule.setContent {
            streamControlScreen(
                uiState = uiState,
                onSignalingEndpointChanged = {
                    lastEndpoint = it
                    uiState = uiState.copy(signalingEndpoint = it)
                },
                onSessionIdChanged = {
                    lastSessionId = it
                    uiState = uiState.copy(sessionId = it)
                },
                onCodecPreferenceChanged = {},
                onAudioEnabledChanged = {},
                onStartClicked = {},
                onStopClicked = {},
            )
        }

        composeRule.onNodeWithTag(StreamControlTestTags.START_BUTTON).assertIsNotEnabled()
        composeRule
            .onNodeWithTag(StreamControlTestTags.SIGNALING_ENDPOINT_INPUT)
            .performTextInput("ws://relay.example/ws")
        composeRule
            .onNodeWithTag(StreamControlTestTags.SESSION_ID_INPUT)
            .performTextInput("room-77")

        assertEquals("invalidws://relay.example/ws", lastEndpoint)
        assertEquals("room-77", lastSessionId)
    }

    @Test
    fun `音频开关可操作且会展示降级提示`() {
        var audioEnabled = true

        composeRule.setContent {
            streamControlScreen(
                uiState =
                    buildStreamControlUiState(
                        config = StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT),
                        sessionSnapshot =
                            StreamingSessionSnapshot(
                                audioState = AudioStreamState.Degraded,
                                audioDetail = "音频已降级为静音/仅视频",
                            ),
                    ),
                onSignalingEndpointChanged = {},
                onSessionIdChanged = {},
                onCodecPreferenceChanged = {},
                onAudioEnabledChanged = { audioEnabled = it },
                onStartClicked = {},
                onStopClicked = {},
            )
        }

        composeRule.onNodeWithText("音频已降级为静音/仅视频").fetchSemanticsNode()
        composeRule.onNodeWithTag(StreamControlTestTags.AUDIO_SWITCH).performClick()
        assertEquals(false, audioEnabled)
    }

    @Test
    fun `编码选项可切换且不支持项禁用`() {
        var selectedCodec = CodecPreference.H264

        composeRule.setContent {
            streamControlScreen(
                uiState =
                    buildStreamControlUiState(
                        config =
                            StreamConfig(
                                signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                                codecPreference = selectedCodec,
                            ),
                        sessionSnapshot =
                            StreamingSessionSnapshot(
                                capabilities =
                                    CapabilitySnapshot(
                                        supportedCodecs = setOf(CodecPreference.H264, CodecPreference.HEVC),
                                        audioPlaybackCaptureSupported = true,
                                        defaultCodec = CodecPreference.H264,
                                    ),
                            ),
                    ),
                onSignalingEndpointChanged = {},
                onSessionIdChanged = {},
                onCodecPreferenceChanged = { selectedCodec = it },
                onAudioEnabledChanged = {},
                onStartClicked = {},
                onStopClicked = {},
            )
        }

        composeRule.onNodeWithTag(StreamControlTestTags.codecOption(CodecPreference.HEVC)).performClick()
        composeRule.onNodeWithTag(StreamControlTestTags.codecOption(CodecPreference.VP9)).assertIsNotEnabled()
        assertEquals(CodecPreference.HEVC, selectedCodec)
    }

    @Test
    fun `编码回退时会显示请求与实际编码`() {
        composeRule.setContent {
            streamControlScreen(
                uiState =
                    buildStreamControlUiState(
                        config =
                            StreamConfig(
                                signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                                codecPreference = CodecPreference.HEVC,
                            ),
                        sessionSnapshot =
                            StreamingSessionSnapshot(
                                codecSelection =
                                    CodecSelection(
                                        requested = CodecPreference.HEVC,
                                        resolved = CodecPreference.H264,
                                        fellBack = true,
                                    ),
                            ),
                    ),
                onSignalingEndpointChanged = {},
                onSessionIdChanged = {},
                onCodecPreferenceChanged = {},
                onAudioEnabledChanged = {},
                onStartClicked = {},
                onStopClicked = {},
            )
        }

        composeRule
            .onNodeWithText("本次请求 H.265 / HEVC，实际使用 H.264 / AVC")
            .fetchSemanticsNode()
    }

    private fun validConfig() = StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT)

    private companion object {
        const val VALID_SIGNALING_ENDPOINT = "ws://192.168.1.20:8080/ws"
    }
}
