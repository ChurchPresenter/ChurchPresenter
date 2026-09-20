package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_add_item
import org.churchpresenter.calendar.generated.resources.calendar_bible_hint_short
import org.churchpresenter.calendar.generated.resources.calendar_section_hint
import org.churchpresenter.calendar.generated.resources.calendar_pick_adds_to
import org.churchpresenter.calendar.generated.resources.calendar_pick_add_range
import org.churchpresenter.calendar.generated.resources.calendar_pick_replace_with
import org.churchpresenter.calendar.generated.resources.calendar_pick_bible
import org.churchpresenter.calendar.generated.resources.calendar_pick_ministry
import org.churchpresenter.calendar.generated.resources.calendar_pick_presets
import org.churchpresenter.calendar.generated.resources.calendar_cue_filter_presets
import org.churchpresenter.calendar.generated.resources.calendar_pick_search
import org.churchpresenter.calendar.generated.resources.calendar_pick_section
import org.churchpresenter.calendar.generated.resources.calendar_pick_songs
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.calendar.generated.resources.calendar_settings_done
import org.churchpresenter.calendar.generated.resources.calendar_saved_as_you_change
import org.churchpresenter.calendar.generated.resources.calendar_ministry_no_timing
import org.churchpresenter.calendar.generated.resources.calendar_section_no_timing
import org.churchpresenter.calendar.generated.resources.calendar_editing_row
import androidx.compose.material3.HorizontalDivider
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.songs.SongItem
import org.jetbrains.compose.resources.stringResource

/**
 * Wide enough for the timing panel's longest row to stay on one line.
 *
 * `Starts` now carries Cued, After previous, seven offsets, On time, a typed offset and a typed
 * time; wrapped over two lines they stop reading as one choice of many. Still well inside the
 * 1280dp the Calendar Manager window opens at.
 */
private val SHEET_WIDTH = 960.dp
private val BODY_HEIGHT = 230.dp

/**
 * What can be put into a run of show.
 *
 * Laid out to the design: a search field and kind chips above a scrolling body, and a footer saying
 * where the item lands with a duration to give it on the way in.
 *
 * Scripture is browsed rather than typed — book grid, then chapters, then verses, with breadcrumbs
 * back — because an empty list under a text box tells nobody what they can pick. A typed reference
 * still works and appears as its own result; the two are not alternatives.
 */
