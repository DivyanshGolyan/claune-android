package com.divyanshgolyan.claune.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ClauneDarkScheme =
    darkColorScheme(
        primary = ClaunePalette.Accent,
        onPrimary = ClaunePalette.Background,
        secondary = ClaunePalette.InkSoft,
        onSecondary = ClaunePalette.Background,
        background = ClaunePalette.Background,
        onBackground = ClaunePalette.Ink,
        surface = ClaunePalette.Surface,
        onSurface = ClaunePalette.Ink,
    )

private val ClauneLightScheme =
    lightColorScheme(
        primary = ClaunePalette.Accent,
        onPrimary = ClaunePalette.Background,
        secondary = ClaunePalette.InkSoft,
        onSecondary = ClaunePalette.Background,
        background = ClaunePalette.Background,
        onBackground = ClaunePalette.Ink,
        surface = ClaunePalette.Surface,
        onSurface = ClaunePalette.Ink,
        surfaceContainer = ClaunePalette.SurfaceRaised,
        surfaceContainerLow = ClaunePalette.BackgroundDeep,
        surfaceContainerHigh = ClaunePalette.SurfaceMuted,
        surfaceVariant = ClaunePalette.BackgroundDeep,
        onSurfaceVariant = ClaunePalette.InkSoft,
        outline = ClaunePalette.Rule,
        outlineVariant = ClaunePalette.RuleSoft,
    )

@Composable
fun ClauneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ClauneLightScheme,
        typography = ClauneTypography,
        content = content,
    )
}
