package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    // ── Timer state ──────────────────────────────────────────────────
    private val _timerMinutes = mutableStateOf(0)
    val timerMinutes: Int get() = _timerMinutes.value

    private val _timerSeconds = mutableStateOf(0)
    val timerSeconds: Int get() = _timerSeconds.value

    /** Remaining time in whole seconds while the timer is running */
    private val _timerRemaining = mutableStateOf(0)
    val timerRemaining: Int get() = _timerRemaining.value

    private val _timerRunning = mutableStateOf(false)
    val timerRunning: Boolean get() = _timerRunning.value

    /** true once the countdown has reached 0 */
    private val _timerExpired = mutableStateOf(false)
    val timerExpired: Boolean get() = _timerExpired.value

    /** Message to show on screen when the timer expires */
    private val _timerExpiredText = mutableStateOf("")
    val timerExpiredText: String get() = _timerExpiredText.value

    /** Color of the countdown display text */
    private val _timerTextColor = mutableStateOf("#FFFFFF")
    val timerTextColor: String get() = _timerTextColor.value

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null

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
        _timerMinutes.value = settings.timerMinutes
        _timerSeconds.value = settings.timerSeconds
        _timerTextColor.value = settings.timerTextColor
        _timerExpiredText.value = settings.timerExpiredText
        // Reset remaining to match restored minutes/seconds (only if not running)
        if (!_timerRunning.value) {
            _timerRemaining.value = settings.timerMinutes * 60 + settings.timerSeconds
        }
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

    fun setTimerMinutes(value: Int) {
        _timerMinutes.value = value.coerceAtLeast(0)
        // Only reset remaining if timer is not running
        if (!_timerRunning.value) {
            _timerRemaining.value = _timerMinutes.value * 60 + _timerSeconds.value
        }
    }

    fun setTimerSeconds(value: Int) {
        // Accept 0-59 for direct digit entry
        _timerSeconds.value = value.coerceIn(0, 59)
        if (!_timerRunning.value) {
            _timerRemaining.value = _timerMinutes.value * 60 + _timerSeconds.value
        }
    }

    fun stepTimerMinutes(delta: Int) {
        setTimerMinutes((_timerMinutes.value + delta).coerceAtLeast(0))
    }

    fun stepTimerSeconds(delta: Int) {
        // Snap to 15-sec increments: 0 → 15 → 30 → 45 → 0
        val cur = _timerSeconds.value
        val next = if (delta > 0) {
            if (cur >= 45) 0 else ((cur / 15) + 1) * 15
        } else {
            if (cur <= 0) 45 else ((cur - 1) / 15) * 15
        }
        setTimerSeconds(next)
    }

    fun setTimerExpiredText(value: String) { _timerExpiredText.value = value }
    fun setTimerTextColor(value: String) { _timerTextColor.value = value }

    // ── Timer control ────────────────────────────────────────────────
    fun startPauseTimer(
        onTick: ((remaining: Int) -> Unit)? = null,
        onExpired: (String) -> Unit
    ) {
        if (_timerExpired.value) {
            resetTimer()
            return
        }
        if (_timerRunning.value) {
            // Pause
            timerJob?.cancel()
            timerJob = null
            _timerRunning.value = false
        } else {
            // Start / resume
            if (_timerRemaining.value <= 0) {
                val total = _timerMinutes.value * 60 + _timerSeconds.value
                if (total <= 0) return
                _timerRemaining.value = total
            }
            _timerRunning.value = true
            _timerExpired.value = false
            timerJob = scope.launch {
                while (_timerRemaining.value > 0) {
                    delay(1000L)
                    _timerRemaining.value--
                    onTick?.invoke(_timerRemaining.value)
                }
                _timerRunning.value = false
                _timerExpired.value = true
                onExpired(_timerExpiredText.value)
            }
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerRunning.value = false
        _timerExpired.value = false
        _timerRemaining.value = _timerMinutes.value * 60 + _timerSeconds.value
    }

    fun dispose() {
        scope.cancel()
    }

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
        animationDuration = _animationDuration.value,
        timerMinutes = _timerMinutes.value,
        timerSeconds = _timerSeconds.value,
        timerTextColor = _timerTextColor.value,
        timerExpiredText = _timerExpiredText.value
    )

    // ── Go Live ──────────────────────────────────────────────────────
    fun goLive(presenterManager: PresenterManager, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
        saveToSettings(onSettingsChange)
        presenterManager.setAnnouncementText(_text.value)
        presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
    }
}
