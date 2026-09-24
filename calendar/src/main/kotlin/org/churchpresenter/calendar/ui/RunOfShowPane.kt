package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_add_item
import org.churchpresenter.calendar.generated.resources.calendar_copy_last
import org.churchpresenter.calendar.generated.resources.calendar_duration_hint
import org.churchpresenter.calendar.generated.resources.calendar_duration_tip
import org.churchpresenter.calendar.generated.resources.calendar_empty_hint
import org.churchpresenter.calendar.generated.resources.calendar_move_down
import org.churchpresenter.calendar.generated.resources.calendar_move_up
import org.churchpresenter.calendar.generated.resources.calendar_nothing_planned_for
import org.churchpresenter.calendar.generated.resources.calendar_remove_row
import org.churchpresenter.calendar.generated.resources.calendar_timing_follows
import org.churchpresenter.calendar.generated.resources.calendar_timing_follows_hint
import org.churchpresenter.calendar.generated.resources.calendar_timing_follows_stranded
import org.churchpresenter.calendar.generated.resources.calendar_usually
import org.churchpresenter.calendar.generated.resources.calendar_usually_tip
import org.churchpresenter.calendar.CueFeed
import androidx.compose.runtime.collectAsState
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.PreflightProblem
import org.churchpresenter.calendar.model.RowClock
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.cueStatuses
import org.churchpresenter.calendar.model.followsWithoutHandoff
import org.churchpresenter.calendar.model.formatDuration
import org.churchpresenter.calendar.model.parseDuration
import org.churchpresenter.calendar.model.runClocks
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.calendar.generated.resources.calendar_chip_times
import org.churchpresenter.calendar.generated.resources.calendar_chip_next
import org.churchpresenter.calendar.generated.resources.calendar_chip_loop
import org.churchpresenter.calendar.generated.resources.calendar_chip_blank
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.RowEnd
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import org.jetbrains.compose.resources.stringResource
import java.time.LocalTime
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette

private const val ROW_ALPHA = 0.45f
private const val BADGE_ALPHA = 0.18f
private const val DIM_ALPHA = 0.45f
private const val HINT_ALPHA = 0.6f
private val ROW_GAP = 3.dp
private val COPY_LABEL_MAX = 200.dp
private val GRIP_DOT = 2.dp
private val GRIP_WIDTH = 8.dp
private val GRIP_HEIGHT = 14.dp
private const val GRIP_ROWS = 3
private const val GRIP_ALPHA = 0.5f

/**
 * A service's run of show: one row per [ScheduleItem], in order, each with its expected clock time
 * and an optional planned length -- the service's cues among them, as rows, where they fire.
 *
 * Laid out to the design — `[time] [icon] [title/sub] [duration] [remove]`, section headings as
 * colored rules between them, a cue as `[time] [tick] [bolt] [title/sub] [status] [badge] [fire]`,
 * and the add control as a dashed button at the end of the list rather than in the header.
 *
 * [now] is the clock the cues are judged against -- the wall clock when the service is today, a
 * preview clock stepped from the header, or null for a service on another day, which has no
 * "fired" or "next" to speak of.
 *
 * Reordering is by drag on the grip, or by the two arrow buttons. A cue row is placed by its time
 * when it is saved, so it is neither dragged nor dropped on.
 */
