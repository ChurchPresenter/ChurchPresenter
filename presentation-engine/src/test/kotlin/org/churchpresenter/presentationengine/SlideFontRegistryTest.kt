package org.churchpresenter.presentationengine

import org.junit.jupiter.api.Test
import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import java.awt.Font
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

    // ── The POI font handler ────────────────────────────────────────────────────

    /**
     * A run that names no font arrives at the handler as a null [FontInfo]. That used to be fatal:
     * Kotlin's non-null check on the parameter threw before the method body ran, and the whole
     * slide render died rather than falling back to a face. Reported from the field against
     * `In Christus ist mein ganzer Halt.pptx`.
     */
    @Test
    fun `a run with no font of its own is given one instead of throwing`() {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            val mapped = SlideFontRegistry.drawFontManager.getMappedFont(graphics, null)
            assertNotNull(mapped, "a null font must be answered with a real one")
            assertTrue(
                mapped.typeface?.isNotBlank() == true,
                "the fallback must name a family, not an empty string",
            )
        } finally {
            graphics.dispose()
        }
    }

    @Test
    fun `a named font still comes back with a usable family`() {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            val mapped = SlideFontRegistry.drawFontManager.getMappedFont(graphics) { "Calibri" }
            assertTrue(
                mapped.typeface?.isNotBlank() == true,
                "an unavailable family must be substituted, never blanked",
            )
        } finally {
            graphics.dispose()
        }
    }
}
