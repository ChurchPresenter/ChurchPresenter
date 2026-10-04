package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.SlidesOutput
import org.churchpresenter.slides.presenter.PresentationFrame

/**
 * [PresenterManager] as the `:slides` tabs see it: every call goes straight through, except that
 * pictures go through the [PreviewBus] -- a new folder is cued while preview mode is on, and the
 * tab steps whichever of Preview and Program holds its pictures.
 *
 * A separate class rather than `PresenterManager : SlidesOutput` so the manager's own declaration is
 * untouched -- its detekt baseline entry is keyed on that signature.
 */
class PresenterSlidesOutput(private val manager: PresenterManager) : SlidesOutput {
    private val bus get() = manager.previewBus

    override val presentingMode: State<Presenting> get() = manager.presentingMode
    override val picturesCued: Boolean get() = bus.isCued(Presenting.PICTURES)
    override val screenLocks: State<Map<Int, Presenting>> get() = manager.screenLocks
    override val presentationFrame: State<PresentationFrame?> get() = manager.presentationFrame

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

    override fun setSelectedSlide(slide: ImageBitmap?) = manager.setSelectedSlide(slide)
    override fun setNextSlide(slide: ImageBitmap?) = manager.setNextSlide(slide)
    override fun setLiveSlide(fileName: String?, index: Int) = manager.setLiveSlide(fileName, index)
    override fun setPresenterNotes(notes: String) = manager.setPresenterNotes(notes)

    override fun presentationShowSlide(deck: Deck, slideIndex: Int, enterAtLastStep: Boolean) =
        manager.presentationShowSlide(deck, slideIndex, enterAtLastStep)

    override fun advancePresentationStep(deck: Deck, slideIndex: Int): Boolean =
        manager.advancePresentationStep(deck, slideIndex)

    override fun rewindPresentationStep(deck: Deck, slideIndex: Int): Boolean =
        manager.rewindPresentationStep(deck, slideIndex)

    override fun clearPresentationPlayback() = manager.clearPresentationPlayback()
}
