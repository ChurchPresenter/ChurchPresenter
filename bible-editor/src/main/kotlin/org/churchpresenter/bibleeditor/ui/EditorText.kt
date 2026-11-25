package org.churchpresenter.bibleeditor.ui

import org.churchpresenter.bibleedits.EditKind
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import org.churchpresenter.bibleeditor.DiffKind
import org.churchpresenter.bibleeditor.DiffSpan
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_editor_day_time
import org.churchpresenter.strings.generated.resources.bible_editor_just_now
import org.churchpresenter.strings.generated.resources.bible_editor_kind_added
import org.churchpresenter.strings.generated.resources.bible_editor_kind_edited
import org.churchpresenter.strings.generated.resources.bible_editor_kind_removed
import org.churchpresenter.strings.generated.resources.bible_editor_kind_restored
import org.churchpresenter.strings.generated.resources.bible_editor_kind_reverted
import org.churchpresenter.strings.generated.resources.bible_editor_minutes_ago
import org.churchpresenter.strings.generated.resources.bible_editor_today
import org.churchpresenter.strings.generated.resources.bible_editor_yesterday
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 3_600_000L

/** The verse text's face: a serif, as a printed Bible is set. A screenshot pins it to a bundled face. */
val LocalScriptureFont = staticCompositionLocalOf<FontFamily> { FontFamily.Serif }

internal val ScriptureFamily: FontFamily
    @Composable get() = LocalScriptureFont.current

/** [one] for a count of one, [other] for every other count. */
@Composable
internal fun countString(count: Int, one: StringResource, other: StringResource): String =
    stringResource(if (count == 1) one else other, count)

/** "Just now", "12 min ago", or the clock time. */
@Composable
internal fun relativeTime(time: Long, now: Long): String {
    val ago = now - time
    return when {
        ago < MINUTE_MS -> stringResource(Res.string.bible_editor_just_now)
        ago < HOUR_MS -> stringResource(Res.string.bible_editor_minutes_ago, (ago / MINUTE_MS).toInt())
        else -> clockTime(time)
    }
}

/** "Today", "Yesterday", or the date. */
@Composable
internal fun dayLabel(time: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> stringResource(Res.string.bible_editor_today)
        today.minusDays(1) -> stringResource(Res.string.bible_editor_yesterday)
        else -> dateLabel(day)
    }
}

/** When a change was made: its relative time today, else its day and time. */
@Composable
internal fun stamp(time: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return if (day == today) relativeTime(time, now)
    else stringResource(Res.string.bible_editor_day_time, dayLabel(time, now), clockTime(time))
}

private fun clockTime(time: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

private fun dateLabel(day: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(day)

/** What a history entry is called, and the two colors it is drawn in. */
@Composable
internal fun kindLook(kind: EditKind, colors: EditorColors): Triple<String, Color, Color> = when (kind) {
    EditKind.EDITED -> Triple(stringResource(Res.string.bible_editor_kind_edited), colors.accentBg, colors.accent)
    EditKind.ADDED -> Triple(stringResource(Res.string.bible_editor_kind_added), colors.goodBg, colors.good)
    EditKind.REMOVED -> Triple(stringResource(Res.string.bible_editor_kind_removed), colors.badBg, colors.bad)
    EditKind.RESTORED -> Triple(stringResource(Res.string.bible_editor_kind_restored), colors.goodBg, colors.good)
    EditKind.REVERTED -> Triple(stringResource(Res.string.bible_editor_kind_reverted), colors.warnBg, colors.warn)
}

/** A word diff: what went struck through in red, what came in green. */
@Composable
internal fun DiffText(spans: List<DiffSpan>, modifier: Modifier = Modifier) {
    val colors = editorColors()
    val text = buildAnnotatedString {
        spans.forEach { span ->
            val style = when (span.kind) {
                DiffKind.SAME -> SpanStyle()
                DiffKind.DELETED -> SpanStyle(
                    color = colors.bad, background = colors.badBg, textDecoration = TextDecoration.LineThrough,
                )
                DiffKind.INSERTED -> SpanStyle(color = colors.good, background = colors.goodBg)
            }
            withStyle(style) { append(span.text) }
        }
    }
    Text(text, modifier, color = colors.dim, fontFamily = ScriptureFamily, fontSize = 13.5.sp, lineHeight = 21.sp)
}
