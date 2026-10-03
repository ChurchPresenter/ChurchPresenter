package org.churchpresenter.bibletab

import kotlinx.coroutines.flow.first

internal fun BibleViewModel.canonicalBookIdForDisplayIndex(displayIndex: Int): Int =
    _primaryBible.value?.getBookId(displayIndex) ?: (displayIndex + 1)

internal fun BibleViewModel.canonicalRefForDisplay(
    displayBookIndex: Int,
    chapter: Int,
    verse: Int?
): Triple<Int, Int, Int?>? {
    val bible = _primaryBible.value ?: return null
    val bookId = bible.getBookId(displayBookIndex)
    val code = bible.getCodeReference(bookId, chapter, verse ?: 1) ?: return null
    return Triple(code.first, code.second, verse?.let { code.third })
}

internal fun BibleViewModel.displayIndexForBookName(bookName: String): Int =
    _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }

internal fun BibleViewModel.canonicalRefForBookName(
    bookName: String,
    chapter: Int,
    verse: Int
): Triple<Int, Int, Int>? {
    val displayIndex = displayIndexForBookName(bookName).takeIf { it >= 0 } ?: return null
    val (book, mappedChapter, mappedVerse) =
        canonicalRefForDisplay(displayIndex, chapter, verse) ?: return null
    return mappedVerse?.let { Triple(book, mappedChapter, it) }
}

internal fun BibleViewModel.verseTextFor(bookIndex: Int, chapter: Int, verse: Int?): String? {
    if (verse == null) return null
    val bible = _primaryBible.value ?: return null
    return bible.getVerseDetails(bible.getBookId(bookIndex), chapter, verse)?.second
}

internal fun BibleViewModel.buildDetectionLabel(bookIndex: Int, chapter: Int, vs: Int?, ve: Int?): String {
    val bookName = _books.value.getOrNull(bookIndex) ?: return "$chapter"
    val versePart = when {
        vs != null && ve != null && ve > vs -> ":$vs-$ve"
        vs != null -> ":$vs"
        else -> ""
    }
    return "$bookName $chapter$versePart"
}

internal fun BibleViewModel.canonicalBookIdToIndex(canonicalId: Int): Int? =
    _primaryBible.value?.getDisplayIndexForBookId(canonicalId)?.takeIf { it in _books.value.indices }
