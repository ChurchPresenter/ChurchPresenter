package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.*
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting

class PresenterManager {
    private val _presentingMode = mutableStateOf(Presenting.NONE)
    val presentingMode: State<Presenting> = _presentingMode

    private val _selectedVerse = mutableStateOf(SelectedVerse())
    val selectedVerse: State<SelectedVerse> = _selectedVerse

    private val _selectedVerses = mutableStateOf<List<SelectedVerse>>(emptyList())
    val selectedVerses: State<List<SelectedVerse>> = _selectedVerses

    private val _lyricSection = mutableStateOf(LyricSection())
    val lyricSection: State<LyricSection> = _lyricSection

    private val _showPresenterWindow = mutableStateOf(true)
    val showPresenterWindow: State<Boolean> = _showPresenterWindow

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

    fun togglePresenterWindow() {
        _showPresenterWindow.value = !_showPresenterWindow.value
    }

    fun setShowPresenterWindow(show: Boolean) {
        _showPresenterWindow.value = show
    }
}
