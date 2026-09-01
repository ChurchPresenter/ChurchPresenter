package org.churchpresenter.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which side of the light/dark divide each theme falls on.
 *
 * The settings screens group their swatches by this, so a theme that answers wrongly is filed under
 * the wrong heading — and a theme added without an answer would not compile, which is the point of
 * exhausting the enum rather than keeping a list beside it.
 */
class ThemeBrightnessTest {

    /** Relative luminance, WCAG 2.1 — the same maths the ramp test uses. */
    private fun luminance(argb: androidx.compose.ui.graphics.Color): Double {
        fun channel(v: Float): Double =
            if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * channel(argb.red) + 0.7152 * channel(argb.green) + 0.0722 * channel(argb.blue)
    }

    @Test
    fun `system defers rather than claiming a brightness`() {
        assertNull(ThemeMode.SYSTEM.isLightTheme(), "system is whichever the machine is set to")
    }

    @Test
    fun `every other theme states which it is`() {
        ThemeMode.entries.filter { it != ThemeMode.SYSTEM }.forEach { mode ->
            assertNotNull(mode.isLightTheme(), "$mode must declare a brightness")
        }
    }

    @Test
    fun `the claim matches the palette it actually paints`() {
        // The classification is written by hand; this is what stops it drifting from the colours.
        ThemeMode.entries.filter { it != ThemeMode.SYSTEM }.forEach { mode ->
            val background = colorSchemeFor(mode).background
            val actuallyLight = luminance(background) > 0.5
            assertEquals(
                mode.isLightTheme(),
                actuallyLight,
                "$mode is filed as ${if (mode.isLightTheme() == true) "light" else "dark"} " +
                    "but its background luminance is %.3f".format(luminance(background)),
            )
        }
    }

    @Test
    fun `the two sections are the same size, which is what the picker lays out`() {
        val light = ThemeMode.entries.count { it.isLightTheme() == true }
        val dark = ThemeMode.entries.count { it.isLightTheme() == false }
        assertEquals(light, dark, "the wizard draws these as two even blocks; $light light vs $dark dark")
        assertTrue(light >= 6, "there should be at least two rows of three in each section")
    }
}
