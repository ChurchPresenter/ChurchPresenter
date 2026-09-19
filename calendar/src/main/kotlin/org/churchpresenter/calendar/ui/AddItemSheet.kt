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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import org.churchpresenter.calendar.generated.resources.calendar_preview_toggle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.churchpresenter.calendar.generated.resources.calendar_edit_song
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import org.churchpresenter.calendar.generated.resources.calendar_no_results
import org.churchpresenter.calendar.generated.resources.calendar_pick_adds_to
import org.churchpresenter.calendar.generated.resources.calendar_pick_all_bible_books
import org.churchpresenter.calendar.generated.resources.calendar_pick_all_books
import org.churchpresenter.calendar.generated.resources.calendar_pick_add_range
import org.churchpresenter.calendar.generated.resources.calendar_pick_add_tip
import org.churchpresenter.calendar.generated.resources.calendar_pick_verse_hint
import org.churchpresenter.calendar.generated.resources.calendar_pick_bible
import org.churchpresenter.calendar.generated.resources.calendar_pick_chapters
import org.churchpresenter.calendar.generated.resources.calendar_pick_choose_chapter
import org.churchpresenter.calendar.generated.resources.calendar_pick_empty_hint
import org.churchpresenter.calendar.generated.resources.calendar_pick_no_bible
import org.churchpresenter.calendar.generated.resources.calendar_pick_reference
import org.churchpresenter.calendar.generated.resources.calendar_pick_presets
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_all
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_announcements
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_lower_thirds
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_media
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_other
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_presentations
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_scenes
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_slides
import org.churchpresenter.calendar.generated.resources.calendar_preset_kind_timers
import org.churchpresenter.calendar.generated.resources.calendar_cue_filter_presets
import org.churchpresenter.calendar.generated.resources.calendar_presets_empty
import org.churchpresenter.calendar.generated.resources.calendar_presets_empty_sub
import org.churchpresenter.calendar.generated.resources.calendar_pick_search
import org.churchpresenter.calendar.generated.resources.calendar_pick_section
import org.churchpresenter.calendar.generated.resources.calendar_pick_songs
import org.churchpresenter.calendar.generated.resources.calendar_pick_whole_chapter
import org.churchpresenter.calendar.generated.resources.calendar_section_new
import org.churchpresenter.calendar.generated.resources.calendar_songs_loading
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.calendar.model.bibleVerseItem
import org.churchpresenter.calendar.model.parseReference
import org.churchpresenter.calendar.model.sectionItem
import org.churchpresenter.calendar.model.toScheduleItem
import org.churchpresenter.calendar.model.asRow
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.calendar.generated.resources.calendar_settings_done
import org.churchpresenter.calendar.generated.resources.calendar_saved_as_you_change
import org.churchpresenter.calendar.generated.resources.calendar_section_no_timing
import org.churchpresenter.calendar.generated.resources.calendar_editing_row
import androidx.compose.material3.HorizontalDivider
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.songs.SongItem
import org.jetbrains.compose.resources.stringResource

/** The verse range two taps describe, in ascending order, or null when nothing is selected. */
private fun verseRange(anchor: Int?, extent: Int?): IntRange? {
    if (anchor == null) return null
    val other = extent ?: anchor
    return minOf(anchor, other)..maxOf(anchor, other)
}

/** Which source the picker is showing. */
private enum class PickKind { SONGS, BIBLE, SECTION, PRESETS }

/** The tab a row of this kind would have come from. */
private fun pickKindOf(item: ScheduleItem): PickKind = when (item) {
    is ScheduleItem.SongItem -> PickKind.SONGS
    is ScheduleItem.BibleVerseItem -> PickKind.BIBLE
    is ScheduleItem.LabelItem -> PickKind.SECTION
    else -> PickKind.PRESETS
}

/**
 * Wide enough for the timing panel's longest row to stay on one line.
 *
 * `Starts` now carries Cued, After previous, seven offsets, On time, a typed offset and a typed
 * time; wrapped over two lines they stop reading as one choice of many. Still well inside the
 * 1280dp the Calendar Manager window opens at.
 */
