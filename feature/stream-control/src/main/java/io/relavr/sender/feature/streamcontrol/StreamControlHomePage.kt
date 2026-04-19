package io.relavr.sender.feature.streamcontrol

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun streamControlHomePage(
    uiState: StreamControlUiState,
    layoutMode: StreamControlLayoutMode,
    onSignalingEndpointChanged: (String) -> Unit,
    onSignalingHostChanged: (String) -> Unit,
    onSignalingPortChanged: (String) -> Unit,
    onOpenScannerClicked: () -> Unit,
    onOpenSettingsClicked: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        streamPageTitle(
            eyebrow = uiState.title.resolve(),
            title = stringResource(R.string.stream_control_home_heading),
            body = stringResource(R.string.stream_control_home_body),
            statusLabel = uiState.statusLabel.resolve(),
            statusDescription = uiState.statusDescription.resolve(),
        )

        if (layoutMode == StreamControlLayoutMode.Expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                scanPanel(
                    uiState = uiState,
                    onOpenScannerClicked = onOpenScannerClicked,
                    modifier = Modifier.weight(1f),
                )
                manualConnectPanel(
                    uiState = uiState,
                    layoutMode = layoutMode,
                    onSignalingEndpointChanged = onSignalingEndpointChanged,
                    onSignalingHostChanged = onSignalingHostChanged,
                    onSignalingPortChanged = onSignalingPortChanged,
                    onOpenSettingsClicked = onOpenSettingsClicked,
                    onStartClicked = onStartClicked,
                    onStopClicked = onStopClicked,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            scanPanel(
                uiState = uiState,
                onOpenScannerClicked = onOpenScannerClicked,
            )
            manualConnectPanel(
                uiState = uiState,
                layoutMode = layoutMode,
                onSignalingEndpointChanged = onSignalingEndpointChanged,
                onSignalingHostChanged = onSignalingHostChanged,
                onSignalingPortChanged = onSignalingPortChanged,
                onOpenSettingsClicked = onOpenSettingsClicked,
                onStartClicked = onStartClicked,
                onStopClicked = onStopClicked,
            )
        }

        AnimatedVisibility(
            visible = uiState.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            uiState.errorMessage?.let { streamErrorBanner(message = it) }
        }
    }
}

@Composable
private fun scanPanel(
    uiState: StreamControlUiState,
    onOpenScannerClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    glassPanel(modifier = modifier) {
        sectionHeading(
            title = stringResource(R.string.stream_control_home_scan_title),
            body = stringResource(R.string.stream_control_home_scan_body),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onOpenScannerClicked,
            enabled = uiState.scanButtonEnabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag(StreamControlTestTags.SCAN_BUTTON),
        ) {
            Text(
                text = stringResource(R.string.stream_control_scan_button),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = uiState.scanStatusLabel.resolve(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun manualConnectPanel(
    uiState: StreamControlUiState,
    layoutMode: StreamControlLayoutMode,
    onSignalingEndpointChanged: (String) -> Unit,
    onSignalingHostChanged: (String) -> Unit,
    onSignalingPortChanged: (String) -> Unit,
    onOpenSettingsClicked: () -> Unit,
    onStartClicked: () -> Unit,
    onStopClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    glassPanel(modifier = modifier) {
        sectionHeading(
            title = stringResource(R.string.stream_control_home_manual_title),
            body = stringResource(R.string.stream_control_home_manual_body),
        )

        if (layoutMode == StreamControlLayoutMode.Compact) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                hostField(
                    host = uiState.connectionDraft.host,
                    enabled = uiState.configEditable,
                    onHostChanged = onSignalingHostChanged,
                )
                portField(
                    port = uiState.connectionDraft.port,
                    enabled = uiState.configEditable,
                    onPortChanged = onSignalingPortChanged,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                hostField(
                    host = uiState.connectionDraft.host,
                    enabled = uiState.configEditable,
                    onHostChanged = onSignalingHostChanged,
                    modifier = Modifier.weight(1f),
                )
                portField(
                    port = uiState.connectionDraft.port,
                    enabled = uiState.configEditable,
                    onPortChanged = onSignalingPortChanged,
                    modifier = Modifier.width(148.dp),
                )
            }
        }

        Text(
            text = stringResource(R.string.stream_control_home_manual_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = uiState.connectionSummary.resolve(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (uiState.stopEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onStopClicked,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(StreamControlTestTags.STOP_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_stop_button))
                }
                OutlinedButton(
                    onClick = onOpenSettingsClicked,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(StreamControlTestTags.SETTINGS_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_settings_button))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        onSignalingEndpointChanged(uiState.connectionDraft.toPersistedEndpoint())
                        onStartClicked()
                    },
                    enabled = uiState.startEnabled,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(StreamControlTestTags.START_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_start_button))
                }
                OutlinedButton(
                    onClick = onOpenSettingsClicked,
                    modifier =
                        Modifier
                            .weight(1f)
                            .testTag(StreamControlTestTags.SETTINGS_BUTTON),
                ) {
                    Text(stringResource(R.string.stream_control_settings_button))
                }
            }
        }
    }
}

@Composable
private fun hostField(
    host: String,
    enabled: Boolean,
    onHostChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = host,
        onValueChange = onHostChanged,
        label = { Text(stringResource(R.string.stream_control_host_label)) },
        placeholder = { Text(stringResource(R.string.stream_control_host_placeholder)) },
        singleLine = true,
        enabled = enabled,
        colors = streamTextFieldColors(),
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(StreamControlTestTags.HOST_INPUT),
    )
}

@Composable
private fun portField(
    port: String,
    enabled: Boolean,
    onPortChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = port,
        onValueChange = onPortChanged,
        label = { Text(stringResource(R.string.stream_control_port_label)) },
        placeholder = { Text(stringResource(R.string.stream_control_port_placeholder)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = streamTextFieldColors(),
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(StreamControlTestTags.PORT_INPUT),
    )
}
