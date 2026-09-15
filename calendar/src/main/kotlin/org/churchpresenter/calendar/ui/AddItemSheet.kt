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
import org.churchpresenter.calendar.generated.resources.calendar_duration_hint
import org.churchpresenter.calendar.generated.resources.calendar_bible_hint_short
import org.churchpresenter.calendar.generated.resources.calendar_section_hint
import org.churchpresenter.calendar.generated.resources.calendar_no_results
import org.churchpresenter.calendar.generated.resources.calendar_pick_adds_to
import org.churchpresenter.calendar.generated.resources.calendar_pick_all_bible_books
import org.churchpresenter.calendar.generated.resources.calendar_pick_all_books
import org.churchpresenter.calendar.generated.resources.calendar_pick_add_range
import org.churchpresenter.calendar.generated.resources.calendar_pick_verse_hint
import org.churchpresenter.calendar.generated.resources.calendar_pick_bible
import org.churchpresenter.calendar.generated.resources.calendar_pick_chapters
import org.churchpresenter.calendar.generated.resources.calendar_pick_choose_chapter
import org.churchpresenter.calendar.generated.resources.calendar_pick_duration
import org.churchpresenter.calendar.generated.resources.calendar_pick_empty_hint
import org.churchpresenter.calendar.generated.resources.calendar_pick_no_bible
import org.churchpresenter.calendar.generated.resources.calendar_pick_reference
import org.churchpresenter.calendar.generated.resources.calendar_pick_schedule
import org.churchpresenter.calendar.generated.resources.calendar_pick_search
import org.churchpresenter.calendar.generated.resources.calendar_pick_section
import org.churchpresenter.calendar.generated.resources.calendar_pick_songs
import org.churchpresenter.calendar.generated.resources.calendar_pick_whole_chapter
import org.churchpresenter.calendar.generated.resources.calendar_schedule_empty
import org.churchpresenter.calendar.generated.resources.calendar_section_new
import org.churchpresenter.calendar.generated.resources.calendar_songs_loading
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.calendar.model.bibleVerseItem
import org.churchpresenter.calendar.model.parseDuration
import org.churchpresenter.calendar.model.parseReference
import org.churchpresenter.calendar.model.sectionItem
import org.churchpresenter.calendar.model.toScheduleItem
import org.churchpresenter.calendar.model.withNewId
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.jetbrains.compose.resources.stringResource

/** The verse range two taps describe, in ascending order, or null when nothing is selected. */
private fun verseRange(anchor: Int?, extent: Int?): IntRange? {
    if (anchor == null) return null
    val other = extent ?: anchor
    return minOf(anchor, other)..maxOf(anchor, other)
}

/** Which source the picker is showing. */
private enum class PickKind { SONGS, BIBLE, SECTION, SCHEDULE }

private val SHEET_WIDTH = 470.dp
private val BODY_HEIGHT = 330.dp
private val RESULT_ICON = 24.dp
private val ADD_BADGE = 21.dp
private val DURATION_FIELD = 62.dp
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
    currentSchedule: List<ScheduleItem>,
    sections: List<SectionStyle>,
    bibleBooks: List<CalendarBibleBook>,
    serviceName: String,
    onAdd: (items: List<ScheduleItem>, plannedSeconds: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var kind by remember { mutableStateOf(PickKind.SONGS) }
    var query by remember { mutableStateOf("") }
    var songBook by remember { mutableStateOf<String?>(null) }
    var durationText by remember { mutableStateOf("") }
    var book by remember { mutableStateOf<CalendarBibleBook?>(null) }
    var chapter by remember { mutableStateOf<Int?>(null) }
    // The verse range being built: the first tap anchors it, a second tap extends it.
    var anchor by remember { mutableStateOf<Int?>(null) }
    var extent by remember { mutableStateOf<Int?>(null) }

    val planned = parseDuration(durationText)
    val add: (List<ScheduleItem>) -> Unit = { items -> onAdd(items, planned) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetScaffold(
            title = stringResource(Res.string.calendar_add_item),
            icon = Icons.Filled.Add,
            width = SHEET_WIDTH,
            onDismiss = onDismiss,
            footer = {
                val selection = verseRange(anchor, extent)
                val pending = if (kind == PickKind.BIBLE && book != null && chapter != null && selection != null) {
                    bibleVerseItem(book!!.bookId, book!!.name, chapter!!, selection.first, selection.last)
                } else {
                    null
                }
                Text(
                    text = stringResource(Res.string.calendar_pick_adds_to, serviceName),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(Res.string.calendar_pick_duration),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompactTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    placeholder = stringResource(Res.string.calendar_duration_hint),
                    height = 27.dp,
                    fontSize = 11f,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(DURATION_FIELD),
                )
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
                    PickKind.SONGS -> SongResults(songs, songsLoaded, songBook, query, add)
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
                    PickKind.SCHEDULE -> ScheduleResults(currentSchedule, add)
                }
            }
        }
    }
}

@Composable
private fun searchPlaceholder(kind: PickKind): String = when (kind) {
    PickKind.SONGS -> stringResource(Res.string.calendar_pick_search)
    PickKind.BIBLE -> stringResource(Res.string.calendar_bible_hint_short)
    PickKind.SECTION -> stringResource(Res.string.calendar_section_hint)
    PickKind.SCHEDULE -> ""
}

@Composable
private fun pickKindLabel(kind: PickKind): String = stringResource(
    when (kind) {
        PickKind.SONGS -> Res.string.calendar_pick_songs
        PickKind.BIBLE -> Res.string.calendar_pick_bible
        PickKind.SECTION -> Res.string.calendar_pick_section
        PickKind.SCHEDULE -> Res.string.calendar_pick_schedule
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
) {
    val scoped = remember(songs, songBook) { songs.filter { songBook == null || it.songbook == songBook } }
    val matches by remember(scoped, query) { derivedStateOf { matchSongs(scoped, query) } }
    val reference = remember(query) { parseReference(query) }

    when {
        !songsLoaded -> EmptyBody(stringResource(Res.string.calendar_songs_loading), "")
        matches.isEmpty() && reference == null ->
            EmptyBody(stringResource(Res.string.calendar_no_results), stringResource(Res.string.calendar_pick_empty_hint))

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

@Composable
private fun ScheduleResults(currentSchedule: List<ScheduleItem>, onAdd: (List<ScheduleItem>) -> Unit) {
    if (currentSchedule.isEmpty()) {
        EmptyBody(stringResource(Res.string.calendar_schedule_empty), "")
        return
    }
    ScrollableList(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        itemsIndexed(currentSchedule, key = { index, item -> "$index:${item.id}" }) { _, item ->
            ResultRow(
                title = item.displayText,
                subtitle = item.subtitle(),
                onClick = { onAdd(listOf(item.withNewId())) },
            )
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
        Box(
            Modifier.size(ADD_BADGE).clip(RoundedCornerShape(6.dp)).background(scheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(12.dp))
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
