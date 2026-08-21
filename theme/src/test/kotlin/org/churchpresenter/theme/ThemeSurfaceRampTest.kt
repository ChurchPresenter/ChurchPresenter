@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * That every theme's settings screens stay readable as three distinct layers (issue #95).
 *
 * A settings tab paints its page with `surfaceVariant`, puts `SettingsSection` cards on it in
 * `surfaceContainer`, and puts `SettingsTextField` inputs on those cards in `surfaceContainerHigh`.
 * If two of the three land on the same tone, the layer between them stops reading as a layer: a card
 * becomes a flat region of page, or a field becomes a hole in a card with only a 1dp border to say
 * otherwise — and in several themes `outlineVariant` equals `surfaceVariant`, so even the border can
 * disappear.
 *
 * That is what went wrong before: no scheme declared the container roles, so both came from the M3
 * baseline rather than the theme, and in the Studio theme the card came out at exactly the luminance
 * of its own page. Since the tones are now chosen per theme, they need something holding them apart —
 * every theme, not just the two anyone happens to open.
 *
 * The bar is deliberately low. These are adjacent tones on one ramp, not text on a background, so
 * WCAG's 4.5:1 does not apply; what matters is that the step is present and above the ~1.1:1 where an
 * edge stops being visible on a projector-lit booth monitor. Body text against the field is held to
 * the real contrast bar separately below.
 */
class ThemeSurfaceRampTest {

    /** Relative luminance, WCAG 2.1. */
    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double =
            if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun hex(c: Color): String =
        "#%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())

    /** Runs [check] against every theme the app ships, reporting all failures at once. */
    private fun everyTheme(check: (ColorScheme) -> String?) {
        val failures = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                ThemeMode.entries.forEach { mode ->
                    ChurchPresenterTheme(themeMode = mode) {
                        check(MaterialTheme.colorScheme)?.let { failures.add("${mode.name}: $it") }
                    }
                }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n", prefix = "\n"))
    }

    private val minStep = 1.10

    @Test
    fun `a settings card is distinguishable from the page it sits on`() {
        everyTheme { s ->
            val ratio = contrast(s.surfaceContainer, s.surfaceVariant)
            if (ratio >= minStep) null
            else "card ${hex(s.surfaceContainer)} on page ${hex(s.surfaceVariant)} is only %.3f:1".format(ratio)
        }
    }

    @Test
    fun `an input field is distinguishable from the card it sits on`() {
        everyTheme { s ->
            val ratio = contrast(s.surfaceContainerHigh, s.surfaceContainer)
            if (ratio >= minStep) null
            else "field ${hex(s.surfaceContainerHigh)} on card ${hex(s.surfaceContainer)} is only %.3f:1".format(ratio)
        }
    }

    @Test
    fun `a field is not the same tone as the page two layers behind it`() {
        // The change originally proposed for #95 — pointing the field at surfaceVariant — is exactly
        // this: the field would have been painted the page's own colour.
        everyTheme { s ->
            val ratio = contrast(s.surfaceContainerHigh, s.surfaceVariant)
            if (ratio >= minStep) null
            else "field ${hex(s.surfaceContainerHigh)} matches the page ${hex(s.surfaceVariant)} (%.3f:1)".format(ratio)
        }
    }

    @Test
    fun `a field's border reads against the field`() {
        // In several themes outlineVariant is the same value as surfaceVariant, so a field painted
        // the page colour loses its border as well as its fill. This is what keeps the two apart.
        everyTheme { s ->
            val ratio = contrast(s.outlineVariant, s.surfaceContainerHigh)
            if (ratio >= minStep) null
            else "border ${hex(s.outlineVariant)} on field ${hex(s.surfaceContainerHigh)} is only %.3f:1".format(ratio)
        }
    }

    @Test
    fun `body text on a field clears the real contrast bar`() {
        // Text, unlike the ramp above, is held to WCAG AA for normal text.
        everyTheme { s ->
            val ratio = contrast(s.onSurface, s.surfaceContainerHigh)
            if (ratio >= 4.5) null
            else "text ${hex(s.onSurface)} on field ${hex(s.surfaceContainerHigh)} is only %.2f:1".format(ratio)
        }
    }

    @Test
    fun `every theme declares the container roles rather than inheriting the baseline`() {
        // The M3 baseline values, which is what a scheme that omits them silently gets. They are a
        // lavender tint that belongs to none of these palettes.
        val baseline = setOf(Color(0xFFF3EDF7), Color(0xFFECE6F0), Color(0xFF211F26), Color(0xFF2B2930))
        everyTheme { s ->
            when {
                s.surfaceContainer in baseline -> "surfaceContainer is still the M3 baseline ${hex(s.surfaceContainer)}"
                s.surfaceContainerHigh in baseline ->
                    "surfaceContainerHigh is still the M3 baseline ${hex(s.surfaceContainerHigh)}"
                else -> null
            }
        }
    }

    @Test
    fun `the ramp check would have caught what shipped before`() {
        // Guards the assertions above against passing vacuously: fed the old baseline pair against
        // the Studio page, the card/page step is the 1.000 that was actually shipping.
        val studioPage = Color(0xFF182030)
        val baselineCard = Color(0xFF211F26)
        assertTrue(
            contrast(baselineCard, studioPage) < minStep,
            "the old baseline card must fail the bar this test sets",
        )
    }

    @Test
    fun `every theme's label swatch reads its own text on its own band`() {
        // The schedule-label presets show one pair per theme -- primaryContainer with its own
        // onPrimaryContainer -- so every scheme's accent pair has to work as a band with text on
        // it, not just as a button. A label whose text does not read on its band is a section
        // heading nobody can see.
        everyTheme { s ->
            val accent = contrast(s.onPrimaryContainer, s.primaryContainer)
            val card = contrast(s.onSurface, s.surfaceContainer)
            when {
                accent < 4.5 ->
                    "accent swatch ${hex(s.onPrimaryContainer)} on ${hex(s.primaryContainer)} is only %.2f:1".format(
                        accent,
                    )
                // The head of the list, and the default a new label opens on.
                card < 4.5 ->
                    "card swatch ${hex(s.onSurface)} on ${hex(s.surfaceContainer)} is only %.2f:1".format(card)
                else -> null
            }
        }
    }
}
