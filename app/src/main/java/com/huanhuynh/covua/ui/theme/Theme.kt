package com.huanhuynh.covua.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Màu chính của app (tên "Red" giữ lại từ khung chung các app cờ) – xanh mực. */
val Red = Color(0xFF2C4A7A)
val RedDark = Color(0xFF15253F)
val Gold = Color(0xFFF6D58E)
val Ink = Color(0xFF2B2420)
val Muted = Color(0xFF7A6E62)
val Paper = Color(0xFFFAF5EC)
val Line = Color(0xFFE7DCC8)
val Chip = Color(0xFFF1EADF)
val Good = Color(0xFF2E7D4F)
val Warn = Color(0xFFE08A00)
val Bad = Color(0xFFC62828)
val Best = Color(0xFF1565C0)
val WoodLight = Color(0xFFF0D3A0)
val WoodDark = Color(0xFFDDB277)
val WoodLine = Color(0xFF8A5A2B)

private val Colors = lightColorScheme(
    primary = Red,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E8F4),
    onPrimaryContainer = RedDark,
    secondary = RedDark,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    surfaceVariant = Chip,
    outline = Line,
    outlineVariant = Line,
)

@Composable
fun CoVuaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
