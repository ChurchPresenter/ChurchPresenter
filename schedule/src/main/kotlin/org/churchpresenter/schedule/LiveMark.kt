package org.churchpresenter.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.schedule_left_off
import org.churchpresenter.strings.generated.resources.schedule_live_badge
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.stringResource

/*
 * Which Schedule row is on screen (#1049). The row last put live from here keeps a LIVE mark while
 * the app says it is still what the outputs show; once something else is, or nothing, the mark
 * gives way to a "Left off" bar under that row -- where the service picks up again.
 */

/** The row last put live from the Schedule, and whether it is still on screen. */
internal data class LiveMark(val rowId: String, val onAir: Boolean)

/** Whether [this] row can be on screen at all: a label, a cue or a ministry row never is. */
internal fun ScheduleItem.canBeOnScreen(): Boolean =
    this !is ScheduleItem.LabelItem && this !is ScheduleItem.CueItem && this !is ScheduleItem.MinistryItem

/** [liveRowId]'s mark among [rows], asking [isOnAir] whether it is still up; none for a row that is gone. */
internal fun liveMark(rows: List<ScheduleItem>, liveRowId: String?, isOnAir: (ScheduleItem) -> Boolean): LiveMark? =
    rows.firstOrNull { it.id == liveRowId && it.canBeOnScreen() }?.let { LiveMark(it.id, isOnAir(it)) }

/** The LIVE badge after the live row's title. */
internal const val SCHEDULE_ROW_LIVE_BADGE_TAG = "schedule_row_live_badge"

private const val LIVE_BADGE_TINT = 0.16f

@Composable
internal fun ScheduleRowLiveBadge() {
    val tone = MaterialTheme.colorScheme.error
    Text(
        text = stringResource(Res.string.schedule_live_badge),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tone,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .testTag(SCHEDULE_ROW_LIVE_BADGE_TAG)
            .clip(AppShape(4.dp))
            .background(tone.copy(alpha = LIVE_BADGE_TINT))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** The bar left under the row that was live, once it no longer is. */
internal const val SCHEDULE_LEFT_OFF_TAG = "schedule_left_off"

private const val LEFT_OFF_ALPHA = 0.5f

/**
 * "Left off" and a line, in the live red at half strength, between the row that was last on
 * screen and the next one -- the row list stacks it under the card in a column. The word keeps it
 * from reading as the drop line a drag draws there.
 */
@Composable
internal fun ScheduleLeftOffBar() {
    val tone = MaterialTheme.colorScheme.error.copy(alpha = LEFT_OFF_ALPHA)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SCHEDULE_LEFT_OFF_TAG)
            .padding(start = 6.dp, end = 6.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(Res.string.schedule_left_off),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tone,
            maxLines = 1,
            softWrap = false,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(tone, AppShape(1.dp))
        )
    }
}
