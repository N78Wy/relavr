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
import io.relavr.sender.core.model.PublishState
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.UiText
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
    fun `start and stop buttons trigger the matching callbacks`() {
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
    fun `error messages are shown on screen`() {
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

        composeRule.onNodeWithText("An unexpected sender error occurred.").assertIsDisplayed()
    }

    @Test
    fun `the quest3 lan endpoint hint is displayed`() {
        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = StreamConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
        )

        composeRule
            .onNodeWithText(
                "On a Quest 3 device, use the development machine LAN address such as ws://192.168.1.20:8080/ws. Use 10.0.2.2 only on the Android emulator.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Example: ws://192.168.1.20:8080/ws").assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.SCAN_BUTTON).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Scan the receiver console QR code to autofill the endpoint and start streaming immediately.",
            ).assertIsDisplayed()
    }

    @Test
    fun `the scan button triggers its callback`() {
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
    fun `the scan status shows the most recent receiver result`() {
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
            .onNodeWithText("Last scan: Living Room (192.168.1.20:17888). The receiver still requires local confirmation.")
            .assertIsDisplayed()
    }

    @Test
    fun `invalid config disables start while inputs still forward callbacks`() {
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
        composeRule.onNodeWithTag(StreamControlTestTags.SIGNALING_ENDPOINT_INPUT).performTextInput("ws://relay.example/ws")
        composeRule.onNodeWithTag(StreamControlTestTags.SESSION_ID_INPUT).performTextInput("room-77")

        assertEquals("invalidws://relay.example/ws", lastEndpoint)
        assertEquals("room-77", lastSessionId)
    }

    @Test
    fun `the audio toggle works and shows degradation hints`() {
        var audioEnabled = true

        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = StreamConfig(signalingEndpoint = VALID_SIGNALING_ENDPOINT),
                    sessionSnapshot =
                        StreamingSessionSnapshot(
                            audioState = AudioStreamState.Degraded,
                            audioDetail = UiText.of(io.relavr.sender.core.model.R.string.sender_audio_degraded_video_only),
                        ),
                ),
            onAudioEnabledChanged = { audioEnabled = it },
        )

        composeRule.onNodeWithText("Audio capture degraded. Continuing with video only.").assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.AUDIO_SWITCH).performClick()
        assertEquals(false, audioEnabled)
    }

    @Test
    fun `codec options are switchable and unsupported ones stay disabled`() {
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
    fun `codec fallback shows the requested and resolved codec`() {
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

        composeRule.onNodeWithText("Requested H.265 / HEVC. Using H.264 / AVC instead.").assertIsDisplayed()
    }

    @Test
    fun `profile options are switchable and callbacks receive user selections`() {
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

        composeRule.onNodeWithTag(StreamControlTestTags.resolutionOption(VideoResolution(width = 1920, height = 1080))).performClick()
        composeRule.onNodeWithTag(StreamControlTestTags.fpsOption(60)).performClick()
        composeRule.onNodeWithTag(StreamControlTestTags.bitrateOption(8000)).performClick()

        assertEquals(VideoResolution(width = 1920, height = 1080), selectedResolution)
        assertEquals(60, selectedFps)
        assertEquals(8000, selectedBitrate)
    }

    @Test
    fun `the profile card and action buttons remain visible at narrow widths`() {
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
        composeRule.onNodeWithTag(StreamControlTestTags.resolutionOption(StreamConfig.DEFAULT_RESOLUTION)).assertIsDisplayed()
    }

    private fun setStreamControlContent(
        uiState: StreamControlUiState,
        containerModifier: Modifier = Modifier,
        onSignalingEndpointChanged: (String) -> Unit = {},
        onSessionIdChanged: (String) -> Unit = {},
        onCodecPreferenceChanged: (CodecPreference) -> Unit = {},
        onAudioEnabledChanged: (Boolean) -> Unit = {},
        onOpenScannerClicked: () -> Unit = {},
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
                    selectedLanguageTag = "en",
                    onLanguageTagSelected = {},
                    onSignalingEndpointChanged = onSignalingEndpointChanged,
                    onSessionIdChanged = onSessionIdChanged,
                    onCodecPreferenceChanged = onCodecPreferenceChanged,
                    onAudioEnabledChanged = onAudioEnabledChanged,
                    onOpenScannerClicked = onOpenScannerClicked,
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
