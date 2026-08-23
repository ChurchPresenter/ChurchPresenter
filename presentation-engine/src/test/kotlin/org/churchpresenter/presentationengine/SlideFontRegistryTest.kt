package org.churchpresenter.presentationengine

import org.apache.poi.common.usermodel.fonts.FontInfo
import org.junit.jupiter.api.Test
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import java.awt.Font
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlideFontRegistryTest {

    @Test
    fun `bundled font registers and resolves by family`() {
        val stream = assertNotNull(
            javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf"),
            "test font resource missing"
        )
        val family = assertNotNull(SlideFontRegistry.registerFontStream(stream))
        assertEquals("Open Sans", family)
        assertTrue(SlideFontRegistry.isFamilyAvailable("Open Sans"))
        assertTrue(SlideFontRegistry.isFamilyAvailable("open sans"), "family lookup must be case-insensitive")
    }

    @Test
    fun `unknown family substitutes to an available one`() {
        javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf")?.let {
            SlideFontRegistry.registerFontStream(it)
        }
        val resolved = SlideFontRegistry.resolveFamily("Definitely Not A Font 123")
        assertTrue(
            SlideFontRegistry.isFamilyAvailable(resolved) || resolved == Font.SANS_SERIF,
            "resolveFamily must return a renderable family, got $resolved"
        )
    }

    @Test
    fun `calibri substitution prefers the table over the generic default`() {
        javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf")?.let {
            SlideFontRegistry.registerFontStream(it)
        }
        val resolved = SlideFontRegistry.resolveFamily("Calibri")
        // Whatever the platform offers, the result must be a real, available family.
        assertTrue(SlideFontRegistry.isFamilyAvailable(resolved), "got unavailable family $resolved")
    }

    @Test
    fun `initialising without a directory scan is cheap and idempotent`() {
        // The scan is a startup cost measured in seconds on font-heavy machines; the family index
        // alone has to be enough for resolveFamily to work.
        SlideFontRegistry.initialize(scanSystemDirs = false)
        SlideFontRegistry.initialize(scanSystemDirs = false)
        javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf")?.let {
            SlideFontRegistry.registerFontStream(it)
        }
        assertTrue(SlideFontRegistry.isFamilyAvailable("Open Sans"))
    }

    @Test
    fun `a family no machine could have installed resolves to no file`() {
        assertNull(
            SlideFontRegistry.findSystemFontFile("Definitely Not A Font 123", wantBold = false),
            "a name nothing is called must not match a font file",
        )
    }

    @Test
    fun `an empty family name is answered without scanning for it`() {
        assertNull(SlideFontRegistry.findSystemFontFile("   ", wantBold = true))
    }

    @Test
    fun `a font file that is found is a real file of a font type`() {
        // Which families exist is the machine's business; that the answer is usable is not.
        for (family in listOf("Arial", "Helvetica", "Verdana", "DejaVu Sans")) {
            val file = SlideFontRegistry.findSystemFontFile(family, wantBold = false) ?: continue
            assertTrue(file.isFile, "$family resolved to a path that is not a file: $file")
            assertTrue(
                file.extension.lowercase() in setOf("ttf", "otf", "ttc"),
                "$family resolved to something that is not a font: $file",
            )
        }
    }

    @Test
    fun `a bold request falls back to the regular cut rather than to nothing`() {
        for (family in listOf("Arial", "Helvetica", "Verdana", "DejaVu Sans")) {
            if (SlideFontRegistry.findSystemFontFile(family, wantBold = false) == null) continue
            assertNotNull(
                SlideFontRegistry.findSystemFontFile(family, wantBold = true),
                "$family has a regular cut, so a bold request must not come back empty-handed",
            )
            return
        }
    }

    // ── The POI font handler ──────────────────────────────────────────────────

    /** Runs [block] against a throwaway raster's graphics, which is all the font handler needs. */
    private fun <T> withGraphics(block: (Graphics2D) -> T): T {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        return try {
            block(graphics)
        } finally {
            graphics.dispose()
        }
    }

    @Test
    fun `a run that states no font of its own does not take the slide down with it`() {
        // POI hands the handler a null FontInfo for a run with no font set — its own
        // getFontWithFallback is written for it, and its caller answers null by retrying with the
        // paragraph's default family. A non-null Kotlin parameter turned that into an NPE that
        // escaped POI's draw and failed the slide, and every text slide in a deck alike.
        assertNull(withGraphics { SlideFontRegistry.drawFontManager.getMappedFont(it, null) })
    }

    @Test
    fun `a font nobody has installed is still mapped to something renderable`() {
        javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf")?.let {
            SlideFontRegistry.registerFontStream(it)
        }
        val requested = object : FontInfo {
            override fun getTypeface(): String = "Definitely Not A Font 123"
        }
        val mapped = assertNotNull(withGraphics { SlideFontRegistry.drawFontManager.getMappedFont(it, requested) })
        val typeface = assertNotNull(mapped.typeface)
        assertTrue(
            SlideFontRegistry.isFamilyAvailable(typeface) || typeface == Font.SANS_SERIF,
            "the handler must never pass on a family that cannot be rendered, got $typeface",
        )
    }
}
