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
import androidx.compose.ui.test.assertIsEnabled
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
    fun start_and_stop_buttons_trigger_the_matching_callbacks() {
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
    fun error_messages_are_shown_on_screen() {
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
    fun quest3_lan_endpoint_hint_is_displayed() {
        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = StreamConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
        )

        composeRule
            .onNodeWithText(
                "Use ws://192.168.1.20:8080/ws for a LAN receiver app, or wss://signal.example/ws for a deployed browser preview. Use 10.0.2.2 only on the Android emulator.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Example: ws://192.168.1.20:8080/ws or wss://signal.example/ws").assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.SCAN_BUTTON).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Scan the receiver QR code to autofill the exact ws:// or wss:// endpoint and start streaming immediately.",
            ).assertIsDisplayed()
    }

    @Test
    fun scan_button_triggers_its_callback() {
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
    fun scan_status_shows_the_most_recent_receiver_result() {
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
                                    host = "preview.relavr.example",
                                    port = 443,
                                    authRequired = true,
                                    scheme = "wss",
                                    path = "/ws",
                                ),
                        ),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
        )

        composeRule
            .onNodeWithText("Last scan: Living Room (wss://preview.relavr.example:443/ws). The receiver still requires local confirmation.")
            .assertIsDisplayed()
    }

    @Test
    fun invalid_config_disables_start_while_inputs_still_forward_callbacks() {
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
    fun audio_toggle_works_and_shows_degradation_hints() {
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
    fun pending_audio_permission_disables_the_audio_toggle_and_start_button() {
        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = validConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                    audioPermissionRequestPending = true,
                ),
        )

        composeRule.onNodeWithText("Permission requested. Complete the system dialog to continue.").assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.AUDIO_SWITCH).assertIsNotEnabled()
        composeRule.onNodeWithTag(StreamControlTestTags.START_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun permanently_denied_audio_permission_shows_the_settings_action() {
        var openSettingsCount = 0

        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config =
                        StreamConfig(
                            signalingEndpoint = VALID_SIGNALING_ENDPOINT,
                            audioEnabled = false,
                        ),
                    sessionSnapshot = StreamingSessionSnapshot(),
                    recordAudioPermissionStatus = RecordAudioPermissionStatus.PermanentlyDenied,
                ),
            onOpenAudioPermissionSettingsClicked = { openSettingsCount += 1 },
        )

        composeRule
            .onNodeWithText("Audio permission was permanently denied. Open system settings to re-enable audio capture.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.AUDIO_SWITCH).assertIsNotEnabled()
        composeRule.onNodeWithTag(StreamControlTestTags.START_BUTTON).assertIsEnabled()
        composeRule.onNodeWithTag(StreamControlTestTags.AUDIO_PERMISSION_SETTINGS_BUTTON).performClick()
        assertEquals(1, openSettingsCount)
    }

    @Test
    fun codec_options_are_switchable_and_unsupported_ones_stay_disabled() {
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
    fun codec_fallback_shows_the_requested_and_resolved_codec() {
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
    fun profile_options_are_switchable_and_callbacks_receive_user_selections() {
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
    fun profile_card_and_action_buttons_remain_visible_at_narrow_widths() {
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
        onOpenAudioPermissionSettingsClicked: () -> Unit = {},
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
                    onOpenAudioPermissionSettingsClicked = onOpenAudioPermissionSettingsClicked,
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
