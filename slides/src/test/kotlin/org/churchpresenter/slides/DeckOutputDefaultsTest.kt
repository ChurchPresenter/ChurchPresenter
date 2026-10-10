package org.churchpresenter.slides

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.slides.presenter.PresentationFrame
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DeckOutputDefaultsTest {

    private class BareDeckOutput : DeckOutput {
        override val presentationFrame: State<PresentationFrame?> = mutableStateOf(null)
        override fun setSelectedSlide(slide: ImageBitmap?) = Unit
        override fun setNextSlide(slide: ImageBitmap?) = Unit
        override fun setLiveSlide(fileName: String?, index: Int) = Unit
        override fun setPresenterNotes(notes: String) = Unit
        override fun presentationShowSlide(deck: Deck, slideIndex: Int, enterAtLastStep: Boolean) = Unit
        override fun advancePresentationStep(deck: Deck, slideIndex: Int) = false
        override fun rewindPresentationStep(deck: Deck, slideIndex: Int) = false
        override fun clearPresentationPlayback() = Unit
    }

    @Test
    fun `an output that says nothing about its deck has none on air and none cued`() {
        val output = BareDeckOutput()
        assertNull(output.slideOnAir)
        assertFalse(output.presentationCued)
    }
}
