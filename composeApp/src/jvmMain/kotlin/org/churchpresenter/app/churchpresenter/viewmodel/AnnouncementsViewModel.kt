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

    private val _shadowColor = mutableStateOf("#000000")
    private val _shadowSize = mutableStateOf(100)
    private val _shadowOpacity = mutableStateOf(78)

    private val _horizontalAlignment = mutableStateOf(Constants.CENTER)
    val horizontalAlignment: String get() = _horizontalAlignment.value

    private val _position = mutableStateOf(Constants.CENTER)
    val position: String get() = _position.value

    private val _animationType = mutableStateOf(Constants.ANIMATION_SLIDE_FROM_BOTTOM)
    val animationType: String get() = _animationType.value

    private val _animationDuration = mutableStateOf(500)
    val animationDuration: Int get() = _animationDuration.value

    private val _loopCount = mutableStateOf(0)
    val loopCount: Int get() = _loopCount.value

    // ── Timer state ──────────────────────────────────────────────────
    private val _timerHours = mutableStateOf(0)
    val timerHours: Int get() = _timerHours.value

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

    /** Whether the timer counts a duration or counts down to a specific clock time */
    private val _timerMode = mutableStateOf(Constants.TIMER_MODE_DURATION)
    val timerMode: String get() = _timerMode.value

    /** Target clock time fields (used when timerMode == TIMER_MODE_CLOCK) */
    private val _targetHour = mutableStateOf(0)
    val targetHour: Int get() = _targetHour.value

    private val _targetMinute = mutableStateOf(0)
    val targetMinute: Int get() = _targetMinute.value

    private val _targetSecond = mutableStateOf(0)
    val targetSecond: Int get() = _targetSecond.value

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null
    /** Ticks every second in clock mode while the timer is not running, keeping the display current. */
    private var clockPreviewJob: Job? = null

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
        _shadowColor.value = settings.shadowColor
        _shadowSize.value = settings.shadowSize
        _shadowOpacity.value = settings.shadowOpacity
        _horizontalAlignment.value = settings.horizontalAlignment
        _position.value = settings.position
        _animationType.value = settings.animationType
        _animationDuration.value = settings.animationDuration
        _loopCount.value = settings.loopCount
        _timerHours.value = settings.timerHours
        _timerMinutes.value = settings.timerMinutes
        _timerSeconds.value = settings.timerSeconds
        _timerTextColor.value = settings.timerTextColor
        _timerExpiredText.value = settings.timerExpiredText
        _timerMode.value = settings.timerMode
        _targetHour.value = settings.targetHour
        _targetMinute.value = settings.targetMinute
        _targetSecond.value = settings.targetSecond
        if (!_timerRunning.value) {
            _timerRemaining.value = if (settings.timerMode == Constants.TIMER_MODE_CLOCK) secondsUntilTarget() else totalSeconds()
            if (settings.timerMode == Constants.TIMER_MODE_CLOCK) startClockPreview()
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
    fun setLoopCount(value: Int) { _loopCount.value = value.coerceAtLeast(0) }

    private fun totalSeconds(): Int =
        _timerHours.value * 3600 + _timerMinutes.value * 60 + _timerSeconds.value

    fun setTimerHours(value: Int) {
        _timerHours.value = value.coerceAtLeast(0)
        if (!_timerRunning.value) {
            _timerRemaining.value = totalSeconds()
        }
    }

    fun stepTimerHours(delta: Int) {
        setTimerHours((_timerHours.value + delta).coerceAtLeast(0))
    }

    fun setTimerMinutes(value: Int) {
        _timerMinutes.value = value.coerceIn(0, 59)
        if (!_timerRunning.value) {
            _timerRemaining.value = totalSeconds()
        }
    }

    fun setTimerSeconds(value: Int) {
        _timerSeconds.value = value.coerceIn(0, 59)
        if (!_timerRunning.value) {
            _timerRemaining.value = totalSeconds()
        }
    }

    fun stepTimerMinutes(delta: Int) {
        val cur = _timerMinutes.value
        if (delta > 0) {
            if (cur >= 59) {
                _timerMinutes.value = 0
                setTimerHours(_timerHours.value + 1)
            } else {
                setTimerMinutes(cur + 1)
            }
        } else {
            if (cur <= 0) {
                if (_timerHours.value > 0) {
                    _timerMinutes.value = 59
                    setTimerHours(_timerHours.value - 1)
                }
            } else {
                setTimerMinutes(cur - 1)
            }
        }
    }

    fun stepTimerSeconds(delta: Int) {
        val cur = _timerSeconds.value
        if (delta > 0) {
            val next = if (cur >= 55) 0 else ((cur / 5) + 1) * 5
            if (next == 0) {
                _timerSeconds.value = 0
                stepTimerMinutes(1)
            } else {
                setTimerSeconds(next)
            }
        } else {
            val next = if (cur <= 0) 55 else ((cur - 1) / 5) * 5
            if (cur <= 0) {
                if (_timerHours.value > 0 || _timerMinutes.value > 0) {
                    _timerSeconds.value = 55
                    stepTimerMinutes(-1)
                }
            } else {
                setTimerSeconds(next)
            }
        }
    }

    fun setTimerExpiredText(value: String) { _timerExpiredText.value = value }
    fun setTimerTextColor(value: String) { _timerTextColor.value = value }

    private fun startClockPreview() {
        clockPreviewJob?.cancel()
        clockPreviewJob = scope.launch {
            while (true) {
                delay(1000L)
                if (!_timerRunning.value && _timerMode.value == Constants.TIMER_MODE_CLOCK) {
                    _timerRemaining.value = secondsUntilTarget()
                }
            }
        }
    }

    private fun stopClockPreview() {
        clockPreviewJob?.cancel()
        clockPreviewJob = null
    }

    fun setTimerMode(value: String) {
        _timerMode.value = value
        if (!_timerRunning.value) {
            _timerRemaining.value = if (value == Constants.TIMER_MODE_CLOCK) secondsUntilTarget() else totalSeconds()
        }
        if (value == Constants.TIMER_MODE_CLOCK) startClockPreview() else stopClockPreview()
    }

    private fun secondsUntilTarget(): Int {
        val now = java.time.LocalTime.now()
        val nowSec = now.toSecondOfDay()
        val targetSec = _targetHour.value * 3600 + _targetMinute.value * 60 + _targetSecond.value
        val diff = targetSec - nowSec
        return if (diff > 0) diff else diff + 86400
    }

    fun setTargetHour(value: Int) {
        _targetHour.value = value.coerceIn(0, 23)
        if (!_timerRunning.value && _timerMode.value == Constants.TIMER_MODE_CLOCK) {
            _timerRemaining.value = secondsUntilTarget()
        }
    }

    fun stepTargetHour(delta: Int) { setTargetHour(_targetHour.value + delta) }

    fun setTargetMinute(value: Int) {
        _targetMinute.value = value.coerceIn(0, 59)
        if (!_timerRunning.value && _timerMode.value == Constants.TIMER_MODE_CLOCK) {
            _timerRemaining.value = secondsUntilTarget()
        }
    }

    fun stepTargetMinute(delta: Int) {
        val cur = _targetMinute.value
        if (delta > 0) {
            if (cur >= 59) { _targetMinute.value = 0; setTargetHour(_targetHour.value + 1) }
            else setTargetMinute(cur + 1)
        } else {
            if (cur <= 0) { _targetMinute.value = 59; setTargetHour(_targetHour.value - 1) }
            else setTargetMinute(cur - 1)
        }
    }

    fun setTargetSecond(value: Int) {
        _targetSecond.value = value.coerceIn(0, 59)
        if (!_timerRunning.value && _timerMode.value == Constants.TIMER_MODE_CLOCK) {
            _timerRemaining.value = secondsUntilTarget()
        }
    }

    fun stepTargetSecond(delta: Int) {
        val cur = _targetSecond.value
        if (delta > 0) {
            val next = if (cur >= 55) 0 else ((cur / 5) + 1) * 5
            if (next == 0) { _targetSecond.value = 0; stepTargetMinute(1) }
            else setTargetSecond(next)
        } else {
            val next = if (cur <= 0) 55 else ((cur - 1) / 5) * 5
            if (cur <= 0) { _targetSecond.value = 55; stepTargetMinute(-1) }
            else setTargetSecond(next)
        }
    }

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
            if (_timerMode.value == Constants.TIMER_MODE_CLOCK) startClockPreview()
        } else {
            // Start / resume
            stopClockPreview()
            if (_timerRemaining.value <= 0) {
                val total = if (_timerMode.value == Constants.TIMER_MODE_CLOCK) secondsUntilTarget() else totalSeconds()
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
                if (_timerMode.value == Constants.TIMER_MODE_CLOCK) startClockPreview()
                onExpired(_timerExpiredText.value)
            }
        }
    }

    fun pauseTimer() {
        if (_timerRunning.value) {
            timerJob?.cancel()
            timerJob = null
            _timerRunning.value = false
            if (_timerMode.value == Constants.TIMER_MODE_CLOCK) startClockPreview()
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerRunning.value = false
        _timerExpired.value = false
        _timerRemaining.value = if (_timerMode.value == Constants.TIMER_MODE_CLOCK) secondsUntilTarget() else totalSeconds()
        if (_timerMode.value == Constants.TIMER_MODE_CLOCK) startClockPreview()
    }

    fun dispose() {
        stopClockPreview()
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
        shadowColor = _shadowColor.value,
        shadowSize = _shadowSize.value,
        shadowOpacity = _shadowOpacity.value,
        horizontalAlignment = _horizontalAlignment.value,
        position = _position.value,
        animationType = _animationType.value,
        animationDuration = _animationDuration.value,
        loopCount = _loopCount.value,
        timerHours = _timerHours.value,
        timerMinutes = _timerMinutes.value,
        timerSeconds = _timerSeconds.value,
        timerTextColor = _timerTextColor.value,
        timerExpiredText = _timerExpiredText.value,
        timerMode = _timerMode.value,
        targetHour = _targetHour.value,
        targetMinute = _targetMinute.value,
        targetSecond = _targetSecond.value
    )

    companion object {
        fun formatTimer(remaining: Int): String {
            val h = remaining / 3600
            val m = (remaining % 3600) / 60
            val s = remaining % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s)
            else "%02d:%02d".format(m, s)
        }
    }

    // ── Go Live ──────────────────────────────────────────────────────
    fun goLive(presenterManager: PresenterManager, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
        saveToSettings(onSettingsChange)
        presenterManager.setAnnouncementText(_text.value)
        presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
    }
}
