package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.cef.browser.CefBrowser
import org.churchpresenter.app.churchpresenter.models.Scene
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.models.SelectedVerse

class PresenterManager {
    private val _presentingMode = mutableStateOf(Presenting.NONE)
    val presentingMode: State<Presenting> = _presentingMode

    private val _selectedVerse = mutableStateOf(SelectedVerse())
    val selectedVerse: State<SelectedVerse> = _selectedVerse

    private val _selectedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    val selectedVerses: State<List<SelectedVerse>> = _selectedVerses

    // Shared Bible transition state — driven by a single animation so all windows stay in sync
    private val _displayedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    val displayedVerses: State<List<SelectedVerse>> = _displayedVerses

    private val _bibleTransitionAlpha = mutableStateOf(1f)
    val bibleTransitionAlpha: State<Float> = _bibleTransitionAlpha

    // Hold mode — when active, verse selection changes are staged but not sent to display
    private val _bibleHold = mutableStateOf(false)
    val bibleHold: State<Boolean> = _bibleHold

    private val _lyricSection = mutableStateOf(LyricSection())
    val lyricSection: State<LyricSection> = _lyricSection

    // Monotonic counter incremented on every setLyricSection call.
    // Ensures Compose always sees a state change even when the LyricSection
    // content is structurally identical (same song/section clicked twice).
    private val _lyricSectionVersion = mutableStateOf(0)
    val lyricSectionVersion: State<Int> = _lyricSectionVersion

    // Shared Song transition state
    private val _displayedLyricSection = mutableStateOf(LyricSection())
    val displayedLyricSection: State<LyricSection> = _displayedLyricSection

    private val _songTransitionAlpha = mutableStateOf(1f)
    val songTransitionAlpha: State<Float> = _songTransitionAlpha

    private val _songDisplayLineIndex = mutableStateOf(-1)
    val songDisplayLineIndex: State<Int> = _songDisplayLineIndex

    private val _songDisplaySectionIndex = mutableStateOf(-1)
    val songDisplaySectionIndex: State<Int> = _songDisplaySectionIndex

    // All sections of the currently selected song — used for auto-fit font sizing
    private val _allLyricSections = mutableStateOf<List<LyricSection>>(emptyList())
    val allLyricSections: State<List<LyricSection>> = _allLyricSections

    private val _selectedImagePath = mutableStateOf<String?>(null)
    val selectedImagePath: State<String?> = _selectedImagePath

    // Shared Picture transition state
    private val _displayedImagePath = mutableStateOf<String?>(null)
    val displayedImagePath: State<String?> = _displayedImagePath

    private val _pictureTransitionAlpha = mutableStateOf(1f)
    val pictureTransitionAlpha: State<Float> = _pictureTransitionAlpha

    private val _animationType = mutableStateOf(AnimationType.CROSSFADE)
    val animationType: State<AnimationType> = _animationType

    private val _transitionDuration = mutableStateOf(500)
    val transitionDuration: State<Int> = _transitionDuration

    private val _showPresenterWindow = mutableStateOf(true)
    val showPresenterWindow: State<Boolean> = _showPresenterWindow

    private val _selectedSlide = mutableStateOf<ImageBitmap?>(null)
    val selectedSlide: State<ImageBitmap?> = _selectedSlide

    // Shared Slide transition state
    private val _displayedSlide = mutableStateOf<ImageBitmap?>(null)
    val displayedSlide: State<ImageBitmap?> = _displayedSlide

    private val _slideTransitionAlpha = mutableStateOf(1f)
    val slideTransitionAlpha: State<Float> = _slideTransitionAlpha

    // Shared Announcements transition state
    private val _displayedAnnouncementText = mutableStateOf("")
    val displayedAnnouncementText: State<String> = _displayedAnnouncementText

    private val _announcementTransitionAlpha = mutableStateOf(1f)
    val announcementTransitionAlpha: State<Float> = _announcementTransitionAlpha

    // Shared Lottie (lower third) animation progress — driven centrally
    private val _lottieProgress = mutableStateOf(0f)
    val lottieProgress: State<Float> = _lottieProgress

    // Shared Media transition alpha
    private val _mediaTransitionAlpha = mutableStateOf(1f)
    val mediaTransitionAlpha: State<Float> = _mediaTransitionAlpha

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

    fun setDisplayedVerses(verses: List<SelectedVerse>) {
        _displayedVerses.value = verses
    }

    fun setBibleTransitionAlpha(alpha: Float) {
        _bibleTransitionAlpha.value = alpha
    }

    fun setBibleHold(hold: Boolean) {
        _bibleHold.value = hold
    }

    fun setLyricSection(section: LyricSection) {
        _lyricSection.value = section
        _lyricSectionVersion.value++
    }

    fun setDisplayedLyricSection(section: LyricSection) {
        _displayedLyricSection.value = section
    }

    fun setSongTransitionAlpha(alpha: Float) {
        _songTransitionAlpha.value = alpha
    }

    fun setSongDisplayLineIndex(index: Int) {
        _songDisplayLineIndex.value = index
    }

    fun setSongDisplaySectionIndex(index: Int) {
        _songDisplaySectionIndex.value = index
    }

    fun setAllLyricSections(sections: List<LyricSection>) {
        _allLyricSections.value = sections
    }

    fun setSelectedImagePath(imagePath: String?) {
        _selectedImagePath.value = imagePath
    }

    fun setDisplayedImagePath(path: String?) {
        _displayedImagePath.value = path
    }

    fun setPictureTransitionAlpha(alpha: Float) {
        _pictureTransitionAlpha.value = alpha
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

    fun setDisplayedSlide(slide: ImageBitmap?) {
        _displayedSlide.value = slide
    }

    fun setSlideTransitionAlpha(alpha: Float) {
        _slideTransitionAlpha.value = alpha
    }

    fun setDisplayedAnnouncementText(text: String) {
        _displayedAnnouncementText.value = text
    }

    fun setAnnouncementTransitionAlpha(alpha: Float) {
        _announcementTransitionAlpha.value = alpha
    }

    fun setLottieProgress(progress: Float) {
        _lottieProgress.value = progress
    }

    fun setMediaTransitionAlpha(alpha: Float) {
        _mediaTransitionAlpha.value = alpha
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

    // Reference to the presenter's live CefBrowser — used by WebTab to forward input events
    private val _liveBrowser = mutableStateOf<CefBrowser?>(null)
    val liveBrowser: State<CefBrowser?> = _liveBrowser

    fun setLiveBrowser(browser: CefBrowser?) {
        _liveBrowser.value = browser
    }

    // Scene compositor state
    private val _activeScene = mutableStateOf<Scene?>(null)
    val activeScene: State<Scene?> = _activeScene

    fun setActiveScene(scene: Scene?) {
        _activeScene.value = scene
    }
}
