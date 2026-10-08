package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.SlidesOutput
import org.churchpresenter.slides.presenter.PresentationFrame

/**
 * [PresenterManager] as the `:slides` tabs see it: every call goes straight through, except that
 * pictures and slides go through the [PreviewBus] -- a new folder or deck is cued while preview
 * mode is on, and the tab steps whichever of Preview and Program holds it.
 *
 * A cued slide shows still on Preview; its animation is kept and started on air by Take.
 *
 * A separate class rather than `PresenterManager : SlidesOutput` so the manager's own declaration is
 * untouched -- its detekt baseline entry is keyed on that signature.
 */
class PresenterSlidesOutput(private val manager: PresenterManager) : SlidesOutput {
    private val bus get() = manager.previewBus

    override fun isLive(mode: Presenting): Boolean = manager.isLive(mode)
    override val picturesCued: Boolean get() = bus.isCued(Presenting.PICTURES)
    override val presentationCued: Boolean get() = bus.isCued(Presenting.PRESENTATION)

    /** Where the deck's slides last went: Preview for a deck cued there, else Program. */
    private var deckTarget: PresenterManager = manager

    /** Where a slide's next slide, notes and playback go now -- see [deckTarget]. */
    private val slideTarget: PresenterManager
        get() = if (presentationCued && deckTarget === bus.manager) bus.manager else manager

    /**
     * The slide pushed ahead of the [setLiveSlide] that names its deck, held until then while
     * preview mode is on -- which bus it goes to depends on the deck.
     */
    private var heldSlide: ImageBitmap? = null
    private var holdingSlide = false
    override val screenLocks: State<Map<Int, Presenting>> get() = manager.screenLocks
    override val presentationFrame: State<PresentationFrame?> get() = manager.presentationFrame
    override val slideOnAir: Pair<String?, Int>?
        get() = manager.liveSlide.value?.let { it.fileName to it.index }

    override fun setPresentingMode(mode: Presenting) = bus.present(mode)
    override fun setShowPresenterWindow(show: Boolean) = manager.setShowPresenterWindow(show)
    override fun setAnimationType(type: AnimationType) {
        manager.setAnimationType(type)
        bus.manager.setAnimationType(type)
    }

    override fun setTransitionDuration(duration: Int) {
        manager.setTransitionDuration(duration)
        bus.manager.setTransitionDuration(duration)
    }

    override fun setSelectedImagePath(imagePath: String?) {
        val target = bus.forPicture(imagePath)
        target.setSelectedImagePath(imagePath)
        if (target !== manager) target.setPresentingMode(Presenting.PICTURES)
    }

    override fun setNextImagePath(path: String?) =
        (if (picturesCued) bus.manager else manager).setNextImagePath(path)

    override fun setSelectedSlide(slide: ImageBitmap?) {
        if (!bus.enabled.value) return manager.setSelectedSlide(slide)
        heldSlide = slide
        holdingSlide = true
    }

    override fun setLiveSlide(fileName: String?, index: Int) {
        // A slide of the deck on air is a step; any other deck is cued.
        deckTarget = if (bus.forNewItem(Presenting.PRESENTATION) === manager ||
            manager.isLive(Presenting.PRESENTATION) && fileName == manager.liveSlide.value?.fileName
        ) {
            manager
        } else {
            bus.manager
        }
        if (holdingSlide) deckTarget.setSelectedSlide(heldSlide)
        holdingSlide = false
        heldSlide = null
        deckTarget.setLiveSlide(fileName, index)
        if (deckTarget !== manager) deckTarget.setPresentingMode(Presenting.PRESENTATION)
    }

    override fun setNextSlide(slide: ImageBitmap?) = slideTarget.setNextSlide(slide)
    override fun setPresenterNotes(notes: String) = slideTarget.setPresenterNotes(notes)

    override fun presentationShowSlide(deck: Deck, slideIndex: Int, enterAtLastStep: Boolean) {
        if (slideTarget === manager) return manager.presentationShowSlide(deck, slideIndex, enterAtLastStep)
        bus.cuedPlayback = CuedPlayback(deck, slideIndex, enterAtLastStep)
    }

    /** A cued slide has no build steps on Preview, so the tab moves to the next slide instead. */
    override fun advancePresentationStep(deck: Deck, slideIndex: Int): Boolean =
        slideTarget === manager && manager.advancePresentationStep(deck, slideIndex)

    override fun rewindPresentationStep(deck: Deck, slideIndex: Int): Boolean =
        slideTarget === manager && manager.rewindPresentationStep(deck, slideIndex)

    override fun clearPresentationPlayback() {
        if (slideTarget === manager) manager.clearPresentationPlayback() else bus.cuedPlayback = null
    }
}
