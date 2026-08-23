package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the picker is told about each installed family.
 *
 * Split deliberately: the tables — what is hidden, what is shaped how, what is worth projecting —
 * are asserted exactly, because they are the app's own judgement. The glyph scan is asserted only
 * on its shape, never on a particular family: the machine running this decides what is installed,
 * and CI has almost nothing.
 */
class FontCatalogTest {

    @AfterTest
    fun forgetMeasurements() = FontCatalog.reset()

    // --- what a text picker has no business offering ---

    @Test
    fun `the system's own internal faces are hidden`() {
        assertTrue(FontCatalog.isHidden(".AppleSystemUIFont"))
        assertTrue(FontCatalog.isHidden("#Fallback"))
    }

    @Test
    fun `dingbat and icon sets are hidden`() {
        listOf("Wingdings", "Webdings", "Zapf Dingbats", "Segoe MDL2 Assets", "Apple Color Emoji")
            .forEach { assertTrue(FontCatalog.isHidden(it), "$it is not text") }
    }

    @Test
    fun `hiding ignores case, so a differently-cased name is not smuggled through`() {
        assertTrue(FontCatalog.isHidden("WINGDINGS"))
    }

    @Test
    fun `an ordinary family is not hidden`() {
        listOf("Arial", "Georgia", "Papyrus", "PT Sans").forEach { assertFalse(FontCatalog.isHidden(it)) }
    }

    @Test
    fun `a hidden family already in use is still offered`() {
        val snapshot = FontCatalog.unmeasuredSnapshot(listOf("Arial", "Wingdings"), keep = "Wingdings")

        assertEquals(listOf("Arial", "Wingdings"), snapshot.faces.map { it.name })
        assertEquals(0, snapshot.hiddenCount)
    }

    @Test
    fun `what was hidden is counted rather than quietly dropped`() {
        val snapshot = FontCatalog.unmeasuredSnapshot(listOf("Arial", "Wingdings", "Webdings"))

        assertEquals(listOf("Arial"), snapshot.faces.map { it.name })
        assertEquals(2, snapshot.hiddenCount)
    }

    // --- what a family is shaped like ---

    @Test
    fun `a measured monospace is monospace whatever it is called`() {
        assertEquals(FontCategory.MONO, FontCatalog.categoryOf("Andale", monospaced = true))
    }

    @Test
    fun `the name settles the shape when the glyphs cannot`() {
        assertEquals(FontCategory.MONO, FontCatalog.categoryOf("Courier New", monospaced = null))
        assertEquals(FontCategory.SERIF, FontCatalog.categoryOf("Times New Roman", monospaced = null))
        assertEquals(FontCategory.SERIF, FontCatalog.categoryOf("Georgia", monospaced = false))
        assertEquals(FontCategory.DISPLAY, FontCatalog.categoryOf("Arial Black", monospaced = false))
        assertEquals(FontCategory.DISPLAY, FontCatalog.categoryOf("Comic Sans MS", monospaced = false))
    }

    @Test
    fun `a family the tables have never heard of is a sans`() {
        // The honest default: nothing in a font file says "serif", so an unknown name gets the
        // commonest shape rather than a guess dressed up as a measurement.
        assertEquals(FontCategory.SANS, FontCatalog.categoryOf("Wibble Neue", monospaced = false))
    }

    @Test
    fun `display beats serif where a name suggests both`() {
        // "Bookman Old Style" carries a serif hint and a display one; a poster face it is.
        assertEquals(FontCategory.DISPLAY, FontCatalog.categoryOf("Copperplate Book", monospaced = false))
    }

    // --- what is worth putting on a screen ---

    @Test
    fun `the plainly legible families are recommended`() {
        listOf("Arial", "Verdana", "Tahoma", "Georgia", "PT Sans", "Helvetica Neue")
            .forEach { assertTrue(FontCatalog.isRecommended(it), "$it should be recommended") }
    }

    @Test
    fun `a display face is not recommended, however well known`() {
        listOf("Papyrus", "Comic Sans MS", "Impact", "Zapfino")
            .forEach { assertFalse(FontCatalog.isRecommended(it), "$it should not be recommended") }
    }

    @Test
    fun `recommending ignores case`() {
        assertTrue(FontCatalog.isRecommended("ARIAL"))
    }

    @Test
    fun `the unmeasured description already knows the shape and the recommendation`() {
        val face = FontCatalog.unmeasuredSnapshot(listOf("Georgia")).faces.single()

        assertEquals(FontCategory.SERIF, face.category)
        assertTrue(face.recommended)
        // Coverage is the one thing it cannot know, and it claims nothing rather than guessing.
        assertFalse(face.cyrillic)
        assertFalse(face.hebrew)
    }

    @Test
    fun `an unmeasured snapshot says so`() {
        assertFalse(FontCatalog.unmeasuredSnapshot(listOf("Arial")).measured)
    }

    // --- the glyph scan ---

    @Test
    fun `a measured snapshot describes every family it was asked about`() {
        val families = listOf("Arial", "Georgia", "Papyrus", "Wibble Neue")

        val snapshot = FontCatalog.snapshot(families)

        assertTrue(snapshot.measured)
        assertEquals(families, snapshot.faces.map { it.name })
    }

    @Test
    fun `a family Skia has never heard of falls back rather than dropping out of the list`() {
        val face = FontCatalog.snapshot(listOf("Definitely Not Installed 42")).faces.single()

        assertEquals("Definitely Not Installed 42", face.name)
        assertFalse(face.cyrillic)
    }

    @Test
    fun `measuring twice gives the same answer`() {
        val once = FontCatalog.snapshot(listOf("Arial", "Georgia"))
        val twice = FontCatalog.snapshot(listOf("Arial", "Georgia"))

        assertEquals(once.faces, twice.faces)
    }

    @Test
    fun `a second call about different families measures those too`() {
        // The scan is cached per family, not per list: the canvas asks about one set and the
        // settings dialog another, and a cache keyed by the whole list would serve one of them stale.
        FontCatalog.snapshot(listOf("Arial"))

        val second = FontCatalog.snapshot(listOf("Georgia", "Papyrus"))

        assertEquals(listOf("Georgia", "Papyrus"), second.faces.map { it.name })
    }

    @Test
    fun `an empty machine yields an empty catalog rather than an error`() {
        val snapshot = FontCatalog.snapshot(emptyList())

        assertTrue(snapshot.faces.isEmpty())
        assertEquals(0, snapshot.hiddenCount)
    }
}
