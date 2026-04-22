package com.divyanshgolyan.claune.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Suppress("ktlint:standard:property-naming")
internal object ClaunePalette {
    const val BackgroundArgb = 0xFFFFFFFF.toInt()
    const val SurfaceArgb = 0xFFFFFFFF.toInt()
    const val SurfaceRaisedArgb = 0xFFFFFFFF.toInt()
    const val RuleArgb = 0x1A000000
    const val InkArgb = 0xFF0D0D0D.toInt()
    const val InkSoftArgb = 0xFF615D59.toInt()
    const val InkFaintArgb = 0xFFA39E98.toInt()
    const val AccentArgb = 0xFF0075DE.toInt()
    const val AccentDeepArgb = 0xFF005BAB.toInt()

    val Background = Color(0xFFFFFFFF)
    val BackgroundDeep = Color(0xFFF6F5F4)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF7F6F4)
    val SurfaceRaised = Color(0xFFFFFFFF)
    val Rule = Color(0x1A000000)
    val RuleSoft = Color(0x10000000)
    val Ink = Color(0xFF0D0D0D)
    val InkSoft = Color(0xFF615D59)
    val InkFaint = Color(0xFFA39E98)
    val Accent = Color(0xFF0075DE)
    val AccentSoft = Color(0xFFF2F9FF)
    val AccentDeep = Color(0xFF005BAB)
    val Success = Color(0xFF217A52)
    val Warning = Color(0xFF9A5A30)
    val Muted = Color(0xFF7B7670)
}

internal object ClauneLayout {
    val ScreenPadding = 20.dp
    val SurfacePadding = 16.dp
    val CardPadding = 16.dp
    val ControlGap = 12.dp
    val SectionGap = 16.dp
    val TightGap = 8.dp
}

internal object ClauneShapes {
    val Card = RoundedCornerShape(16.dp)
    val Control = RoundedCornerShape(4.dp)
    val AssistantBubble = RoundedCornerShape(topStart = 12.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    val UserBubble = RoundedCornerShape(topStart = 16.dp, topEnd = 12.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
}
