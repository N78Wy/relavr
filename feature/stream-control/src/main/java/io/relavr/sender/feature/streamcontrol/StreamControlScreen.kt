package io.relavr.sender.feature.streamcontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.StreamConfig
import io.relavr.sender.core.model.StreamingSessionSnapshot

object StreamControlTestTags {
    const val START_BUTTON = "stream-start"
    const val STOP_BUTTON = "stream-stop"
    const val AUDIO_SWITCH = "stream-audio-switch"
}

@Composable
fun streamControlScreen(
    uiState: StreamControlUiState,
    onCodecSelected: (CodecPreference) -> Unit,
    onAudioEnabledChanged: (Boolean) -> Unit,
    onStartClicked: () -> Unit,
    onStopClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color(0xFF07111F),
                                    Color(0xFF14273D),
                                    Color(0xFF233C57),
                                ),
                        ),
                ),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                heroCard(
                    title = uiState.title,
                    statusLabel = uiState.statusLabel,
                    statusDescription = uiState.statusDescription,
                )
            }
            item {
                configCard(
                    uiState = uiState,
                    onCodecSelected = onCodecSelected,
                    onAudioEnabledChanged = onAudioEnabledChanged,
                )
            }
            item {
                metricsCard(uiState = uiState)
            }
            item {
                actionCard(
                    uiState = uiState,
                    onStartClicked = onStartClicked,
                    onStopClicked = onStopClicked,
                )
            }
            uiState.errorMessage?.let { errorMessage ->
                item {
                    errorCard(message = errorMessage)
                }
            }
        }
    }
}

@Composable
private fun heroCard(
    title: String,
    statusLabel: String,
    statusDescription: String,
) {
    surfaceCard {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF7DE2A7),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = statusDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFD6E2F0),
        )
    }
}

@Composable
private fun configCard(
    uiState: StreamControlUiState,
    onCodecSelected: (CodecPreference) -> Unit,
    onAudioEnabledChanged: (Boolean) -> Unit,
) {
    surfaceCard {
        Text(
            text = "发送配置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.availableCodecs.chunked(2).forEach { codecRow ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    codecRow.forEach { codec ->
                        FilterChip(
                            selected = codec == uiState.selectedCodec,
                            onClick = { onCodecSelected(codec) },
                            enabled = uiState.startEnabled,
                            label = { Text(codec.displayName) },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "音频采集",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = uiState.audioCapabilityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD6E2F0),
                )
            }
            Switch(
                checked = uiState.audioEnabled,
                onCheckedChange = onAudioEnabledChanged,
                enabled = uiState.audioToggleEnabled,
                modifier = Modifier.testTag(StreamControlTestTags.AUDIO_SWITCH),
            )
        }
    }
}

@Composable
private fun metricsCard(uiState: StreamControlUiState) {
    surfaceCard {
        Text(
            text = "默认规格",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            metricBadge(label = "分辨率", value = uiState.resolutionLabel)
            metricBadge(label = "帧率", value = uiState.fpsLabel)
            metricBadge(label = "码率", value = uiState.bitrateLabel)
        }
    }
}

@Composable
private fun actionCard(
    uiState: StreamControlUiState,
    onStartClicked: () -> Unit,
    onStopClicked: () -> Unit,
) {
    surfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "会话控制",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "开始前会触发 MediaProjection 系统授权",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD6E2F0),
                )
            }
            Row {
                Button(
                    onClick = onStartClicked,
                    enabled = uiState.startEnabled,
                    modifier = Modifier.testTag(StreamControlTestTags.START_BUTTON),
                ) {
                    Text("开始推流")
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = onStopClicked,
                    enabled = uiState.stopEnabled,
                    modifier = Modifier.testTag(StreamControlTestTags.STOP_BUTTON),
                ) {
                    Text("停止")
                }
            }
        }
    }
}

@Composable
private fun errorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4F1E28)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "异常",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFFC1C1),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFFFE7E7),
            )
        }
    }
}

@Composable
private fun metricBadge(
    label: String,
    value: String,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x223CA3FF)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFB8D5F1),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun surfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC0E1C2D)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Preview
@Composable
private fun streamControlScreenPreview() {
    streamControlScreen(
        uiState =
            buildStreamControlUiState(
                config = StreamConfig(),
                sessionSnapshot = StreamingSessionSnapshot(),
            ),
        onCodecSelected = {},
        onAudioEnabledChanged = {},
        onStartClicked = {},
        onStopClicked = {},
    )
}
