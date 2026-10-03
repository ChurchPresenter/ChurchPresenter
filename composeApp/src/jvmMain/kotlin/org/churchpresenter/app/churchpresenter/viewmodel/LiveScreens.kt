package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.sharedui.models.Presenting

/**
 * The rest of what an output can show, each a few fields of its own: the Canvas scene, the Q&A
 * question or its QR code, the Strong's entry, and the media playing. Part of [PresenterManager].
 */
interface LiveScreens {
    val activeScene: State<Scene?>
    val displayedQuestion: State<Question?>
    val qaTransitionAlpha: State<Float>
    val showQRCodeOnDisplay: State<Boolean>
    val displayedDictionaryEntry: State<StrongsEntry?>
    val mediaTransitionAlpha: State<Float>

    /**
     * The media playing -- not driven by the media view model's own state, so it is tracked here
     * purely to report what is live (to an Instance Link follower, say).
     */
    val currentMediaUrl: State<String>
    val currentMediaType: State<String>

    fun setActiveScene(scene: Scene?)
    fun setDisplayedQuestion(question: Question?)
    fun setQaTransitionAlpha(alpha: Float)
    fun setShowQRCodeOnDisplay(show: Boolean)
    fun setDisplayedDictionaryEntry(entry: StrongsEntry?)
    fun setMediaTransitionAlpha(alpha: Float)
    fun setCurrentMedia(url: String, type: String)
}

internal class LiveScreensState(private val context: PresenterContext) : LiveScreens {

    private val _activeScene = mutableStateOf<Scene?>(null)
    override val activeScene: State<Scene?> = _activeScene

    private val _displayedQuestion = mutableStateOf<Question?>(null)
    override val displayedQuestion: State<Question?> = _displayedQuestion

    private val _qaTransitionAlpha = mutableStateOf(1f)
    override val qaTransitionAlpha: State<Float> = _qaTransitionAlpha

    private val _showQRCodeOnDisplay = mutableStateOf(false)
    override val showQRCodeOnDisplay: State<Boolean> = _showQRCodeOnDisplay

    private val _displayedDictionaryEntry = mutableStateOf<StrongsEntry?>(null)
    override val displayedDictionaryEntry: State<StrongsEntry?> = _displayedDictionaryEntry

    private val _mediaTransitionAlpha = mutableStateOf(1f)
    override val mediaTransitionAlpha: State<Float> = _mediaTransitionAlpha

    private val _currentMediaUrl = mutableStateOf("")
    override val currentMediaUrl: State<String> = _currentMediaUrl

    private val _currentMediaType = mutableStateOf("")
    override val currentMediaType: State<String> = _currentMediaType

    override fun setActiveScene(scene: Scene?) {
        _activeScene.value = scene
        context.notify(Presenting.CANVAS)
    }

    override fun setDisplayedQuestion(question: Question?) {
        _displayedQuestion.value = question
        context.notify(Presenting.QA)
    }

    override fun setQaTransitionAlpha(alpha: Float) {
        _qaTransitionAlpha.value = alpha
    }

    override fun setShowQRCodeOnDisplay(show: Boolean) {
        _showQRCodeOnDisplay.value = show
    }

    override fun setDisplayedDictionaryEntry(entry: StrongsEntry?) {
        if (entry != null && entry != _displayedDictionaryEntry.value) UsageEvents.record(UsageEvent.STRONGS_SHOWN)
        _displayedDictionaryEntry.value = entry
        context.notify(Presenting.DICTIONARY)
    }

    override fun setMediaTransitionAlpha(alpha: Float) {
        _mediaTransitionAlpha.value = alpha
    }

    override fun setCurrentMedia(url: String, type: String) {
        _currentMediaUrl.value = url
        _currentMediaType.value = type
        context.notify(Presenting.MEDIA)
    }
}
