package com.codemobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CodeMobileExtendedColors(
    val userBubble: Color,
    val onUserBubble: Color,
    val toolCallAccent: Color,
    val onToolCallAccent: Color,
    val toolCallContainer: Color,
    val streamingIndicator: Color
)

internal val LightExtendedColors = CodeMobileExtendedColors(
    userBubble = Color(0xFF3B82F6),
    onUserBubble = Color.White,
    toolCallAccent = Color(0xFFB45309),
    onToolCallAccent = Color.White,
    toolCallContainer = Color(0xFFFDE68A),
    streamingIndicator = Color(0xFF2563EB)
)

internal val DarkExtendedColors = CodeMobileExtendedColors(
    userBubble = Color(0xFF60A5FA),
    onUserBubble = Color(0xFF0A1628),
    toolCallAccent = Color(0xFFF59E0B),
    onToolCallAccent = Color(0xFF241303),
    toolCallContainer = Color(0xFF3B2A08),
    streamingIndicator = Color(0xFF93C5FD)
)

internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object CodeMobileThemeTokens {
    val extendedColors: CodeMobileExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
