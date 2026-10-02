package org.churchpresenter.announcements

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.isSystemUsing24HourFormat
import java.time.LocalTime

internal const val SECONDS_PER_MINUTE = 60
internal const val SECONDS_PER_HOUR = 3600
internal const val SECONDS_PER_DAY = 86400
internal const val HOURS_PER_DAY = 24
internal const val MAX_HOUR = 23
internal const val MAX_MINUTE = 59
internal const val MAX_SECOND = 59
internal const val LAST_SECOND_STEP = 55
internal const val SECOND_STEP = 5
internal const val TICK_INTERVAL_MS = 1000L

/**
 * Everything [AnnouncementsViewModel] and its timer parts read and write: the announcement's text and
 * look, and the timer's configuration and local preview.
 */
internal class AnnouncementsState {
    var text by mutableStateOf("")
    var textColor by mutableStateOf("#FFFFFF")
    var backgroundColor by mutableStateOf("#000000")
    var fontSize by mutableStateOf(48)
    var fontType by mutableStateOf("Arial")
    var bold by mutableStateOf(false)
    var italic by mutableStateOf(false)
    var underline by mutableStateOf(false)
    var shadow by mutableStateOf(false)
    var backdrop by mutableStateOf(TextBackdrop())
    var outline by mutableStateOf(TextOutline())
    var shadowColor by mutableStateOf("#000000")
    var shadowSize by mutableStateOf(100)
    var shadowOpacity by mutableStateOf(78)
    var horizontalAlignment by mutableStateOf(Constants.CENTER)
    var position by mutableStateOf(Constants.CENTER)
    var animationType by mutableStateOf(Constants.ANIMATION_SLIDE_FROM_BOTTOM)
    var animationDuration by mutableStateOf(500)
    var loopCount by mutableStateOf(0)

    // Timer — the configured duration.
    var timerHours by mutableStateOf(0)
    var timerMinutes by mutableStateOf(0)
    var timerSeconds by mutableStateOf(0)

    /**
     * The local, not-yet-live preview of the remaining time. Once started, the timer ticks on the
     * output instead (see [AnnouncementsTimerControl]).
     */
    var timerRemaining by mutableStateOf(0)
    var timerExpiredText by mutableStateOf("")
    var timerTextColor by mutableStateOf("#FFFFFF")
    var timerMode by mutableStateOf(Constants.TIMER_MODE_DURATION)

    // Timer — the Specific Time target.
    var targetHour by mutableStateOf(0)
    var targetMinute by mutableStateOf(0)
    var targetSecond by mutableStateOf(0)

    var countUpElapsed by mutableStateOf(0)

    /** Java DateTimeFormatter pattern for TIMER_MODE_CLOCK_DISPLAY, e.g. "h:mm:ss a" or "HH:mm". */
    var liveClockFormat by mutableStateOf(if (isSystemUsing24HourFormat()) "HH:mm:ss" else "h:mm:ss a")

    /** The current wall-clock time formatted with [liveClockFormat], updated every second. */
    var liveClockText by mutableStateOf("")

    fun totalSeconds(): Int = timerHours * SECONDS_PER_HOUR + timerMinutes * SECONDS_PER_MINUTE + timerSeconds

    fun secondsUntilTarget(): Int {
        val nowSec = LocalTime.now().toSecondOfDay()
        val targetSec = targetHour * SECONDS_PER_HOUR + targetMinute * SECONDS_PER_MINUTE + targetSecond
        val diff = targetSec - nowSec
        return if (diff > 0) diff else diff + SECONDS_PER_DAY
    }
}
