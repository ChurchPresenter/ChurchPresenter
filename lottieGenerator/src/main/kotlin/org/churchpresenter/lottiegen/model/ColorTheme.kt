package org.churchpresenter.lottiegen.model

import kotlinx.serialization.Serializable
import org.churchpresenter.lottiegen.ui.Strings

@Serializable
data class ColorThemeColors(
    val nameColor: String,
    val infoColor: String,
    val accentColor: String,
    val bgColor: String,
    val borderColor: String,
    val nameColorAlpha: Int = 100,
    val infoColorAlpha: Int = 100,
    val accentColorAlpha: Int = 100,
    val bgColorAlpha: Int = 100,
    val borderColorAlpha: Int = 100
)

@Serializable
data class ColorTheme(
    val name: String,
    val colors: ColorThemeColors
)

fun defaultColorThemes(): List<ColorTheme> = listOf(
    ColorTheme(Strings.themeClassicRed, ColorThemeColors("#F2F2F2", "#CCCCCC", "#D54141", "#222222", "#D54141")),
    ColorTheme(Strings.themeOceanBlue, ColorThemeColors("#FFFFFF", "#B0D4F1", "#1A73E8", "#0D253F", "#1A73E8")),
    ColorTheme(Strings.themeEmerald, ColorThemeColors("#FFFFFF", "#A8E6CF", "#2ECC71", "#1A332A", "#27AE60")),
    ColorTheme(Strings.themeSunset, ColorThemeColors("#FFFFFF", "#FFD9B3", "#FF6B35", "#2D1810", "#E85D26")),
    ColorTheme(Strings.themePurpleHaze, ColorThemeColors("#F0E6FF", "#C9A8FF", "#8B5CF6", "#1A1033", "#7C3AED")),
    ColorTheme(Strings.themeMinimalLight, ColorThemeColors("#222222", "#555555", "#333333", "#F5F5F5", "#DDDDDD")),
    ColorTheme(Strings.themeGold, ColorThemeColors("#FFFFFF", "#F0D9A0", "#D4A934", "#1C1408", "#B8941E")),
    ColorTheme(Strings.themeNeonPink, ColorThemeColors("#FFFFFF", "#FFB3D9", "#FF2D8A", "#1A0A12", "#E6196E"))
)
