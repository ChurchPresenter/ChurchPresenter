package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.app.churchpresenter.data.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants

class AnnouncementsViewModel {

    // ── Live text being edited ───────────────────────────────────────
    private val _text = mutableStateOf("")
    val text: String get() = _text.value

    // ── Settings mirror (loaded from AppSettings) ────────────────────
    private val _textColor = mutableStateOf("#FFFFFF")
    val textColor: String get() = _textColor.value

    private val _backgroundColor = mutableStateOf("#000000")
    val backgroundColor: String get() = _backgroundColor.value

    private val _fontSize = mutableStateOf(48)
    val fontSize: Int get() = _fontSize.value

    private val _fontType = mutableStateOf("Arial")
    val fontType: String get() = _fontType.value

    private val _bold = mutableStateOf(false)
    val bold: Boolean get() = _bold.value

    private val _italic = mutableStateOf(false)
    val italic: Boolean get() = _italic.value

    private val _underline = mutableStateOf(false)
    val underline: Boolean get() = _underline.value

    private val _shadow = mutableStateOf(false)
    val shadow: Boolean get() = _shadow.value

    private val _horizontalAlignment = mutableStateOf(Constants.CENTER)
    val horizontalAlignment: String get() = _horizontalAlignment.value

    private val _position = mutableStateOf(Constants.CENTER)
    val position: String get() = _position.value

    private val _animationType = mutableStateOf(Constants.ANIMATION_SLIDE_FROM_BOTTOM)
    val animationType: String get() = _animationType.value

    private val _animationDuration = mutableStateOf(500)
    val animationDuration: Int get() = _animationDuration.value

    // ── Sync from settings ───────────────────────────────────────────
    fun syncFromSettings(settings: AnnouncementsSettings) {
        _text.value = settings.text
        _textColor.value = settings.textColor
        _backgroundColor.value = settings.backgroundColor
        _fontSize.value = settings.fontSize
        _fontType.value = settings.fontType
        _bold.value = settings.bold
        _italic.value = settings.italic
        _underline.value = settings.underline
        _shadow.value = settings.shadow
        _horizontalAlignment.value = settings.horizontalAlignment
        _position.value = settings.position
        _animationType.value = settings.animationType
        _animationDuration.value = settings.animationDuration
    }

    // ── Mutations ────────────────────────────────────────────────────
    fun setText(value: String) { _text.value = value }
    fun setTextColor(value: String) { _textColor.value = value }
    fun setBackgroundColor(value: String) { _backgroundColor.value = value }
    fun setFontSize(value: Int) { _fontSize.value = value }
    fun setFontType(value: String) { _fontType.value = value }
    fun setBold(value: Boolean) { _bold.value = value }
    fun setItalic(value: Boolean) { _italic.value = value }
    fun setUnderline(value: Boolean) { _underline.value = value }
    fun setShadow(value: Boolean) { _shadow.value = value }
    fun setHorizontalAlignment(value: String) { _horizontalAlignment.value = value }
    fun setPosition(value: String) { _position.value = value }
    fun setAnimationType(value: String) { _animationType.value = value }
    fun setAnimationDuration(value: Int) { _animationDuration.value = value }

    // ── Persist to AppSettings ───────────────────────────────────────
    fun saveToSettings(onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
        val snap = buildSettings()
        onSettingsChange { s -> s.copy(announcementsSettings = snap) }
    }

    fun buildSettings(): AnnouncementsSettings = AnnouncementsSettings(
        text = _text.value,
        textColor = _textColor.value,
        backgroundColor = _backgroundColor.value,
        fontSize = _fontSize.value,
        fontType = _fontType.value,
        bold = _bold.value,
        italic = _italic.value,
        underline = _underline.value,
        shadow = _shadow.value,
        horizontalAlignment = _horizontalAlignment.value,
        position = _position.value,
        animationType = _animationType.value,
        animationDuration = _animationDuration.value
    )

    // ── Go Live ──────────────────────────────────────────────────────
    fun goLive(presenterManager: PresenterManager) {
        presenterManager.setAnnouncementText(_text.value)
        presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
    }
}

