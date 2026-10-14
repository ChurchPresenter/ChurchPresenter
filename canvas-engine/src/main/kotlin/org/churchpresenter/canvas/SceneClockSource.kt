package org.churchpresenter.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.churchpresenter.core.models.scene.ClockModes
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.sharedui.utils.Utils.parseHexColor

import org.churchpresenter.sharedui.utils.Utils.systemFontFamilyOrDefault
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalTime
import java.time.format.DateTimeFormatter

import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.sharedui.composables.mode
import org.churchpresenter.sharedui.composables.rememberTextBackdropPainter

@Composable
internal fun ClockSourceContent(source: SceneSource.ClockSource, modifier: Modifier, fontScale: Float = 1f) {
    val bgColor = parseHexColor(source.backgroundColor)
    val fontColor = parseHexColor(source.fontColor)
    val fontFamily = systemFontFamilyOrDefault(source.fontFamily)

    val displayText = when (source.mode) {
        ClockModes.COUNTDOWN -> countdownText(source)
        ClockModes.COUNT_UP -> countUpText(source)
        ClockModes.TARGET_TIME -> targetTimeText(source)
        else -> wallClockText(source)
    }

    val style = TextStyle(
        color = fontColor,
        fontSize = drawnFontSize(source.fontSize, fontScale),
        fontFamily = fontFamily,
        fontWeight = if (source.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (source.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = textDecorationOf(source.underline, source.strikethrough),
        letterSpacing = trackingOf(source.letterSpacing)
    )

    Box(
        modifier = modifier.fillMaxSize().background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (source.curve != 0f) {
            // A bent line is drawn glyph by glyph rather than laid out as text, so there is no line
            // box to band and no block to box: the backdrop is skipped rather than misplaced.
            CurvedText(
                displayText, source.curve, style, Modifier.fillMaxSize(),
                outline = source.outline, outlineScale = fontScale,
            )
        } else {
            val painter = rememberTextBackdropPainter(source.backdrop, fontScale)
            OutlinedText(
                text = displayText,
                outline = source.outline,
                scaleFactor = fontScale,
                color = Color.Unspecified,
                fontSize = TextUnit.Unspecified,
                style = style,
                fillWidth = false,
                modifier = painter.modifier,
                onTextLayout = painter::onTextLayout,
            )
        }
    }
}

/** hh:mm:ss, dropping either end as the source asks. */
private fun formatElapsed(seconds: Int, showHours: Boolean, showSeconds: Boolean): String = buildString {
    if (showHours) append("%02d:".format(seconds / SECONDS_PER_HOUR))
    append("%02d".format((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE))
    if (showSeconds) append(":%02d".format(seconds % SECONDS_PER_MINUTE))
}

/**
 * Seconds from now until the next occurrence of a time of day — tomorrow's, once today's has been
 * and gone, which is what keeps a "service starts at 10:00" countdown from going negative.
 */
private fun secondsUntilTimeOfDay(hour: Int, minute: Int, second: Int): Int {
    val target = hour * SECONDS_PER_HOUR + minute * SECONDS_PER_MINUTE + second
    val diff = target - LocalTime.now().toSecondOfDay()
    return if (diff > 0) diff else diff + SECONDS_PER_DAY
}

/** The countdown's remaining time, or its expiry message once it has run out. */
@Composable
private fun countdownText(source: SceneSource.ClockSource): String {
    val totalSeconds = source.targetHour * SECONDS_PER_HOUR +
        source.targetMinute * SECONDS_PER_MINUTE + source.targetSecond

    // Sync TimerStateManager when duration fields change
    LaunchedEffect(totalSeconds) {
        TimerStateManager.onDurationChanged(source.id, totalSeconds)
    }

    val remaining = TimerStateManager.getState(source.id, totalSeconds).remainingSeconds
    val expired = remaining == 0 && totalSeconds > 0
    return if (expired && source.expiredText.isNotBlank()) source.expiredText
    else formatElapsed(remaining, source.showHours, source.showSeconds)
}

/** A stopwatch: seeded at zero and counted up by the same shared state the countdown uses. */
@Composable
private fun countUpText(source: SceneSource.ClockSource): String {
    return formatElapsed(
        TimerStateManager.getState(source.id, 0).remainingSeconds,
        source.showHours,
        source.showSeconds
    )
}

/** Counts down to a time of day off the wall clock, so it needs no transport of its own. */
@Composable
private fun targetTimeText(source: SceneSource.ClockSource): String {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(
        source.targetTimeHour, source.targetTimeMinute, source.targetTimeSecond,
        source.showHours, source.showSeconds
    ) {
        while (isActive) {
            val remaining =
                secondsUntilTimeOfDay(source.targetTimeHour, source.targetTimeMinute, source.targetTimeSecond)
            text = formatElapsed(remaining, source.showHours, source.showSeconds)
            delay(POLL_INTERVAL_MS)
        }
    }
    return text
}

/** The wall clock itself, in the source's own 12h/24h format. */
@Composable
private fun wallClockText(source: SceneSource.ClockSource): String {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(source.timeFormat, source.showHours, source.showSeconds) {
        while (isActive) {
            val pattern = buildString {
                if (source.showHours) {
                    append(if (source.timeFormat == "12h") "hh:" else "HH:")
                }
                append("mm")
                if (source.showSeconds) append(":ss")
                if (source.timeFormat == "12h") append(" a")
            }
            text = LocalTime.now().format(DateTimeFormatter.ofPattern(pattern))
            delay(POLL_INTERVAL_MS)
        }
    }
    return text
}

private const val POLL_INTERVAL_MS = 1000L
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_DAY = 86400
