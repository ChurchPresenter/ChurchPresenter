package org.churchpresenter.songlibrary.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import kotlinx.coroutines.launch
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTranslation
import org.churchpresenter.songlibrary.RawSection
import org.churchpresenter.songlibrary.SectionStatus
import org.churchpresenter.songlibrary.TranslationComparison
import org.churchpresenter.songlibrary.generated.resources.Res
import org.churchpresenter.songlibrary.generated.resources.compare_all_good
import org.churchpresenter.songlibrary.generated.resources.compare_chip_missing
import org.churchpresenter.songlibrary.generated.resources.compare_chip_ok
import org.churchpresenter.songlibrary.generated.resources.compare_language_n
import org.churchpresenter.songlibrary.generated.resources.compare_legend_mismatch
import org.churchpresenter.songlibrary.generated.resources.compare_note_mismatch
import org.churchpresenter.songlibrary.generated.resources.compare_note_missing
import org.churchpresenter.songlibrary.generated.resources.compare_note_ok
import org.churchpresenter.songlibrary.generated.resources.compare_note_slides
import org.churchpresenter.songlibrary.generated.resources.compare_placeholder
import org.churchpresenter.songlibrary.generated.resources.compare_section_n
import org.churchpresenter.songlibrary.generated.resources.compare_window_title
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

/** One language as the comparison shows it: where it sits in the song, what it is called, its color. */
internal data class CompareLanguage(val index: Int, val code: String, val name: String, val accent: Color)

/** One language's cell of one section, with how it stands against the reference. */
internal data class CompareCell(
    val language: CompareLanguage,
    val text: String,
    val status: SectionStatus,
    val lines: Int,
    val slides: Int,
)

/** One section across every shown language. */
internal data class CompareRow(val index: Int, val label: String, val cells: List<CompareCell>) {
    val status: SectionStatus
        get() = when {
            cells.any { it.status == SectionStatus.MISSING } -> SectionStatus.MISSING
            cells.any { it.status == SectionStatus.MISMATCH } -> SectionStatus.MISMATCH
            else -> SectionStatus.OK
        }
}

/**
 * Every language of a song side by side, section against section, the way the presenter pairs them.
 *
 * A section whose languages hold a different number of lines is where the screen will run out of
 * step during a service, so it is marked, listed in the rail, and can be fixed right here: each cell
 * is the section as written, and Save puts the edited languages back into the song. Nothing touches
 * the disk until the library's own Save, as with every other edit in this window.
 */
@Composable
fun CompareTranslationsDialog(
    song: SongItem,
    onDismiss: () -> Unit,
    onSave: (SongItem) -> Unit,
) {
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(size = DpSize(1320.dp, 800.dp)),
        title = stringResource(Res.string.compare_window_title),
        resizable = true,
    ) {
        CompareContent(song, onDismiss, onSave)
    }
}

