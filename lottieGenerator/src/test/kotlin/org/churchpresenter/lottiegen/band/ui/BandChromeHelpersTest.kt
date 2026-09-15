package org.churchpresenter.lottiegen.band.ui

import androidx.compose.ui.graphics.Color
import org.churchpresenter.lottiegen.band.BandColorRole
import org.churchpresenter.lottiegen.band.BandImage
import org.churchpresenter.lottiegen.band.BandStyle
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.ui.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The plain functions behind the Band pane's rows: hex handling and the colour-role plumbing. */
class BandChromeHelpersTest {

    @Test
    fun `a hex is accepted once it is six digits, with or without its hash, and upper-cased`() {
        assertEquals("#1A2B3C", normalizeHex("1a2b3c"))
        assertEquals("#1A2B3C", normalizeHex(" #1a2b3c "))
        assertNull(normalizeHex("#1a2b3"), "five digits is still being typed")
        assertNull(normalizeHex("#1a2b3cd"))
        assertNull(normalizeHex("#12345g"), "not hex")
        assertNull(normalizeHex(""))
        assertEquals(Color(0xFF1A2B3C), parseBandHex("1a2b3c"))
        assertNull(parseBandHex("nope"))
    }

    @Test
    fun `the roles a style paints are listed in the pane's order, with the labels the file names them by`() {
        val cfg = BibleLottieGenConfig()
        val solid = cfg.copy(bandStyle = BandStyle.SOLID_BAR)
        assertEquals(listOf(BandColorRole.BACKGROUND, BandColorRole.ACCENT), solid.roles())
        assertEquals(
            listOf(BandColorRole.BACKGROUND, BandColorRole.SECOND, BandColorRole.ACCENT, BandColorRole.TERTIARY),
            cfg.copy(bandStyle = BandStyle.WAVE_DECK).roles(),
        )
        assertEquals(Strings.bandColorBackground, roleLabel(cfg, BandColorRole.BACKGROUND))
        val picture = BandImage("data:image/png;base64,", 1, 1, "p")
        val pictured = cfg.copy(images = mapOf(BandColorRole.BACKGROUND to picture))
        assertEquals(Strings.bandColorTint, roleLabel(pictured, BandColorRole.BACKGROUND), "a picture makes it a tint")
        assertEquals(Strings.bandColorGradient, roleLabel(cfg, BandColorRole.SECOND))
        assertEquals(Strings.bandColorAccent, roleLabel(cfg, BandColorRole.ACCENT))
        assertEquals(Strings.bandColorThird, roleLabel(cfg, BandColorRole.TERTIARY))
    }

    @Test
    fun `each role reads and writes its own colour and alpha`() {
        val cfg = BibleLottieGenConfig(bgAlpha = 10, secondAlpha = 20, accentAlpha = 30, tertiaryAlpha = 40)
        for (role in BandColorRole.entries) {
            val recoloured = cfg.withColor(role, "#ABCDEF")
            assertEquals("#ABCDEF", recoloured.colorOf(role), "$role colour")
            BandColorRole.entries.filter { it != role }
                .forEach { assertEquals(cfg.colorOf(it), recoloured.colorOf(it), "$role leaves $it") }
            val faded = cfg.withAlpha(role, 77)
            assertEquals(77, faded.alphaOf(role), "$role alpha")
            BandColorRole.entries.filter { it != role }
                .forEach { assertEquals(cfg.alphaOf(it), faded.alphaOf(it), "$role leaves $it's alpha") }
        }
        assertEquals(listOf(10, 20, 30, 40), BandColorRole.entries.map { cfg.alphaOf(it) })
    }
}
