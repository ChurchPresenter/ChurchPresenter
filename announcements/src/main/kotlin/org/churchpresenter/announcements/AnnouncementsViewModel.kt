package org.churchpresenter.announcements

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting

/**
 * The announcement being edited — its text, its look and its timer — loaded from and saved back to
 * [AnnouncementsSettings].
 *
 * The timer's work is split across parts sharing one [AnnouncementsState]: the configured duration
 * ([AnnouncementsDuration]), the Specific Time target ([AnnouncementsTarget]), the mode and its
 * clock previews ([AnnouncementsClock]) and starting it on the output
 * ([AnnouncementsTimerControl]). Their members are this class's own, by delegation.
 *
 * Stable: every property reads snapshot state in [AnnouncementsState].
 */
@Stable
class AnnouncementsViewModel private constructor(
    private val state: AnnouncementsState,
    private val scope: CoroutineScope,
    private val clock: AnnouncementsClock,
) : AnnouncementsDurationActions by AnnouncementsDuration(state),
    AnnouncementsTargetActions by AnnouncementsTarget(state),
    AnnouncementsClockActions by clock,
    AnnouncementsTimerActions by AnnouncementsTimerControl(state) {

    constructor() : this(AnnouncementsState(), CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private constructor(state: AnnouncementsState, scope: CoroutineScope) :
        this(state, scope, AnnouncementsClock(state, scope))

    var text: String
        get() = state.text
        set(value) {
            state.text = value
        }
    var textColor: String
        get() = state.textColor
        set(value) {
            state.textColor = value
        }
    var backgroundColor: String
        get() = state.backgroundColor
        set(value) {
            state.backgroundColor = value
        }
    var fontSize: Int
        get() = state.fontSize
        set(value) {
            state.fontSize = value
        }
    var fontType: String
        get() = state.fontType
        set(value) {
            state.fontType = value
        }
    var bold: Boolean
        get() = state.bold
        set(value) {
            state.bold = value
        }
    var italic: Boolean
        get() = state.italic
        set(value) {
            state.italic = value
        }
    var underline: Boolean
        get() = state.underline
        set(value) {
            state.underline = value
        }
    var shadow: Boolean
        get() = state.shadow
        set(value) {
            state.shadow = value
        }
    var backdrop: TextBackdrop
        get() = state.backdrop
        set(value) {
            state.backdrop = value
        }
    var outline: TextOutline
        get() = state.outline
        set(value) {
            state.outline = value
        }
    var horizontalAlignment: String
        get() = state.horizontalAlignment
        set(value) {
            state.horizontalAlignment = value
        }
    var position: String
        get() = state.position
        set(value) {
            state.position = value
        }
    var animationType: String
        get() = state.animationType
        set(value) {
            state.animationType = value
        }
    var animationDuration: Int
        get() = state.animationDuration
        set(value) {
            state.animationDuration = value
        }

    /** How many times the animation repeats; never below zero. */
    var loopCount: Int
        get() = state.loopCount
        set(value) {
            state.loopCount = value.coerceAtLeast(0)
        }

    var timerExpiredText: String
        get() = state.timerExpiredText
        set(value) {
            state.timerExpiredText = value
        }
    var timerTextColor: String
        get() = state.timerTextColor
        set(value) {
            state.timerTextColor = value
        }

    /** The remaining time the tab previews before the timer is started; see [AnnouncementsState]. */
    val timerRemaining: Int get() = state.timerRemaining
    val countUpElapsed: Int get() = state.countUpElapsed
    val liveClockText: String get() = state.liveClockText

    fun syncFromSettings(settings: AnnouncementsSettings) = with(state) {
        text = settings.text
        textColor = settings.textColor
        backgroundColor = settings.backgroundColor
        fontSize = settings.fontSize
        fontType = settings.fontType
        bold = settings.bold
        italic = settings.italic
        underline = settings.underline
        shadow = settings.shadow
        backdrop = settings.backdrop
        outline = settings.outline
        shadowColor = settings.shadowColor
        shadowSize = settings.shadowSize
        shadowOpacity = settings.shadowOpacity
        horizontalAlignment = settings.horizontalAlignment
        position = settings.position
        animationType = settings.animationType
        animationDuration = settings.animationDuration
        loopCount = settings.loopCount
        timerHours = settings.timerHours
        timerMinutes = settings.timerMinutes
        timerSeconds = settings.timerSeconds
        timerTextColor = settings.timerTextColor
        timerExpiredText = settings.timerExpiredText
        timerMode = settings.timerMode
        targetHour = settings.targetHour
        targetMinute = settings.targetMinute
        targetSecond = settings.targetSecond
        liveClockFormat = settings.liveClockFormat
        countUpElapsed = 0
        timerRemaining = if (settings.timerMode == Constants.TIMER_MODE_CLOCK) secondsUntilTarget() else totalSeconds()
        clock.startFor(settings.timerMode)
    }

    fun dispose() {
        clock.stop()
        scope.cancel()
    }

    fun saveToSettings(onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
        val snap = buildSettings()
        onSettingsChange { s -> s.copy(announcementsSettings = snap) }
    }

    fun buildSettings(): AnnouncementsSettings = with(state) {
        AnnouncementsSettings(
            text = text,
            textColor = textColor,
            backgroundColor = backgroundColor,
            fontSize = fontSize,
            fontType = fontType,
            bold = bold,
            italic = italic,
            underline = underline,
            shadow = shadow,
            backdrop = backdrop,
            outline = outline,
            shadowColor = shadowColor,
            shadowSize = shadowSize,
            shadowOpacity = shadowOpacity,
            horizontalAlignment = horizontalAlignment,
            position = position,
            animationType = animationType,
            animationDuration = animationDuration,
            loopCount = loopCount,
            timerHours = timerHours,
            timerMinutes = timerMinutes,
            timerSeconds = timerSeconds,
            timerTextColor = timerTextColor,
            timerExpiredText = timerExpiredText,
            timerMode = timerMode,
            targetHour = targetHour,
            targetMinute = targetMinute,
            targetSecond = targetSecond,
            liveClockFormat = liveClockFormat
        )
    }

    fun goLive(output: AnnouncementsOutput, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
        saveToSettings(onSettingsChange)
        pauseTimer(output)
        output.setAnnouncementText(state.text)
        output.setPresentingMode(Presenting.ANNOUNCEMENTS)
    }

    companion object {
        fun formatTimer(remaining: Int): String {
            val h = remaining / SECONDS_PER_HOUR
            val m = (remaining % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
            val s = remaining % SECONDS_PER_MINUTE
            return if (h > 0) "%d:%02d:%02d".format(h, m, s)
            else "%02d:%02d".format(m, s)
        }
    }
}
