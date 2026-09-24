package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Custom theme's settings: the accent, base, per-role colours, UI font and UI text size.
 *
 * None of them needed a migration step, because every one has a default that means "as before" — no
 * accent, a dark base, every role automatic, the platform font at its own size. These pin that a
 * settings file written before they existed still loads to exactly that, and that what the Customize
 * Theme window writes comes back unchanged.
 */
class CustomThemeSettingsTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun `a settings file from before the custom theme loads with every choice at its default`() {
        val old = json.decodeFromString<AppSettings>("""{"theme":"OCEAN","language":"en"}""")

        assertEquals("OCEAN", old.theme)
        assertEquals("", old.customThemeAccent)
        assertEquals(true, old.customThemeDark)
        assertEquals(CustomThemeColors(), old.customThemeColors)
        assertEquals("", old.uiFontFamily)
        assertEquals(1f, old.uiFontScale)
    }

    @Test
    fun `every role starts automatic`() {
        val colors = CustomThemeColors()
        listOf(
            colors.background, colors.secondary, colors.text, colors.success,
            colors.warning, colors.error, colors.selection,
        ).forEach { assertEquals("", it) }
    }

    @Test
    fun `what the customize window writes comes back unchanged`() {
        val written = AppSettings(
            theme = "CUSTOM",
            customThemeAccent = "#3F7FBF",
            customThemeDark = false,
            customThemeColors = CustomThemeColors(
                background = "#102030",
                secondary = "#20A0A0",
                text = "#F0F0F0",
                success = "#30C060",
                warning = "#E0A020",
                error = "#D02040",
                selection = "#405080",
            ),
            uiFontFamily = "Inter",
            uiFontScale = 1.15f,
        )

        assertEquals(written, json.decodeFromString<AppSettings>(json.encodeToString(written)))
    }

    @Test
    fun `a single role set on its own leaves the others automatic`() {
        val colors = json.decodeFromString<CustomThemeColors>("""{"warning":"#E0A020"}""")

        assertEquals(CustomThemeColors(warning = "#E0A020"), colors)
    }
}
