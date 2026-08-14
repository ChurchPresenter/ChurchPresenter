package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.flow.first
import org.churchpresenter.app.churchpresenter.models.SelectedVerse

/**
 * What is currently selected, resolved into the verses that go on screen — including the
 * look-ahead list the stage monitor shows next.
 */

internal fun BibleViewModel.getSelectedVerses(): List<SelectedVerse> {
    val verseList = mutableListOf<SelectedVerse>()

    if (_verses.value.isEmpty()) {
        return verseList
    }

    val bookId = _primaryBible.value?.getBookId(_selectedBookIndex.value) ?: (_selectedBookIndex.value + 1)

    if (_multiVerseEnabled.value && _selectedVerseIndices.isNotEmpty()) {
        val sortedIndices = _selectedVerseIndices.sorted()
        val primaryTexts = mutableListOf<String>()
        val parallelTexts = _loadedBibles.value.drop(1).map { mutableListOf<String>() }
        val verseNumbers = mutableListOf<Int>()
        var bookName = ""
        val parallelBookNames = MutableList(parallelTexts.size) { "" }
        val parallelBookIds = MutableList(parallelTexts.size) { bookId }

        for (idx in sortedIndices) {
            val verse = _verses.value.getOrNull(idx) ?: continue
            val vNum = verseNumberOf(verse) ?: continue
            verseNumbers.add(vNum)

            val primaryText = verseTextOf(verse)
            if (primaryText.isNotEmpty()) {
                if (bookName.isEmpty()) bookName = _primaryBible.value?.getBookName(bookId) ?: ""
                primaryTexts.add(primaryText)
            }

            val codeRef = _primaryBible.value?.getCodeReference(bookId, _selectedChapter.value, vNum)
            val sB = codeRef?.first ?: bookId
            val sCh = codeRef?.second ?: _selectedChapter.value
            val sV = codeRef?.third ?: vNum
            _loadedBibles.value.drop(1).forEachIndexed { bibleIndex, bible ->
                bible.takeIf { it.getVerseCount() > 0 }
                    ?.getVerseDetailsByCode(sB, sCh, sV)?.let { result ->
                if (parallelBookNames[bibleIndex].isEmpty()) {
                    parallelBookNames[bibleIndex] = result.bookName
                    parallelBookIds[bibleIndex] = sB
                }
                parallelTexts[bibleIndex].add(result.verseText)
                }
            }
        }

        val rangeStr = formatVerseRange(verseNumbers)

        if (primaryTexts.isNotEmpty()) {
            verseList.add(
                SelectedVerse(
                    translationFileName = _loadedTranslations.value.firstOrNull()?.fileName.orEmpty(),
                    bibleAbbreviation = _primaryBible.value?.getBibleAbbreviation() ?: "",
                    bibleName = _primaryBible.value?.getBibleTitle() ?: "",
                    bookName = bookName,
                    chapter = _selectedChapter.value,
                    verseNumber = verseNumbers.first(),
                    verseText = primaryTexts.joinToString(" "),
                    verseRange = rangeStr,
                    bookId = bookId
                )
            )
        }
        parallelTexts.forEachIndexed { index, texts ->
            if (texts.isNotEmpty()) verseList.add(
                SelectedVerse(
                    translationFileName = _loadedTranslations.value[index + 1].fileName,
                    bibleAbbreviation = _loadedBibles.value[index + 1].getBibleAbbreviation(),
                    bibleName = _loadedBibles.value[index + 1].getBibleTitle(),
                    bookName = parallelBookNames[index],
                    chapter = _selectedChapter.value,
                    verseNumber = verseNumbers.first(),
                    verseText = texts.joinToString(" "),
                    verseRange = rangeStr,
                    bookId = parallelBookIds[index]
                )
            )
        }
        return verseList
    }

    val safeIndex = _selectedVerseIndex.value.coerceIn(0, _verses.value.size - 1)

    if (safeIndex != _selectedVerseIndex.value) {
        _selectedVerseIndex.value = safeIndex
    }

    val verse = _verses.value[safeIndex]
    val verseNumber = verseNumberOf(verse) ?: 1

    val primaryVerseText = verseTextOf(verse)
    val primaryBookName = _primaryBible.value?.getBookName(bookId) ?: ""
    if (primaryVerseText.isNotEmpty()) {
        verseList.add(
            SelectedVerse(
                translationFileName = _loadedTranslations.value.firstOrNull()?.fileName.orEmpty(),
                bibleAbbreviation = _primaryBible.value?.getBibleAbbreviation() ?: "",
                bibleName = _primaryBible.value?.getBibleTitle() ?: "",
                bookName = primaryBookName,
                chapter = _selectedChapter.value,
                verseNumber = verseNumber,
                verseText = primaryVerseText,
                bookId = bookId
            )
        )
    }

    val codeRef = _primaryBible.value?.getCodeReference(bookId, _selectedChapter.value, verseNumber)
    val secBook = codeRef?.first ?: bookId
    val secChapter = codeRef?.second ?: _selectedChapter.value
    val secVerse = codeRef?.third ?: verseNumber
    _loadedTranslations.value.drop(1).forEach { loadedTranslation ->
        val bible = loadedTranslation.bible
        bible.takeIf { it.getVerseCount() > 0 }
            ?.getVerseDetailsByCode(secBook, secChapter, secVerse)?.let { result ->
            verseList.add(SelectedVerse(
                translationFileName = loadedTranslation.fileName,
                bibleAbbreviation = bible.getBibleAbbreviation(),
                bibleName = bible.getBibleTitle(),
                bookName = result.bookName,
                chapter = result.displayChapter,
                verseNumber = result.displayVerse,
                verseText = result.verseText,
                bookId = secBook
            ))
        }
    }

    return verseList
}