@Composable
fun AddItemSheet(
    songs: List<SongItem>,
    songsLoaded: Boolean,
    presets: List<ItemPreset>,
    sections: List<SectionStyle>,
    bibleBooks: List<CalendarBibleBook>,
    serviceName: String,
    /** The service's start, which the timing panel offers as a start time. */
    serviceStartTime: String,
    /** The row being edited -- replaced or retimed -- or null when the picker is appending. */
    replacing: ScheduleItem?,
    songbooks: List<String>,
    songEditor: (@Composable (SongEditRequest) -> Unit)?,
    onSaveSong: suspend (original: SongItem, edited: SongItem) -> Unit,
    onAdd: (items: List<ScheduleItem>, plannedSeconds: Int?, timing: RowTiming) -> Unit,
    onDismiss: () -> Unit,
    /** How [replacing] runs today, and its planned length -- what the timing panel opens showing. */
    timing: RowTiming = RowTiming.DEFAULT,
    plannedSeconds: Int? = null,
    /** A timing change on [replacing], applied as it is made -- the row is saved as you change it. */
    onTimingChange: (timing: RowTiming, plannedSeconds: Int?) -> Unit = { _, _ -> },
    /** What [replacing] has actually taken on screen, offered in the timing panel; null when unknown. */
    measuredSeconds: Int? = null,
    /** What a preset's preview can draw with; see [PreviewSources]. */
    previewSources: PreviewSources = PreviewSources(),
) {
    val scope = rememberCoroutineScope()
    var editingSong by remember { mutableStateOf<SongItem?>(null) }
    // Editing a row, the picker opens on that row -- its kind, and its book, chapter and verses
    // or its title -- so a replacement is one step away. Rebuilt if the Bible arrives after the
    // sheet opened, since the verse row cannot be found in an empty book list.
    val picker = remember(replacing, bibleBooks.isEmpty()) { pickerFor(replacing, bibleBooks, plannedSeconds) }
    val use24Hour = LocalUse24HourClock.current
    var draft by remember(replacing) { mutableStateOf(TimingDraft.of(timing, plannedSeconds, use24Hour)) }
    // Editing a row, a change lands on it at once; adding, it waits for the pick.
    fun changeTiming(next: TimingDraft) {
        draft = next
        if (replacing != null) onTimingChange(next.toTiming(), next.runSeconds())
    }

    // The length a pick goes on with: the timing panel's, or -- on the ministry tab, where the
    // panel is off -- the form's own duration field.
    val planned = if (picker.kind == PickKind.MINISTRY) picker.ministrySeconds() else draft.runSeconds()
    val add: (List<ScheduleItem>) -> Unit = { items -> onAdd(items, planned, draft.toTiming()) }
    // A section heading is structure, not something that goes on screen: nothing about it starts,
    // runs or ends, so the timing panel is shown for what it is -- inert -- and the footer says why.
    // A ministry item happens up front and never on screen, so the same: its length is typed on
    // the row, and there is nothing for the engine to start, repeat or end.
    val isSection = replacing is ScheduleItem.LabelItem || picker.kind == PickKind.SECTION
    val isMinistry = replacing is ScheduleItem.MinistryItem || picker.kind == PickKind.MINISTRY
    val noTiming = isSection || isMinistry

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetScaffold(
            title = if (replacing == null) {
                stringResource(Res.string.calendar_add_item)
            } else {
                stringResource(Res.string.calendar_editing_row, replacing.displayText)
            },
            subtitle = if (replacing == null) null else stringResource(Res.string.calendar_saved_as_you_change),
            icon = if (replacing == null) Icons.Filled.Add else lookFor(replacing).icon,
            width = SHEET_WIDTH,
            onDismiss = onDismiss,
            footer = {
                PickerFooter(
                    // What the panel says, read back; and where a pick lands while adding.
                    summary = when {
                        isSection -> stringResource(Res.string.calendar_section_no_timing)
                        isMinistry -> stringResource(Res.string.calendar_ministry_no_timing)
                        replacing == null ->
                            timingSummary(draft) + " · " + stringResource(Res.string.calendar_pick_adds_to, serviceName)
                        else -> timingSummary(draft)
                    },
                    // What the footer offers to add: the verse range built on the Bible tab, or
                    // the ministry item typed on its tab -- the two tabs where the pick is
                    // assembled rather than clicked.
                    pending = picker.pendingVerses ?: picker.pendingMinistry,
                    onDone = if (replacing != null) onDismiss else null,
                    onAddPending = {
                        add(listOf(it))
                        picker.clearVerses()
                    },
                    replacing = replacing != null,
                )
            },
        ) {
            PickerHead(picker, songs, presets, bibleBooks)
            Box(Modifier.heightIn(min = BODY_HEIGHT, max = BODY_HEIGHT).padding(horizontal = 14.dp)) {
                PickerBody(
                    picker = picker,
                    songs = songs,
                    songsLoaded = songsLoaded,
                    presets = presets,
                    sections = sections,
                    bibleBooks = bibleBooks,
                    previewSources = previewSources,
                    onAdd = add,
                    onEditSong = if (songEditor != null) {
                        { editingSong = it }
                    } else {
                        null
                    },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TimingPanel(
                draft = draft,
                serviceStartTime = serviceStartTime,
                onChange = ::changeTiming,
                enabled = !noTiming,
                measuredSeconds = measuredSeconds,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }

    val editing = editingSong
    SongEditorHost(
        editing = editing,
        songs = songs,
        songbooks = songbooks,
        songEditor = songEditor,
        onSave = { edited ->
            if (editing != null) scope.launch { onSaveSong(editing, edited) }
            editingSong = null
        },
        onDismiss = { editingSong = null },
    )
}

/**
 * The sheet's footer: the timing summary, then Done while editing a row, and the verse range built
 * on the Bible tab once there is one.
 */
@Composable
private fun RowScope.PickerFooter(
    summary: String,
    pending: ScheduleItem?,
    onDone: (() -> Unit)?,
    onAddPending: (ScheduleItem) -> Unit,
    /** True while a row is being edited: the built range then *replaces* it, and the button says so. */
    replacing: Boolean = false,
) {
    Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    if (onDone != null) {
        PrimaryButton(label = stringResource(Res.string.calendar_settings_done), onClick = onDone)
    }
    if (pending != null) {
        PrimaryButton(
            label = stringResource(
                if (replacing) Res.string.calendar_pick_replace_with else Res.string.calendar_pick_add_range,
                pending.displayText,
            ),
            onClick = { onAddPending(pending) },
        )
    }
}

/** The search field, the kind chips and, under them, whichever scope row the kind has. */
@Composable
private fun PickerHead(
    picker: PickerState,
    songs: List<SongItem>,
    presets: List<ItemPreset>,
    bibleBooks: List<CalendarBibleBook>,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        // The ministry tab has nothing to search: its fields are the item, and live in the body.
        if (picker.kind != PickKind.MINISTRY) CompactTextField(
            value = picker.query,
            onValueChange = { picker.query = it },
            placeholder = searchPlaceholder(picker.kind),
            height = 34.dp,
            fontSize = 12.5f,
            focused = true,
            modifier = Modifier.fillMaxWidth(),
            leading = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp).padding(end = 2.dp),
                )
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PickKind.entries.forEach { entry ->
                PickChip(label = pickKindLabel(entry), selected = picker.kind == entry) { picker.showKind(entry) }
            }
        }
        if (picker.kind == PickKind.SONGS && songs.isNotEmpty()) {
            SongBookScope(songs = songs, selected = picker.songBook) { picker.songBook = it }
        }
        if (picker.kind == PickKind.PRESETS && presets.isNotEmpty()) {
            PresetKindScope(presets = presets, selected = picker.presetKind) { picker.presetKind = it }
        }
        if (picker.kind == PickKind.BIBLE && bibleBooks.isNotEmpty()) {
            BibleCrumbs(
                book = picker.book,
                chapter = picker.chapter,
                onAllBooks = picker::showAllBooks,
                onBook = picker::showBook,
                wholeChapter = if (picker.chapter == null) null else picker::selectWholeChapter,
            )
        }
    }
}

