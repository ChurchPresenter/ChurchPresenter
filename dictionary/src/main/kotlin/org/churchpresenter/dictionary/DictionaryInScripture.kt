package org.churchpresenter.dictionary

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.book
import org.churchpresenter.strings.generated.resources.dictionary_filter_all
import org.churchpresenter.strings.generated.resources.dictionary_go_to_verse
import org.churchpresenter.strings.generated.resources.dictionary_in_scripture_count
import org.churchpresenter.strings.generated.resources.dictionary_in_scripture_header
import org.churchpresenter.strings.generated.resources.dictionary_in_scripture_loading
import org.churchpresenter.strings.generated.resources.dictionary_in_scripture_none
import org.churchpresenter.strings.generated.resources.dictionary_in_scripture_show_more
import org.churchpresenter.strings.generated.resources.chapter
import org.churchpresenter.strings.generated.resources.verse
import org.churchpresenter.dictionary.data.InterlinearVerse
import org.churchpresenter.dictionary.data.InterlinearWord
import org.churchpresenter.dictionary.data.StrongsEntry
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.components.RaisedChip
import org.churchpresenter.theme.elevationPalette

private const val DEFINITION_PREVIEW_CHARS = 200

/**
 * Where this entry's word appears in scripture, with the book and chapter filters over that list.
 *
 * Takes [highlightedNumber] rather than the entry: the number is all the verse rows need, and
 * passing it keeps this section independent of the entry it was opened from.
 */
@Composable
internal fun InScriptureSection(
    highlightedNumber: String,
    interlinearVerses: List<InterlinearVerse>,
    totalInterlinearCount: Int,
    isInterlinearLoading: Boolean,
    interlinearDisplayLimit: Int,
    onShowMore: () -> Unit,
    cardBookFilter: Int?,
    cardChapterFilter: Int?,
    cardAvailableBooks: List<Int>,
    cardAvailableChapters: List<Int>,
    onFilterCardsByBook: (Int?) -> Unit,
    onFilterCardsByChapter: (Int?) -> Unit,
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)?,
    getBookName: ((bookId: Int) -> String?)?,
    onWordClick: ((String) -> Unit)?,
    onVerseClick: ((bookId: Int, chapter: Int, verse: Int) -> Unit)?,
    getEntry: ((strongsNumber: String) -> StrongsEntry?)?,
) {
    HorizontalDivider()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.dictionary_in_scripture_header),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isInterlinearLoading && cardAvailableBooks.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InScriptureBookDropdown(
                    allBooksLabel = stringResource(Res.string.dictionary_filter_all),
                    selectedBookId = cardBookFilter,
                    availableBooks = cardAvailableBooks,
                    getBookName = getBookName,
                    onSelect = onFilterCardsByBook,
                )
                if (cardBookFilter != null && cardAvailableChapters.size > 1) {
                    InScriptureChapterDropdown(
                        allChaptersLabel = stringResource(Res.string.dictionary_filter_all),
                        selectedChapter = cardChapterFilter,
                        availableChapters = cardAvailableChapters,
                        onSelect = onFilterCardsByChapter,
                    )
                }
            }
        }
    }

    if (isInterlinearLoading) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = stringResource(Res.string.dictionary_in_scripture_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else if (interlinearVerses.isEmpty()) {
        Text(
            text = stringResource(Res.string.dictionary_in_scripture_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = stringResource(Res.string.dictionary_in_scripture_count, totalInterlinearCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            interlinearVerses.take(interlinearDisplayLimit).forEach { verse ->
                InterlinearVerseRow(
                    interlinearVerse = verse,
                    highlightedNumber = highlightedNumber,
                    getVerseText = getVerseText,
                    getBookName = getBookName,
                    onWordClick = onWordClick,
                    onVerseClick = onVerseClick,
                    getEntry = getEntry,
                )
            }
        }
        if (interlinearVerses.size > interlinearDisplayLimit) {
            val remaining = interlinearVerses.size - interlinearDisplayLimit
            GhostButton(shape = AppShape(6.dp), onClick = onShowMore) {
                Text(
                    text = stringResource(Res.string.dictionary_in_scripture_show_more, remaining),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun InterlinearVerseRow(
    interlinearVerse: InterlinearVerse,
    highlightedNumber: String,
    getVerseText: ((bookId: Int, chapter: Int, verse: Int) -> String?)?,
    getBookName: ((bookId: Int) -> String?)?,
    onWordClick: ((String) -> Unit)?,
    onVerseClick: ((bookId: Int, chapter: Int, verse: Int) -> Unit)?,
    getEntry: ((strongsNumber: String) -> StrongsEntry?)?,
) {
    val verseText = getVerseText?.invoke(
        interlinearVerse.bookId,
        interlinearVerse.chapter,
        interlinearVerse.verseNumber
    )
    val bookLabel = stringResource(Res.string.book)
    val bookName = getBookName?.invoke(interlinearVerse.bookId) ?: "$bookLabel ${interlinearVerse.bookId}"
    val refLabel = "$bookName ${interlinearVerse.chapter}:${interlinearVerse.verseNumber}"
    val goToVerseStr = stringResource(Res.string.dictionary_go_to_verse)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = AppShape(8.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onVerseClick != null) {
            TooltipArea(
                tooltip = {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shape = MaterialTheme.shapes.extraSmall,
                        tonalElevation = 4.dp
                    ) {
                        Text(
                            goToVerseStr,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                tooltipPlacement = TooltipPlacement.ComponentRect(
                    anchor = Alignment.BottomEnd,
                    offset = DpOffset(0.dp, 4.dp)
                ),
            ) {
                Text(
                    text = refLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        onVerseClick(interlinearVerse.bookId, interlinearVerse.chapter, interlinearVerse.verseNumber)
                    },
                )
            }
        } else {
            Text(
                text = refLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (verseText != null) {
            Text(
                text = verseText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            interlinearVerse.words.forEach { word ->
                InterlinearWordChip(
                    word = word,
                    isHighlighted = word.strongsNumber == highlightedNumber,
                    onClick = if (word.strongsNumber != highlightedNumber && onWordClick != null) {
                        { onWordClick(word.strongsNumber) }
                    } else null,
                    entry = getEntry?.invoke(word.strongsNumber),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InterlinearWordChip(
    word: InterlinearWord,
    isHighlighted: Boolean,
    onClick: (() -> Unit)?,
    entry: StrongsEntry?,
) {
    val containerColor = when {
        isHighlighted -> MaterialTheme.colorScheme.primaryContainer
        onClick != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val labelColor = when {
        isHighlighted -> MaterialTheme.colorScheme.onPrimaryContainer
        onClick != null -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val chip = @Composable {
        RaisedChip(
            onClick = onClick ?: {},
            fill = elevationPalette().tinted(containerColor, labelColor),
            label = { Text(text = word.text, style = MaterialTheme.typography.labelSmall, color = labelColor) },
            enabled = isHighlighted || onClick != null,
        )
    }
    if (entry != null) {
        TooltipArea(
            tooltip = {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = entry.number,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                            )
                            Text(
                                text = entry.word,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                            )
                            Text(
                                text = entry.transliteration,
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.8f),
                            )
                        }
                        Text(
                            text = entry.definition.take(DEFINITION_PREVIEW_CHARS).let {
                                if (entry.definition.length > DEFINITION_PREVIEW_CHARS) "$it…" else it
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                        )
                    }
                }
            },
            tooltipPlacement = TooltipPlacement.ComponentRect(
                anchor = Alignment.BottomCenter,
                offset = DpOffset(0.dp, 4.dp)
            ),
        ) { chip() }
    } else {
        chip()
    }
}
