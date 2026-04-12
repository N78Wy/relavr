package io.relavr.sender.feature.streamcontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot
import io.relavr.sender.core.model.UiText
import io.relavr.sender.core.model.VideoResolution

object StreamControlTestTags {
    const val START_BUTTON = "stream-start"
    const val STOP_BUTTON = "stream-stop"
    const val SCAN_BUTTON = "stream-scan"
    const val AUDIO_SWITCH = "stream-audio-switch"
    const val AUDIO_PERMISSION_SETTINGS_BUTTON = "stream-audio-permission-settings"
    const val SIGNALING_ENDPOINT_INPUT = "stream-signaling-endpoint"
    const val SESSION_ID_INPUT = "stream-session-id"
    const val STREAM_PROFILE_CARD = "stream-profile-card"

    fun codecOption(preference: CodecPreference): String = "stream-codec-option-${preference.name.lowercase()}"

    fun resolutionOption(resolution: VideoResolution): String = "stream-resolution-option-${resolution.width}x${resolution.height}"

    fun fpsOption(fps: Int): String = "stream-fps-option-$fps"

    fun bitrateOption(bitrateKbps: Int): String = "stream-bitrate-option-$bitrateKbps"
}

private enum class StreamControlLayoutMode {
    Compact,
    Medium,
    Expanded,
}

