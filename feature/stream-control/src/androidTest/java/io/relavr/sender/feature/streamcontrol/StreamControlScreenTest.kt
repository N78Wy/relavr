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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.relavr.sender.core.model.CaptureState
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.CodecSelection
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
    fun home_page_shows_dual_entry_actions_and_opens_settings_page() {
        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = validConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
        )

        composeRule.onNodeWithTag(StreamControlTestTags.HOME_PAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.SCAN_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.HOST_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.PORT_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.SETTINGS_BUTTON).performClick()

        composeRule.onNodeWithTag(StreamControlTestTags.SETTINGS_PAGE).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.PATH_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.SESSION_ID_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.STREAM_PROFILE_CARD).assertIsDisplayed()
    }

    @Test
    fun scan_button_triggers_callback() {
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
    fun start_and_stop_buttons_trigger_matching_callbacks() {
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
    fun manual_connection_inputs_forward_callbacks() {
        var lastHost = ""
        var lastPort = ""

        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = StreamConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
            onSignalingHostChanged = { lastHost = it },
            onSignalingPortChanged = { lastPort = it },
        )

        composeRule.onNodeWithTag(StreamControlTestTags.HOST_INPUT).performTextInput("192.168.1.20")
        composeRule.onNodeWithTag(StreamControlTestTags.PORT_INPUT).performTextClearance()
        composeRule.onNodeWithTag(StreamControlTestTags.PORT_INPUT).performTextInput("9000")

        assertEquals("192.168.1.20", lastHost)
        assertEquals("9000", lastPort)
    }

    @Test
    fun settings_page_controls_forward_secondary_callbacks() {
        var selectedScheme = "ws"
        var lastPath = ""
        var lastSessionId = ""
        var selectedResolution = StreamConfig.DEFAULT_RESOLUTION

        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = validConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
            onSignalingSchemeChanged = { selectedScheme = it },
            onSignalingPathChanged = { lastPath = it },
            onSessionIdChanged = { lastSessionId = it },
            onResolutionChanged = { selectedResolution = it },
        )

        composeRule.onNodeWithTag(StreamControlTestTags.SETTINGS_BUTTON).performClick()
        composeRule.onNodeWithTag(StreamControlTestTags.SCHEME_WSS_BUTTON).performClick()
        composeRule.onNodeWithTag(StreamControlTestTags.PATH_INPUT).performTextClearance()
        composeRule.onNodeWithTag(StreamControlTestTags.PATH_INPUT).performTextInput("/relay")
        composeRule.onNodeWithTag(StreamControlTestTags.SESSION_ID_INPUT).performTextClearance()
        composeRule.onNodeWithTag(StreamControlTestTags.SESSION_ID_INPUT).performTextInput("room-77")
        composeRule.onNodeWithTag(StreamControlTestTags.resolutionOption(VideoResolution(width = 1920, height = 1080))).performClick()

        assertEquals("wss", selectedScheme)
        assertEquals("/relay", lastPath)
        assertEquals("room-77", lastSessionId)
        assertEquals(VideoResolution(width = 1920, height = 1080), selectedResolution)
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
    fun invalid_manual_config_disables_start_button() {
        setStreamControlContent(
            uiState =
                buildStreamControlUiState(
                    config = StreamConfig(signalingEndpoint = "ws://192.168.1.20/ws"),
                    signalingEndpointDraft =
                        SignalingEndpointDraft(
                            host = "192.168.1.20",
                            port = "",
                            path = "/ws",
                        ),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
        )

        composeRule.onNodeWithTag(StreamControlTestTags.START_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun narrow_width_keeps_manual_connect_and_settings_entry_visible() {
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

        composeRule.onNodeWithTag(StreamControlTestTags.HOST_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.PORT_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.START_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(StreamControlTestTags.SETTINGS_BUTTON).assertIsDisplayed()
    }

    @Test
    fun codec_fallback_message_remains_visible_in_settings_page() {
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

        composeRule.onNodeWithTag(StreamControlTestTags.SETTINGS_BUTTON).performClick()
        composeRule.onNodeWithText("Requested H.265 / HEVC. Using H.264 / AVC instead.").assertIsDisplayed()
    }

    private fun setStreamControlContent(
        uiState: StreamControlUiState,
        containerModifier: Modifier = Modifier,
        onSignalingEndpointChanged: (String) -> Unit = {},
        onSignalingSchemeChanged: (String) -> Unit = {},
        onSignalingHostChanged: (String) -> Unit = {},
        onSignalingPortChanged: (String) -> Unit = {},
        onSignalingPathChanged: (String) -> Unit = {},
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
                    onSignalingSchemeChanged = onSignalingSchemeChanged,
                    onSignalingHostChanged = onSignalingHostChanged,
                    onSignalingPortChanged = onSignalingPortChanged,
                    onSignalingPathChanged = onSignalingPathChanged,
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
