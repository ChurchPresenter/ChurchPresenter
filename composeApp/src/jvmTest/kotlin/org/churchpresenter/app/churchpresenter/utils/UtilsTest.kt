package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import java.util.Locale
import kotlin.test.AfterTest
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
 * The rest of [Utils]: locale-driven clock format and font lookup. Both read process-global state
 * (default Locale, installed fonts), so the locale is restored after each test.
 */
class UtilsSystemTest {

    private val originalLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `24-hour detection follows the default locale`() {
        // Drives the clock/countdown display format, so it must track the OS locale, not a guess.
        Locale.setDefault(Locale.US)
        assertEquals(false, Utils.isSystemUsing24HourFormat(), "en-US uses AM/PM")

        Locale.setDefault(Locale.GERMANY)
        assertEquals(true, Utils.isSystemUsing24HourFormat(), "de-DE uses a 24-hour clock")

        Locale.setDefault(Locale.FRANCE)
        assertEquals(true, Utils.isSystemUsing24HourFormat(), "fr-FR uses a 24-hour clock")
    }

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
