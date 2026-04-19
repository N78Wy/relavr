package io.relavr.sender.feature.streamcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.VideoResolution

@Composable
internal fun streamControlSettingsPage(
    uiState: StreamControlUiState,
    selectedLanguageTag: String,
    layoutMode: StreamControlLayoutMode,
    onBackClicked: () -> Unit,
    onLanguageTagSelected: (String) -> Unit,
    onSignalingSchemeChanged: (String) -> Unit,
    onSignalingPathChanged: (String) -> Unit,
    onSessionIdChanged: (String) -> Unit,
    onCodecPreferenceChanged: (CodecPreference) -> Unit,
    onAudioEnabledChanged: (Boolean) -> Unit,
    onOpenAudioPermissionSettingsClicked: () -> Unit,
    onResolutionChanged: (VideoResolution) -> Unit,
    onFpsChanged: (Int) -> Unit,
    onBitrateChanged: (Int) -> Unit,
    onStartClicked: () -> Unit,
    onStopClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalPadding =
        when (layoutMode) {
            StreamControlLayoutMode.Compact -> 18.dp
            StreamControlLayoutMode.Medium -> 24.dp
            StreamControlLayoutMode.Expanded -> 32.dp
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .streamContentWidth(layoutMode)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBackClicked,
                modifier = Modifier.testTag(StreamControlTestTags.SETTINGS_BACK_BUTTON),
            ) {
                Text(stringResource(R.string.stream_control_settings_back))
            }
            statusPill(label = uiState.statusLabel.resolve())
        }

        streamPageTitle(
            eyebrow = uiState.title.resolve(),
            title = stringResource(R.string.stream_control_settings_title),
            body = stringResource(R.string.stream_control_settings_body),
            statusLabel = uiState.statusLabel.resolve(),
            statusDescription = uiState.statusDescription.resolve(),
        )

        glassPanel {
            sectionHeading(
                title = stringResource(R.string.stream_control_settings_connection_title),
                body = uiState.connectionSummary.resolve(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                schemeButton(
                    label = "ws",
                    selected = uiState.connectionDraft.scheme.normalizedScheme() == SignalingEndpointDraft.DEFAULT_SCHEME,
                    enabled = uiState.configEditable,
                    onClick = { onSignalingSchemeChanged(SignalingEndpointDraft.DEFAULT_SCHEME) },
                    modifier = Modifier.testTag(StreamControlTestTags.SCHEME_WS_BUTTON),
                )
                schemeButton(
                    label = "wss",
                    selected = uiState.connectionDraft.scheme.normalizedScheme() == SignalingEndpointDraft.SECURE_SCHEME,
                    enabled = uiState.configEditable,
                    onClick = { onSignalingSchemeChanged(SignalingEndpointDraft.SECURE_SCHEME) },
                    modifier = Modifier.testTag(StreamControlTestTags.SCHEME_WSS_BUTTON),
                )
            }
            OutlinedTextField(
                value = uiState.connectionDraft.path,
                onValueChange = onSignalingPathChanged,
                label = { Text(stringResource(R.string.stream_control_path_label)) },
                placeholder = { Text(stringResource(R.string.stream_control_path_placeholder)) },
                singleLine = true,
                enabled = uiState.configEditable,
                colors = streamTextFieldColors(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(StreamControlTestTags.PATH_INPUT),
            )
            OutlinedTextField(
                value = uiState.sessionId,
                onValueChange = onSessionIdChanged,
                label = { Text(stringResource(R.string.stream_control_session_id_label)) },
                singleLine = true,
                enabled = uiState.configEditable,
                colors = streamTextFieldColors(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(StreamControlTestTags.SESSION_ID_INPUT),
            )
            if (!uiState.configEditable) {
                Text(
                    text = stringResource(R.string.stream_control_settings_locked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        glassPanel {
            sectionHeading(
                title = stringResource(R.string.stream_control_language_label),
                body = stringResource(R.string.stream_control_language_hint),
            )
            languageSelector(
                selectedLanguageTag = selectedLanguageTag,
                onLanguageTagSelected = onLanguageTagSelected,
            )
        }

        glassPanel {
            sectionHeading(
                title = stringResource(R.string.stream_control_audio_title),
                body = uiState.audioStatusDescription.resolve(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.audioEnabled) {
                    Button(
                        onClick = { onAudioEnabledChanged(true) },
                        enabled = uiState.audioConfigEnabled,
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(StreamControlTestTags.AUDIO_ENABLE_BUTTON),
                    ) {
                        Text(stringResource(R.string.stream_control_audio_on))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onAudioEnabledChanged(true) },
                        enabled = uiState.audioConfigEnabled,
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(StreamControlTestTags.AUDIO_ENABLE_BUTTON),
                    ) {
                        Text(stringResource(R.string.stream_control_audio_on))
                    }
                }
                if (!uiState.audioEnabled) {
                    Button(
                        onClick = { onAudioEnabledChanged(false) },
                        enabled = uiState.audioConfigEnabled,
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(StreamControlTestTags.AUDIO_DISABLE_BUTTON),
                    ) {
                        Text(stringResource(R.string.stream_control_audio_off))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onAudioEnabledChanged(false) },
                        enabled = uiState.audioConfigEnabled,
                        modifier =
                            Modifier
                                .weight(1f)
                                .testTag(StreamControlTestTags.AUDIO_DISABLE_BUTTON),
                    ) {
                        Text(stringResource(R.string.stream_control_audio_off))
                    }
                }
            }
            if (uiState.audioSettingsVisible) {
                OutlinedButton(
                    onClick = onOpenAudioPermissionSettingsClicked,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(StreamControlTestTags.AUDIO_SETTINGS_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_audio_open_settings))
                }
            }
        }

        glassPanel(modifier = Modifier.testTag(StreamControlTestTags.STREAM_PROFILE_CARD)) {
            sectionHeading(
                title = stringResource(R.string.stream_control_profile_title),
                body = uiState.streamProfileSummary.resolve(),
            )
            Text(
                text = uiState.codecStatusLabel.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.stream_control_codec_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.codecOptions.forEach { option ->
                    codecOptionButton(
                        option = option,
                        onSelected = onCodecPreferenceChanged,
                    )
                }
            }
            selectionGroup(
                title = stringResource(R.string.stream_control_resolution_title),
                options = uiState.resolutionOptions,
                tagOf = StreamControlTestTags::resolutionOption,
                onSelected = onResolutionChanged,
            )
            selectionGroup(
                title = stringResource(R.string.stream_control_frame_rate_title),
                options = uiState.fpsOptions,
                tagOf = StreamControlTestTags::fpsOption,
                onSelected = onFpsChanged,
            )
            selectionGroup(
                title = stringResource(R.string.stream_control_bitrate_title),
                options = uiState.bitrateOptions,
                tagOf = StreamControlTestTags::bitrateOption,
                onSelected = onBitrateChanged,
            )
        }

        if (uiState.errorMessage != null) {
            streamErrorBanner(message = uiState.errorMessage)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onBackClicked,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.stream_control_settings_done))
            }
            if (uiState.stopEnabled) {
                Button(
                    onClick = onStopClicked,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(StreamControlTestTags.STOP_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_stop_button))
                }
            } else {
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
            }
        }
    }
}

@Composable
private fun schemeButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(label)
        }
    }
}
