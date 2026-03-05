package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.models.SelectedVerse

class PresenterManager {
    private val _presentingMode = mutableStateOf(Presenting.NONE)
    val presentingMode: State<Presenting> = _presentingMode

    private val _selectedVerse = mutableStateOf(SelectedVerse())
    val selectedVerse: State<SelectedVerse> = _selectedVerse

    private val _selectedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    val selectedVerses: State<List<SelectedVerse>> = _selectedVerses

    private val _lyricSection = mutableStateOf(LyricSection())
    val lyricSection: State<LyricSection> = _lyricSection

    // Monotonic counter incremented on every setLyricSection call.
    // Ensures Compose always sees a state change even when the LyricSection
    // content is structurally identical (same song/section clicked twice).
    private val _lyricSectionVersion = mutableStateOf(0)
    val lyricSectionVersion: State<Int> = _lyricSectionVersion

    private val _selectedImagePath = mutableStateOf<String?>(null)
    val selectedImagePath: State<String?> = _selectedImagePath

    private val _animationType = mutableStateOf(AnimationType.CROSSFADE)
    val animationType: State<AnimationType> = _animationType

    private val _transitionDuration = mutableStateOf(500)
    val transitionDuration: State<Int> = _transitionDuration

    private val _showPresenterWindow = mutableStateOf(true)
    val showPresenterWindow: State<Boolean> = _showPresenterWindow

    private val _selectedSlide = mutableStateOf<ImageBitmap?>(null)
    val selectedSlide: State<ImageBitmap?> = _selectedSlide

    fun setPresentingMode(mode: Presenting) {
        _presentingMode.value = mode
    }

    fun setSelectedVerse(verse: SelectedVerse) {
        _selectedVerse.value = verse
    }

    fun setSelectedVerses(verses: List<SelectedVerse>) {
        _selectedVerses.value = verses
        if (verses.isNotEmpty()) {
            _selectedVerse.value = verses.first()
        }
    }

    fun setLyricSection(section: LyricSection) {
        _lyricSection.value = section
        _lyricSectionVersion.value++
    }

    fun setSelectedImagePath(imagePath: String?) {
        _selectedImagePath.value = imagePath
    }

    fun setAnimationType(type: AnimationType) {
        _animationType.value = type
    }

    fun setTransitionDuration(duration: Int) {
        _transitionDuration.value = duration
    }

    fun togglePresenterWindow() {
        _showPresenterWindow.value = !_showPresenterWindow.value
    }

    fun setShowPresenterWindow(show: Boolean) {
        _showPresenterWindow.value = show
    }

    fun setSelectedSlide(slide: ImageBitmap?) {
        _selectedSlide.value = slide
    }

    private val _lottieJsonContent = mutableStateOf("")
    val lottieJsonContent: State<String> = _lottieJsonContent

    private val _lottiePauseAtFrame = mutableStateOf(false)
    val lottiePauseAtFrame: State<Boolean> = _lottiePauseAtFrame

    private val _lottiePauseDurationMs = mutableStateOf(2000L)
    val lottiePauseDurationMs: State<Long> = _lottiePauseDurationMs

    private val _lottieTrigger = mutableStateOf(0)
    val lottieTrigger: State<Int> = _lottieTrigger

    fun setLottieContent(json: String, pauseAtFrame: Boolean, pauseFrame: Float, pauseDurationMs: Long) {
        _lottieJsonContent.value = json
        _lottiePauseAtFrame.value = pauseAtFrame
        _lottiePauseFrame.value = pauseFrame
        _lottiePauseDurationMs.value = pauseDurationMs
        _lottieTrigger.value++
    }

    private val _lottiePauseFrame = mutableStateOf(-1f)
    val lottiePauseFrame: State<Float> = _lottiePauseFrame

    private val _announcementText = mutableStateOf("")
    val announcementText: State<String> = _announcementText

    fun setAnnouncementText(text: String) {
        _announcementText.value = text
    }

    private val _websiteUrl = mutableStateOf("")
    val websiteUrl: State<String> = _websiteUrl

    fun setWebsiteUrl(url: String) {
        _websiteUrl.value = url
    }

    private val _webPageTitle = mutableStateOf("")
    val webPageTitle: State<String> = _webPageTitle

    fun setWebPageTitle(title: String) {
        _webPageTitle.value = title
    }

    // Periodically-updated screenshot of the live WebView — used by LivePreviewPanel
    // to mirror the exact visible content without needing a second WebView instance.
    private val _webSnapshot = mutableStateOf<ImageBitmap?>(null)
    val webSnapshot: State<ImageBitmap?> = _webSnapshot

    fun setWebSnapshot(bitmap: ImageBitmap?) {
        _webSnapshot.value = bitmap
    }
}