/** The results of whichever tab is showing. */
@Composable
private fun PickerBody(
    picker: PickerState,
    songs: List<SongItem>,
    songsLoaded: Boolean,
    presets: List<ItemPreset>,
    sections: List<SectionStyle>,
    bibleBooks: List<CalendarBibleBook>,
    previewSources: PreviewSources,
    onAdd: (List<ScheduleItem>) -> Unit,
    onEditSong: ((SongItem) -> Unit)?,
) {
    when (picker.kind) {
        PickKind.SONGS -> SongResults(
            songs = songs,
            songsLoaded = songsLoaded,
            songBook = picker.songBook,
            query = picker.query,
            onAdd = onAdd,
            onEditSong = onEditSong,
        )
        PickKind.BIBLE -> BibleResults(
            books = bibleBooks,
            query = picker.query,
            book = picker.book,
            chapter = picker.chapter,
            selection = picker.selection,
            onBook = { picker.book = it },
            onChapter = picker::showChapter,
            onVerse = picker::tapVerse,
            onAdd = onAdd,
        )
        PickKind.SECTION -> SectionResults(sections, picker.query, onAdd)
        PickKind.MINISTRY -> MinistryResults(
            title = picker.query,
            onTitle = { picker.query = it },
            detail = picker.detail,
            onDetail = { picker.detail = it },
            duration = picker.duration,
            onDuration = { picker.duration = it },
            onAdd = onAdd,
        )
        PickKind.PRESETS -> PresetResults(presets, picker.presetKind, picker.query, previewSources, onAdd)
    }
}

/** The app's Edit Song dialog, opened from a result row's pencil. */
@Composable
private fun SongEditorHost(
    editing: SongItem?,
    songs: List<SongItem>,
    songbooks: List<String>,
    songEditor: (@Composable (SongEditRequest) -> Unit)?,
    onSave: (SongItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (editing == null || songEditor == null) return
    songEditor(
        SongEditRequest(
            song = editing,
            songbooks = songbooks,
            allSongs = songs,
            onSave = onSave,
            onDismiss = onDismiss,
        )
    )
}

@Composable
private fun searchPlaceholder(kind: PickKind): String = when (kind) {
    PickKind.SONGS -> stringResource(Res.string.calendar_pick_search)
    PickKind.BIBLE -> stringResource(Res.string.calendar_bible_hint_short)
    PickKind.SECTION -> stringResource(Res.string.calendar_section_hint)
    // The ministry tab draws no search field; its own fields carry their own hints.
    PickKind.MINISTRY -> ""
    PickKind.PRESETS -> stringResource(Res.string.calendar_cue_filter_presets)
}

@Composable
private fun pickKindLabel(kind: PickKind): String = stringResource(
    when (kind) {
        PickKind.SONGS -> Res.string.calendar_pick_songs
        PickKind.BIBLE -> Res.string.calendar_pick_bible
        PickKind.SECTION -> Res.string.calendar_pick_section
        PickKind.MINISTRY -> Res.string.calendar_pick_ministry
        PickKind.PRESETS -> Res.string.calendar_pick_presets
    }
)
