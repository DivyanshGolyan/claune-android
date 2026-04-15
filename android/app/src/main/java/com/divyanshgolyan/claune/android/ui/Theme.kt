package com.divyanshgolyan.claune.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ClauneDarkScheme =
    darkColorScheme(
        primary = Color(0xFFF09A52),
        onPrimary = Color(0xFF2A1608),
        secondary = Color(0xFFF6E9DB),
        onSecondary = Color(0xFF1F130D),
        background = Color(0xFF120D0A),
        onBackground = Color(0xFFF6E9DB),
        surface = Color(0xFF1A120D),
        onSurface = Color(0xFFF6E9DB),
    )

private val ClauneLightScheme =
    lightColorScheme(
        primary = Color(0xFF8F4E22),
        onPrimary = Color.White,
        secondary = Color(0xFF51392B),
        onSecondary = Color.White,
        background = Color(0xFFF8EFE7),
        onBackground = Color(0xFF22140E),
        surface = Color(0xFFFFF7F1),
        onSurface = Color(0xFF22140E),
    )

@Composable
fun ClauneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClauneDarkScheme,
        typography = ClauneTypography,
        content = content,
    )
}
