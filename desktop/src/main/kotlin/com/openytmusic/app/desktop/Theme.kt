package com.openytmusic.app.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

/** Modo de tema elegible en Ajustes. */
enum class ThemeMode(val label: String) {
    System("Sistema"),
    Light("Claro"),
    Dark("Oscuro"),
}

val themeModeState = mutableStateOf(ThemeMode.Dark)

@Composable
fun LunaTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
