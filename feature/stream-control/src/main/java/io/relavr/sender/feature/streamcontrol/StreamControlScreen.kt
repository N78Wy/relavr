package io.relavr.sender.feature.streamcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.VideoResolution

object StreamControlTestTags {
    const val HOME_PAGE = "stream-home-page"
    const val SETTINGS_PAGE = "stream-settings-page"
    const val START_BUTTON = "stream-start"
    const val STOP_BUTTON = "stream-stop"
    const val SCAN_BUTTON = "stream-scan"
    const val SETTINGS_BUTTON = "stream-settings"
    const val SETTINGS_BACK_BUTTON = "stream-settings-back"
    const val AUDIO_ENABLE_BUTTON = "stream-audio-enable"
    const val AUDIO_DISABLE_BUTTON = "stream-audio-disable"
    const val AUDIO_SETTINGS_BUTTON = "stream-audio-settings"
    const val HOST_INPUT = "stream-host-input"
    const val PORT_INPUT = "stream-port-input"
    const val PATH_INPUT = "stream-path-input"
    const val SESSION_ID_INPUT = "stream-session-id"
    const val STREAM_PROFILE_CARD = "stream-profile-card"
    const val SCHEME_WS_BUTTON = "stream-scheme-ws"
    const val SCHEME_WSS_BUTTON = "stream-scheme-wss"

    fun codecOption(preference: CodecPreference): String = "stream-codec-option-${preference.name.lowercase()}"

    fun resolutionOption(resolution: VideoResolution): String = "stream-resolution-option-${resolution.width}x${resolution.height}"

    fun fpsOption(fps: Int): String = "stream-fps-option-$fps"

    fun bitrateOption(bitrateKbps: Int): String = "stream-bitrate-option-$bitrateKbps"
}

internal enum class StreamControlLayoutMode {
    Compact,
    Medium,
    Expanded,
}

private enum class StreamControlPage {
    Home,
    Settings,
}

@Composable
fun streamControlScreen(
    uiState: StreamControlUiState,
    selectedLanguageTag: String,
    onLanguageTagSelected: (String) -> Unit,
    onSignalingEndpointChanged: (String) -> Unit,
    onSignalingSchemeChanged: (String) -> Unit,
    onSignalingHostChanged: (String) -> Unit,
    onSignalingPortChanged: (String) -> Unit,
    onSignalingPathChanged: (String) -> Unit,
    onSessionIdChanged: (String) -> Unit,
    onCodecPreferenceChanged: (CodecPreference) -> Unit,
    onAudioEnabledChanged: (Boolean) -> Unit,
    onOpenAudioPermissionSettingsClicked: () -> Unit,
    onOpenScannerClicked: () -> Unit,
    onResolutionChanged: (VideoResolution) -> Unit,
    onFpsChanged: (Int) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onStartClicked: () -> Unit,
    onStopClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPage by rememberSaveable { mutableStateOf(StreamControlPage.Home) }

    LaunchedEffect(uiState.scannerVisible) {
        if (uiState.scannerVisible) {
            currentPage = StreamControlPage.Home
        }
    }
    BackHandler(enabled = currentPage == StreamControlPage.Settings) {
        currentPage = StreamControlPage.Home
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF05111D)),
    ) {
        val layoutMode =
            when {
                maxWidth < 600.dp -> StreamControlLayoutMode.Compact
                maxWidth < 840.dp -> StreamControlLayoutMode.Medium
                else -> StreamControlLayoutMode.Expanded
            }

        Box(modifier = Modifier.fillMaxSize()) {
            streamBackdrop()

            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    val isForward = targetState == StreamControlPage.Settings
                    slideInHorizontally { fullWidth ->
                        if (isForward) fullWidth / 6 else -fullWidth / 6
                    } + fadeIn() togetherWith slideOutHorizontally { fullWidth ->
                        if (isForward) -fullWidth / 10 else fullWidth / 10
                    } + fadeOut()
                },
                label = "stream-control-page",
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    StreamControlPage.Home ->
                        streamControlHomePage(
                            uiState = uiState,
                            layoutMode = layoutMode,
                            onSignalingEndpointChanged = onSignalingEndpointChanged,
                            onSignalingHostChanged = onSignalingHostChanged,
                            onSignalingPortChanged = onSignalingPortChanged,
                            onOpenScannerClicked = onOpenScannerClicked,
                            onOpenSettingsClicked = { currentPage = StreamControlPage.Settings },
                            onStartClicked = onStartClicked,
                            onStopClicked = onStopClicked,
                            modifier = Modifier.testTag(StreamControlTestTags.HOME_PAGE),
                        )

                    StreamControlPage.Settings ->
                        streamControlSettingsPage(
                            uiState = uiState,
                            selectedLanguageTag = selectedLanguageTag,
                            layoutMode = layoutMode,
                            onBackClicked = { currentPage = StreamControlPage.Home },
                            onLanguageTagSelected = onLanguageTagSelected,
                            onSignalingSchemeChanged = onSignalingSchemeChanged,
                            onSignalingPathChanged = onSignalingPathChanged,
                            onSessionIdChanged = onSessionIdChanged,
                            onCodecPreferenceChanged = onCodecPreferenceChanged,
                            onAudioEnabledChanged = onAudioEnabledChanged,
                            onOpenAudioPermissionSettingsClicked = onOpenAudioPermissionSettingsClicked,
                            onResolutionChanged = onResolutionChanged,
                            onFpsChanged = onFpsChanged,
                            onBitrateChanged = onBitrateChanged,
                            onStartClicked = onStartClicked,
                            onStopClicked = onStopClicked,
                            modifier = Modifier.testTag(StreamControlTestTags.SETTINGS_PAGE),
                        )
                }
            }
        }
    }
}

@Composable
private fun streamBackdrop() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color(0xFF04111C),
                                    Color(0xFF081C2E),
                                    Color(0xFF0D2436),
                                ),
                        ),
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-80).dp, y = (-48).dp)
                    .size(320.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0x6637D9D0),
                                    Color(0x0037D9D0),
                                ),
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 88.dp, y = 24.dp)
                    .size(260.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0x5533A1FF),
                                    Color(0x0033A1FF),
                                ),
                        ),
                    ),
        )
    }
}

internal fun Modifier.streamContentWidth(layoutMode: StreamControlLayoutMode): Modifier =
    when (layoutMode) {
        StreamControlLayoutMode.Compact -> fillMaxWidth()
        StreamControlLayoutMode.Medium -> fillMaxWidth().widthIn(max = 760.dp)
        StreamControlLayoutMode.Expanded -> fillMaxWidth().widthIn(max = 1120.dp)
    }

@Preview(widthDp = 900, heightDp = 1200)
@Composable
private fun streamControlScreenPreview() {
    MaterialTheme {
        streamControlScreen(
            uiState =
                buildStreamControlUiState(
                    config = StreamConfig(),
                    sessionSnapshot = StreamingSessionSnapshot(),
                ),
            selectedLanguageTag = "en",
            onLanguageTagSelected = {},
            onSignalingEndpointChanged = {},
            onSignalingSchemeChanged = {},
            onSignalingHostChanged = {},
            onSignalingPortChanged = {},
            onSignalingPathChanged = {},
            onSessionIdChanged = {},
            onCodecPreferenceChanged = {},
            onAudioEnabledChanged = {},
            onOpenAudioPermissionSettingsClicked = {},
            onOpenScannerClicked = {},
            onResolutionChanged = {},
            onFpsChanged = {},
            onBitrateChanged = {},
            onStartClicked = {},
            onStopClicked = {},
        )
    }
}
