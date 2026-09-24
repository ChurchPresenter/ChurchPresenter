package org.churchpresenter.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The palette [customColorScheme] builds from a user's colours, across the whole range of colours a
 * user can pick rather than the one default a render test happens to use.
 *
 * The Custom theme is the one theme nobody chose the colours for. Every preset was looked at before
 * it shipped; this one is whatever an operator picks five minutes before a service, so the guarantees
 * the presets get by inspection — layers that step apart, text that reads — have to hold for every
 * pick. The sweep is every 15° of hue at a muted and a fully saturated strength, on both bases.
 */
class ThemeCustomizationTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb).toDouble()
    }

    private fun hex(c: Color) =
        "#%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    /** Every hue in 15° steps, muted and fully saturated. */
    private val picks: List<Color> = (0 until 360 step 15).flatMap { hue ->
        listOf(0.25f, 1f).map { saturation -> Color.hsl(hue.toFloat(), saturation, 0.5f) }
    }

    /** Runs [check] against a palette per pick and base, reporting every failure at once. */
    private fun everyPick(build: (Color, Boolean) -> ThemeCustomization, check: (ColorScheme) -> String?) {
        val failures = buildList {
            for (dark in listOf(true, false)) for (pick in picks) {
                check(customColorScheme(build(pick, dark)))?.let { add("dark=$dark pick=${hex(pick)}: $it") }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n", prefix = "\n"))
    }

    private fun rampFailure(s: ColorScheme): String? {
        val steps = mapOf(
            "card on page" to contrast(s.surfaceContainer, s.surfaceVariant),
            "field on card" to contrast(s.surfaceContainerHigh, s.surfaceContainer),
            "field on page" to contrast(s.surfaceContainerHigh, s.surfaceVariant),
            "border on field" to contrast(s.outlineVariant, s.surfaceContainerHigh),
        )
        return steps.entries.firstOrNull { it.value < 1.10 }?.let { "${it.key} is only %.3f:1".format(it.value) }
    }

    private fun textFailure(s: ColorScheme): String? {
        val surfaces = listOf(s.background, s.surface, s.surfaceContainer, s.surfaceContainerHigh, s.surfaceVariant)
        val worst = surfaces.minOf { contrast(s.onSurface, it) }
        return if (worst >= 4.5) null else "text ${hex(s.onSurface)} is only %.2f:1 on its worst surface".format(worst)
    }

    // ── From the accent alone ───────────────────────────────────────────────────

    @Test
    fun `any accent keeps the three settings layers apart`() =
        everyPick({ accent, dark -> ThemeCustomization(accent = accent, dark = dark) }, ::rampFailure)

    @Test
    fun `any accent keeps body text readable on every surface`() =
        everyPick({ accent, dark -> ThemeCustomization(accent = accent, dark = dark) }, ::textFailure)

    @Test
    fun `any accent gives a label band whose text reads`() =
        everyPick({ accent, dark -> ThemeCustomization(accent = accent, dark = dark) }) { s ->
            val ratio = contrast(s.onPrimaryContainer, s.primaryContainer)
            if (ratio >= 4.5) null else "selected-row text is only %.2f:1".format(ratio)
        }

    @Test
    fun `any accent gives a button whose label reads`() =
        everyPick({ accent, dark -> ThemeCustomization(accent = accent, dark = dark) }) { s ->
            // 3:1 is WCAG's bar for UI components and bold labels, which is what a button carries.
            // On a light base a yellow or cyan accent is bright even at the fixed lightness, which is
            // why the label is chosen by contrast rather than always light.
            val buttons = mapOf(
                "primary" to (s.onPrimary to s.primary),
                "secondary" to (s.onSecondary to s.secondary),
                "tertiary" to (s.onTertiary to s.tertiary),
            )
            buttons.entries.firstNotNullOfOrNull { (name, pair) ->
                val ratio = contrast(pair.first, pair.second)
                if (ratio >= 3.0) null else "$name button label is only %.2f:1".format(ratio)
            }
        }

    @Test
    fun `the base decides light or dark, not the accent`() {
        for (pick in picks) {
            assertTrue(isDarkScheme(customColorScheme(ThemeCustomization(accent = pick, dark = true))))
            assertFalse(isDarkScheme(customColorScheme(ThemeCustomization(accent = pick, dark = false))))
        }
    }

    @Test
    fun `the primary carries the accent's hue`() {
        val accent = Color.hsl(200f, 0.6f, 0.5f)
        assertEquals(200f, Hsl.of(customColorScheme(ThemeCustomization(accent = accent)).primary).hue, 1f)
    }

    @Test
    fun `status and tooltip roles come from the preset of the same base when not picked`() {
        val dark = customColorScheme(ThemeCustomization(dark = true))
        val preset = colorSchemeFor(ThemeMode.DARK)
        assertEquals(preset.error, dark.error)
        assertEquals(preset.inverseSurface, dark.inverseSurface)
        assertEquals(colorSchemeFor(ThemeMode.LIGHT).error, customColorScheme(ThemeCustomization(dark = false)).error)
    }

    @Test
    fun `colorSchemeFor custom is the generated palette`() {
        // ColorScheme has no value equality, so compare the roles the app paints with.
        val custom = ThemeCustomization(accent = Color.hsl(30f, 0.7f, 0.5f), dark = false)
        val expected = customColorScheme(custom)
        val actual = colorSchemeFor(ThemeMode.CUSTOM, custom = custom)
        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.surfaceContainer, actual.surfaceContainer)
    }

    // ── Per-role overrides ──────────────────────────────────────────────────────

    @Test
    fun `any background keeps the three settings layers apart`() =
        everyPick({ background, dark -> ThemeCustomization(dark = dark, background = background) }, ::rampFailure)

    @Test
    fun `any background still leaves body text readable`() =
        everyPick({ background, dark -> ThemeCustomization(dark = dark, background = background) }, ::textFailure)

    @Test
    fun `a background tints the surfaces with its own hue`() {
        val navy = Color.hsl(225f, 0.6f, 0.3f)
        val scheme = customColorScheme(ThemeCustomization(background = navy))
        assertEquals(225f, Hsl.of(scheme.surface).hue, 1f)
    }

    @Test
    fun `any text colour is moved until it reads, keeping its hue`() {
        everyPick({ text, dark -> ThemeCustomization(dark = dark, text = text) }, ::textFailure)
        // A text colour that already reads is left exactly as picked.
        val white = Color.White
        assertEquals(white, customColorScheme(ThemeCustomization(dark = true, text = white)).onSurface)
        // One that does not keeps its hue on the way to reading.
        val darkRed = Color.hsl(0f, 0.8f, 0.2f)
        val moved = customColorScheme(ThemeCustomization(dark = true, text = darkRed)).onSurface
        assertTrue(abs(Hsl.of(moved).hue - 0f) < 2f || abs(Hsl.of(moved).hue - 360f) < 2f)
    }

    @Test
    fun `a picked selection is used exactly, with text that reads on it`() =
        everyPick({ selection, dark -> ThemeCustomization(dark = dark, selection = selection) }) { s ->
            val ratio = contrast(s.onPrimaryContainer, s.primaryContainer)
            if (ratio >= 4.5) null else "selection text is only %.2f:1".format(ratio)
        }

    @Test
    fun `a picked error is used exactly, with text that reads on it`() {
        val red = Color.hsl(350f, 0.9f, 0.45f)
        val scheme = customColorScheme(ThemeCustomization(error = red))
        assertEquals(red, scheme.error)
        assertTrue(contrast(scheme.onError, scheme.error) >= 4.5)
        assertTrue(contrast(scheme.onErrorContainer, scheme.errorContainer) >= 4.5)
    }

    @Test
    fun `a picked secondary carries its own hue`() {
        val teal = Color.hsl(170f, 0.7f, 0.4f)
        assertEquals(170f, Hsl.of(customColorScheme(ThemeCustomization(secondary = teal)).secondary).hue, 1f)
    }

    @Test
    fun `picked status colours are used exactly, with text that reads on them`() {
        for (dark in listOf(true, false)) for (pick in picks) {
            val status = customSemanticColorsFor(ThemeCustomization(dark = dark, success = pick, warning = pick))
            assertEquals(pick, status.success)
            assertEquals(pick, status.warning)
            assertTrue(contrast(status.onSuccess, status.success) >= 4.5, "success text on ${hex(pick)}")
            assertTrue(contrast(status.onWarning, status.warning) >= 4.5, "warning text on ${hex(pick)}")
            assertTrue(contrast(status.onSuccessContainer, status.successContainer) >= 4.5)
            assertTrue(contrast(status.onWarningContainer, status.warningContainer) >= 4.5)
        }
    }

    @Test
    fun `unpicked status colours are the base's own`() {
        val dark = customSemanticColorsFor(ThemeCustomization(dark = true))
        assertEquals(semanticColorsFor(colorSchemeFor(ThemeMode.DARK)).success, dark.success)
        val light = customSemanticColorsFor(ThemeCustomization(dark = false))
        assertEquals(semanticColorsFor(colorSchemeFor(ThemeMode.LIGHT)).warning, light.warning)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    @Test
    fun `hsl reads hue, saturation and lightness`() {
        val red = Hsl.of(Color.Red)
        assertEquals(0f, red.hue, 0.5f)
        assertEquals(1f, red.saturation, 0.01f)
        assertEquals(0.5f, red.lightness, 0.01f)
        assertEquals(120f, Hsl.of(Color.Green).hue, 0.5f)
        assertEquals(240f, Hsl.of(Color.Blue).hue, 0.5f)
        val grey = Hsl.of(Color.Gray)
        assertEquals(0f, grey.saturation)
    }

    @Test
    fun `a UI font is applied to every text style`() {
        val family = FontFamily.Monospace
        val typography = AppTypography.withFontFamily(family)
        listOf(
            typography.displayLarge, typography.displayMedium, typography.displaySmall,
            typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
            typography.titleLarge, typography.titleMedium, typography.titleSmall,
            typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
            typography.labelLarge, typography.labelMedium, typography.labelSmall,
        ).forEach { assertEquals(family, it.fontFamily) }
        assertEquals(AppTypography.bodyMedium.fontSize, typography.bodyMedium.fontSize, "only the family changes")
    }

    @Test
    fun `the offered text sizes run small to large around the default`() {
        assertEquals(UI_FONT_SCALES.sorted(), UI_FONT_SCALES)
        assertTrue(1f in UI_FONT_SCALES)
    }
}
