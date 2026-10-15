package org.churchpresenter.canvas

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.bible.Bible
import java.io.File

/** The canonical books, as the Bible tab lists them: the 66, not a module's apocrypha. */
private const val CANONICAL_BOOK_COUNT = 66

/**
 * The translation a scene's Bible source is picked from, and where its picker is paged to: a book,
 * a chapter and that chapter's verses. Read straight off [bible] -- the Bible tab's view model is
 * that tab's own, and this panel needs no more of it than a book list and a chapter.
 */
@Stable
internal class BibleSourceBrowser(val bible: Bible?) {
    val books: List<String> = bible?.getBooks()?.take(CANONICAL_BOOK_COUNT).orEmpty()

    var selectedBookIndex by mutableIntStateOf(0)
        private set
    var selectedChapter by mutableIntStateOf(1)
        private set
    var verses by mutableStateOf(emptyList<String>())
        private set

    init {
        loadChapter(0, 1)
    }

    /** Pages to [chapter] of the book at [bookIndex], clamped to the books there are. */
    fun loadChapter(bookIndex: Int, chapter: Int) {
        val source = bible ?: return
        if (books.isEmpty()) return
        val index = bookIndex.coerceIn(0, books.lastIndex)
        selectedBookIndex = index
        selectedChapter = chapter
        verses = source.getChapter(source.getBookId(index), chapter).verses
    }
}

/** The translation [fileName] in [storageDir], read off disk; null when there is none to read. */
internal fun loadBibleSource(storageDir: String, fileName: String): Bible? {
    if (storageDir.isEmpty() || fileName.isEmpty()) return null
    val file = File(storageDir, fileName)
    if (!file.isFile) return null
    return Bible().apply { loadFromSpb(file.absolutePath) }
}
