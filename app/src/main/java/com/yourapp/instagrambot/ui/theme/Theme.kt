package com.yourapp.instagrambot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = IgPink,
    secondary = IgPurple,
    tertiary = IgOrange
)

private val DarkColors = darkColorScheme(
    primary = IgPink,
    secondary = IgPurple,
    tertiary = IgOrange
)

@Composable
fun InstagramBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
