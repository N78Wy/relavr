package io.relavr.sender.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RelavrColors =
    darkColorScheme(
        primary = Mint300,
        background = Slate900,
        surface = Slate800,
        surfaceVariant = Slate600,
        errorContainer = Rose950,
        onPrimary = Slate900,
        onPrimaryContainer = Cloud100,
        onBackground = Cloud100,
        onSurface = Cloud100,
        onSurfaceVariant = Sky300,
        primaryContainer = Mint900,
        outline = Sky300.copy(alpha = 0.7f),
        onErrorContainer = Rose200,
    )

@Composable
fun relavrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RelavrColors,
        content = content,
    )
}
