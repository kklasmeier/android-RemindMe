package com.phoenix.remindme.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = Color(0xFF080B14)
val Glass = Color(0x12FFFFFF)
val GlassBorder = Color(0x1AFFFFFF)
val Accent = Color(0xFF7B6EF6)
val Accent2 = Color(0xFF3ECFCF)
val Warning = Color(0xFFF97316)
val Danger = Color(0xFFEF4444)
val Success = Color(0xFF22C55E)
val TextPrimary = Color(0xFFF0F2F8)
val TextMuted = Color(0xB3F0F2F8)
val TextDim = Color(0x73F0F2F8)

private val RemindMeColors = darkColorScheme(
    primary = Accent,
    secondary = Accent2,
    tertiary = Warning,
    background = Background,
    surface = Background,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Danger,
)

@Composable
fun RemindMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RemindMeColors, content = content)
}