private val SHEET_WIDTH = 960.dp
private val BODY_HEIGHT = 230.dp
private val RESULT_ICON = 24.dp
private val ADD_BADGE = 21.dp
private const val DEFAULT_SECTION_COLOR = "#5B9DF5"
private val SCOPE_LIST_HEIGHT = 196.dp

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
    /** What a preset's preview can draw with; see [PreviewSources]. */
    previewSources: PreviewSources = PreviewSources(),
) {
    val scope = rememberCoroutineScope()
    var editingSong by remember { mutableStateOf<SongItem?>(null) }
    // Editing a row, the picker opens on that row's kind, so a replacement is one click away.
    var kind by remember(replacing) { mutableStateOf(replacing?.let(::pickKindOf) ?: PickKind.SONGS) }
    var query by remember { mutableStateOf("") }
    var songBook by remember { mutableStateOf<String?>(null) }
    // Which kind of preset the Presets tab is narrowed to, or null for all of them.
    var presetKind by remember { mutableStateOf<PresetKind?>(null) }
    val use24Hour = LocalUse24HourClock.current
    var draft by remember(replacing) { mutableStateOf(TimingDraft.of(timing, plannedSeconds, use24Hour)) }
    // Editing a row, a change lands on it at once; adding, it waits for the pick.
    fun changeTiming(next: TimingDraft) {
        draft = next
        if (replacing != null) onTimingChange(next.toTiming(), next.runSeconds())
    }
    var book by remember { mutableStateOf<CalendarBibleBook?>(null) }
    var chapter by remember { mutableStateOf<Int?>(null) }
    // The verse range being built: the first tap anchors it, a second tap extends it.
    var anchor by remember { mutableStateOf<Int?>(null) }
    var extent by remember { mutableStateOf<Int?>(null) }

    val planned = draft.runSeconds()
    val add: (List<ScheduleItem>) -> Unit = { items -> onAdd(items, planned, draft.toTiming()) }
    // A section heading is structure, not something that goes on screen: nothing about it starts,
    // runs or ends, so the timing panel is shown for what it is -- inert -- and the footer says why.
    val isSection = replacing is ScheduleItem.LabelItem || kind == PickKind.SECTION

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
                val selection = verseRange(anchor, extent)
                val pending = if (kind == PickKind.BIBLE && book != null && chapter != null && selection != null) {
                    bibleVerseItem(book!!.bookId, book!!.name, chapter!!, selection.first, selection.last)
                } else {
                    null
                }
                // What the panel says, read back; and where a pick lands while adding.
                Text(
                    text = when {
                        isSection -> stringResource(Res.string.calendar_section_no_timing)
                        replacing == null ->
                            timingSummary(draft) + " · " + stringResource(Res.string.calendar_pick_adds_to, serviceName)
                        else -> timingSummary(draft)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (replacing != null) {
                    PrimaryButton(label = stringResource(Res.string.calendar_settings_done), onClick = onDismiss)
                }
                if (pending != null) {
                    PrimaryButton(
                        label = stringResource(Res.string.calendar_pick_add_range, pending.displayText),
                        onClick = {
                            add(listOf(pending))
                            anchor = null
                            extent = null
                        },
                    )
                }
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                CompactTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = searchPlaceholder(kind),
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
                        PickChip(label = pickKindLabel(entry), selected = kind == entry) {
                            kind = entry
                            query = ""
                            book = null
                            chapter = null
                            anchor = null
                            extent = null
                        }
                    }
                }
                if (kind == PickKind.SONGS && songs.isNotEmpty()) {
                    SongBookScope(songs = songs, selected = songBook) { songBook = it }
                }
                if (kind == PickKind.PRESETS && presets.isNotEmpty()) {
                    PresetKindScope(presets = presets, selected = presetKind) { presetKind = it }
                }
                if (kind == PickKind.BIBLE && bibleBooks.isNotEmpty()) {
                    BibleCrumbs(
                        book = book,
                        chapter = chapter,
                        onAllBooks = { book = null; chapter = null; anchor = null; extent = null },
                        onBook = { chapter = null; anchor = null; extent = null },
                        wholeChapter = chapter?.let { current ->
                            {
                                anchor = 1
                                extent = book?.verseCount(current) ?: 1
                            }
                        },
                    )
                }
            }

            Box(Modifier.heightIn(min = BODY_HEIGHT, max = BODY_HEIGHT).padding(horizontal = 14.dp)) {
                when (kind) {
                    PickKind.SONGS -> SongResults(
                        songs = songs,
                        songsLoaded = songsLoaded,
                        songBook = songBook,
                        query = query,
                        onAdd = add,
                        onEditSong = if (songEditor != null) {
                            { editingSong = it }
                        } else {
                            null
                        },
                    )
                    PickKind.BIBLE -> BibleResults(
                        books = bibleBooks,
                        query = query,
                        book = book,
                        chapter = chapter,
                        selection = verseRange(anchor, extent),
                        onBook = { book = it },
                        onChapter = { chapter = it; anchor = null; extent = null },
                        onVerse = { verse ->
                            when {
                                anchor == null -> { anchor = verse; extent = verse }
                                // Tapping the only selected verse again clears it.
                                anchor == verse && extent == verse -> { anchor = null; extent = null }
                                else -> extent = verse
                            }
                        },
                        onAdd = add,
                    )
                    PickKind.SECTION -> SectionResults(sections, query, add)
                    PickKind.PRESETS -> PresetResults(presets, presetKind, query, previewSources, add)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TimingPanel(
                draft = draft,
                serviceStartTime = serviceStartTime,
                onChange = ::changeTiming,
                enabled = !isSection,
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
    PickKind.PRESETS -> stringResource(Res.string.calendar_cue_filter_presets)
}

@Composable
private fun pickKindLabel(kind: PickKind): String = stringResource(
    when (kind) {
        PickKind.SONGS -> Res.string.calendar_pick_songs
        PickKind.BIBLE -> Res.string.calendar_pick_bible
        PickKind.SECTION -> Res.string.calendar_pick_section
        PickKind.PRESETS -> Res.string.calendar_pick_presets
    }
)

@Composable
private fun PickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) scheme.primary.copy(alpha = 0.55f) else scheme.outlineVariant,
                shape = RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

/** The kinds a preset can be, for narrowing the Presets tab -- one per content type it can hold. */
private enum class PresetKind { SLIDES, PRESENTATIONS, MEDIA, TIMERS, ANNOUNCEMENTS, SCENES, LOWER_THIRDS, OTHER }

private fun presetKindOf(item: ScheduleItem): PresetKind = when (item) {
    is ScheduleItem.PictureItem -> PresetKind.SLIDES
    is ScheduleItem.PresentationItem -> PresetKind.PRESENTATIONS
    is ScheduleItem.MediaItem -> PresetKind.MEDIA
    is ScheduleItem.AnnouncementItem -> if (item.isTimer) PresetKind.TIMERS else PresetKind.ANNOUNCEMENTS
    is ScheduleItem.SceneItem -> PresetKind.SCENES
    is ScheduleItem.LowerThirdItem -> PresetKind.LOWER_THIRDS
    else -> PresetKind.OTHER
}

@Composable
private fun presetKindLabel(kind: PresetKind): String = stringResource(
    when (kind) {
        PresetKind.SLIDES -> Res.string.calendar_preset_kind_slides
        PresetKind.PRESENTATIONS -> Res.string.calendar_preset_kind_presentations
        PresetKind.MEDIA -> Res.string.calendar_preset_kind_media
        PresetKind.TIMERS -> Res.string.calendar_preset_kind_timers
        PresetKind.ANNOUNCEMENTS -> Res.string.calendar_preset_kind_announcements
        PresetKind.SCENES -> Res.string.calendar_preset_kind_scenes
        PresetKind.LOWER_THIRDS -> Res.string.calendar_preset_kind_lower_thirds
        PresetKind.OTHER -> Res.string.calendar_preset_kind_other
    }
)

/**
 * The preset kind chips: `All` and one per kind that has a preset, each with its count. Only the
 * kinds present -- a row of eight chips for a library of three slideshows says nothing.
 */
@Composable
private fun PresetKindScope(presets: List<ItemPreset>, selected: PresetKind?, onSelect: (PresetKind?) -> Unit) {
    val counts = remember(presets) { presets.groupingBy { presetKindOf(it.item) }.eachCount() }
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PickChip(
            label = stringResource(Res.string.calendar_preset_kind_all) + "  " + presets.size,
            selected = selected == null,
        ) { onSelect(null) }
        PresetKind.entries.forEach { kind ->
            val count = counts[kind] ?: return@forEach
            PickChip(label = presetKindLabel(kind) + "  " + count, selected = selected == kind) {
                onSelect(if (selected == kind) null else kind)
            }
        }
    }
}

