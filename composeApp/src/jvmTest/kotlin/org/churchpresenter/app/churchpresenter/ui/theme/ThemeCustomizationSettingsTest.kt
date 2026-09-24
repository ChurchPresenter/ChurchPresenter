package org.churchpresenter.app.churchpresenter.ui.theme

import org.churchpresenter.app.churchpresenter.composables.cpColorToHex
import org.churchpresenter.app.churchpresenter.composables.cpTryParseHex
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CustomThemeColors
import org.churchpresenter.theme.DefaultCustomAccent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The one place the stored theme strings become the values the theme module paints with.
 *
 * A settings file can hold anything by the time it is read — hand-edited, from a newer build, or
 * naming a font that has since been uninstalled — so each field has to land on something sensible
 * rather than on a crash or on a colour nobody picked.
 */
class ThemeCustomizationSettingsTest {

    @Test
    fun `nothing saved is the default accent, every role automatic, the platform font at its size`() {
        val custom = themeCustomizationFrom(AppSettings())

        assertEquals(DefaultCustomAccent, custom.accent)
        assertEquals(true, custom.dark)
        assertNull(custom.background)
        assertNull(custom.text)
        assertNull(custom.selection)
        assertNull(custom.fontFamily)
        assertEquals(1f, custom.fontScale)
    }

    @Test
    fun `saved colours are parsed, each on its own role`() {
        val custom = themeCustomizationFrom(
            AppSettings(
                customThemeAccent = "#AA3355",
                customThemeDark = false,
                customThemeColors = CustomThemeColors(
                    background = "#102030", secondary = "#20A0A0", text = "#F0F0F0",
                    success = "#30C060", warning = "#E0A020", error = "#D02040", selection = "#405080",
                ),
            ),
        )

        assertEquals(cpTryParseHex("#AA3355"), custom.accent)
        assertEquals(false, custom.dark)
        assertEquals(cpTryParseHex("#102030"), custom.background)
        assertEquals(cpTryParseHex("#20A0A0"), custom.secondary)
        assertEquals(cpTryParseHex("#F0F0F0"), custom.text)
        assertEquals(cpTryParseHex("#30C060"), custom.success)
        assertEquals(cpTryParseHex("#E0A020"), custom.warning)
        assertEquals(cpTryParseHex("#D02040"), custom.error)
        assertEquals(cpTryParseHex("#405080"), custom.selection)
    }

    @Test
    fun `a colour that no longer parses falls back rather than failing`() {
        val custom = themeCustomizationFrom(
            AppSettings(customThemeAccent = "not a colour", customThemeColors = CustomThemeColors(error = "#12")),
        )

        assertEquals(DefaultCustomAccent, custom.accent)
        assertNull(custom.error, "an unreadable role goes back to automatic")
    }

    @Test
    fun `a saved font is resolved and a blank one is the platform's`() {
        assertNotNull(themeCustomizationFrom(AppSettings(uiFontFamily = "Serif")).fontFamily)
        assertNull(themeCustomizationFrom(AppSettings(uiFontFamily = "  ")).fontFamily)
    }

    @Test
    fun `a text size outside the offered range is clamped into it`() {
        assertEquals(1.3f, themeCustomizationFrom(AppSettings(uiFontScale = 5f)).fontScale)
        assertEquals(0.9f, themeCustomizationFrom(AppSettings(uiFontScale = 0.1f)).fontScale)
        assertEquals(1.15f, themeCustomizationFrom(AppSettings(uiFontScale = 1.15f)).fontScale)
    }

    @Test
    fun `the window opens on what was saved, with the custom switch as the caller decides`() {
        val settings = AppSettings(
            customThemeAccent = "#AA3355",
            customThemeDark = false,
            customThemeColors = CustomThemeColors(warning = "#E0A020"),
            uiFontFamily = "Serif",
            uiFontScale = 1.15f,
        )

        val choice = themeChoiceFrom(settings, useCustomColors = false)

        assertEquals(false, choice.useCustomColors)
        assertEquals("#AA3355", choice.accentHex)
        assertEquals(false, choice.dark)
        assertEquals(CustomThemeColors(warning = "#E0A020"), choice.colors)
        assertEquals("Serif", choice.fontFamily)
        assertEquals(1.15f, choice.fontScale)
    }

    @Test
    fun `the accent field shows the default accent until one is saved`() {
        assertEquals(cpColorToHex(DefaultCustomAccent), customAccentHex(AppSettings()))
        assertEquals(cpColorToHex(DefaultCustomAccent), customAccentHex(AppSettings(customThemeAccent = "zz")))
        assertEquals("#AA3355", customAccentHex(AppSettings(customThemeAccent = "#AA3355")))
    }
}
