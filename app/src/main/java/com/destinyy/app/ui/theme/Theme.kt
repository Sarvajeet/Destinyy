package com.destinyy.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(primary = Forest, onPrimary = Color.White, secondary = Forest, secondaryContainer = MintContainer, background = LightBackground, surface = LightSurface)
private val DarkColors = darkColorScheme(primary = ForestDark, secondary = ForestDark, secondaryContainer = Color(0xFF244B3C), background = NightBackground, surface = NightSurface)

@Composable
fun DestinyyTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
