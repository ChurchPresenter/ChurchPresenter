package org.churchpresenter.app.churchpresenter

import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import java.io.File

/**
 * The Presentation tab's view model as a [PresentationSlideCursor]: every read is live, and a step
 * either way also tells any Instance Link follower through [link].
 */
internal fun PresentationViewModel.slideCursor(link: InstanceLinkBridge): PresentationSlideCursor {
    val viewModel = this
    return object : PresentationSlideCursor {
        override val selectedSlideIndex: Int get() = viewModel.selectedSlideIndex
        override val slideFiles: List<File> get() = viewModel.slideFiles
        override val presentationName: String? get() = viewModel.selectedPresentation?.name
        override val slideNotes: List<String> get() = viewModel.slideNotes
        override val deck: Deck? get() = viewModel.deck
        override fun nextShownSlideIndex(index: Int): Int? = viewModel.nextShownSlideIndex(index)
        override fun nextSlide() = viewModel.nextSlide(link.sendNextSlide)
        override fun previousSlide() = viewModel.previousSlide(link.sendPreviousSlide)
    }
}
