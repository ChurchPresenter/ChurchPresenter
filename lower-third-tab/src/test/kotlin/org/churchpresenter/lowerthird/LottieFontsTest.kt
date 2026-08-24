package org.churchpresenter.lowerthird

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.alexzhirkevich.compottie.assets.LottieFontSpec
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [LottieFonts] declares the bundled font families by filename. A typo or a font that never made it
 * into `resources/fonts/` doesn't fail the build — it silently falls back to a default typeface,
 * which is exactly the blocky-lower-third bug this object was written to fix. Resolving every
 * declared path against the classpath is what makes that a build failure instead.
 */
class LottieFontsTest {

    @Test
    fun `every declared bundled font actually exists on the classpath`() {
        val missing = LottieFonts.bundledFontResources().filter {
            LottieFonts::class.java.getResourceAsStream(it) == null
        }
        assertTrue(missing.isEmpty(), "declared but not bundled: $missing")
    }

    @Test
    fun `bundled font paths are well-formed and unique`() {
        val resources = LottieFonts.bundledFontResources()
        assertTrue(resources.isNotEmpty())
        assertTrue(resources.all { it.startsWith("/fonts/") }, "paths must be absolute classpath refs")
        assertTrue(resources.all { it.endsWith(".ttf") }, "only TrueType files are registered")
        assertEquals(resources.size, resources.toSet().size, "duplicate entries would register a font twice")
    }

    @Test
    fun `the families LottieGen offers are all bundled`() {
        // These are the family names lower-third lotties reference by name; a family missing here
        // renders with the wrong typeface rather than failing.
        val resources = LottieFonts.bundledFontResources()
        for (stem in listOf(
            "OpenSans", "Poppins", "Raleway", "AnonymousPro", "PatuaOne",
            "Lora", "AbrilFatface", "Cookie", "OleoScript", "Kalam", "FredokaOne",
        )) {
            assertTrue(resources.any { it.contains(stem) }, "no bundled font for family stem $stem")
        }
    }

    @Test
    fun `every font file has a regular cut`() {
        val resources = LottieFonts.bundledFontResources()
        val regulars = resources.filter { it.endsWith("-Regular.ttf") }
        val bolds = resources.filter { it.endsWith("-Bold.ttf") }
        assertEquals(11, regulars.size, "one regular cut per declared family")
        // Bold is optional per family, but a bold must never appear without its regular.
        for (bold in bolds) {
            val regular = bold.removeSuffix("-Bold.ttf") + "-Regular.ttf"
            assertTrue(regular in resources, "$bold has no matching regular cut")
        }
    }

    @Test
    fun `a bundled family loads real font bytes`() {
        val bytes = assertNotNull(LottieFonts.bundledFontBytes("Open Sans", wantBold = false))
        assertTrue(bytes.size > 1000, "a TTF should be more than a few bytes")
    }

    @Test
    fun `a bold request loads a different file than the regular one`() {
        val regular = assertNotNull(LottieFonts.bundledFontBytes("Open Sans", wantBold = false))
        val bold = assertNotNull(LottieFonts.bundledFontBytes("Open Sans", wantBold = true))
        assertFalse(regular.contentEquals(bold), "the bold cut must be a distinct file from the regular one")
    }

    @Test
    fun `a boldless family serves its regular bytes even when bold is asked for`() {
        val regular = assertNotNull(LottieFonts.bundledFontBytes("Patua One", wantBold = false))
        val boldRequest = assertNotNull(LottieFonts.bundledFontBytes("Patua One", wantBold = true))
        assertTrue(regular.contentEquals(boldRequest), "no bold file means the regular cut is served")
    }

    @Test
    fun `an unbundled family yields no bytes`() {
        assertNull(LottieFonts.bundledFontBytes("No Such Family", wantBold = false))
    }

    @Test
    fun `bold is wanted for heavy weights or an explicit -Bold name`() {
        assertTrue(LottieFonts.wantsBold(FontWeight.SemiBold, "Poppins"))
        assertTrue(LottieFonts.wantsBold(FontWeight.Bold, "Poppins"))
        assertTrue(
            LottieFonts.wantsBold(FontWeight.Normal, "Poppins-Bold"),
            "the -Bold suffix forces bold at any weight",
        )
    }

    @Test
    fun `bold is not wanted for light weights without a -Bold name`() {
        assertFalse(LottieFonts.wantsBold(FontWeight.Normal, "Poppins"))
        assertFalse(LottieFonts.wantsBold(FontWeight.Light, "Lora"))
        assertFalse(LottieFonts.wantsBold(FontWeight.Medium, "Raleway"), "Medium is below SemiBold")
    }

    @Test
    fun `systemFontBytes yields null for a family no platform installs`() {
        assertNull(LottieFonts.systemFontBytes("No Such Family ZZZ", wantBold = false))
    }

    @Test
    fun `loadFont returns null when the family is neither bundled nor installed`() {
        assertNull(LottieFonts.loadFont("No Such Family ZZZ", wantBold = false, style = FontStyle.Normal))
    }

    @Test
    fun `loadFont builds a typeface for a bundled family`() {
        assertNotNull(LottieFonts.loadFont("Open Sans", wantBold = false, style = FontStyle.Normal))
        assertNotNull(LottieFonts.loadFont("Open Sans", wantBold = true, style = FontStyle.Italic))
    }

    private fun fontSpec(
        family: String,
        name: String = family,
        weight: FontWeight = FontWeight.Normal,
        style: FontStyle = FontStyle.Normal,
    ): LottieFontSpec {
        val spec = mockk<LottieFontSpec>()
        every { spec.family } returns family
        every { spec.name } returns name
        every { spec.weight } returns weight
        every { spec.style } returns style
        return spec
    }

    @Test
    fun `font resolves a bundled family and caches the result`() = runBlocking {
        val first = assertNotNull(LottieFonts.font(fontSpec("Open Sans")))
        val second = LottieFonts.font(fontSpec("Open Sans"))
        assertSame(first, second, "a repeat lookup must return the cached typeface, not rebuild it")
    }

    @Test
    fun `font returns null for a family that is nowhere`() = runBlocking {
        assertNull(LottieFonts.font(fontSpec("No Such Family ZZZ")))
    }

    @Test
    fun `font takes the bold cut when the name ends in -Bold`() = runBlocking {
        assertNotNull(LottieFonts.font(fontSpec(family = "Open Sans", name = "Open Sans-Bold")))
        Unit
    }
}
