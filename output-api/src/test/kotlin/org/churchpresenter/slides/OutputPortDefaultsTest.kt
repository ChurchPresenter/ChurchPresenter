package org.churchpresenter.slides

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.media.MediaOutput
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.presentationengine.model.pdfDeck
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.presenter.PresentationFrame
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * What a port answers for what an output does not track: nothing cued, nothing on air. A tab asks
 * these of whatever output it is handed, the preview bus's or a test's, and must not have to know.
 */
class OutputPortDefaultsTest {

    private class PlainPictures : PictureOutput {
        override fun setSelectedImagePath(imagePath: String?) = Unit
        override fun setNextImagePath(path: String?) = Unit
    }

    private class PlainDeck : DeckOutput {
        var shown: Triple<Deck, Int, Boolean>? = null
        override val presentationFrame: State<PresentationFrame?> = mutableStateOf(null)
        override fun setSelectedSlide(slide: ImageBitmap?) = Unit
        override fun setNextSlide(slide: ImageBitmap?) = Unit
        override fun setLiveSlide(fileName: String?, index: Int) = Unit
        override fun setPresenterNotes(notes: String) = Unit
        override fun presentationShowSlide(deck: Deck, slideIndex: Int, enterAtLastStep: Boolean) {
            shown = Triple(deck, slideIndex, enterAtLastStep)
        }
        override fun advancePresentationStep(deck: Deck, slideIndex: Int) = false
        override fun rewindPresentationStep(deck: Deck, slideIndex: Int) = false
        override fun clearPresentationPlayback() = Unit
    }

    private class PlainMedia : MediaOutput {
        override fun isLive(mode: Presenting) = false
        override val showPresenterWindow: State<Boolean> = mutableStateOf(true)
        override fun setPresentingMode(mode: Presenting) = Unit
        override fun setShowPresenterWindow(show: Boolean) = Unit
        override fun setCurrentMedia(url: String, type: String) = Unit
        override fun requestClearDisplay() = Unit
    }

    @Test
    fun `an output that cues nothing reports no pictures and no deck cued`() {
        assertFalse(PlainPictures().picturesCued)
        assertFalse(PlainDeck().presentationCued)
    }

    @Test
    fun `an output that does not say what slide is on air reports none`() {
        assertNull(PlainDeck().slideOnAir)
    }

    @Test
    fun `a slide is shown from its first step unless asked to enter at its last`() {
        val deck = PlainDeck()
        val pdf = pdfDeck(File("sermon.pdf"), slides = emptyList())

        deck.presentationShowSlide(pdf, 3)

        assertEquals(Triple(pdf, 3, false), deck.shown)
    }

    @Test
    fun `an output that does not track media has none on air`() {
        assertEquals("", PlainMedia().mediaOnAir)
    }
}