@Composable
internal fun RunOfShowPane(
    service: PlannedService,
    now: LocalTime?,
    /** Whether [now] is a stepped preview clock -- the header offers to put it back. */
    previewing: Boolean,
    header: RunOfShowHeaderActions,
    /** What each row has actually taken on screen, by row id -- offered where it differs from the plan. */
    measuredSeconds: Map<String, Int>,
    /** The rows that will not go on screen on the day, by row id -- see `preflight`. */
    problems: Map<String, PreflightProblem>,
    onAddItem: () -> Unit,
    onChangeItem: (ScheduleItem) -> Unit,
    onRemove: (itemId: String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onPlannedSecondsChange: (itemId: String, seconds: Int?) -> Unit,
    onCueEnabled: (cueId: String, enabled: Boolean) -> Unit,
    onFireCue: (ScheduleItem.CueItem) -> Unit,
    /** The one-click fix for a row's problem -- locate the file, or pick again. See `ProblemFix`. */
    onFixProblem: (item: ScheduleItem, problem: PreflightProblem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clocks = remember(service) { runClocks(service) }
    // Rows that wait for a turn nothing gives them -- see followsWithoutHandoff.
    val stranded = remember(service) { service.followsWithoutHandoff() }
    // What the engine reported skipped this session: past its time, but not fired -- the clock
    // alone cannot tell the two apart.
    val feed by CueFeed.fired.collectAsState()
    val skippedIds = remember(feed) { feed.filter { it.skipped }.mapTo(HashSet()) { it.row.id } }
    val statuses = remember(service, now, skippedIds) { service.cueStatuses(now, skippedIds) }
    val listState = rememberLazyListState()
    // A drop lands on an item or a section, never on a cue; the keys are the rows' ids, and the
    // position each stands for is looked up here rather than assumed from the list.
    val positions = remember(service.items) {
        service.items.withIndex()
            .filter { (_, item) -> item !is ScheduleItem.CueItem }
            .associate { (index, item) -> item.id to index }
    }
    val reorder = rememberReorderState(
        listState = listState,
        isTarget = { it in positions },
        onMove = { from, to ->
            val fromIndex = positions[from]
            val toIndex = positions[to]
            if (fromIndex != null && toIndex != null) onMove(fromIndex, toIndex)
        },
    )
    val lastIndex = service.items.lastIndex
    Column(modifier.fillMaxSize()) {
        RunOfShowHeader(
            service = service,
            now = now,
            previewing = previewing,
            actions = header,
            problemCount = problems.size,
        )
        ScrollableList(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP),
        ) {
            // Keyed by row id alone, never the index: a reorder has to keep a row's key so the
            // list follows it rather than redrawing everything. Uniqueness is guaranteed on load
            // by CalendarDocument.withUniqueRowIds, not assumed here.
            itemsIndexed(service.items, key = { _, item -> item.id }) { index, item ->
                when (item) {
                    is ScheduleItem.LabelItem -> SectionRow(
                        item = item,
                        reorder = reorder,
                        onRemove = { onRemove(item.id) },
                        modifier = Modifier.animateItem(),
                    )
                    is ScheduleItem.CueItem -> CueRow(
                        cue = item,
                        startTime = service.startTime,
                        armed = service.armed,
                        status = statuses[item.id],
                        problem = problems[item.id],
                        onToggle = { onCueEnabled(item.id, !item.enabled) },
                        onFire = { onFireCue(item) },
                        onFixProblem = { problems[item.id]?.let { onFixProblem(item, it) } },
                        modifier = Modifier.animateItem(),
                    )
                    else -> RunRow(
                        item = item,
                        clock = clocks[item.id],
                        timing = service.timingOf(item.id),
                        serviceStartTime = service.startTime,
                        stranded = item.id in stranded,
                        plannedSeconds = service.plannedSeconds[item.id],
                        measuredSeconds = measuredSeconds[item.id],
                        problem = problems[item.id],
                        isFirst = index == 0,
                        isLast = index == lastIndex,
                        reorder = reorder,
                        onChange = { onChangeItem(item) },
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) },
                        onRemove = { onRemove(item.id) },
                        onPlannedSecondsChange = { onPlannedSecondsChange(item.id, it) },
                        onFixProblem = { problems[item.id]?.let { onFixProblem(item, it) } },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "add") { AddItemButton(onClick = onAddItem) }
        }
    }
}

@Composable
private fun RunRow(
    item: ScheduleItem,
    clock: RowClock?,
    timing: RowTiming,
    /** What a pinned start is said relative to -- see [startOffsetLabel]. */
    serviceStartTime: String,
    /** True when this row waits for a turn nothing hands it -- drawn as a warning, not a plan. */
    stranded: Boolean = false,
    plannedSeconds: Int?,
    /** What the row has actually taken here, or null until it has been shown enough to say. */
    measuredSeconds: Int?,
    /** Why the row will not go on screen on the day, or null when nothing is wrong with it. */
    problem: PreflightProblem?,
    isFirst: Boolean,
    isLast: Boolean,
    reorder: ReorderState,
    onChange: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onPlannedSecondsChange: (Int?) -> Unit,
    onFixProblem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val look = lookFor(item)
    val dragging = reorder.isDragging(item.id)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // The whole row opens the picker on this item, so a wrong song is changed in place rather
        // than removed and re-added. The grip, the duration and the three actions sit on top with
        // gestures of their own, which win over the row's — a click lands on them first.
        modifier = modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = reorder.translationFor(item.id) }
            .fillMaxWidth()
            .clip(CalendarMetrics.rowRadius)
            .background(scheme.surfaceVariant.copy(alpha = if (dragging) 1f else ROW_ALPHA))
            .border(
                width = 1.dp,
                color = if (dragging) scheme.primary else scheme.outlineVariant.copy(alpha = ROW_ALPHA),
                shape = CalendarMetrics.rowRadius,
            )
            .clickable(onClick = onChange)
            .padding(horizontal = 9.dp, vertical = 8.dp),
    ) {
        DragGrip(reorder = reorder, key = item.id)
        Text(
            text = clock?.let { clockText(it.time, LocalUse24HourClock.current) }.orEmpty(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            // Dimmed once an earlier row has no estimate: the clock is a projection from there on,
            // and a confident-looking time nobody can rely on is worse than an obviously soft one.
            color = scheme.onSurfaceVariant.copy(alpha = if (clock?.exact == true) 1f else DIM_ALPHA),
            textAlign = TextAlign.Start,
            maxLines = 1,
            modifier = Modifier.width(rowTimeColumnWidth()),
        )
        Box(
            Modifier
                .size(CalendarMetrics.rowIcon)
                .clip(RoundedCornerShape(6.dp))
                .background(look.color.copy(alpha = BADGE_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(look.icon, contentDescription = null, tint = look.color, modifier = Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f).padding(vertical = 1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (problem != null) ProblemMark(problem, onFix = onFixProblem)
                Text(
                    text = item.displayText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = if (problem == null) scheme.onSurface else scheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val subtitle = item.subtitle()
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TimingChips(
            timing = timing,
            serviceStartTime = serviceStartTime,
            stranded = stranded,
            onOpen = onChange,
        )
        // What it has actually taken, where that is not what is planned: one click adopts it. Not
        // shown once the plan agrees -- the chip is a suggestion, and a suggestion already taken
        // is noise.
        if (measuredSeconds != null && measuredSeconds != plannedSeconds) {
            Hint(stringResource(Res.string.calendar_usually_tip, formatDuration(measuredSeconds))) {
                RowChip(
                    text = stringResource(Res.string.calendar_usually, formatDuration(measuredSeconds)),
                    icon = Icons.Filled.History,
                    tone = scheme.secondary,
                    onClick = { onPlannedSecondsChange(measuredSeconds) },
                )
            }
        }
        DurationControl(seconds = plannedSeconds, onChange = onPlannedSecondsChange)
        RowAction(Icons.Filled.ArrowUpward, stringResource(Res.string.calendar_move_up), !isFirst, onMoveUp)
        RowAction(Icons.Filled.ArrowDownward, stringResource(Res.string.calendar_move_down), !isLast, onMoveDown)
        RowAction(Icons.Filled.Close, stringResource(Res.string.calendar_remove_row), true, onRemove)
    }
}

/**
 * What the row does on its own, as the design's chips: `⟳ Loop` / `⟳ 3`, `→ Next` / `Blank`, and
 * `⚡ −15` for a row that starts by itself. Each opens the row editor, where they are set.
 */
@Composable
private fun TimingChips(
    timing: RowTiming,
    serviceStartTime: String,
    stranded: Boolean,
    onOpen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    if (timing.repeats != 1) {
        RowChip(
            text = if (timing.loops()) {
                stringResource(Res.string.calendar_chip_loop)
            } else {
                stringResource(Res.string.calendar_chip_times, timing.repeats)
            },
            icon = Icons.Filled.Repeat,
            tone = scheme.tertiary,
            onClick = onOpen,
        )
    }
    if (timing.atEnd != RowEnd.HOLD) {
        RowChip(
            text = stringResource(
                if (timing.atEnd == RowEnd.NEXT) Res.string.calendar_chip_next else Res.string.calendar_chip_blank
            ),
            icon = null,
            tone = if (timing.atEnd == RowEnd.NEXT) scheme.tertiary else scheme.error,
            onClick = onOpen,
        )
    }
    if (timing.followsPrevious && !timing.startsOnItsOwn()) {
        Hint(
            stringResource(
                if (stranded) Res.string.calendar_timing_follows_stranded else Res.string.calendar_timing_follows_hint
            )
        ) {
            RowChip(
                text = stringResource(Res.string.calendar_timing_follows),
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                tone = if (stranded) scheme.error else scheme.tertiary,
                onClick = onOpen,
            )
        }
    }
    if (timing.startsOnItsOwn()) {
        Hint(clockText(timing.startAt, LocalUse24HourClock.current)) {
            RowChip(
                text = startOffsetLabel(timing.startAt, serviceStartTime),
                icon = Icons.Filled.Bolt,
                tone = scheme.tertiary,
                onClick = onOpen,
            )
        }
    }
}

@Composable
private fun RowChip(text: String, icon: ImageVector?, tone: Color, onClick: () -> Unit) {
    // A raised key: the danger key when the tone is the error red (whose raw red is under 4.5:1 on
    // a dark key), the neutral key otherwise with the tone kept on the icon.
    val palette = elevationPalette()
    val fill = if (tone == MaterialTheme.colorScheme.error) palette.danger else palette.key
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .height(CalendarMetrics.rowAction)
            .raisedKey(CalendarMetrics.smallRadius, fill, onClick = onClick)
            .padding(horizontal = 6.dp),
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (fill == palette.danger) fill.ink else tone,
                modifier = Modifier.size(9.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.Bold,
            color = fill.ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun RowAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Hint(description) {
        Box(
            Modifier
                .size(CalendarMetrics.rowAction)
                .then(
                    if (enabled) {
                        Modifier.raisedKey(CalendarMetrics.smallRadius, elevationPalette().key, onClick = onClick)
                    } else {
                        Modifier.clip(CalendarMetrics.smallRadius).clickable(enabled = false, onClick = onClick)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = scheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DIM_ALPHA * 0.6f),
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

/**
 * The six-dot grip a row is dragged by.
 *
 * Dragging is deliberately confined to this handle rather than the whole row, which is what the
 * design shows. A drag detector over the whole row competes with the duration field and the three
 * action buttons sitting inside it for the same pointer stream; a dedicated handle cannot be
 * ambiguous, and it is also the only part of the row that says "this moves".
 */
@Composable
private fun DragGrip(reorder: ReorderState, key: Any) {
    val scheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(width = GRIP_WIDTH, height = GRIP_HEIGHT)
            .pointerHoverIcon(PointerIcon.Hand)
            // A drag detector lets a plain click through to the row's clickable beneath, which
            // would open the picker from the one place that says "this moves". Swallow it.
            .pointerInput(key) { detectTapGestures { } }
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { reorder.start(key) },
                    onDrag = { change, amount ->
                        change.consume()
                        reorder.drag(amount.y)
                    },
                    onDragEnd = { reorder.end() },
                    onDragCancel = { reorder.end() },
                )
            },
    ) {
        repeat(GRIP_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) {
                    Box(
                        Modifier
                            .size(GRIP_DOT)
                            .clip(CircleShape)
                            .background(scheme.onSurfaceVariant.copy(alpha = GRIP_ALPHA))
                    )
                }
            }
        }
    }
}

/** A section heading — structure, so it is a colored rule and a label, not a card. */
@Composable
private fun SectionRow(
    item: ScheduleItem,
    reorder: ReorderState,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val color = (item as? ScheduleItem.LabelItem)?.let { parseHex(it.backgroundColor) } ?: scheme.outline
    val dragging = reorder.isDragging(item.id)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = reorder.translationFor(item.id) }
            .fillMaxWidth()
            .padding(top = 9.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
    ) {
        DragGrip(reorder = reorder, key = item.id)
        Box(
            Modifier
                .size(width = CalendarMetrics.accentBarWidth, height = CalendarMetrics.sectionBarHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = item.displayText.uppercase(),
            style = overlineStyle(letterSpacingEm = SECTION_TRACKING).copy(fontSize = 10.5.sp),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RowAction(Icons.Filled.Close, stringResource(Res.string.calendar_remove_row), true, onRemove)
    }
}

/**
 * The per-row planned length: a quiet label until it is clicked, then a field.
 *
 * The design's behaviour, and worth keeping — a row of always-on input boxes down the right of the
 * list reads as a form, whereas the run of show is something you scan.
 *
 * Two things here are less obvious than they look:
 *
 * - **The first unfocused callback is ignored.** `onFocusChanged` fires once with `isFocused=false`
 *   as the field attaches, *before* [FocusRequester.requestFocus] can land — so reacting to it
 *   closed the field the instant it opened. Only a loss that follows a real focus counts.
 * - **The value is committed when the edit ends, not per keystroke.** Every commit rewrites the
 *   document and saves `calendar.json`; doing that per character writes the file a dozen times for
 *   one duration and churns the row the field lives in.
 */
@Composable
private fun DurationControl(seconds: Int?, onChange: (Int?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var editing by remember { mutableStateOf(false) }

    if (!editing) {
        Hint(stringResource(Res.string.calendar_duration_tip)) {
            Box(
                Modifier
                    .height(CalendarMetrics.rowAction)
                    .clip(CalendarMetrics.smallRadius)
                    .clickable { editing = true }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatDuration(seconds),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                    color = scheme.onSurfaceVariant.copy(alpha = if (seconds == null) DIM_ALPHA else 1f),
                    maxLines = 1,
                )
            }
        }
        return
    }

    val focusRequester = remember { FocusRequester() }
    var text by remember { mutableStateOf(seconds?.let { formatDuration(it) } ?: "") }
    var everFocused by remember { mutableStateOf(false) }
    // Closing the cell takes the focus with it, and losing focus is itself a commit -- so Escape
    // would discard the text and then save it on the way out. The first call to finish decides.
    var finished by remember { mutableStateOf(false) }

    fun finish(commit: Boolean) {
        if (finished) return
        finished = true
        if (commit) onChange(if (text.isBlank()) null else parseDuration(text))
        editing = false
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        Modifier
            .width(CalendarMetrics.durationFieldWidth)
            .height(20.dp)
            .clip(CalendarMetrics.smallRadius)
            .background(scheme.surface)
            .border(1.dp, scheme.primary, CalendarMetrics.smallRadius)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isEmpty()) {
            Text(
                text = stringResource(Res.string.calendar_duration_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                color = scheme.onSurfaceVariant.copy(alpha = HINT_ALPHA),
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 9.5.sp,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(scheme.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        everFocused = true
                    } else if (everFocused) {
                        finish(commit = true)
                    }
                }
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                            finish(commit = true)
                            true
                        }
                        event.key == Key.Escape -> {
                            finish(commit = false)
                            true
                        }
                        else -> false
                    }
                },
        )
    }
}

/** The full-width sunken well that ends the list — a click or a drop adds an item. */
@Composable
private fun AddItemButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(32.dp)
            .sunken(CalendarMetrics.rowRadius, elevationPalette())
            .clickable(onClick = onClick),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(13.dp))
        Text(
            text = stringResource(Res.string.calendar_add_item),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}

/**
 * The empty state for a day with no services — the design's icon, two lines and two buttons.
 */
@Composable
fun NoServicesPane(
    dayLabel: String,
    copyLabel: String?,
    onAddService: () -> Unit,
    onCopyLast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceVariant.copy(alpha = ROW_ALPHA))
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.calendar_nothing_planned_for, dayLabel),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(Res.string.calendar_empty_hint),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = scheme.onSurfaceVariant.copy(alpha = HINT_ALPHA),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            AddServiceButton(onClick = onAddService)
            if (copyLabel != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(CalendarMetrics.addServiceButtonHeight)
                        .raisedKey(CalendarMetrics.buttonRadius, elevationPalette().key, onClick = onCopyLast)
                        .padding(horizontal = 13.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.calendar_copy_last, copyLabel),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = elevationPalette().key.ink,
                        // The label carries a service name somebody typed, so it has no natural
                        // length limit — cap it rather than let one long name stretch the button
                        // past the pane.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = COPY_LABEL_MAX),
                    )
                }
            }
        }
    }
}

private const val SECTION_TRACKING = 0.07f

