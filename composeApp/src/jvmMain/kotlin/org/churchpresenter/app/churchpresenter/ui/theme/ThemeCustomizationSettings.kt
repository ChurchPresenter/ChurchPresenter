package org.churchpresenter.app.churchpresenter.ui.theme

import org.churchpresenter.app.churchpresenter.composables.cpColorToHex
import org.churchpresenter.app.churchpresenter.composables.cpTryParseHex
import org.churchpresenter.app.churchpresenter.dialogs.ThemeCustomizationChoice
import org.churchpresenter.app.churchpresenter.dialogs.toCustomization
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.theme.DefaultCustomAccent
import org.churchpresenter.theme.ThemeCustomization
import org.churchpresenter.theme.UI_FONT_SCALES

/**
 * What the Customize Theme dialog opens on: the saved choices, with [useCustomColors] decided by the
 * caller. A scale outside the offered range is clamped into it.
 */
fun themeChoiceFrom(settings: AppSettings, useCustomColors: Boolean): ThemeCustomizationChoice =
    ThemeCustomizationChoice(
        useCustomColors = useCustomColors,
        accentHex = customAccentHex(settings),
        dark = settings.customThemeDark,
        colors = settings.customThemeColors,
        fontFamily = settings.uiFontFamily,
        fontScale = settings.uiFontScale.coerceIn(UI_FONT_SCALES.first(), UI_FONT_SCALES.last()),
    )

/**
 * The user's theme choices as the theme module takes them: the colours parsed, the font resolved.
 *
 * The theme module reads no settings, so this is the one place the stored strings become values. A
 * hex that no longer parses falls back to automatic (or, for the accent, the default one), and a font
 * that is no longer installed to the platform face.
 */
fun themeCustomizationFrom(settings: AppSettings): ThemeCustomization =
    themeChoiceFrom(settings, useCustomColors = true).toCustomization()

/** The accent [settings] holds, as the hex the colour field edits — the default when none is saved. */
fun customAccentHex(settings: AppSettings): String =
    settings.customThemeAccent.takeIf { cpTryParseHex(it) != null } ?: cpColorToHex(DefaultCustomAccent)
