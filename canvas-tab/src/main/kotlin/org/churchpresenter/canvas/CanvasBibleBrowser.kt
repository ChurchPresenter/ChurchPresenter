package org.churchpresenter.canvas

import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.readTranslationTitle
import org.churchpresenter.settings.utils.Constants
import java.io.File

/** Protestant canon. A translation carrying apocrypha lists them after these, and the picker stops here. */
private const val CANONICAL_BOOK_COUNT = 66

/** How far into the Bible folder to look. A symlink loop or a deep tree must not hang the panel. */
private const val MAX_BIBLE_SCAN_DEPTH = 4

/**
 * The translations on disk, as `file name to title` — what the version dropdown lists.
 *
 * Sorted, and relative to [directory] so a translation in a sub-folder still reads as one entry.
 * An empty or missing folder is an empty list rather than an error: an operator who has not
 * downloaded a Bible yet is an ordinary state, not a fault.
 */
internal fun bibleTranslationsIn(directory: String): List<Pair<String, String>> {
    if (directory.isEmpty()) return emptyList()
    val dir = File(directory)
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.walkTopDown().maxDepth(MAX_BIBLE_SCAN_DEPTH)
        .filter { it.isFile && it.extension.lowercase() == Constants.EXTENSION_SPB }
        .map { it.toRelativeString(dir).replace('\\', '/') }
        .sorted()
        .map { name -> name to readTranslationTitle(File(dir, name)) }
        .toList()
}

/**
 * One loaded translation, browsed a chapter at a time — the verse picker behind a Bible scene source.
 *
 * The Bible tab's own `BibleViewModel` does all this and a great deal more (search, history,
 * auto-follow, two translations at once, multi-verse selection), and it lives in `:composeApp`. The
 * canvas needs six things from it: the books, the chapters in a book, the verses in a chapter, and
 * the text of a range of them. Reaching for the view model would have made this editor un-movable
 * and, when it stayed behind, left a Bible source with no picker at all — so this is those six
 * things, over `:bible`, owned by the tab that draws them.
 *
 * Loading is done off the composition; [of] is what a `remember` calls.
 */
internal class CanvasBibleBrowser private constructor(val bible: Bible?) {

    /** The books this translation has, canon only, in the order the dropdown shows them. */
    val books: List<String> = bible?.getBooks()?.take(CANONICAL_BOOK_COUNT).orEmpty()

    /** How many chapters are in the book at [bookIndex], or 0 when there is no such book. */
    fun chapterCount(bookIndex: Int): Int {
        val loaded = bible ?: return 0
        if (bookIndex !in books.indices) return 0
        return loaded.getChapterCount(loaded.getBookId(bookIndex))
    }

    /** The verses of one chapter, as text. Empty when the chapter is not there. */
    fun verses(bookIndex: Int, chapter: Int): List<String> {
        val loaded = bible ?: return emptyList()
        if (bookIndex !in books.indices) return emptyList()
        return loaded.getChapter(loaded.getBookId(bookIndex), chapter).verses
    }

    /**
     * The text of verses [start] through [end] of a chapter, joined into one passage.
     *
     * A verse the translation does not have is left out rather than becoming an empty gap, which is
     * what a range running off the end of a chapter produces.
     */
    fun passage(bookIndex: Int, chapter: Int, start: Int, end: Int): String {
        val loaded = bible ?: return ""
        if (bookIndex !in books.indices) return ""
        val bookId = loaded.getBookId(bookIndex)
        return (start..end)
            .mapNotNull { verse -> loaded.getVerseDetails(bookId, chapter, verse)?.second }
            .joinToString(" ")
    }

    /** How a passage is written out beside the verse — "John 3:16", or "John 3:16-18" for a range. */
    fun reference(bookIndex: Int, chapter: Int, start: Int, end: Int): String {
        val book = books.getOrElse(bookIndex) { "" }
        return if (start == end) "$book $chapter:$start" else "$book $chapter:$start-$end"
    }

    companion object {
        /** Nothing loaded — every list empty, every lookup blank. */
        val Empty = CanvasBibleBrowser(null)

        /**
         * Opens [fileName] inside [directory], or [Empty] when it cannot be read.
         *
         * Reads a file, so it belongs off the main thread.
         */
        fun of(directory: String, fileName: String): CanvasBibleBrowser {
            if (directory.isEmpty() || fileName.isEmpty()) return Empty
            val file = File(directory, fileName)
            if (!file.exists()) return Empty
            val bible = Bible()
            bible.loadFromSpb(file.absolutePath)
            return CanvasBibleBrowser(bible)
        }
    }
}
