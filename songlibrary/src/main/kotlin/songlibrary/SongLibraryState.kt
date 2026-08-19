package songlibrary

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.models.songs.GridView
import core.models.songs.SaveOutcome
import core.models.songs.SongEdits
import core.models.songs.SongField
import core.models.songs.SongGrid
import core.models.songs.SongItem
import core.models.songs.SongLibrary
import core.models.songs.SortColumn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Which columns the grid shows. The title is not among them: a row without it is unreadable. */
val OPTIONAL_COLUMNS: List<SongField> = listOf(
    SongField.NUMBER,
    SongField.SECONDARY_TITLE,
    SongField.SONGBOOK,
    SongField.AUTHOR,
    SongField.COMPOSER,
    SongField.TUNE,
    SongField.CCLI,
)

/**
 * What the window is showing and what has been typed into it.
 *
 * The decisions live in `core.models.songs` — this holds the answers where Compose can see them,
 * and is the one place that touches the disk: everything else asks it to.
 *
 * **Everything a library-sized list makes expensive is handled here, not at the call site.** A
 * library is thousands of songs, every row of the grid reads this on every frame, and the two ways
 * that goes wrong are a `get()` that recomputes the whole library per read and a disk write on the
 * thread that draws:
 * - What the grid reads — [rows], [songbooks], [counts], [visibleColumns] — is
 *   `derivedStateOf`, so it is computed when [songs]/[view] change and cached until they do. As
 *   plain getters they filtered and sorted the entire library on **every** read, and [songbooks]
 *   was read once per visible row: a full scan and sort of the library, twenty-five times a frame.
 * - [selected] and [hiddenColumns] are sets. Membership is tested per row per frame
 *   (`song.sourceFile in selected`), and against a list that is O(n) — quadratic once "select all"
 *   has been used on a big library.
 * - Every function that touches the disk is `suspend` and does the file work under [io]. Only the
 *   load was ever off-thread; [save] wrote every changed file and then re-read the whole folder
 *   inline, which is the same seconds-long freeze the load was moved off the thread to avoid.
 */
// One function per thing the window can do to the library, which is what keeps each of them a
// couple of lines. Splitting the class would split the state they all read.
@Suppress("TooManyFunctions")
class SongLibraryState(private val root: File) {

    private val library = SongLibrary(root)
    private var edits = SongEdits(emptyList())

    var songs by mutableStateOf<List<SongItem>>(emptyList())
        private set
    var view by mutableStateOf(GridView())
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var hiddenColumns by mutableStateOf<Set<SongField>>(emptySet())
        private set

    /**
     * True until the folder has been read once.
     *
     * Starts true so the first frame says "reading" rather than "this library is empty", which is
     * what an unread library and an empty one otherwise look like.
     */
    var isLoading by mutableStateOf(true)
        private set

    /**
     * True while something is being written to the folder.
     *
     * The write is off this thread now, so the window keeps drawing while it runs — which also
     * means the buttons that started it are still live. This is what turns them off, so a save is
     * not started twice over the same edits.
     */
    var isWriting by mutableStateOf(false)
        private set

    /** The song whose editor is open, by source file, or null when none is. */
    var editing by mutableStateOf<String?>(null)
    var isDirty by mutableStateOf(false)
        private set
    var lastOutcome by mutableStateOf<SaveOutcome?>(null)

    val rows: List<SongItem> by derivedStateOf { SongGrid.rows(songs, view) }
    val songbooks: List<String> by derivedStateOf { library.songbooks(songs) }
    val counts: Map<String, Int> by derivedStateOf { SongGrid.countsBySongbook(songs) }
    val visibleColumns: List<SongField> by derivedStateOf {
        listOf(SongField.TITLE) + OPTIONAL_COLUMNS.filterNot { it in hiddenColumns }
    }
    val changedCount: Int get() = edits.changed.size

    /**
     * Reads the folder, with the disk work off the caller's thread.
     *
     * [SongLibrary.load] walks the folder and parses every file in it, which on a real library is
     * seconds. Run from a `LaunchedEffect` that is the composition's own dispatcher, so the window
     * did not appear until it had finished: clicking Song Library Manager did nothing for five
     * seconds and then showed a full grid. Now the window opens immediately and shows that it is
     * reading.
     */
    suspend fun reloadAsync(io: CoroutineDispatcher = Dispatchers.IO) {
        isLoading = true
        try {
            adopt(withContext(io) { library.load() })
        } finally {
            isLoading = false
        }
    }

