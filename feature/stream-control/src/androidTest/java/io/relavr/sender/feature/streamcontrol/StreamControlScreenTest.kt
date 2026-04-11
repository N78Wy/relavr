package io.relavr.sender.feature.streamcontrol

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.relavr.sender.core.model.AudioStreamState
import io.relavr.sender.core.model.CapabilitySnapshot
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.CodecSelection
import io.relavr.sender.core.model.DiscoveredReceiver
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.VideoResolution
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

        setStreamControlContent(
            uiState = uiState,
            onStartClicked = { startCount += 1 },
            onStopClicked = { stopCount += 1 },
        )

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
        setStreamControlContent(
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
        )

        composeRule.onNodeWithText("mock-error").assertIsDisplayed()
    }

    @Test
    fun `会显示Quest3实机局域网地址提示`() {
        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = StreamConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
        )

        composeRule
            .onNodeWithText("Quest 3 实机请填写开发机局域网地址，例如 ws://192.168.1.20:8080/ws；10.0.2.2 仅适用于 Android 模拟器。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("例如 ws://192.168.1.20:8080/ws").assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.SCAN_BUTTON).assertIsDisplayed()
        composeRule
            .onNodeWithText("扫描 receiver 控制台二维码后会自动回填地址并立即开播")
            .assertIsDisplayed()
    }

    @Test
    fun `扫码按钮会触发回调`() {
        var openScannerCount = 0

        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = validConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
            onOpenScannerClicked = { openScannerCount += 1 },
        )

        composeRule.onNodeWithTag(StreamControlTestTags.SCAN_BUTTON).performClick()
        assertEquals(1, openScannerCount)
    }

    @Test
    fun `发现列表会展示接收端并触发回调`() {
        var clickedReceiver: DiscoveredReceiver? = null

        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = validConfig(),
                    discoveryState =
                        io.relavr.sender.core.model.ReceiverDiscoverySnapshot(
                            receivers =
                                listOf(
                                    DiscoveredReceiver(
                                        serviceName = "living-room",
                                        receiverName = "Living Room",
                                        sessionId = "room-1",
                                        host = "192.168.1.20",
                                        port = 17888,
                                        authRequired = true,
                                    ),
                                ),
                        ),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
            onDiscoveredReceiverClicked = { clickedReceiver = it },
        )

        composeRule.onNodeWithText("局域网接收端").assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.discoveryReceiver("living-room")).performClick()
        assertEquals("Living Room", clickedReceiver?.receiverName)
    }

    @Test
    fun `发现确认弹窗按钮会触发回调`() {
        var confirmCount = 0
        var dismissCount = 0

        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = validConfig(),
                    pendingReceiver =
                        DiscoveredReceiver(
                            serviceName = "living-room",
                            receiverName = "Living Room",
                            sessionId = "room-1",
                            host = "192.168.1.20",
                            port = 17888,
                            authRequired = false,
                        ),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
            onDiscoveryConnectionDismissed = { dismissCount += 1 },
            onDiscoveryConnectionConfirmed = { confirmCount += 1 },
        )

        composeRule.onNodeWithText("连接到 Living Room").assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.DISCOVERY_CONFIRM_BUTTON).performClick()
        composeRule.onNodeWithTag(StreamControlTestTags.DISCOVERY_CANCEL_BUTTON).performClick()
        assertEquals(1, confirmCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun `扫码状态文案会显示最近扫描结果`() {
        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = validConfig(),
                    scannerState =
                        QrScannerState(
                            lastReceiver =
                                io.relavr.sender.core.model.ReceiverConnectionInfo(
                                    receiverName = "Living Room",
                                    sessionId = "demo",
                                    host = "192.168.1.20",
                                    port = 17888,
                                    authRequired = true,
                                ),
                        ),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
        )

        composeRule
            .onNodeWithText("最近扫码：Living Room（192.168.1.20:17888），接收端仍需本地确认")
            .assertIsDisplayed()
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

        setStreamControlContent(
            uiState = uiState,
            onSignalingEndpointChanged = {
                lastEndpoint = it
                uiState = uiState.copy(signalingEndpoint = it)
            },
            onSessionIdChanged = {
                lastSessionId = it
                uiState = uiState.copy(sessionId = it)
            },
        )

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

        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT),
                    sessionSnapshot =
                        StreamingSessionSnapshot(
                            audioState = AudioStreamState.Degraded,
                            audioDetail = "音频已降级为静音/仅视频",
                        ),
                ),
            onAudioEnabledChanged = { audioEnabled = it },
        )

        composeRule.onNodeWithText("音频已降级为静音/仅视频").assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.AUDIO_SWITCH).performClick()
        assertEquals(false, audioEnabled)
    }

    @Test
    fun `编码选项可切换且不支持项禁用`() {
        var selectedCodec = CodecPreference.H264

        setStreamControlContent(
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
            onCodecPreferenceChanged = { selectedCodec = it },
        )

        composeRule.onNodeWithTag(StreamControlTestTags.codecOption(CodecPreference.HEVC)).performClick()
        composeRule.onNodeWithTag(StreamControlTestTags.codecOption(CodecPreference.VP9)).assertIsNotEnabled()
        assertEquals(CodecPreference.HEVC, selectedCodec)
    }

    @Test
    fun `编码回退时会显示请求与实际编码`() {
        setStreamControlContent(
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
        )

        composeRule
            .onNodeWithText("本次请求 H.265 / HEVC，实际使用 H.264 / AVC")
            .assertIsDisplayed()
    }

    @Test
    fun `规格选项可切换且回调会收到用户选择`() {
        var selectedResolution = StreamConfig.DEFAULT_RESOLUTION
        var selectedFps = StreamConfig.DEFAULT_FPS
        var selectedBitrate = StreamConfig.DEFAULT_BITRATE_KBPS

        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = validConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
            onResolutionChanged = { selectedResolution = it },
            onFpsChanged = { selectedFps = it },
            onBitrateChanged = { selectedBitrate = it },
        )

        composeRule
            .onNodeWithTag(
                StreamControlTestTags.resolutionOption(VideoResolution(width = 1920, height = 1080)),
            ).performClick()
        composeRule.onNodeWithTag(StreamControlTestTags.fpsOption(60)).performClick()
        composeRule.onNodeWithTag(StreamControlTestTags.bitrateOption(8000)).performClick()

        assertEquals(VideoResolution(width = 1920, height = 1080), selectedResolution)
        assertEquals(60, selectedFps)
        assertEquals(8000, selectedBitrate)
    }

    @Test
    fun `窄宽度下规格卡和操作按钮仍然可见`() {
        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = validConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
            containerModifier =
                Modifier
                    .requiredWidth(360.dp)
                    .requiredHeight(1200.dp),
        )

        composeRule.onNodeWithTag(StreamControlTestTags.STREAM_PROFILE_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.START_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.STOP_BUTTON).assertIsDisplayed()
        composeRule
            .onNodeWithTag(StreamControlTestTags.resolutionOption(StreamConfig.DEFAULT_RESOLUTION))
            .assertIsDisplayed()
    }

    private fun setStreamControlContent(
        uiState: StreamControlUiState,
        containerModifier: Modifier = Modifier,
        onSignalingEndpointChanged: (String) -> Unit = {},
        onSessionIdChanged: (String) -> Unit = {},
        onCodecPreferenceChanged: (CodecPreference) -> Unit = {},
        onAudioEnabledChanged: (Boolean) -> Unit = {},
        onOpenScannerClicked: () -> Unit = {},
        onDiscoveryRefreshClicked: () -> Unit = {},
        onDiscoveredReceiverClicked: (DiscoveredReceiver) -> Unit = {},
        onDiscoveryConnectionDismissed: () -> Unit = {},
        onDiscoveryConnectionConfirmed: () -> Unit = {},
        onResolutionChanged: (VideoResolution) -> Unit = {},
        onFpsChanged: (Int) -> Unit = {},
        onBitrateChanged: (Int) -> Unit = {},
        onStartClicked: () -> Unit = {},
        onStopClicked: () -> Unit = {},
    ) {
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxSize().then(containerModifier)) {
                streamControlScreen(
                    uiState = uiState,
                    onSignalingEndpointChanged = onSignalingEndpointChanged,
                    onSessionIdChanged = onSessionIdChanged,
                    onCodecPreferenceChanged = onCodecPreferenceChanged,
                    onAudioEnabledChanged = onAudioEnabledChanged,
                    onOpenScannerClicked = onOpenScannerClicked,
                    onDiscoveryRefreshClicked = onDiscoveryRefreshClicked,
                    onDiscoveredReceiverClicked = onDiscoveredReceiverClicked,
                    onDiscoveryConnectionDismissed = onDiscoveryConnectionDismissed,
                    onDiscoveryConnectionConfirmed = onDiscoveryConnectionConfirmed,
                    onResolutionChanged = onResolutionChanged,
                    onFpsChanged = onFpsChanged,
                    onBitrateChanged = onBitrateChanged,
                    onStartClicked = onStartClicked,
                    onStopClicked = onStopClicked,
                )
            }
        }
    }

    private fun validConfig() = StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT)

    private companion object {
        const val VALID_SIGNALING_ENDPOINT = "ws://192.168.1.20:8080/ws"
    }
}
