package org.churchpresenter.slides

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.presenter.PresentationFrame

/**
 * What the Pictures and Presentation tabs need from the live output: putting a picture or a slide
 * on screen, stepping a deck's builds, and reading back what is showing.
 *
 * The app's `PresenterSlidesOutput` is the one implementation, passing every call to
 * `PresenterManager`. The tabs take this rather than the manager itself so the module never reaches
 * into the app, and so a test can hand a tab a fake that records what it was told.
 */
interface SlidesOutput : LiveOutput, PictureOutput, DeckOutput

/** What is on the output, and how it changes. */
interface LiveOutput {
    val presentingMode: State<Presenting>
    val screenLocks: State<Map<Int, Presenting>>

    fun setPresentingMode(mode: Presenting)
    fun setShowPresenterWindow(show: Boolean)
    fun setAnimationType(type: AnimationType)
    fun setTransitionDuration(duration: Int)
}

/** The picture on the output, and the one queued behind it for the crossfade. */
interface PictureOutput {
    /**
     * Whether pictures are cued on a preview, waiting to go on air. The tab keeps stepping them
     * there as it would on air; off unless the app has a preview.
     */
    val picturesCued: Boolean get() = false

    fun setSelectedImagePath(imagePath: String?)
    fun setNextImagePath(path: String?)
}

/** The deck slide on the output: its bitmaps, its notes, and its animated playback. */
interface DeckOutput {
    val presentationFrame: State<PresentationFrame?>

    fun setSelectedSlide(slide: ImageBitmap?)
    fun setNextSlide(slide: ImageBitmap?)
    fun setLiveSlide(fileName: String?, index: Int)
    fun setPresenterNotes(notes: String)

    fun presentationShowSlide(deck: Deck, slideIndex: Int, enterAtLastStep: Boolean = false)
    fun advancePresentationStep(deck: Deck, slideIndex: Int): Boolean
    fun rewindPresentationStep(deck: Deck, slideIndex: Int): Boolean
    fun clearPresentationPlayback()
}
