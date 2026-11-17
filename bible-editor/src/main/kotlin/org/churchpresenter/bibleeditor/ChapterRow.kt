package org.churchpresenter.bibleeditor

import org.churchpresenter.bibleedits.EditEntry
import org.churchpresenter.bibleedits.VerseAddress
import org.churchpresenter.bibleedits.VerseChange
import org.churchpresenter.bible.MAX_BIBLE_SCAN_DEPTH
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.bible.readTranslationTitle
import java.io.File

/** One line of the open chapter: a verse, or the place a missing one goes. */
sealed interface ChapterRow {
    val verse: Int

    data class Gap(override val verse: Int) : ChapterRow

    data class Verse(
        override val verse: Int,
        val text: String,
        /** What the file has, or null for a verse the person added. */
        val original: String?,
        val change: VerseChange?,
        val last: EditEntry?,
    ) : ChapterRow {
        val removed: Boolean get() = change?.removed == true
        val added: Boolean get() = change?.added == true
        val edited: Boolean get() = change != null && !added && !removed && change.text != null
    }
}

/** Every `.spb` under [folder], with its title and abbreviation read from its header. */
internal fun scanShelf(folder: File): List<ShelfBible> {
    if (!folder.isDirectory) return emptyList()
    return folder.walkTopDown().maxDepth(MAX_BIBLE_SCAN_DEPTH)
        .filter { it.isFile && it.extension.equals("spb", ignoreCase = true) }
        .map { file ->
            val title = readTranslationTitle(file)
            ShelfBible(file.relativeTo(folder).path, file, title, defaultTranslationAbbreviation(title, file.name))
        }
        .sortedBy { it.title.lowercase() }
        .toList()
}

/** The open chapter, verse by verse, with a gap wherever a verse is missing. */
fun BibleEditorState.chapterRows(): List<ChapterRow> {
    val loaded = bible ?: return emptyList()
    val key = selected?.key ?: return emptyList()
    val verses = loaded.getChapterVerses(bookId, chapter).associate { it.verseNumber to it.verseText }
    val changes = history.changes
    val missingHere = missingInChapter()
    val addedHere = changes.keys
        .filter { it.bible == key && it.book == bookId && it.chapter == chapter }
        .map { it.verse }
    val count = maxOf(verses.keys.maxOrNull() ?: 0, missingHere.maxOrNull() ?: 0, addedHere.maxOrNull() ?: 0)
    return (1..count).map { n ->
        val address = VerseAddress(key, bookId, chapter, n)
        val change = changes[address]
        val base = verses[n]
        if (base == null && change?.added != true) {
            ChapterRow.Gap(n)
        } else {
            ChapterRow.Verse(n, change?.text ?: base.orEmpty(), base, change, history.lastFor(address))
        }
    }
}

/** The missing verses of the open chapter, by number. */
fun BibleEditorState.missingInChapter(): Set<Int> {
    val key = selected?.key ?: return emptySet()
    return missing[key].orEmpty().filter { it.book == bookId && it.chapter == chapter }.map { it.verse }.toSet()
}

/** The open Bible's missing verses that are not typed back in yet. */
val BibleEditorState.openMissing: List<MissingVerse> get() = missingHere.orEmpty().filterNot { isRestored(it) }

/** [book]'s name in the open Bible, or its number when the Bible has no such book. */
fun BibleEditorState.bookNameOf(book: Int): String = bible?.getBookName(book) ?: book.toString()

/** [book]'s name in [bibleKey]: the open Bible names its books; another one is shown by number. */
fun BibleEditorState.bookNameIn(bibleKey: String, book: Int): String =
    if (selected?.key == bibleKey) bookNameOf(book) else book.toString()

/** Whether [verse] of the open Bible has been typed back in. */
fun BibleEditorState.isRestored(verse: MissingVerse): Boolean {
    val key = selected?.key ?: return false
    val change = history.changes[VerseAddress(key, verse.book, verse.chapter, verse.verse)]
    return change?.added == true && !change.text.isNullOrBlank()
}

/** Saves what is typed for [row]; a gap goes in as an added verse. */
fun BibleEditorState.saveDraft(row: ChapterRow) {
    val address = address(row.verse) ?: return
    val typed = draft.trim()
    if (typed.isEmpty()) return
    val verse = row as? ChapterRow.Verse
    val after = when {
        verse == null || verse.added -> VerseChange(text = typed, added = true)
        typed == verse.original -> null
        else -> VerseChange(text = typed)
    }
    history.commit(address, after, verse?.original)
    clearEditing()
}

/** Removes [row], or puts back a verse that was removed. An added verse is simply taken out. */
fun BibleEditorState.toggleRemoved(row: ChapterRow.Verse) {
    val address = address(row.verse) ?: return
    val after = when {
        row.removed -> row.change?.text?.let { VerseChange(text = it, added = row.change.added) }
        row.added -> null
        else -> (row.change ?: VerseChange()).copy(removed = true)
    }
    history.commit(address, after, row.original)
}

/** The history the panel lists, newest first, for the scope it is set to. */
fun BibleEditorState.visibleHistory(): List<EditEntry> {
    val key = selected?.key
    val list = when (historyScope) {
        HistoryScope.CHAPTER -> history.entries.filter {
            it.bible == key && it.book == bookId && it.chapter == chapter &&
                (historyVerse == null || it.verse == historyVerse)
        }
        HistoryScope.BIBLE -> history.entries.filter { it.bible == key }
        HistoryScope.ALL -> history.entries
    }
    return list.asReversed()
}

/** What [change] makes the verse read, given what the file had. */
fun textOf(change: VerseChange?, original: String?): String {
    val text = change?.text
    return when {
        change?.removed == true -> ""
        text != null -> text
        change?.added == true -> ""
        else -> original.orEmpty()
    }
}
