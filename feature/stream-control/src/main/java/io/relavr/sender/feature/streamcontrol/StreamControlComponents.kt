package io.relavr.sender.feature.streamcontrol

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import io.relavr.sender.core.model.CodecPreference
import io.relavr.sender.core.model.UiText

private val PanelShape = RoundedCornerShape(30.dp)

@Composable
internal fun glassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color(0xFF0C1A29).copy(alpha = 0.9f),
                                    Color(0xFF102437).copy(alpha = 0.72f),
                                ),
                        ),
                    shape = PanelShape,
                ).border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = PanelShape,
                ).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
internal fun streamPageTitle(
    eyebrow: String,
    title: String,
    body: String,
    statusLabel: String,
    statusDescription: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF93E7E0),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            statusPill(label = statusLabel)
            Text(
                text = statusDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun statusPill(label: String) {
    Text(
        text = label,
        modifier =
            Modifier
                .background(
                    color = Color(0x2237D9D0),
                    shape = RoundedCornerShape(999.dp),
                ).border(
                    width = 1.dp,
                    color = Color(0x6637D9D0),
                    shape = RoundedCornerShape(999.dp),
                ).padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFB8FFFA),
    )
}

@Composable
internal fun sectionHeading(
    title: String,
    body: String? = null,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    if (body != null) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun streamErrorBanner(message: UiText) {
    glassPanel(
        modifier =
            Modifier.background(
                color = Color.Transparent,
                shape = PanelShape,
            ),
    ) {
        Text(
            text = stringResource(R.string.stream_control_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFFFD7DB),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = message.resolve(),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFFD7DB),
        )
    }
}

@Composable
internal fun codecOptionButton(
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
                    containerColor = Color(0xFF1F8F88),
                    contentColor = Color(0xFF031014),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
@OptIn(ExperimentalLayoutApi::class)
internal fun <T> selectionGroup(
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
    Spacer(modifier = Modifier.height(8.dp))
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
            .defaultMinSize(minWidth = 108.dp)
            .testTag(testTag)

    if (option.selected) {
        Button(
            onClick = { onSelected(option.value) },
            enabled = option.enabled,
            modifier = modifier,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8AE7DE),
                    contentColor = Color(0xFF041018),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
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
internal fun languageSelector(
    selectedLanguageTag: String,
    onLanguageTagSelected: (String) -> Unit,
) {
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
internal fun UiText.resolve(): String = stringResource(resId, *args.toTypedArray())

@Composable
internal fun streamTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedBorderColor = Color(0xFF8AE7DE),
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        focusedLabelColor = Color(0xFF8AE7DE),
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = Color(0xFF8AE7DE),
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