/** The song-book scope selector — a library of thousands is unusable without it. */
@Composable
private fun SongBookScope(songs: List<SongItem>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val counts by remember(songs) {
        derivedStateOf { songs.groupingBy { it.songbook }.eachCount().toList().sortedBy { it.first } }
    }
    val scheme = MaterialTheme.colorScheme
    val label = selected ?: stringResource(Res.string.calendar_pick_all_books)
    val count = if (selected == null) songs.size else counts.firstOrNull { it.first == selected }?.second ?: 0

    Column {
        SettingCard(horizontalPadding = 10.dp, verticalPadding = 7.dp, modifier = Modifier.clickable { open = !open }) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = scheme.onSurfaceVariant,
            )
        }
        // `if (open) { ... }`, never an early `return` here: a bare return inside Column's inline
        // lambda is a *non-local* return out of a composable lambda, which leaves the composition's
        // group structure unbalanced. An early return at the top of a composable function body is
        // fine; one from inside a layout's content is not.
        if (open) {
            // A list, not a Column: capping a Column's height does not make it scroll, so a
            // library with more song books than fit simply lost the ones past the cap.
            ScrollableList(
                modifier = Modifier.padding(top = 4.dp).heightIn(max = SCOPE_LIST_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "all") {
                    ScopeRow(stringResource(Res.string.calendar_pick_all_books), songs.size, selected == null) {
                        onSelect(null)
                        open = false
                    }
                }
                items(counts, key = { it.first }) { (name, number) ->
                    ScopeRow(name, number, selected == name) {
                        onSelect(name)
                        open = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeRow(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SongResults(
    songs: List<SongItem>,
    songsLoaded: Boolean,
    songBook: String?,
    query: String,
    onAdd: (List<ScheduleItem>) -> Unit,
    onEditSong: ((SongItem) -> Unit)?,
) {
    val scoped = remember(songs, songBook) { songs.filter { songBook == null || it.songbook == songBook } }
    val matches by remember(scoped, query) { derivedStateOf { matchSongs(scoped, query) } }
    val reference = remember(query) { parseReference(query) }

    when {
        !songsLoaded -> EmptyBody(stringResource(Res.string.calendar_songs_loading), "")
        matches.isEmpty() && reference == null ->
            EmptyBody(
                stringResource(Res.string.calendar_no_results),
                stringResource(Res.string.calendar_pick_empty_hint),
            )

        else -> ScrollableList(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // A reference typed into the song box is still a reference — offering it here saves
            // switching tabs to add the passage somebody just typed out in full.
            if (reference != null) {
                item(key = "reference") {
                    ResultRow(
                        title = reference.display,
                        subtitle = "",
                        badge = stringResource(Res.string.calendar_pick_reference),
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onAdd(listOf(reference.toScheduleItem())) },
                    )
                }
            }
            itemsIndexed(
                matches,
                key = { index, song -> song.sourceFile.ifBlank { "$index:${song.songId}" } },
            ) { _, song ->
                ResultRow(
                    title = if (song.number.isNotBlank()) "${song.number} - ${song.title}" else song.title,
                    subtitle = song.songbook,
                    onClick = { onAdd(listOf(song.toScheduleItem())) },
                    onEdit = if (onEditSong != null) {
                        { onEditSong(song) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionResults(sections: List<SectionStyle>, query: String, onAdd: (List<ScheduleItem>) -> Unit) {
    val trimmed = query.trim()
    val matches = sections.filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }
    val isNew = trimmed.isNotEmpty() && sections.none { it.name.equals(trimmed, ignoreCase = true) }

    if (matches.isEmpty() && !isNew) {
        EmptyBody(stringResource(Res.string.calendar_no_results), stringResource(Res.string.calendar_pick_empty_hint))
        return
    }
    ScrollableList(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(matches, key = { it.name }) { section ->
            ResultRow(
                title = section.name,
                subtitle = "",
                color = parseHex(section.colorHex),
                onClick = { onAdd(listOf(sectionItem(section.name, section.colorHex))) },
            )
        }
        if (isNew) {
            item(key = "new") {
                ResultRow(
                    title = trimmed,
                    subtitle = stringResource(Res.string.calendar_section_new),
                    color = parseHex(DEFAULT_SECTION_COLOR),
                    onClick = { onAdd(listOf(sectionItem(trimmed, DEFAULT_SECTION_COLOR))) },
                )
            }
        }
    }
}

/**
 * The saved presets, newest first, narrowed to one [kind] and to what the query matches -- the
 * preset's name, or the item's own text, so a scene preset renamed "Opener" is still found by
 * typing the scene's name.
 */
@Composable
private fun PresetResults(
    presets: List<ItemPreset>,
    kind: PresetKind?,
    query: String,
    previewSources: PreviewSources,
    onAdd: (List<ScheduleItem>) -> Unit,
) {
    // The one preset opened to show what it puts on screen; opening another closes it.
    var expanded by remember { mutableStateOf<String?>(null) }
    if (presets.isEmpty()) {
        EmptyBody(
            stringResource(Res.string.calendar_presets_empty),
            stringResource(Res.string.calendar_presets_empty_sub),
        )
        return
    }
    val q = query.trim()
    val shown = presets.filter { preset ->
        (kind == null || presetKindOf(preset.item) == kind) &&
            (q.isEmpty() || preset.name.contains(q, true) || preset.item.displayText.contains(q, true))
    }
    if (shown.isEmpty()) {
        EmptyBody(stringResource(Res.string.calendar_no_results), "")
        return
    }
    ScrollableList(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        itemsIndexed(shown, key = { _, preset -> preset.id }) { _, preset ->
            val look = lookFor(preset.item)
            val open = expanded == preset.id
            Column {
                ResultRow(
                    title = preset.name,
                    subtitle = preset.item.displayText,
                    color = look.color,
                    onClick = { onAdd(listOf(preset.asRow())) },
                    expanded = open,
                    onToggle = { expanded = if (open) null else preset.id },
                )
                if (open) PresetPreview(item = preset.item, sources = previewSources)
            }
        }
    }
}

@Composable
private fun EmptyBody(title: String, hint: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = scheme.onSurfaceVariant,
        )
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/**
 * One result — the design's row: an icon badge, a title with an optional badge, a second line, and
 * the add affordance on the right.
 */
@Composable
private fun ResultRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: String? = null,
    color: Color? = null,
    onEdit: (() -> Unit)? = null,
    /** With [onToggle], the row carries a caret that opens a preview beneath it. */
    expanded: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = color ?: scheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(SheetMetrics.cardRadius)
            .background(scheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), SheetMetrics.cardRadius)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier.size(RESULT_ICON).clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(scheme.primary.copy(alpha = 0.18f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    ) {
                        Text(
                            text = badge.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                            ),
                            color = scheme.primary,
                        )
                    }
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onEdit != null) {
            SmallIconButton(
                icon = Icons.Filled.Edit,
                description = stringResource(Res.string.calendar_edit_song),
                onClick = onEdit,
                size = ADD_BADGE,
            )
        }
        if (onToggle != null) {
            SmallIconButton(
                icon = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                description = stringResource(Res.string.calendar_preview_toggle),
                onClick = onToggle,
                size = ADD_BADGE,
            )
        }
        val addTip = stringResource(Res.string.calendar_pick_add_tip)
        Hint(addTip) {
            Box(
                Modifier.size(ADD_BADGE).clip(RoundedCornerShape(6.dp)).background(scheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = addTip,
                    tint = scheme.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** The trail back out of a book and a chapter, as the design draws it. */
@Composable
private fun BibleCrumbs(
    book: CalendarBibleBook?,
    chapter: Int?,
    onAllBooks: () -> Unit,
    onBook: () -> Unit,
    wholeChapter: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Crumb(stringResource(Res.string.calendar_pick_all_bible_books), onAllBooks)
        if (book != null) Crumb(book.name, onBook)
        if (chapter != null) Crumb(chapter.toString()) {}
        // The hint carries the weight, not the button. A Row measures its unweighted children
        // first, so a full-width Text here took every remaining pixel and squeezed the button down
        // to a box its own label wrapped inside. Weighted, the hint is measured last and is the
        // thing that shrinks.
        Text(
            text = when {
                book == null -> ""
                chapter == null -> stringResource(Res.string.calendar_pick_choose_chapter)
                else -> stringResource(Res.string.calendar_pick_verse_hint)
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (wholeChapter != null) {
            QuietButton(
                label = stringResource(Res.string.calendar_pick_whole_chapter),
                onClick = wholeChapter,
                height = 23.dp,
                accent = true,
            )
        }
    }
}

@Composable
private fun Crumb(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(23.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
    }
}

/**
 * Scripture, browsed: books, then that book's chapters, then that chapter's verses.
 *
 * Every step is a grid of real values taken from the loaded Bible, so a chapter that has 25 verses
 * offers 25 — there is nothing to mistype and nothing to look up. Picking a chapter and stopping
 * adds the whole chapter; picking a verse adds that verse.
 */
@Composable
private fun BibleResults(
    books: List<CalendarBibleBook>,
    query: String,
    book: CalendarBibleBook?,
    chapter: Int?,
    selection: IntRange?,
    onBook: (CalendarBibleBook) -> Unit,
    onChapter: (Int) -> Unit,
    onVerse: (Int) -> Unit,
    onAdd: (List<ScheduleItem>) -> Unit,
) {
    val reference = remember(query) { parseReference(query) }

    if (books.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (reference != null) {
                ResultRow(
                    title = reference.display,
                    subtitle = "",
                    badge = stringResource(Res.string.calendar_pick_reference),
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onAdd(listOf(reference.toScheduleItem())) },
                )
            }
            EmptyBody(stringResource(Res.string.calendar_pick_no_bible), "")
        }
        return
    }

    when {
        book == null -> {
            val trimmed = query.trim()
            val matches = books.filter { trimmed.isEmpty() || it.name.contains(trimmed, ignoreCase = true) }
            ScrollableGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // A fully typed reference still wins: it is more specific than any book tile.
                if (reference != null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "reference") {
                        ResultRow(
                            title = reference.display,
                            subtitle = "",
                            badge = stringResource(Res.string.calendar_pick_reference),
                            color = MaterialTheme.colorScheme.primary,
                            onClick = { onAdd(listOf(reference.toScheduleItem())) },
                        )
                    }
                }
                items(matches, key = { it.bookId }) { entry ->
                    BookTile(entry) { onBook(entry) }
                }
            }
        }

        chapter == null -> NumberGrid((1..book.chapterCount).toList(), selected = null, onPick = onChapter)

        else -> NumberGrid((1..book.verseCount(chapter)).toList(), selected = selection, onPick = onVerse)
    }
}

@Composable
private fun BookTile(book: CalendarBibleBook, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
    ) {
        Text(
            text = book.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(Res.string.calendar_pick_chapters, book.chapterCount),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The chapter and verse grids — same tile, different numbers.
 *
 * [selected] highlights a run of verses. Taps build it rather than committing straight away: the
 * first anchors the range, the second extends it, and the footer's Add is what commits it — so a
 * passage goes on as **one** row carrying `16-18`, which is what the Bible tab and the presenter
 * both understand, rather than three separate rows.
 */
@Composable
private fun NumberGrid(values: List<Int>, selected: IntRange?, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ScrollableGrid(
        columns = GridCells.Adaptive(40.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(values, key = { it }) { value ->
            val on = selected?.contains(value) == true
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) scheme.primary else scheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(
                        width = 1.dp,
                        color = if (on) scheme.primary else scheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(7.dp),
                    )
                    .clickable { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) scheme.onPrimary else scheme.onSurface,
                )
            }
        }
    }
}
