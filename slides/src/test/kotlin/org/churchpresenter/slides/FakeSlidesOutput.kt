package org.churchpresenter.slides

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.presentationengine.model.Deck
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.presenter.PresentationFrame

class FakeSlidesOutput : SlidesOutput {
    private val mode = mutableStateOf(Presenting.NONE)
    private val locks = mutableStateOf<Map<Int, Presenting>>(emptyMap())

    val onAir: State<Presenting> = mode

    override fun isLive(mode: Presenting): Boolean = mode != Presenting.NONE && onAir.value == mode
    override val screenLocks: State<Map<Int, Presenting>> = locks
    override val presentationFrame: MutableState<PresentationFrame?> = mutableStateOf(null)

    val showPresenterWindow = mutableStateOf(false)
    val selectedImagePath = mutableStateOf<String?>(null)
    val nextImagePath = mutableStateOf<String?>(null)
    val selectedSlide = mutableStateOf<ImageBitmap?>(null)
    val nextSlide = mutableStateOf<ImageBitmap?>(null)
    val liveSlide = mutableStateOf<Pair<String?, Int>?>(null)
    override val slideOnAir: Pair<String?, Int>? get() = liveSlide.value
    val presenterNotes = mutableStateOf("")
    val animationType = mutableStateOf<AnimationType?>(null)
    val transitionDuration = mutableStateOf<Int?>(null)
    val shownSlides = mutableListOf<Int>()

    /** Build steps the live slide still has to play forward, and back; each step taken uses one up. */
    var stepsAhead = 0
    var stepsBehind = 0
    var playbackClears = 0
        private set

    fun setScreenLock(screenIndex: Int, mode: Presenting?) {
        locks.value = if (mode == null) locks.value - screenIndex else locks.value + (screenIndex to mode)
    }

    override fun setPresentingMode(mode: Presenting) {
        this.mode.value = mode
    }

    override fun setShowPresenterWindow(show: Boolean) {
        showPresenterWindow.value = show
    }

    override fun setAnimationType(type: AnimationType) {
        animationType.value = type
    }

    override fun setTransitionDuration(duration: Int) {
        transitionDuration.value = duration
    }

    override fun setSelectedImagePath(imagePath: String?) {
        selectedImagePath.value = imagePath
    }

    override fun setNextImagePath(path: String?) {
        nextImagePath.value = path
    }

    override fun setSelectedSlide(slide: ImageBitmap?) {
        selectedSlide.value = slide
    }

    override fun setNextSlide(slide: ImageBitmap?) {
        nextSlide.value = slide
    }

    override fun setLiveSlide(fileName: String?, index: Int) {
        liveSlide.value = fileName to index
    }

    override fun setPresenterNotes(notes: String) {
        presenterNotes.value = notes
    }

    override fun presentationShowSlide(deck: Deck, slideIndex: Int, enterAtLastStep: Boolean) {
        shownSlides += slideIndex
    }

    override fun advancePresentationStep(deck: Deck, slideIndex: Int): Boolean =
        (stepsAhead > 0).also { if (it) stepsAhead-- }

    override fun rewindPresentationStep(deck: Deck, slideIndex: Int): Boolean =
        (stepsBehind > 0).also { if (it) stepsBehind-- }

    override fun clearPresentationPlayback() {
        playbackClears++
    }
}
