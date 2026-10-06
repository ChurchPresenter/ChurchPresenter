package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.sharedui.models.Presenting

/** The picture chosen, the one the outputs draw, and how they move between them. Part of [PresenterManager]. */
interface LivePictures {
    val selectedImagePath: State<String?>

    /** What the outputs draw. */
    val displayedImagePath: State<String?>
    val nextImagePath: State<String?>
    val pictureTransitionAlpha: State<Float>
    val previousDisplayedImagePath: State<String?>
    val pictureSlideOffset: State<Float>

    /** The transition pictures and slides change with. */
    val animationType: State<AnimationType>
    val transitionDuration: State<Int>

    fun setSelectedImagePath(imagePath: String?)
    fun setDisplayedImagePath(path: String?)
    fun setNextImagePath(path: String?)
    fun setPictureTransitionAlpha(alpha: Float)
    fun setPreviousDisplayedImagePath(path: String?)
    fun setPictureSlideOffset(offset: Float)
    fun setAnimationType(type: AnimationType)
    fun setTransitionDuration(duration: Int)
}

internal class LivePicturesState(private val context: PresenterContext) : LivePictures {

    private val _selectedImagePath = mutableStateOf<String?>(null)
    override val selectedImagePath: State<String?> = _selectedImagePath

    private val _displayedImagePath = mutableStateOf<String?>(null)
    override val displayedImagePath: State<String?> = _displayedImagePath

    private val _nextImagePath = mutableStateOf<String?>(null)
    override val nextImagePath: State<String?> = _nextImagePath

    private val _pictureTransitionAlpha = mutableStateOf(1f)
    override val pictureTransitionAlpha: State<Float> = _pictureTransitionAlpha

    private val _previousDisplayedImagePath = mutableStateOf<String?>(null)
    override val previousDisplayedImagePath: State<String?> = _previousDisplayedImagePath

    private val _pictureSlideOffset = mutableStateOf(1f)
    override val pictureSlideOffset: State<Float> = _pictureSlideOffset

    private val _animationType = mutableStateOf(AnimationType.CROSSFADE)
    override val animationType: State<AnimationType> = _animationType

    private val _transitionDuration = mutableStateOf(DEFAULT_TRANSITION_MS)
    override val transitionDuration: State<Int> = _transitionDuration

    override fun setSelectedImagePath(imagePath: String?) {
        _selectedImagePath.value = imagePath
        context.notify(Presenting.PICTURES)
    }

    override fun setDisplayedImagePath(path: String?) {
        _displayedImagePath.value = path
    }

    override fun setNextImagePath(path: String?) {
        _nextImagePath.value = path
    }

    override fun setPictureTransitionAlpha(alpha: Float) {
        _pictureTransitionAlpha.value = alpha
    }

    override fun setPreviousDisplayedImagePath(path: String?) {
        _previousDisplayedImagePath.value = path
    }

    override fun setPictureSlideOffset(offset: Float) {
        _pictureSlideOffset.value = offset
    }

    override fun setAnimationType(type: AnimationType) {
        _animationType.value = type
    }

    override fun setTransitionDuration(duration: Int) {
        _transitionDuration.value = duration
    }
}

private const val DEFAULT_TRANSITION_MS = 500