internal fun BibleViewModel.getNextVerses(): List<SelectedVerse> {
    if (_verses.value.isEmpty()) return emptyList()

    val referenceIndex = if (_multiVerseEnabled.value && _selectedVerseIndices.isNotEmpty()) {
        _selectedVerseIndices.max()
    } else {
        _selectedVerseIndex.value.coerceIn(0, _verses.value.size - 1)
    }

    val bookId = _primaryBible.value?.getBookId(_selectedBookIndex.value) ?: (_selectedBookIndex.value + 1)

    if (referenceIndex < _verses.value.size - 1) {
        val verse = _verses.value[referenceIndex + 1]
        val verseNumber = verseNumberOf(verse) ?: return emptyList()
        return buildNextVerseList(bookId, _selectedChapter.value, verseNumber, verseTextOf(verse))
    }

    val bible = _primaryBible.value ?: return emptyList()
    var nextBookIndex = _selectedBookIndex.value
    var nextChapter = _selectedChapter.value + 1
    if (nextChapter > bible.getChapterCount(nextBookIndex)) {
        nextBookIndex += 1
        nextChapter = 1
        if (nextBookIndex >= _books.value.size) return emptyList()
    }
    val nextBookId = bible.getBookId(nextBookIndex)
    val firstVerse = bible.getChapter(nextBookId, nextChapter).verses.firstOrNull() ?: return emptyList()
    val verseNumber = verseNumberOf(firstVerse) ?: return emptyList()
    return buildNextVerseList(nextBookId, nextChapter, verseNumber, verseTextOf(firstVerse))
}

internal fun BibleViewModel.buildNextVerseList(bookId: Int,
    chapter: Int,
    verseNumber: Int,
    verseText: String): List<SelectedVerse> {
    val verseList = mutableListOf<SelectedVerse>()
    if (verseText.isNotEmpty()) {
        verseList.add(
            SelectedVerse(
                translationFileName = _loadedTranslations.value.firstOrNull()?.fileName.orEmpty(),
                bibleAbbreviation = _primaryBible.value?.getBibleAbbreviation() ?: "",
                bibleName = _primaryBible.value?.getBibleTitle() ?: "",
                bookName = _primaryBible.value?.getBookName(bookId) ?: "",
                chapter = chapter,
                verseNumber = verseNumber,
                verseText = verseText
            )
        )
    }
    val codeRef = _primaryBible.value?.getCodeReference(bookId, chapter, verseNumber)
    val secBook = codeRef?.first ?: bookId
    val secChapter = codeRef?.second ?: chapter
    val secVerse = codeRef?.third ?: verseNumber
    _loadedTranslations.value.drop(1).forEach { loadedTranslation ->
        val bible = loadedTranslation.bible
        bible.takeIf { it.getVerseCount() > 0 }
            ?.getVerseDetailsByCode(secBook, secChapter, secVerse)?.let { result ->
        verseList.add(SelectedVerse(
                translationFileName = loadedTranslation.fileName,
                bibleAbbreviation = bible.getBibleAbbreviation(),
                bibleName = bible.getBibleTitle(),
                bookName = result.bookName,
                chapter = result.displayChapter,
                verseNumber = result.displayVerse,
                verseText = result.verseText
            ))
        }
    }
    return verseList
}
