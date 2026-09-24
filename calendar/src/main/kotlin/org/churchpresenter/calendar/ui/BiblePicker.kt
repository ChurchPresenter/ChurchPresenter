package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_pick_all_bible_books
import org.churchpresenter.calendar.generated.resources.calendar_pick_verse_hint
import org.churchpresenter.calendar.generated.resources.calendar_pick_chapters
import org.churchpresenter.calendar.generated.resources.calendar_pick_choose_chapter
import org.churchpresenter.calendar.generated.resources.calendar_pick_no_bible
import org.churchpresenter.calendar.generated.resources.calendar_pick_reference
import org.churchpresenter.calendar.generated.resources.calendar_pick_whole_chapter
import org.churchpresenter.calendar.model.parseReference
import org.churchpresenter.calendar.model.toScheduleItem
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.elevationPalette

/** The trail back out of a book and a chapter, as the design draws it. */
@Composable
internal fun BibleCrumbs(
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
            .raisedKey(RoundedCornerShape(6.dp), elevationPalette().key, onClick = onClick)
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
internal fun BibleResults(
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
            .raisedKey(RoundedCornerShape(8.dp), elevationPalette().key, onClick = onClick)
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
    val palette = elevationPalette()
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
                    .raisedKey(RoundedCornerShape(7.dp), if (on) palette.accent else palette.key) { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) palette.accent.ink else palette.key.ink,
                )
            }
        }
    }
}
