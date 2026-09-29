package org.churchpresenter.theme

import androidx.compose.ui.graphics.Color

object FixedColors {
    val screenBlack = Color.Black
    val screenWhite = Color.White

    val inkLight = Color.White
    val inkDark = Color.Black

    val hueSpectrum = listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000),
    )

    val checkerLight = Color(0xFF8C8C8C)
    val checkerDark = Color(0xFF6B6B6B)

    val lookStage = Color(0xFF7C848F)

    val canvasSelection = Color.Cyan
    val canvasGuide = Color.Magenta

    val placeholderSurface = Color.DarkGray
    val placeholderMuted = Color.Gray

    val live = Color.Red

    val confetti = listOf(
        Color(0xFFE53935),
        Color(0xFF8E24AA),
        Color(0xFF1E88E5),
        Color(0xFF00ACC1),
        Color(0xFF43A047),
        Color(0xFFFDD835),
        Color(0xFFFB8C00),
        Color(0xFFFFB300),
    )

    val paperCell = Color.White
    val paperInk = Color(0xFF1A1A1A)
    val paperBorder = Color(0xFF9E9E9E)
    val paperBlocked = Color(0xFF1A1A1A)
    val paperFocusBorder = Color(0xFF1565C0)
    val paperFocusFill = Color(0xFFBBDEFB)
}
