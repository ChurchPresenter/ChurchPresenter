package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [Utils.parseHexColor] parses colour strings that arrive from settings.json and from the
 * Companion/Instance Link wire formats, i.e. from sources a user or another machine can get
 * wrong. Its contract is "never throw, fall back to white", so the malformed cases matter as
 * much as the well-formed ones.
 */
class UtilsParseHexColorTest {

    @Test
    fun `parses 6-digit rgb with and without the leading hash`() {
        assertEquals(Color(0xFF, 0x00, 0x00), Utils.parseHexColor("#FF0000"))
        assertEquals(Color(0xFF, 0x00, 0x00), Utils.parseHexColor("FF0000"))
        assertEquals(Color(0x12, 0x34, 0x56), Utils.parseHexColor("#123456"))
    }

    @Test
    fun `treats an 8-digit value as ARGB, not RGBA`() {
        // Alpha leads. A trailing-alpha reading would give red = 0x80 here instead of 0xFF.
        val c = Utils.parseHexColor("#80FF0000")
        assertEquals(Color(0xFF, 0x00, 0x00, 0x80), c)
    }

    @Test
    fun `recognises the transparent keyword case-insensitively`() {
        assertEquals(Color.Transparent, Utils.parseHexColor("transparent"))
        assertEquals(Color.Transparent, Utils.parseHexColor("Transparent"))
        assertEquals(Color.Transparent, Utils.parseHexColor("TRANSPARENT"))
    }

    @Test
    fun `falls back to white rather than throwing on malformed input`() {
        // Wrong length, non-hex digits, empty, and a stray 3-digit CSS shorthand -- none of
        // which this parser supports. Every one must degrade, never propagate an exception.
        for (bad in listOf("", "#", "#FFF", "#12345", "#1234567", "GGGGGG", "#GGGGGG", "not a color")) {
            assertEquals(Color.White, Utils.parseHexColor(bad), "expected white fallback for \"$bad\"")
        }
    }

    @Test
    fun `is case-insensitive across hex digits`() {
        assertEquals(Utils.parseHexColor("#abcdef"), Utils.parseHexColor("#ABCDEF"))
    }
}

/**
 * The rest of [Utils]: font lookup, which reads process-global state (the installed families). The
 * locale-driven clock format moved to :settings with the settings default that asks it —
 * `ClockFormatTest` there.
 */
class UtilsSystemTest {

    @Test
    fun `system font list is non-empty and sorted case-insensitively`() {
        val fonts = Utils.getAvailableSystemFonts()
        assertTrue(fonts.isNotEmpty(), "a JVM always reports at least the logical font families")
        assertEquals(fonts.sortedBy { it.lowercase() }, fonts, "the settings dropdowns rely on this order")
    }

    @Test
    fun `font family lookup never throws, whatever the name`() {
        // Font names come from saved settings and can name a font that is not installed here.
        assertNotNull(Utils.systemFontFamilyOrDefault("Arial"))
        assertNotNull(Utils.systemFontFamilyOrDefault("A Font That Does Not Exist 12345"))
        assertNotNull(Utils.systemFontFamilyOrDefault(""))
    }

    /**
     * Documents that the function's `catch -> FontFamily.Default` fallback is effectively DEAD
     * CODE: `FontFamily(name)` performs no validation and never throws, so even an empty or
     * nonsense name yields a FontListFontFamily carrying that name rather than the default family.
     * Resolution to a real typeface happens later, in the font resolver.
     *
     * Not a crash risk — the contract "never throws" does hold — but anyone reading the fallback
     * and assuming a bad setting gets corrected here would be wrong.
     */
    @Test
    fun `the default-font fallback branch is never actually reached`() {
        assertTrue(
            Utils.systemFontFamilyOrDefault("") != FontFamily.Default,
            "if this now equals FontFamily.Default, the fallback became reachable — update this test",
        )
    }
}

/**
 * [Utils.contrastRatio] and [Utils.ensureContrast] back the WCAG-driven text-color fallback
 * `ScheduleTab` applies to a section label's own user-chosen text/background pair — the only two
 * callers of [Utils.contrastRatio] in the app, and previously with no direct test of their own
 * (`ThemeTest` re-derives the same WCAG math independently for its theme-palette checks, rather
 * than exercising this code). The ratio math is [private] `relativeLuminance`, reached only through
 * these two public functions.
 */
class UtilsContrastTest {

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 0.01) =
        assertTrue(abs(expected - actual) <= tolerance, "expected ~$expected, was $actual")

    // ── contrastRatio ─────────────────────────────────────────────────────────

    @Test
    fun `black on white is the maximum WCAG ratio, 21 to 1`() =
        assertApprox(21.0, Utils.contrastRatio(Color.Black, Color.White))

    @Test
    fun `a color against itself has the minimum ratio, 1 to 1`() {
        assertApprox(1.0, Utils.contrastRatio(Color.Red, Color.Red))
        assertApprox(1.0, Utils.contrastRatio(Color.White, Color.White))
    }

    @Test
    fun `the ratio is symmetric regardless of argument order`() {
        val ab = Utils.contrastRatio(Color.Red, Color.Blue)
        val ba = Utils.contrastRatio(Color.Blue, Color.Red)
        assertApprox(ab, ba)
    }

    // ── ensureContrast ────────────────────────────────────────────────────────

    @Test
    fun `a foreground that already meets the minimum ratio is returned unchanged`() {
        // Black on white comfortably clears the 4.5 default, so ensureContrast must be a no-op —
        // never silently replacing a color pair the caller's own choice already satisfies.
        assertEquals(Color.Black, Utils.ensureContrast(Color.Black, Color.White))
    }

    @Test
    fun `a foreground that fails the ratio is replaced by whichever of white or black contrasts better`() {
        // White text on white background has nowhere near enough contrast; black is the obvious
        // rescue, so this also confirms the substitution actually engages rather than passing the
        // original color through by accident.
        assertEquals(Color.Black, Utils.ensureContrast(Color.White, Color.White))
        // A near-black foreground on a black background is the mirror case: white must win.
        assertEquals(Color.White, Utils.ensureContrast(Color(0x22, 0x22, 0x22), Color.Black))
    }

    @Test
    fun `minRatio changes the outcome for a pair that clears AA but not AAA`() {
        // Medium green (#008000) on white measures ~5.1:1 — above the 4.5 default (AA) but below
        // the stricter 7.0 ScheduleTab uses for its label text (AAA), so the same pair must resolve
        // two different ways depending on which threshold is asked for.
        val green = Color(0, 128, 0)
        assertEquals(
            green,
            Utils.ensureContrast(green, Color.White, minRatio = 4.5),
            "5.1:1 clears the default AA threshold, so the original color must come back untouched",
        )
        assertEquals(
            Color.Black,
            Utils.ensureContrast(green, Color.White, minRatio = 7.0),
            "the same 5.1:1 pair fails AAA, so it must fall back to whichever of white/black wins — black, on white",
        )
    }
}
