package org.churchpresenter.app.churchpresenter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The nine colour schemes behind the theme picker, checked as data rather than as pixels.
 *
 * Each scheme is written out by hand, twenty-odd colours at a time, and the ones that go wrong go
 * wrong quietly: a foreground copied from the wrong scheme leaves text the same colour as what it
 * sits on, which no compiler notices and nobody sees until that theme is selected during a service.
 * These tests read the schemes directly and assert the two properties that make a theme usable —
 * text stands out from what it is drawn on, and a light theme is actually light.
 *
 * The schemes are private top-level values, so they are reached by reflection on their file class;
 * the mapping from [ThemeMode] to scheme is duplicated here on purpose, because pinning "MIDNIGHT
 * is a dark theme" is precisely the point.
 *
 * `ChurchPresenterTheme` itself is a composable and needs a composition to run, so it is not
 * covered here.
 */
class ThemeTest {

    private fun scheme(name: String): ColorScheme =
        Class.forName("org.churchpresenter.app.churchpresenter.ui.theme.ThemeKt")
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(null) as ColorScheme

    /** Every theme the picker offers, paired with the scheme `ChurchPresenterTheme` selects for it. */
    private val lightThemes = mapOf(
        ThemeMode.LIGHT to "LightColorScheme",
        ThemeMode.WARM to "WarmColorScheme",
        ThemeMode.OCEAN to "OceanColorScheme",
        ThemeMode.ROSE to "RoseColorScheme",
    )

    private val darkThemes = mapOf(
        ThemeMode.DARK to "DarkColorScheme",
        ThemeMode.MIDNIGHT to "MidnightColorScheme",
        ThemeMode.FOREST to "ForestColorScheme",
        ThemeMode.MOCHA to "MochaColorScheme",
        ThemeMode.STUDIO to "StudioColorScheme",
    )

    private val allThemes get() = lightThemes + darkThemes

    // ── Colour maths (sRGB relative luminance, as WCAG defines it) ──────────────

    private fun Color.relativeLuminance(): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.relativeLuminance()
        val lb = b.relativeLuminance()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** The foreground/background pairings Material draws text and icons with. */
    private fun ColorScheme.readablePairs(): List<Triple<String, Color, Color>> = listOf(
        Triple("onBackground on background", onBackground, background),
        Triple("onSurface on surface", onSurface, surface),
        Triple("onPrimary on primary", onPrimary, primary),
        Triple("onSecondary on secondary", onSecondary, secondary),
        Triple("onError on error", onError, error),
        Triple("onSurfaceVariant on surfaceVariant", onSurfaceVariant, surfaceVariant),
    )

    // ── Every theme is legible ──────────────────────────────────────────────────

    @Test
    fun `every theme draws its text clearly enough to read`() {
        // 3.0 is WCAG's floor for large text. Well below what a designed scheme achieves, so this
        // catches mistakes (a foreground taken from the wrong scheme) rather than judging taste.
        val failures = allThemes.flatMap { (mode, name) ->
            scheme(name).readablePairs()
                .map { (label, foreground, background) -> Triple(mode, label, contrast(foreground, background)) }
                .filter { it.third < 3.0 }
        }

        assertTrue(
            failures.isEmpty(),
            "unreadable colour pairings: " + failures.joinToString { "${it.first} ${it.second} = %.2f:1".format(it.third) },
        )
    }

    @Test
    fun `no theme has text the same colour as what it sits on`() {
        val identical = allThemes.flatMap { (mode, name) ->
            scheme(name).readablePairs()
                .filter { (_, foreground, background) -> foreground == background }
                .map { "$mode ${it.first}" }
        }

        assertTrue(identical.isEmpty(), "invisible text in: $identical")
    }

    // ── Light themes are light, dark themes are dark ────────────────────────────

    @Test
    fun `a light theme is drawn on a light background`() {
        lightThemes.forEach { (mode, name) ->
            val colors = scheme(name)
            assertTrue(
                colors.background.relativeLuminance() > colors.onBackground.relativeLuminance(),
                "$mode is offered as a light theme but its background is darker than its text",
            )
        }
    }

    @Test
    fun `a dark theme is drawn on a dark background`() {
        darkThemes.forEach { (mode, name) ->
            val colors = scheme(name)
            assertTrue(
                colors.background.relativeLuminance() < colors.onBackground.relativeLuminance(),
                "$mode is offered as a dark theme but its background is lighter than its text",
            )
        }
    }

    @Test
    fun `a theme's surfaces sit on the same side as its background`() {
        // A light scheme with a dark surface (or the reverse) produces panels that fight the window.
        lightThemes.forEach { (mode, name) ->
            val colors = scheme(name)
            assertTrue(colors.surface.relativeLuminance() > 0.5, "$mode has a dark surface in a light theme")
        }
        darkThemes.forEach { (mode, name) ->
            val colors = scheme(name)
            assertTrue(colors.surface.relativeLuminance() < 0.5, "$mode has a light surface in a dark theme")
        }
    }

    // ── The picker offers distinct themes ───────────────────────────────────────

    @Test
    fun `every theme mode except system has its own scheme`() {
        assertEquals(
            ThemeMode.entries.size - 1,
            allThemes.size,
            "a newly added ThemeMode needs a scheme here and in ChurchPresenterTheme's when",
        )
        assertTrue(ThemeMode.SYSTEM !in allThemes.keys, "system follows the OS rather than having its own colours")
    }

    @Test
    fun `no two themes are the same colour`() {
        val backgrounds = allThemes.mapValues { (_, name) -> scheme(name).primary to scheme(name).background }

        assertEquals(
            backgrounds.size,
            backgrounds.values.toSet().size,
            "two themes that look identical are two ways to pick the same thing: $backgrounds",
        )
    }
}

/**
 * The picker's own state.
 *
 * [ThemeManager] is deliberately a plain holder — the composable layer reads it through a
 * CompositionLocal — so it is testable directly.
 */
class ThemeManagerTest {

    @Test
    fun `a new manager follows the operating system`() {
        assertEquals(
            ThemeMode.SYSTEM,
            ThemeManager().themeMode.value,
            "matching the OS is the least surprising default before anything is configured",
        )
    }

    @Test
    fun `every theme can be selected`() {
        val manager = ThemeManager()

        ThemeMode.entries.forEach { mode ->
            manager.setThemeMode(mode)
            assertEquals(mode, manager.themeMode.value)
        }
    }

    @Test
    fun `selecting the same theme twice is stable`() {
        val manager = ThemeManager()
        manager.setThemeMode(ThemeMode.OCEAN)
        manager.setThemeMode(ThemeMode.OCEAN)
        assertEquals(ThemeMode.OCEAN, manager.themeMode.value)
    }

    @Test
    fun `two managers do not share a theme`() {
        val one = ThemeManager()
        val other = ThemeManager()

        one.setThemeMode(ThemeMode.FOREST)

        assertEquals(ThemeMode.SYSTEM, other.themeMode.value, "a second window must not be switched by the first")
    }
}
