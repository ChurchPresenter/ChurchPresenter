package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.calendar.CueFeed
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import churchpresenter.composeapp.generated.resources.schedule_timing_blank
import churchpresenter.composeapp.generated.resources.schedule_timing_loop
import churchpresenter.composeapp.generated.resources.schedule_timing_next
import churchpresenter.composeapp.generated.resources.schedule_timing_times
import org.churchpresenter.calendar.model.RowClock
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.formatDuration
import org.churchpresenter.calendar.model.storedTime
import org.churchpresenter.calendar.model.localeUses24HourClock
import androidx.compose.runtime.collectAsState
import churchpresenter.composeapp.generated.resources.schedule_cue_fired
import churchpresenter.composeapp.generated.resources.schedule_cue_skipped
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.pause_duration_ms
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.ScheduleDensity
import org.churchpresenter.app.churchpresenter.utils.scheduleShowKindDetails
import org.churchpresenter.app.churchpresenter.viewmodel.announcementTimerSubtext
import org.churchpresenter.app.churchpresenter.viewmodel.scheduleItemDetailText
import org.churchpresenter.app.churchpresenter.viewmodel.scheduleItemKindLabel
import org.churchpresenter.app.churchpresenter.viewmodel.scheduleItemPaletteIndex
import org.jetbrains.compose.resources.stringResource
/**
 * What a schedule row *says* — the four lines `ScheduleItemContent` stacks.
 *
 * Their own file because `ScheduleItemRow.kt` draws the card, its hover actions and its note, and
 * one file holding both grew past what detekt's `TooManyFunctions` allows. They are `internal`
 * rather than private for the same reason, and nothing outside this file calls them.
 */

/** The row itself: a song's number, what it is, how long it runs and when it goes live. */
@Composable
internal fun ScheduleRowTitleLine(
    item: ScheduleItem,
    isSelected: Boolean,
    timing: RowTiming,
    clock: RowClock?,
) {
        val titleColor = MaterialTheme.colorScheme.onSurface

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (item is ScheduleItem.SongItem && item.songNumber > 0) {
            Text(
                text = item.songNumber.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(

            text = if (item is ScheduleItem.SongItem) item.title else item.displayText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // The row's own length, where the plan knows it -- `5:00`.
        timing.runSeconds?.let { seconds ->
            Text(
                text = formatDuration(seconds * timing.repeats.coerceAtLeast(1)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        // When it goes live: its own pinned time, or the time the plan works out to. A reckoned
        // time is dimmed, and dimmer still once a row of unknown length has been passed -- it is
        // an estimate from there on, and should not read like a promise.
        val shown = timing.startAt.takeIf { it.isNotEmpty() } ?: clock?.let { storedTime(it.time) }
        if (shown != null) {
            Text(
                text = clockText(shown, localeUses24HourClock()),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (timing.startsOnItsOwn()) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    timing.startsOnItsOwn() -> MaterialTheme.colorScheme.tertiary
                    clock?.exact == true -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ESTIMATE_ALPHA)
                },
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** `Loop · then next` -- what the row does around its run, under the title. */
@Composable
internal fun ScheduleRowTimingLine(timing: RowTiming) {
    if (timing.repeats != 1 || timing.atEnd != RowEnd.HOLD) {
        // `Loop · then next` -- what the row does around its run, under the title.
        val parts = listOfNotNull(
            when {
                timing.loops() -> stringResource(Res.string.schedule_timing_loop)
                timing.repeats > 1 -> stringResource(Res.string.schedule_timing_times, timing.repeats)
                else -> null
            },
            when (timing.atEnd) {
                RowEnd.NEXT -> stringResource(Res.string.schedule_timing_next)
                RowEnd.BLANK -> stringResource(Res.string.schedule_timing_blank)
                else -> null
            },
        )
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            maxLines = 1,
        )
    }
}

/** The second line, which is a different thing for every kind of row. */
@Composable
internal fun ScheduleRowDetailLine(item: ScheduleItem, density: ScheduleDensity) {
        val detailColor = MaterialTheme.colorScheme.onSurfaceVariant

    when (item) {
        is ScheduleItem.SongItem -> if (item.songbook.isNotBlank()) {
            Text(
                text = item.songbook,
                style = MaterialTheme.typography.bodySmall,
                color = detailColor,
                maxLines = 1,

                overflow = TextOverflow.StartEllipsis
            )
        }
        is ScheduleItem.BibleVerseItem -> Text(
            text = scheduleItemDetailText(item).orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = detailColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        is ScheduleItem.PictureItem -> Text(
            text = scheduleItemDetailText(item).orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = detailColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        is ScheduleItem.PresentationItem -> if (!scheduleShowKindDetails(density.percent)) {
            Text(
                text = scheduleItemDetailText(item).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = detailColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        is ScheduleItem.MediaItem -> if (!scheduleShowKindDetails(density.percent)) {
            Text(
                text = scheduleItemDetailText(item).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = detailColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        is ScheduleItem.LowerThirdItem -> if (item.pauseAtFrame) {
            Text(
                text = stringResource(Res.string.pause_duration_ms, item.pauseDurationMs),
                style = MaterialTheme.typography.bodySmall, color = detailColor, maxLines = 1
            )
        }
        is ScheduleItem.AnnouncementItem -> {
            val timerSubtext = announcementTimerSubtext(item)
            if (item.isTimer && timerSubtext != null) {
                Text(
                    text = timerSubtext,
                    style = MaterialTheme.typography.bodySmall, color = detailColor, maxLines = 1
                )
            }
        }
        is ScheduleItem.WebsiteItem -> Text(
            text = item.url,
            style = MaterialTheme.typography.bodySmall,
            color = detailColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        is ScheduleItem.DictionaryItem -> Text(
            text = item.transliteration,
            style = MaterialTheme.typography.bodySmall,
            color = detailColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        is ScheduleItem.CueItem -> {
            // `Fired 9:45 AM` once the engine -- or a hand -- has set it off this session; or
            // `Skipped 9:45 AM` when it was due while the operator was live with something else.
            val fired by CueFeed.fired.collectAsState()
            val event = fired.firstOrNull { it.row.id == item.id }
            val detail = scheduleItemDetailText(item).orEmpty()
            Text(
                text = if (event == null) {
                    detail
                } else {
                    val at = clockText(event.at, localeUses24HourClock())
                    detail + " · " + stringResource(
                        if (event.skipped) Res.string.schedule_cue_skipped else Res.string.schedule_cue_fired,
                        at,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    event == null -> detailColor
                    event.skipped -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        is ScheduleItem.LabelItem, is ScheduleItem.SceneItem -> {  }
    }
}

/** At the roomiest density: what kind of row this is, and the file behind it. */
@Composable
internal fun ScheduleRowKindChips(item: ScheduleItem, density: ScheduleDensity) {
        val detailColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (scheduleShowKindDetails(density.percent)) {
        val path = when (item) {
            is ScheduleItem.PresentationItem -> item.filePath
            is ScheduleItem.MediaItem -> item.mediaUrl
            else -> null
        }
        Row(
            modifier = Modifier.padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (chipBg, chipFg) = scheduleChipColors(scheduleItemPaletteIndex(item))
            Box(
                modifier = Modifier
                    .background(chipBg, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = stringResource(scheduleItemKindLabel(item)).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = chipFg
                )
            }
            if (path != null) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = detailColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** How faint a reckoned time goes once a row of unknown length has been passed. */
private const val ESTIMATE_ALPHA = 0.55f
