package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.sharedui.models.Presenting

/** A live presentation slide by identity: the deck's file name and the slide's index in it. */
data class LiveSlide(val fileName: String?, val index: Int)

/** The presentation slide chosen, the one the outputs draw, and its notes. Part of [PresenterManager]. */
interface LiveSlides {
    val selectedSlide: State<ImageBitmap?>

    /** Which deck and slide [selectedSlide] is, for the on-screen history. Null outside PRESENTATION. */
    val liveSlide: State<LiveSlide?>

    /** What the outputs draw. */
    val displayedSlide: State<ImageBitmap?>
    val nextSlide: State<ImageBitmap?>

    /** The current slide's presenter notes, from PowerPoint or Keynote. */
    val presenterNotes: State<String>
    val slideTransitionAlpha: State<Float>
    val previousDisplayedSlide: State<ImageBitmap?>
    val slideSlideOffset: State<Float>
    val slideFrozen: State<Boolean>

    fun setSlideFrozen(frozen: Boolean)
    fun setSelectedSlide(slide: ImageBitmap?)

    /**
     * Names the slide just pushed with [setSelectedSlide]. Reported only while PRESENTATION is the
     * live mode: a slide pushed ahead of the mode switch is picked up when the mode switch reports.
     */
    fun setLiveSlide(fileName: String?, index: Int)
    fun setDisplayedSlide(slide: ImageBitmap?)
    fun setNextSlide(slide: ImageBitmap?)
    fun setPresenterNotes(notes: String)
    fun setSlideTransitionAlpha(alpha: Float)
    fun setPreviousDisplayedSlide(slide: ImageBitmap?)
    fun setSlideSlideOffset(offset: Float)
}

internal class LiveSlidesState(private val context: PresenterContext) : LiveSlides {

    private val _selectedSlide = mutableStateOf<ImageBitmap?>(null)
    override val selectedSlide: State<ImageBitmap?> = _selectedSlide

    private val _liveSlide = mutableStateOf<LiveSlide?>(null)
    override val liveSlide: State<LiveSlide?> = _liveSlide

    private val _displayedSlide = mutableStateOf<ImageBitmap?>(null)
    override val displayedSlide: State<ImageBitmap?> = _displayedSlide

    private val _nextSlide = mutableStateOf<ImageBitmap?>(null)
    override val nextSlide: State<ImageBitmap?> = _nextSlide

    private val _presenterNotes = mutableStateOf("")
    override val presenterNotes: State<String> = _presenterNotes

    private val _slideTransitionAlpha = mutableStateOf(1f)
    override val slideTransitionAlpha: State<Float> = _slideTransitionAlpha

    private val _previousDisplayedSlide = mutableStateOf<ImageBitmap?>(null)
    override val previousDisplayedSlide: State<ImageBitmap?> = _previousDisplayedSlide

    private val _slideSlideOffset = mutableStateOf(1f)
    override val slideSlideOffset: State<Float> = _slideSlideOffset

    private val _slideFrozen = mutableStateOf(false)
    override val slideFrozen: State<Boolean> = _slideFrozen

    override fun setSlideFrozen(frozen: Boolean) {
        _slideFrozen.value = frozen
    }

    override fun setSelectedSlide(slide: ImageBitmap?) {
        _selectedSlide.value = slide
    }

    override fun setLiveSlide(fileName: String?, index: Int) {
        _liveSlide.value = LiveSlide(fileName, index)
        if (context.presentingMode.value == Presenting.PRESENTATION) context.notify(Presenting.PRESENTATION)
    }

    override fun setDisplayedSlide(slide: ImageBitmap?) {
        _displayedSlide.value = slide
    }

    override fun setNextSlide(slide: ImageBitmap?) {
        _nextSlide.value = slide
    }

    override fun setPresenterNotes(notes: String) {
        _presenterNotes.value = notes
    }

    override fun setSlideTransitionAlpha(alpha: Float) {
        _slideTransitionAlpha.value = alpha
    }

    override fun setPreviousDisplayedSlide(slide: ImageBitmap?) {
        _previousDisplayedSlide.value = slide
    }

    override fun setSlideSlideOffset(offset: Float) {
        _slideSlideOffset.value = offset
    }

    /** Forgets which slide is live, when the outputs leave PRESENTATION. */
    fun clearLiveSlide() {
        _liveSlide.value = null
    }
}
