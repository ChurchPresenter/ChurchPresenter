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
}
