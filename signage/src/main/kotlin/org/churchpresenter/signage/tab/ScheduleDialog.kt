package org.churchpresenter.signage.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.DropdownSettingsField
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.sharedui.composables.bibleListCard
import org.churchpresenter.signage.core.BlockAction
import org.churchpresenter.signage.core.ClockTime
import org.churchpresenter.signage.core.Day
import org.churchpresenter.signage.core.ScheduleBlock
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_schedule_add
import org.churchpresenter.strings.generated.resources.signage_schedule_from
import org.churchpresenter.strings.generated.resources.signage_schedule_hint
import org.churchpresenter.strings.generated.resources.signage_schedule_remove
import org.churchpresenter.strings.generated.resources.signage_schedule_service
import org.churchpresenter.strings.generated.resources.signage_schedule_shows
import org.churchpresenter.strings.generated.resources.signage_schedule_title
import org.churchpresenter.strings.generated.resources.signage_schedule_to
import org.churchpresenter.strings.generated.resources.signage_schedule_tv_off
import org.jetbrains.compose.resources.stringResource
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/** One time of a schedule as it is being edited: the times as typed, checked when it is saved. */
private data class Draft(
    val id: String,
    val days: Set<Day>,
    val start: String,
    val end: String,
    val action: BlockAction,
) {
    fun block(): ScheduleBlock? {
        val from = runCatching { ClockTime.parse(start.trim()) }.getOrNull() ?: return null
        val to = runCatching { ClockTime.parse(end.trim()) }.getOrNull() ?: return null
        return ScheduleBlock(id, days, from, to, action).takeIf { days.isNotEmpty() }
    }
}

/**
 * [screenName]'s weekly schedule: times, each on some days, showing a playlist from [playlists],
 * turning the TV off, or leaving it on the service input. Outside them the screen plays what its
 * Plays picker says.
 */
@Composable
internal fun ScheduleDialog(
    screenName: String,
    playlists: List<PlaylistSummary>,
    blocks: List<ScheduleBlock>,
    onSave: (List<ScheduleBlock>) -> Unit,
    onDismiss: () -> Unit,
) {
    val drafts = remember {
        mutableStateListOf<Draft>().apply {
            addAll(blocks.map { Draft(it.id, it.days, it.start.toString(), it.end.toString(), it.action) })
        }
    }
    val tvOff = stringResource(Res.string.signage_schedule_tv_off)
    val service = stringResource(Res.string.signage_schedule_service)
    val names = playlists.map { it.name } + tvOff + service
    fun nameOf(action: BlockAction): String = when (action) {
        BlockAction.TvOff -> tvOff
        BlockAction.Service -> service
        is BlockAction.Play -> playlists.firstOrNull { it.id == action.playlistId }?.name ?: action.playlistId
    }
    fun actionOf(name: String): BlockAction = when (name) {
        tvOff -> BlockAction.TvOff
        service -> BlockAction.Service
        else -> playlists.firstOrNull { it.name == name }?.let { BlockAction.Play(it.id) } ?: BlockAction.TvOff
    }
    val valid = drafts.all { it.block() != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.signage_schedule_title, screenName)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.width(DIALOG_WIDTH)
                    .heightIn(max = DIALOG_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                drafts.forEachIndexed { index, draft ->
                    TimeBlock(
                        draft = draft,
                        names = names,
                        shows = nameOf(draft.action),
                        onChange = { drafts[index] = it },
                        onShows = { drafts[index] = draft.copy(action = actionOf(it)) },
                        onRemove = { drafts.removeAt(index) },
                    )
                }
                TextButton(onClick = { drafts += newDraft(playlists) }) {
                    Icon(rememberVectorPainter(Icons.Filled.Add), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.signage_schedule_add))
                }
                Text(
                    stringResource(Res.string.signage_schedule_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(drafts.mapNotNull { it.block() })
                    onDismiss()
                },
                enabled = valid,
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** One time: its days, from and to, what it shows, and removing it. */
@Composable
private fun TimeBlock(
    draft: Draft,
    names: List<String>,
    shows: String,
    onChange: (Draft) -> Unit,
    onShows: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().bibleListCard().padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Day.entries.forEach { day ->
                FilterChip(
                    selected = day in draft.days,
                    onClick = {
                        val days = if (day in draft.days) draft.days - day else draft.days + day
                        onChange(draft.copy(days = days))
                    },
                    label = { Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                )
            }
            Spacer(Modifier.weight(1f))
            TooltipIconButton(
                painter = rememberVectorPainter(Icons.Filled.Close),
                text = stringResource(Res.string.signage_schedule_remove),
                onClick = onRemove,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeField(stringResource(Res.string.signage_schedule_from), draft.start) {
                onChange(draft.copy(start = it))
            }
            TimeField(stringResource(Res.string.signage_schedule_to), draft.end) { onChange(draft.copy(end = it)) }
            DropdownSettingsField(
                value = shows,
                options = names,
                onValueChange = onShows,
                label = stringResource(Res.string.signage_schedule_shows),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(TIME_PLACEHOLDER) },
        isError = runCatching { ClockTime.parse(value.trim()) }.isFailure,
        singleLine = true,
        modifier = Modifier.width(TIME_WIDTH),
    )
}

/** A new time: weekdays, eight to five, showing the first playlist (or the TV off when there is none). */
private fun newDraft(playlists: List<PlaylistSummary>) = Draft(
    id = UUID.randomUUID().toString(),
    days = setOf(Day.MON, Day.TUE, Day.WED, Day.THU, Day.FRI),
    start = "08:00",
    end = "17:00",
    action = playlists.firstOrNull()?.let { BlockAction.Play(it.id) } ?: BlockAction.TvOff,
)

/** Shown in an empty time field: the form, the same in every language. */
private const val TIME_PLACEHOLDER = "HH:mm"
private val DIALOG_WIDTH = 620.dp
private val DIALOG_MAX_HEIGHT = 560.dp
private val TIME_WIDTH = 96.dp