@Composable
private fun CompareContent(song: SongItem, onDismiss: () -> Unit, onSave: (SongItem) -> Unit) {
    val translations = remember(song) { song.translationList() }
    val original = remember(song) { translations.map { TranslationComparison.sectionsOf(it.lyrics) } }
    val rowCount = original.maxOf { it.size }
    val headers = remember(song) {
        (0 until rowCount).map { i -> original.firstNotNullOfOrNull { it.getOrNull(i)?.header } }
    }
    val languages = languagesOf(translations)
    val edits = remember(song) { mutableStateMapOf<Pair<Int, Int>, String>() }
    var shown by remember(song) { mutableStateOf(languages.map { it.index }) }
    var onlyProblems by remember { mutableStateOf(false) }

    fun originalText(language: Int, section: Int) =
        original[language].getOrNull(section)?.body.orEmpty().joinToString("\n")
    fun textOf(language: Int, section: Int) = edits[language to section] ?: originalText(language, section)
    val changed = edits.filter { (key, value) -> value != originalText(key.first, key.second) }

    val visible = languages.filter { it.index in shown }
    val reference = visible.first()
    val rows = (0 until rowCount).map { i ->
        val referenceBody = textOf(reference.index, i).lines()
        CompareRow(
            index = i,
            label = headers[i]?.trim('[', ']', '{', '}')?.trim()
                ?: stringResource(Res.string.compare_section_n, i + 1),
            cells = visible.map { language ->
                val body = textOf(language.index, i).lines()
                CompareCell(
                    language = language,
                    text = textOf(language.index, i),
                    status = TranslationComparison.statusOf(body, referenceBody),
                    lines = TranslationComparison.presentableLines(body),
                    slides = TranslationComparison.slidesOf(body).size,
                )
            },
        )
    }
    val cards = if (onlyProblems) rows.filter { it.status != SectionStatus.OK } else rows
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompareHeader(
            song = song,
            languages = languages,
            shown = shown,
            onlyProblems = onlyProblems,
            onToggleLanguage = { language ->
                val on = language in shown
                // Two is the fewest there is anything to compare between.
                if (!on || shown.size > 2) shown = if (on) shown - language else (shown + language).sorted()
            },
            onToggleOnlyProblems = { onlyProblems = !onlyProblems },
        )
        Hairline()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            SectionRail(rows) { index ->
                val position = cards.indexOfFirst { it.index == index }
                if (position >= 0) scope.launch { listState.animateScrollToItem(position) }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            CompareCards(visible, reference, cards, listState) { language, section, text ->
                edits[language to section] = text
            }
        }
        Hairline()
        CompareFooter(
            changed = changed.isNotEmpty(),
            onDismiss = onDismiss,
            onSave = { onSave(applied(song, original, headers, changed)) },
        )
    }
}

