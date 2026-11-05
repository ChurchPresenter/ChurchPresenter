package org.churchpresenter.signage.tab

import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.components.RaisedButton
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.churchpresenter.strings.generated.resources.signage_schedule_next_day
import org.churchpresenter.strings.generated.resources.signage_schedule_outside
import org.churchpresenter.strings.generated.resources.signage_schedule_week
import org.churchpresenter.theme.dropdownField
import org.churchpresenter.theme.isDarkScheme
import org.churchpresenter.theme.keyboardFocusRing
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
    /** What the screen's Plays picker is set to, which it plays outside these times. */
    plays: String = "",
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
    val colours = ScheduleColours(playlists)
    val valid = drafts.all { it.block() != null }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.signage_schedule_title, screenName))
                    Spacer(Modifier.width(6.dp))
                    InfoHint(stringResource(Res.string.signage_schedule_hint))
                }
                if (plays.isNotEmpty()) {
                    Text(
                        stringResource(Res.string.signage_schedule_outside, plays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.width(DIALOG_WIDTH)
                    .heightIn(max = DIALOG_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
            ) {
                val blocksNow = drafts.mapNotNull { it.block() }
                WeekOverview(blocksNow, colours)
                drafts.forEachIndexed { index, draft ->
                    TimeBlock(
                        draft = draft,
                        names = names,
                        shows = nameOf(draft.action),
                        colourOf = { colours.of(actionOf(it)) },
                        onChange = { drafts[index] = it },
                        onShows = { drafts[index] = draft.copy(action = actionOf(it)) },
                        onRemove = { drafts.removeAt(index) },
                    )
                }
                RingedGhostButton(onClick = { drafts += newDraft(playlists) }) {
                    Icon(rememberVectorPainter(Icons.Filled.Add), null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.signage_schedule_add))
                }
            }
        },
        confirmButton = {
            RaisedButton(
                onClick = {
                    onSave(drafts.mapNotNull { it.block() })
                    onDismiss()
                },
                enabled = valid,
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { RingedGhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** One time: its days, from and to, what it shows, and removing it. */
@Composable
private fun TimeBlock(
    draft: Draft,
    names: List<String>,
    shows: String,
    colourOf: @Composable (String) -> Color,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            TimeField(stringResource(Res.string.signage_schedule_from), draft.start) {
                onChange(draft.copy(start = it))
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 12.dp).size(14.dp),
            )
            TimeField(stringResource(Res.string.signage_schedule_to), draft.end) { onChange(draft.copy(end = it)) }
            if (draft.block()?.crossesMidnight == true) {
                Text(
                    stringResource(Res.string.signage_schedule_next_day),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 10.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = TAG_ALPHA),
                            RoundedCornerShape(5.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            ShowsPicker(
                shows = shows,
                names = names,
                colourOf = colourOf,
                onShows = onShows,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** What a time shows, its colour from the week above beside each choice. */
@Composable
private fun ShowsPicker(
    shows: String,
    names: List<String>,
    colourOf: @Composable (String) -> Color,
    onShows: (String) -> Unit,
    modifier: Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val label = stringResource(Res.string.signage_schedule_shows)
    Box(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(FIELD_HEIGHT)
                .keyboardFocusRing(shape)
                .dropdownField(shape, open)
                .semantics { contentDescription = "$label $shows" }
                .clickable(role = Role.DropdownList) { open = true }
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    label.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatch(colourOf(shows))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        shows,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
        }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                names.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        leadingIcon = { Swatch(colourOf(name)) },
                        onClick = {
                            open = false
                            onShows(name)
                        },
                    )
                }
            }
    }
}

@Composable
private fun Swatch(colour: Color) {
    Spacer(Modifier.size(10.dp).background(colour, RoundedCornerShape(3.dp)))
}

/**
 * The whole week drawn before the times: a bar per day from midnight to midnight, each time a block
 * in its colour, one that runs past midnight carrying on into the next morning; and what each colour
 * shows.
 */
@Composable
private fun WeekOverview(blocks: List<ScheduleBlock>, colours: ScheduleColours) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.onSurface.copy(alpha = TAG_ALPHA)
    val grid = scheme.surface.copy(alpha = GRID_ALPHA)
    val name = stringResource(Res.string.signage_schedule_week)
    val hours = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(scheme.onSurface.copy(alpha = PANEL_ALPHA), RoundedCornerShape(12.dp))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp)
            // A picture of the times below, read out as one thing rather than every day and label again.
            .clearAndSetSemantics { contentDescription = name },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val spans = blocks.map { block -> block to colours.of(block.action) }
        Day.entries.forEachIndexed { index, day ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.width(DAY_LABEL_WIDTH),
                )
                Canvas(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp))) {
                    drawRect(track)
                    val perMinute = size.width / ClockTime.MINUTES_PER_DAY
                    fun span(from: Int, to: Int, colour: Color) = drawRect(
                        colour,
                        topLeft = Offset(from * perMinute, 0f),
                        size = Size((to - from) * perMinute, size.height),
                    )
                    val yesterday = Day.entries[(index + Day.entries.size - 1) % Day.entries.size]
                    spans.forEach { (block, colour) ->
                        val start = block.start.minuteOfDay
                        val end = block.end.minuteOfDay
                        val todayEnd = if (block.crossesMidnight) ClockTime.MINUTES_PER_DAY else end
                        if (day in block.days) span(start, todayEnd, colour)
                        if (block.crossesMidnight && yesterday in block.days) span(0, end, colour)
                    }
                    listOf(QUARTER, HALF, THREE_QUARTERS).forEach { share ->
                        drawLine(grid, Offset(size.width * share, 0f), Offset(size.width * share, size.height))
                    }
                }
            }
        }
        Row(Modifier.padding(start = DAY_LABEL_WIDTH, top = 2.dp)) {
            AXIS_HOURS.forEachIndexed { i, hour ->
                Text(
                    hours.format(LocalTime.of(hour, 0)),
                    fontSize = 10.5.sp,
                    color = scheme.outline,
                    textAlign = when (i) {
                        0 -> TextAlign.Start
                        AXIS_LAST -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val shown = spans.distinctBy { it.first.action }
        if (shown.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                shown.forEach { (block, colour) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Swatch(colour)
                        Spacer(Modifier.width(6.dp))
                        Text(colours.nameOf(block.action), fontSize = 11.5.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * The colour each thing a time can show is drawn in: a playlist in its kind's hue, TV off in grey,
 * Service in its own.
 */
private class ScheduleColours(private val playlists: List<PlaylistSummary>) {
    @Composable
    fun of(action: BlockAction): Color = when (action) {
        BlockAction.TvOff -> MaterialTheme.colorScheme.outline
        BlockAction.Service -> barOf(SERVICE_HUE)
        is BlockAction.Play -> barOf(
            playlists.firstOrNull { it.id == action.playlistId }
                ?.let { tileKindOf(it.kind).hue }
                ?: TileKind.SCREEN.hue,
        )
    }

    @Composable
    fun nameOf(action: BlockAction): String = when (action) {
        BlockAction.TvOff -> stringResource(Res.string.signage_schedule_tv_off)
        BlockAction.Service -> stringResource(Res.string.signage_schedule_service)
        is BlockAction.Play -> playlists.firstOrNull { it.id == action.playlistId }?.name ?: action.playlistId
    }

    @Composable
    private fun barOf(hue: Float): Color =
        Color.hsl(hue, BAR_S, if (isDarkScheme(MaterialTheme.colorScheme)) BAR_L_DARK else BAR_L_LIGHT)
}

@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    SettingsTextField(
        value = value,
        onValueChange = onChange,
        label = label,
        placeholder = { Text(TIME_PLACEHOLDER, style = MaterialTheme.typography.bodySmall) },
        isError = runCatching { ClockTime.parse(value.trim()) }.isFailure,
        fillWidth = true,
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
private val DIALOG_MAX_HEIGHT = 700.dp
private val TIME_WIDTH = 96.dp
private val FIELD_HEIGHT = 42.dp
private val DAY_LABEL_WIDTH = 40.dp
private const val TAG_ALPHA = 0.07f
private const val PANEL_ALPHA = 0.03f
private const val GRID_ALPHA = 0.6f
private const val QUARTER = 0.25f
private const val HALF = 0.5f
private const val THREE_QUARTERS = 0.75f
private const val AXIS_LAST = 4

/** The hours under the week: midnight, every six hours, and midnight again at the end. */
private val AXIS_HOURS = listOf(0, 6, 12, 18, 0)
private const val SERVICE_HUE = 285f
private const val BAR_S = 0.55f
private const val BAR_L_DARK = 0.62f
private const val BAR_L_LIGHT = 0.6f
