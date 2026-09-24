package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.graphics.Color
import org.churchpresenter.app.churchpresenter.composables.cpColorToHex
import org.churchpresenter.app.churchpresenter.composables.cpTryParseHex
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.settings.CustomThemeColors
import org.churchpresenter.theme.DefaultCustomAccent
import org.churchpresenter.theme.ThemeCustomization

/**
 * What the Customize Theme dialog hands back on Apply, in the shape settings store it.
 *
 * [useCustomColors] says whether to switch to the Custom theme; font and size apply either way.
 */
data class ThemeCustomizationChoice(
    val useCustomColors: Boolean,
    val accentHex: String,
    val dark: Boolean,
    val colors: CustomThemeColors = CustomThemeColors(),
    val fontFamily: String,
    val fontScale: Float,
)

private fun String.toColorOrNull(): Color? = takeIf { it.isNotBlank() }?.let(::cpTryParseHex)

internal fun ThemeCustomizationChoice.toCustomization() = ThemeCustomization(
    accent = cpTryParseHex(accentHex) ?: DefaultCustomAccent,
    dark = dark,
    background = colors.background.toColorOrNull(),
    secondary = colors.secondary.toColorOrNull(),
    text = colors.text.toColorOrNull(),
    success = colors.success.toColorOrNull(),
    warning = colors.warning.toColorOrNull(),
    error = colors.error.toColorOrNull(),
    selection = colors.selection.toColorOrNull(),
    fontFamily = fontFamily.takeIf { it.isNotBlank() }?.let(Utils::systemFontFamilyOrDefault),
    fontScale = fontScale,
)

internal fun defaultChoice(useCustomColors: Boolean) = ThemeCustomizationChoice(
    useCustomColors = useCustomColors,
    accentHex = cpColorToHex(DefaultCustomAccent),
    dark = ThemeCustomization().dark,
    colors = CustomThemeColors(),
    fontFamily = "",
    fontScale = ThemeCustomization().fontScale,
)
