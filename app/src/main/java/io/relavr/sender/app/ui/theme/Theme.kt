package io.relavr.sender.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RelavrColors =
    darkColorScheme(
        primary = Mint300,
        background = Slate900,
        surface = Slate700,
        onPrimary = Slate900,
        onBackground = Cloud100,
        onSurface = Cloud100,
    )

@Composable
fun relavrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RelavrColors,
        content = content,
    )
}
