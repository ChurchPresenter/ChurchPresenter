package org.churchpresenter.bibleeditor

import org.churchpresenter.bibleedits.BibleEditsStore
import org.churchpresenter.bibleedits.VerseAddress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.bible.Bible
import java.io.File

/** One `.spb` file the editor can open. [key] is its path inside the Bible folder. */
data class ShelfBible(val key: String, val file: File, val title: String, val abbreviation: String)

enum class HistoryScope { CHAPTER, BIBLE, ALL }

enum class MissingTab { MISSING, RESTORED, ALL }

private const val FLASH_MS = 1600L

/**
 * What the Bible editor window shows and does: which Bible, book and chapter are open, which verse
 * is being typed, and the history of changes.
 *
 * Every read of a file runs on [io]; what it finds is written back on [scope]'s own thread.
 */
class BibleEditorState(
    private val bibleFolder: File,
    private val preferred: List<File>,
    private val store: BibleEditsStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onSaved: () -> Unit = {},
    clock: () -> Long = System::currentTimeMillis,
) {
    val history = EditHistory(clock)

    var shelf by mutableStateOf<List<ShelfBible>>(emptyList())
        private set
    var loadingShelf by mutableStateOf(true)
        private set
    var selected by mutableStateOf<ShelfBible?>(null)
        private set
    var bible by mutableStateOf<Bible?>(null)
        private set
    var bookIndex by mutableIntStateOf(0)
        private set
    var chapter by mutableIntStateOf(1)
        private set

    var missing by mutableStateOf<Map<String, List<MissingVerse>>>(emptyMap())
        private set

    /** The [Numbering] each checked Bible matched. */
    var numbering by mutableStateOf<Map<String, Numbering>>(emptyMap())
        private set

    var editing by mutableStateOf<Int?>(null)
    var addingAt by mutableStateOf<Int?>(null)
    var draft by mutableStateOf("")
    var flash by mutableStateOf<Int?>(null)
        private set
    private var flashJob: Job? = null

    var historyOpen by mutableStateOf(true)
    var historyScope by mutableStateOf(HistoryScope.CHAPTER)
    var historyVerse by mutableStateOf<Int?>(null)
    var noteHidden by mutableStateOf(false)
    var missingOpen by mutableStateOf(false)
    var missingTab by mutableStateOf(MissingTab.MISSING)

    /** The open Bible's missing verses; null while they are looked for, or with nothing to compare with. */
    val missingHere: List<MissingVerse>? get() = selected?.let { missing[it.key] }

    /** The numbering the open Bible matched; null until the check is done. */
    val numberingHere: Numbering? get() = selected?.let { numbering[it.key] }

    /** The open book's name, as the open Bible gives it. */
    val bookName: String get() = bible?.getBooks()?.getOrNull(bookIndex).orEmpty()

    val bookId: Int get() = bible?.getBookId(bookIndex) ?: (bookIndex + 1)
    val chapterCount: Int get() = bible?.getChapterCount(bookIndex)?.coerceAtLeast(1) ?: 1

    fun start() {
        scope.launch {
            val (found, saved) = withContext(io) { scanShelf(bibleFolder) to store.load() }
            history.reset(saved)
            shelf = found
            loadingShelf = false
            val first = preferred.firstNotNullOfOrNull { p -> found.firstOrNull { it.file == p } }
                ?: found.firstOrNull()
            first?.let { selectBible(it) }
        }
    }

    fun selectBible(target: ShelfBible) {
        if (target == selected && bible != null) return
        selected = target
        clearEditing()
        historyVerse = null
        scope.launch {
            val loaded = withContext(io) { loadBible(target.file) }
            if (selected != target) return@launch
            bible = loaded
            bookIndex = bookIndex.coerceIn(0, (loaded.getBookCount() - 1).coerceAtLeast(0))
            chapter = chapter.coerceIn(1, chapterCount)
            scanMissing(target, loaded)
        }
    }

    private suspend fun scanMissing(target: ShelfBible, loaded: Bible) {
        if (target.key in missing) return
        val found = withContext(io) { checkMissingVerses(loaded) }
        numbering = numbering + (target.key to found.numbering)
        missing = missing + (target.key to found.missing)
    }

    fun selectBook(index: Int) {
        bookIndex = index
        chapter = 1
        clearEditing()
        historyVerse = null
    }

    fun goToChapter(number: Int) {
        chapter = number.coerceIn(1, chapterCount)
        clearEditing()
        historyVerse = null
    }

    /** Opens [book]/[chapter] of [bibleKey] and lights [verse] up for a moment. */
    fun goTo(bibleKey: String, book: Int, chapter: Int, verse: Int?) {
        shelf.firstOrNull { it.key == bibleKey }?.let { if (it != selected) selectBible(it) }
        val index = bible?.getDisplayIndexForBookId(book) ?: (book - 1)
        bookIndex = index.coerceAtLeast(0)
        this.chapter = chapter
        clearEditing()
        if (verse != null) flashVerse(verse)
    }

    fun flashVerse(verse: Int) {
        flash = verse
        flashJob?.cancel()
        flashJob = scope.launch {
            delay(FLASH_MS)
            flash = null
        }
    }

    fun clearEditing() {
        editing = null
        addingAt = null
        draft = ""
    }

    fun save() {
        val snapshot = history.entries
        history.markSaved()
        scope.launch {
            withContext(io) { store.save(snapshot) }
            onSaved()
        }
    }

    fun address(verse: Int): VerseAddress? = selected?.let { VerseAddress(it.key, bookId, chapter, verse) }
}

private fun loadBible(file: File): Bible = Bible().apply { loadFromSpb(file.absolutePath) }