@Composable
fun streamControlScreen(
    uiState: StreamControlUiState,
    selectedLanguageTag: String,
    onLanguageTagSelected: (String) -> Unit,
    onSignalingEndpointChanged: (String) -> Unit,
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
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color(0xFF07111F),
                                    Color(0xFF11253A),
                                    Color(0xFF1D3B58),
                                ),
                        ),
                ),
    ) {
        val layoutMode =
            when {
                maxWidth < 600.dp -> StreamControlLayoutMode.Compact
                maxWidth < 840.dp -> StreamControlLayoutMode.Medium
                else -> StreamControlLayoutMode.Expanded
            }
        val horizontalPadding =
            when (layoutMode) {
                StreamControlLayoutMode.Compact -> 16.dp
                StreamControlLayoutMode.Medium -> 24.dp
                StreamControlLayoutMode.Expanded -> 28.dp
            }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .streamContentWidth(layoutMode)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalPadding, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                heroCard(
                    title = uiState.title,
                    statusLabel = uiState.statusLabel,
                    statusDescription = uiState.statusDescription,
                    selectedLanguageTag = selectedLanguageTag,
                    onLanguageTagSelected = onLanguageTagSelected,
                )

                if (layoutMode == StreamControlLayoutMode.Expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1.15f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            configCard(
                                uiState = uiState,
                                layoutMode = layoutMode,
                                onSignalingEndpointChanged = onSignalingEndpointChanged,
                                onSessionIdChanged = onSessionIdChanged,
                                onCodecPreferenceChanged = onCodecPreferenceChanged,
                                onAudioEnabledChanged = onAudioEnabledChanged,
                                onOpenAudioPermissionSettingsClicked = onOpenAudioPermissionSettingsClicked,
                                onOpenScannerClicked = onOpenScannerClicked,
                            )
                        }
                        Column(
                            modifier = Modifier.weight(0.85f),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            streamProfileCard(
                                uiState = uiState,
                                onResolutionChanged = onResolutionChanged,
                                onFpsChanged = onFpsChanged,
                                onBitrateChanged = onBitrateChanged,
                            )
                            actionCard(
                                uiState = uiState,
                                layoutMode = layoutMode,
                                onStartClicked = onStartClicked,
                                onStopClicked = onStopClicked,
                            )
                            uiState.errorMessage?.let { errorMessage ->
                                errorCard(message = errorMessage)
                            }
                        }
                    }
                } else {
                    configCard(
                        uiState = uiState,
                        layoutMode = layoutMode,
                        onSignalingEndpointChanged = onSignalingEndpointChanged,
                        onSessionIdChanged = onSessionIdChanged,
                        onCodecPreferenceChanged = onCodecPreferenceChanged,
                        onAudioEnabledChanged = onAudioEnabledChanged,
                        onOpenAudioPermissionSettingsClicked = onOpenAudioPermissionSettingsClicked,
                        onOpenScannerClicked = onOpenScannerClicked,
                    )
                    streamProfileCard(
                        uiState = uiState,
                        onResolutionChanged = onResolutionChanged,
                        onFpsChanged = onFpsChanged,
                        onBitrateChanged = onBitrateChanged,
                    )
                    actionCard(
                        uiState = uiState,
                        layoutMode = layoutMode,
                        onStartClicked = onStartClicked,
                        onStopClicked = onStopClicked,
                    )
                    uiState.errorMessage?.let { errorMessage ->
                        errorCard(message = errorMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun heroCard(
    title: UiText,
    statusLabel: UiText,
    statusDescription: UiText,
    selectedLanguageTag: String,
    onLanguageTagSelected: (String) -> Unit,
) {
    surfaceCard {
        Text(
            text = title.resolve(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = statusLabel.resolve(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = statusDescription.resolve(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        languageSelector(
            selectedLanguageTag = selectedLanguageTag,
            onLanguageTagSelected = onLanguageTagSelected,
        )
    }
}

@Composable
private fun configCard(
    uiState: StreamControlUiState,
    layoutMode: StreamControlLayoutMode,
    onSignalingEndpointChanged: (String) -> Unit,
    onSessionIdChanged: (String) -> Unit,
    onCodecPreferenceChanged: (CodecPreference) -> Unit,
    onAudioEnabledChanged: (Boolean) -> Unit,
    onOpenAudioPermissionSettingsClicked: () -> Unit,
    onOpenScannerClicked: () -> Unit,
) {
    surfaceCard {
        Text(
            text = stringResource(R.string.stream_control_config_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.stream_control_codec_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.codecOptions.forEach { option ->
                codecOptionButton(
                    option = option,
                    onSelected = onCodecPreferenceChanged,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = uiState.codecStatusLabel.resolve(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.signalingEndpoint,
            onValueChange = onSignalingEndpointChanged,
            label = { Text(stringResource(R.string.stream_control_endpoint_label)) },
            placeholder = { Text(stringResource(R.string.stream_control_endpoint_placeholder)) },
            singleLine = true,
            enabled = uiState.configEditable,
            colors = streamTextFieldColors(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(StreamControlTestTags.SIGNALING_ENDPOINT_INPUT),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.stream_control_endpoint_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenScannerClicked,
            enabled = uiState.scanButtonEnabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(StreamControlTestTags.SCAN_BUTTON),
        ) {
            Text(stringResource(R.string.stream_control_scan_button))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.scanStatusLabel.resolve(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.sessionId,
            onValueChange = onSessionIdChanged,
            label = { Text("Session ID") },
            singleLine = true,
            enabled = uiState.configEditable,
            colors = streamTextFieldColors(),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(StreamControlTestTags.SESSION_ID_INPUT),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.stream_control_ice_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (layoutMode == StreamControlLayoutMode.Compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                audioSummary(
                    audioStatusLabel = uiState.audioStatusLabel,
                    showSettingsAction = uiState.audioPermissionSettingsVisible,
                    onOpenAudioPermissionSettingsClicked = onOpenAudioPermissionSettingsClicked,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Switch(
                        checked = uiState.audioEnabled,
                        onCheckedChange = onAudioEnabledChanged,
                        enabled = uiState.audioToggleEnabled,
                        modifier = Modifier.testTag(StreamControlTestTags.AUDIO_SWITCH),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                audioSummary(
                    audioStatusLabel = uiState.audioStatusLabel,
                    showSettingsAction = uiState.audioPermissionSettingsVisible,
                    onOpenAudioPermissionSettingsClicked = onOpenAudioPermissionSettingsClicked,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = uiState.audioEnabled,
                    onCheckedChange = onAudioEnabledChanged,
                    enabled = uiState.audioToggleEnabled,
                    modifier = Modifier.testTag(StreamControlTestTags.AUDIO_SWITCH),
                )
            }
        }
    }
}

@Composable
private fun audioSummary(
    audioStatusLabel: UiText,
    showSettingsAction: Boolean,
    onOpenAudioPermissionSettingsClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.stream_control_audio_switch_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = audioStatusLabel.resolve(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showSettingsAction) {
            OutlinedButton(
                onClick = onOpenAudioPermissionSettingsClicked,
                modifier = Modifier.testTag(StreamControlTestTags.AUDIO_PERMISSION_SETTINGS_BUTTON),
            ) {
                Text(stringResource(R.string.stream_control_audio_open_settings))
            }
        }
    }
}

@Composable
private fun codecOptionButton(
    option: CodecOptionUiState,
    onSelected: (CodecPreference) -> Unit,
) {
    val modifier =
        Modifier
            .fillMaxWidth()
            .testTag(StreamControlTestTags.codecOption(option.preference))

    if (option.selected) {
        Button(
            onClick = { onSelected(option.preference) },
            enabled = option.enabled,
            modifier = modifier,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = option.supportLabel.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    } else {
        OutlinedButton(
            onClick = { onSelected(option.preference) },
            enabled = option.enabled,
            modifier = modifier,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = option.supportLabel.resolve(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun streamProfileCard(
    uiState: StreamControlUiState,
    onResolutionChanged: (VideoResolution) -> Unit,
    onFpsChanged: (Int) -> Unit,
    onBitrateChanged: (Int) -> Unit,
) {
    surfaceCard(modifier = Modifier.testTag(StreamControlTestTags.STREAM_PROFILE_CARD)) {
        Text(
            text = stringResource(R.string.stream_control_profile_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = uiState.streamProfileSummary.resolve(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        selectionGroup(
            title = "Resolution",
            options = uiState.resolutionOptions,
            tagOf = StreamControlTestTags::resolutionOption,
            onSelected = onResolutionChanged,
        )
        Spacer(modifier = Modifier.height(16.dp))
        selectionGroup(
            title = "Frame Rate",
            options = uiState.fpsOptions,
            tagOf = StreamControlTestTags::fpsOption,
            onSelected = onFpsChanged,
        )
        Spacer(modifier = Modifier.height(16.dp))
        selectionGroup(
            title = "Bitrate",
            options = uiState.bitrateOptions,
            tagOf = StreamControlTestTags::bitrateOption,
            onSelected = onBitrateChanged,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> selectionGroup(
    title: String,
    options: List<SelectionOptionUiState<T>>,
    tagOf: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(10.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEach { option ->
            selectionOptionButton(
                option = option,
                testTag = tagOf(option.value),
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun <T> selectionOptionButton(
    option: SelectionOptionUiState<T>,
    testTag: String,
    onSelected: (T) -> Unit,
) {
    val modifier =
        Modifier
            .defaultMinSize(minWidth = 110.dp)
            .testTag(testTag)

    if (option.selected) {
        Button(
            onClick = { onSelected(option.value) },
            enabled = option.enabled,
            modifier = modifier,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Text(
                text = option.label,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        OutlinedButton(
            onClick = { onSelected(option.value) },
            enabled = option.enabled,
            modifier = modifier,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Text(
                text = option.label,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun actionCard(
    uiState: StreamControlUiState,
    layoutMode: StreamControlLayoutMode,
    onStartClicked: () -> Unit,
    onStopClicked: () -> Unit,
) {
    surfaceCard {
        Text(
            text = stringResource(R.string.stream_control_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(io.relavr.sender.core.model.R.string.sender_status_permission_requested),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (layoutMode == StreamControlLayoutMode.Compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onStartClicked,
                    enabled = uiState.startEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(StreamControlTestTags.START_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_start_button))
                }
                OutlinedButton(
                    onClick = onStopClicked,
                    enabled = uiState.stopEnabled,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(StreamControlTestTags.STOP_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_stop_button))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onStartClicked,
                    enabled = uiState.startEnabled,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(StreamControlTestTags.START_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_start_button))
                }
                OutlinedButton(
                    onClick = onStopClicked,
                    enabled = uiState.stopEnabled,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(StreamControlTestTags.STOP_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_stop_button))
                }
            }
        }
    }
}

@Composable
private fun errorCard(message: UiText) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
            ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.stream_control_error_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun languageSelector(
    selectedLanguageTag: String,
    onLanguageTagSelected: (String) -> Unit,
) {
    Text(
        text = stringResource(R.string.stream_control_language_label),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        languageButton(
            selected = selectedLanguageTag == "en",
            label = stringResource(R.string.stream_control_language_english),
        ) {
            onLanguageTagSelected("en")
        }
        languageButton(
            selected = selectedLanguageTag == "zh-CN",
            label = stringResource(R.string.stream_control_language_simplified_chinese),
        ) {
            onLanguageTagSelected("zh-CN")
        }
    }
}

@Composable
private fun languageButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label)
        }
    }
}

@Composable
private fun UiText.resolve(): String = stringResource(resId, *args.toTypedArray())

@Composable
private fun surfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
private fun streamTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

private fun Modifier.streamContentWidth(layoutMode: StreamControlLayoutMode): Modifier =
    when (layoutMode) {
        StreamControlLayoutMode.Compact -> fillMaxWidth()
        StreamControlLayoutMode.Medium -> fillMaxWidth().widthIn(max = 720.dp)
        StreamControlLayoutMode.Expanded -> fillMaxWidth().widthIn(max = 1040.dp)
    }

@Preview(widthDp = 700, heightDp = 1200)
@Composable
private fun streamControlScreenPreview() {
    streamControlScreen(
        uiState =
            buildStreamControlUiState(
                config = StreamConfig(),
                sessionSnapshot = StreamingSessionSnapshot(),
            ),
        selectedLanguageTag = "en",
        onLanguageTagSelected = {},
        onSignalingEndpointChanged = {},
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