    private fun adopt(loaded: List<SongItem>) {
        edits = SongEdits(loaded)
        refresh()
        selected = emptySet()
        isLoading = false
    }

    fun edit(sourceFile: String, field: SongField, value: String) {
        edits.edit(sourceFile, field, value)
        refresh()
    }

    fun editAll(fields: Map<SongField, String>) {
        edits.editAll(selected.toList(), fields)
        refresh()
    }

    fun songOf(sourceFile: String): SongItem? = songs.firstOrNull { it.sourceFile == sourceFile }

    fun replace(song: SongItem) {
        SongField.entries.forEach { field -> edits.edit(song.sourceFile, field, field.of(song)) }
        edits.editLyrics(song.sourceFile, song.lyrics, song.secondaryLyrics)
        refresh()
    }

    // ── Selection ─────────────────────────────────────────────────────────────

    fun toggle(sourceFile: String) {
        selected = if (sourceFile in selected) selected - sourceFile else selected + sourceFile
    }

    fun toggleAll() {
        val visible = rows.map { it.sourceFile }
        selected = if (visible.all { it in selected }) selected - visible.toSet() else selected + visible
    }

    fun clearSelection() {
        selected = emptySet()
    }

    // ── Columns ───────────────────────────────────────────────────────────────

    fun toggleColumn(field: SongField) {
        hiddenColumns = if (field in hiddenColumns) hiddenColumns - field else hiddenColumns + field
    }

    fun showAllColumns() {
        hiddenColumns = emptySet()
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    /** Clicking a header sorts by it, and clicking the one already sorted turns it around. */
    fun sortBy(column: SortColumn) {
        view = if (view.sortBy == column) view.copy(ascending = !view.ascending)
        else view.copy(sortBy = column, ascending = true)
    }

    // ── What writes ───────────────────────────────────────────────────────────

    suspend fun save(io: CoroutineDispatcher = Dispatchers.IO): SaveOutcome = writing {
        val original = edits.snapshot()
        val edited = edits.songs
        val outcome = withContext(io) { library.save(original, edited) }
        if (outcome.errors.isEmpty()) edits.markSaved()
        // A rename moves the file the edits are keyed on, so what is on screen has to be re-read.
        adopt(withContext(io) { library.load() })
        lastOutcome = outcome
        outcome
    }

    fun revert() {
        edits.revert()
        refresh()
    }

    suspend fun newSong(titleForNew: String, io: CoroutineDispatcher = Dispatchers.IO) = writing {
        val blank = edits.blank(root, view.songbook.orEmpty(), titleForNew)
        edits.add(withContext(io) { library.writeNew(blank) })
        refresh()
    }

    suspend fun deleteSelected(io: CoroutineDispatcher = Dispatchers.IO): SaveOutcome =
        delete(selectedSongs(), io)

    suspend fun delete(
        songs: List<SongItem>,
        io: CoroutineDispatcher = Dispatchers.IO,
    ): SaveOutcome = writing {
        val outcome = withContext(io) { library.delete(songs) }
        edits.remove(songs.map { it.sourceFile })
        selected = selected - songs.map { it.sourceFile }.toSet()
        refresh()
        lastOutcome = outcome
        outcome
    }

    suspend fun createSongbook(
        name: String,
        assignSelected: Boolean,
        io: CoroutineDispatcher = Dispatchers.IO,
    ): Boolean = writing {
        if (!withContext(io) { library.createSongbook(name) }) return@writing false
        if (assignSelected) editAll(mapOf(SongField.SONGBOOK to name))
        true
    }

    fun selectedSongs(): List<SongItem> = songs.filter { it.sourceFile in selected }

    /** Runs [block] with [isWriting] raised, so the buttons that start a write stay off until it ends. */
    private suspend fun <T> writing(block: suspend () -> T): T {
        isWriting = true
        return try {
            block()
        } finally {
            isWriting = false
        }
    }

    private fun refresh() {
        songs = edits.songs
        isDirty = edits.isDirty
    }
}