@Composable
private fun CompareCards(
    visible: List<CompareLanguage>,
    reference: CompareLanguage,
    cards: List<CompareRow>,
    listState: LazyListState,
    onEdit: (language: Int, section: Int, text: String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 30.dp, end = 42.dp, top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            visible.forEach { language ->
                ColumnHead(language, isReference = language == reference, Modifier.weight(1f))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize().padding(start = 18.dp, end = 30.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(cards, key = { it.index }) { row ->
                    SectionCard(row, reference) { language, text -> onEdit(language, row.index, text) }
                }
                if (cards.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth()
                                .clip(AppShape(12.dp))
                                .background(scheme.surfaceContainer)
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(Res.string.compare_all_good),
                                style = LibraryType.body,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

/** [song] with every language that has an edited section rebuilt around its edits. */
private fun applied(
    song: SongItem,
    original: List<List<RawSection>>,
    headers: List<String?>,
    changed: Map<Pair<Int, Int>, String>,
): SongItem = changed.entries.groupBy({ it.key.first }, { it.key.second to it.value.lines() })
    .entries.fold(song) { result, (language, sections) ->
        val lyrics = TranslationComparison.rebuild(original[language], sections.toMap(), headers)
        TranslationComparison.withLyrics(result, language, lyrics)
    }

/**
 * The languages this song actually uses, the primary always among them.
 *
 * Numbered by position rather than by what is shown, so a song written in languages 1 and 3 still
 * calls the second one "Language 3" — the number an output's song languages use.
 */
@Composable
private fun languagesOf(translations: List<SongTranslation>): List<CompareLanguage> {
    val scheme = MaterialTheme.colorScheme
    val accents = listOf(scheme.primary, scheme.tertiary, MaterialTheme.semantic.info, scheme.secondary)
    return translations.withIndex()
        .filter { (index, translation) -> index == 0 || !translation.isEmpty }
        .map { (index, translation) ->
            val name = translation.label.ifBlank { stringResource(Res.string.compare_language_n, index + 1) }
            val code = translation.label.takeIf { it.isNotBlank() }?.take(2)?.uppercase() ?: "L${index + 1}"
            CompareLanguage(index, code, name, accents[index % accents.size])
        }
}

@Composable
internal fun statusColor(status: SectionStatus): Color = when (status) {
    SectionStatus.OK -> MaterialTheme.semantic.success
    SectionStatus.MISMATCH -> MaterialTheme.semantic.warning
    SectionStatus.MISSING -> MaterialTheme.colorScheme.error
}

@Composable
private fun SectionCard(row: CompareRow, reference: CompareLanguage, onEdit: (language: Int, text: String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val status = row.status
    val tint = statusColor(status)
    val referenceCell = row.cells.first { it.language == reference }
    val shape = AppShape(12.dp)
    val border = if (status == SectionStatus.OK) scheme.outlineVariant else tint.copy(alpha = CARD_BORDER_ALPHA)
    Column(Modifier.fillMaxWidth().clip(shape).background(scheme.surfaceContainer).border(1.dp, border, shape)) {
        Row(
            Modifier.fillMaxWidth().height(38.dp).padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(row.label, style = LibraryType.bodyStrong, color = scheme.onSurface, maxLines = 1)
            Box(
                Modifier.height(22.dp)
                    .clip(AppShape(6.dp))
                    .background(tint.copy(alpha = TAG_ALPHA))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (status) {
                        SectionStatus.OK -> stringResource(Res.string.compare_chip_ok, referenceCell.lines)
                        SectionStatus.MISSING -> stringResource(
                            Res.string.compare_chip_missing,
                            row.cells.filter { it.status == SectionStatus.MISSING }
                                .joinToString(", ") { it.language.code },
                        )
                        SectionStatus.MISMATCH -> stringResource(Res.string.compare_legend_mismatch)
                    },
                    style = LibraryType.small.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = tint,
                    maxLines = 1,
                )
            }
        }
        Hairline()
        // Every cell is as tall as the longest, so line 3 of one language sits beside line 3 of the next.
        val lines = maxOf(2, row.cells.maxOf { it.text.lines().size })
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            row.cells.forEach { cell ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    SectionEditor(cell, row.label, lines) { onEdit(cell.language.index, it) }
                    CellNote(cell, referenceCell)
                }
            }
        }
    }
}

@Composable
private fun SectionEditor(cell: CompareCell, section: String, lines: Int, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = AppShape(9.dp)
    val border = when (cell.status) {
        SectionStatus.OK -> scheme.outlineVariant
        else -> statusColor(cell.status).copy(alpha = CARD_BORDER_ALPHA)
    }
    BasicTextField(
        value = cell.text,
        onValueChange = onChange,
        textStyle = LibraryType.body.copy(color = scheme.onSurface, lineHeight = 20.sp),
        cursorBrush = SolidColor(scheme.primary),
        minLines = lines,
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(scheme.background)
            .border(1.dp, border, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        decorationBox = { field ->
            Box {
                if (cell.text.isEmpty()) {
                    Text(
                        stringResource(Res.string.compare_placeholder, cell.language.name, section),
                        style = LibraryType.body.copy(lineHeight = 20.sp),
                        color = scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA),
                    )
                }
                field()
            }
        },
    )
}

@Composable
private fun CellNote(cell: CompareCell, reference: CompareCell) {
    val scheme = MaterialTheme.colorScheme
    val tint = statusColor(cell.status)
    val code = reference.language.code
    Row(
        Modifier.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(tint, 6.dp)
        Text(
            when {
                cell.status == SectionStatus.MISSING -> stringResource(Res.string.compare_note_missing)
                cell.status == SectionStatus.OK -> stringResource(Res.string.compare_note_ok, cell.lines)
                // Same number of lines, split across a different number of slides.
                cell.lines == reference.lines ->
                    stringResource(Res.string.compare_note_slides, cell.slides, code, reference.slides)
                else -> stringResource(Res.string.compare_note_mismatch, cell.lines, code, reference.lines)
            },
            style = LibraryType.small.copy(fontSize = 11.sp),
            color = if (cell.status == SectionStatus.OK) {
                scheme.onSurfaceVariant.copy(alpha = FAINT_TEXT_ALPHA)
            } else {
                tint
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal const val TAG_ALPHA = 0.16f
private const val CARD_BORDER_ALPHA = 0.55f
