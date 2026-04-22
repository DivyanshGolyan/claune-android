package com.divyanshgolyan.claune.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClauneDarkScheme =
    darkColorScheme(
        primary = Color(0xFFD6E4D9),
        onPrimary = Color(0xFF193022),
        secondary = Color(0xFFF3EADF),
        onSecondary = Color(0xFF1D2A22),
        background = Color(0xFF1C221E),
        onBackground = Color(0xFFF6F0E6),
        surface = Color(0xFF252C27),
        onSurface = Color(0xFFF6F0E6),
    )

private val ClauneLightScheme =
    lightColorScheme(
        primary = Color(0xFF2D5A3F),
        onPrimary = Color(0xFFF3EDE1),
        secondary = Color(0xFF506256),
        onSecondary = Color(0xFFF7F1E8),
        background = Color(0xFFF3EDE1),
        onBackground = Color(0xFF1D2A22),
        surface = Color(0xFFF9F4EC),
        onSurface = Color(0xFF1D2A22),
        surfaceContainer = Color(0xFFFFFBF5),
        surfaceContainerLow = Color(0xFFFBF7F0),
        surfaceContainerHigh = Color(0xFFF0E8DA),
        surfaceVariant = Color(0xFFE6DCCC),
        onSurfaceVariant = Color(0xFF59675E),
        outline = Color(0xFFD4C9B0),
        outlineVariant = Color(0xFFDDD3C1),
    )

@Composable
fun ClauneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClauneLightScheme,
        typography = ClauneTypography,
        content = content,
    )
}
