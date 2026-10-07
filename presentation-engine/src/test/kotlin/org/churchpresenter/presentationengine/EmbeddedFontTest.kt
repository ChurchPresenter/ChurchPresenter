package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.fonts.SlideFontRegistry
import org.churchpresenter.presentationengine.pptx.PowerPointDeckSupport
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fonts a deck carries inside it: a usable one is registered, and one that cannot be read -- an
 * obfuscated or embed-restricted payload -- is skipped without failing the deck.
 */
class EmbeddedFontTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-embedded-font").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `a deck's embedded fonts are registered and a corrupt one is skipped`() {
        val pptx = Fixtures.createPptx(dir, listOf("Body" to "Notes"))
        val font = checkNotNull(javaClass.getResourceAsStream("/fonts/OpenSans-Regular.ttf")).use { it.readBytes() }
        Fixtures.addEmbeddedFontPart(pptx, "font1.fntdata", font)
        Fixtures.addEmbeddedFontPart(pptx, "font2.fntdata", byteArrayOf(1, 2, 3, 4))

        val slides = PowerPointDeckSupport.open(pptx).use { it.slides.size }

        assertEquals(1, slides, "the corrupt font must not fail the deck")
        assertTrue(SlideFontRegistry.isFamilyAvailable("Open Sans"))
    }
}
