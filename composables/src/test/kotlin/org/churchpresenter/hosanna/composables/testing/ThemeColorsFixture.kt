package org.churchpresenter.hosanna.composables.testing

import org.churchpresenter.hosanna.composables.foundation.ThemeColors
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.colorSchemeFor
import org.churchpresenter.theme.semanticColorsFor

private fun themeColors(mode: ThemeMode): ThemeColors =
    colorSchemeFor(mode).let { ThemeColors(it, semanticColorsFor(it)) }

internal val LightThemeColors: ThemeColors = themeColors(ThemeMode.LIGHT)

internal val DarkThemeColors: ThemeColors = themeColors(ThemeMode.DARK)
