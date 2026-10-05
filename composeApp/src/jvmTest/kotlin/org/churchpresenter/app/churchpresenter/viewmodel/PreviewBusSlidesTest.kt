package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.sharedui.models.Presenting
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A presentation through the Preview bus, as the Presentation tab drives it through its output: a
 * new deck is cued and shows its slide still on Preview, a slide of the deck on air goes straight
 * out, and Take puts the cued slide on air and starts its playback there.
 */
class PreviewBusSlidesTest {

    private val program = PresenterManager(showPresenterWindowInitially = false)
    private val bus = program.previewBus
    private val preview = bus.manager
    private val slides = program.slidesOutput

    private val dir = Files.createTempDirectory("cp-preview-slides").toFile()

    @AfterTest
    fun tidy() {
        dir.deleteRecursively()
    }

    private fun staticDeck(name: String): Deck {
        val file = File(dir, name)
        PDDocument().use { doc ->
            doc.addPage(PDPage())
            doc.save(file)
        }
        return (PresentationLoader.load(file) as LoadResult.Success).deck
    }

    private val first = ImageBitmap(1, 1)
    private val second = ImageBitmap(2, 2)

    /** What the tab pushes going live with, or stepping to, [fileName]'s slide [index]. */
    private fun show(fileName: String, index: Int, slide: ImageBitmap = first, deck: Deck? = null) {
        slides.setSelectedSlide(slide)
        slides.setLiveSlide(fileName, index)
        slides.setNextSlide(second)
        slides.setPresenterNotes("$fileName notes $index")
        slides.setPresentingMode(Presenting.PRESENTATION)
        if (deck != null) slides.presentationShowSlide(deck, index, false) else slides.clearPresentationPlayback()
    }

    @Test
    fun `off, a slide goes straight to air`() {
        show("a.pdf", 0)
        assertEquals(Presenting.PRESENTATION, program.slideContent.value)
        assertSame(first, program.selectedSlide.value)
        assertEquals(LiveSlide("a.pdf", 0), program.liveSlide.value)
        assertFalse(preview.anythingLive)
        assertFalse(slides.presentationCued)
    }

    @Test
    fun `a deck going live with nothing on air is cued, its slide still on Preview`() {
        bus.setEnabled(true)
        val deck = staticDeck("a.pdf")
        show("a.pdf", 0, deck = deck)

        assertTrue(slides.presentationCued)
        assertSame(first, preview.selectedSlide.value)
        assertSame(second, preview.nextSlide.value)
        assertEquals("a.pdf notes 0", preview.presenterNotes.value)
        assertEquals(Presenting.NONE, program.slideContent.value)
        assertNull(program.selectedSlide.value)
        assertSame(deck, assertNotNull(bus.cuedPlayback).deck, "its playback waits for Take")
        assertFalse(slides.advancePresentationStep(deck, 0), "no build steps on Preview")
        assertFalse(slides.rewindPresentationStep(deck, 0))
    }

    @Test
    fun `a slide of the deck on air is a step, and goes straight out`() {
        show("a.pdf", 0)
        bus.setEnabled(true)
        show("a.pdf", 1, slide = second)

        assertSame(second, program.selectedSlide.value)
        assertEquals(LiveSlide("a.pdf", 1), program.liveSlide.value)
        assertEquals("a.pdf notes 1", program.presenterNotes.value)
        assertFalse(bus.anythingCued)
    }

    @Test
    fun `another deck is cued while the one on air stays up`() {
        show("a.pdf", 0)
        bus.setEnabled(true)
        show("b.pdf", 0, slide = second)

        assertTrue(slides.presentationCued)
        assertEquals(LiveSlide("b.pdf", 0), preview.liveSlide.value)
        assertEquals(LiveSlide("a.pdf", 0), program.liveSlide.value)
        assertSame(first, program.selectedSlide.value)
    }

    @Test
    fun `Take puts the cued slide on air and starts its playback there`() {
        show("a.pdf", 0)
        bus.setEnabled(true)
        show("b.pdf", 2, slide = second, deck = staticDeck("b.pdf"))
        bus.take()

        assertEquals(LiveSlide("b.pdf", 2), program.liveSlide.value)
        assertSame(second, program.selectedSlide.value)
        assertEquals("b.pdf notes 2", program.presenterNotes.value)
        assertNull(bus.cuedPlayback, "handed to the air")
        assertFalse(bus.anythingCued)

        show("b.pdf", 3, slide = first)
        assertEquals(LiveSlide("b.pdf", 3), program.liveSlide.value, "the taken deck steps on air")
    }

    @Test
    fun `a cued slide with no playback leaves none to start`() {
        bus.setEnabled(true)
        show("a.pdf", 0, deck = staticDeck("a.pdf"))
        show("a.pdf", 1)
        assertNull(bus.cuedPlayback)
    }
}
