package com.divyanshgolyan.claune.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Suppress("ktlint:standard:property-naming")
internal object SoftKraftPalette {
    const val BackgroundArgb = 0xFFF3EDE1.toInt()
    const val SurfaceArgb = 0xFFFBF7F0.toInt()
    const val SurfaceRaisedArgb = 0xFFFFFBF5.toInt()
    const val RuleArgb = 0xFFD4C9B0.toInt()
    const val InkArgb = 0xFF1D2A22.toInt()
    const val InkSoftArgb = 0xFF4A574D.toInt()
    const val InkFaintArgb = 0xFF8A8777.toInt()
    const val AccentArgb = 0xFF2D5A3F.toInt()
    const val AccentDeepArgb = 0xFF1A3D28.toInt()

    val Background = Color(0xFFF3EDE1)
    val BackgroundDeep = Color(0xFFE8DFCE)
    val Surface = Color(0xFFFBF7F0)
    val SurfaceMuted = Color(0xFFF0E8DA)
    val SurfaceRaised = Color(0xFFFFFBF5)
    val Rule = Color(0xFFD4C9B0)
    val RuleSoft = Color(0xFFDDD3C1)
    val Ink = Color(0xFF1D2A22)
    val InkSoft = Color(0xFF4A574D)
    val InkFaint = Color(0xFF8A8777)
    val Accent = Color(0xFF2D5A3F)
    val AccentSoft = Color(0xFFC7D8CA)
    val AccentDeep = Color(0xFF1A3D28)
    val Success = Color(0xFF2F7B55)
    val Warning = Color(0xFF9A5A30)
    val Muted = Color(0xFF726A5F)
}

internal object ClauneLayout {
    val ScreenPadding = 20.dp
    val SurfacePadding = 16.dp
    val CardPadding = 18.dp
    val ControlGap = 12.dp
    val SectionGap = 16.dp
    val TightGap = 8.dp
}

internal object ClauneShapes {
    val Card = RoundedCornerShape(24.dp)
    val Control = RoundedCornerShape(16.dp)
    val AssistantBubble = RoundedCornerShape(topStart = 8.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
    val UserBubble = RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp)
}
