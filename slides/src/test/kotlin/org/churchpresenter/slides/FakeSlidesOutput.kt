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

    override val presentingMode: State<Presenting> = mode
    override val screenLocks: State<Map<Int, Presenting>> = locks
    override val presentationFrame: MutableState<PresentationFrame?> = mutableStateOf(null)

    val showPresenterWindow = mutableStateOf(false)
    val selectedImagePath = mutableStateOf<String?>(null)
    val nextImagePath = mutableStateOf<String?>(null)
    val selectedSlide = mutableStateOf<ImageBitmap?>(null)
    val nextSlide = mutableStateOf<ImageBitmap?>(null)
    val liveSlide = mutableStateOf<Pair<String?, Int>?>(null)
    val presenterNotes = mutableStateOf("")
    val animationType = mutableStateOf<AnimationType?>(null)
    val transitionDuration = mutableStateOf<Int?>(null)
    val shownSlides = mutableListOf<Int>()
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

    override fun advancePresentationStep(deck: Deck, slideIndex: Int): Boolean = false

    override fun rewindPresentationStep(deck: Deck, slideIndex: Int): Boolean = false

    override fun clearPresentationPlayback() {
        playbackClears++
    }
}
