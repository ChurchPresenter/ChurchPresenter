package songlibrary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
    var selected = mutableStateListOf<String>()
        private set
    var hiddenColumns = mutableStateListOf<SongField>()
        private set

    /**
     * True until the folder has been read once.
     *
     * Starts true so the first frame says "reading" rather than "this library is empty", which is
     * what an unread library and an empty one otherwise look like.
     */
    var isLoading by mutableStateOf(true)
        private set

    /** The song whose editor is open, by source file, or null when none is. */
    var editing by mutableStateOf<String?>(null)
    var isDirty by mutableStateOf(false)
        private set
    var lastOutcome by mutableStateOf<SaveOutcome?>(null)

    val rows: List<SongItem> get() = SongGrid.rows(songs, view)
    val songbooks: List<String> get() = library.songbooks(songs)
    val counts: Map<String, Int> get() = SongGrid.countsBySongbook(songs)
    val changedCount: Int get() = edits.changed.size

    fun reload() = adopt(library.load())

    /**
     * The same, with the disk work off the caller's thread.
     *
     * [SongLibrary.load] walks the folder and parses every file in it, which on a real library is
     * seconds. Run from a `LaunchedEffect` that is the composition's own dispatcher, so the window
     * did not appear until it had finished: clicking Song Library did nothing for five seconds and
     * then showed a full grid. Now the window opens immediately and shows that it is reading.
     */
    suspend fun reloadAsync(io: CoroutineDispatcher = Dispatchers.IO) {
        isLoading = true
        try {
            val loaded = withContext(io) { library.load() }
            adopt(loaded)
        } finally {
            isLoading = false
        }
    }

    private fun adopt(loaded: List<SongItem>) {
        edits = SongEdits(loaded)
        refresh()
        selected.clear()
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
        if (!selected.remove(sourceFile)) selected.add(sourceFile)
    }

    fun toggleAll() {
        val visible = rows.map { it.sourceFile }
        if (visible.all { it in selected }) selected.removeAll(visible) else {
            visible.filterNot { it in selected }.forEach { selected.add(it) }
        }
    }

    fun clearSelection() = selected.clear()

    // ── Columns ───────────────────────────────────────────────────────────────

    fun toggleColumn(field: SongField) {
        if (!hiddenColumns.remove(field)) hiddenColumns.add(field)
    }

    fun showAllColumns() = hiddenColumns.clear()

    val visibleColumns: List<SongField>
        get() = listOf(SongField.TITLE) + OPTIONAL_COLUMNS.filterNot { it in hiddenColumns }

    // ── Sorting ───────────────────────────────────────────────────────────────

    /** Clicking a header sorts by it, and clicking the one already sorted turns it around. */
    fun sortBy(column: SortColumn) {
        view = if (view.sortBy == column) view.copy(ascending = !view.ascending)
        else view.copy(sortBy = column, ascending = true)
    }

    // ── What writes ───────────────────────────────────────────────────────────

    fun save(): SaveOutcome {
        val outcome = library.save(edits.snapshot(), edits.songs)
        if (outcome.errors.isEmpty()) edits.markSaved()
        // A rename moves the file the edits are keyed on, so what is on screen has to be re-read.
        reload()
        lastOutcome = outcome
        return outcome
    }

    fun revert() {
        edits.revert()
        refresh()
    }

    fun newSong(titleForNew: String) {
        val blank = edits.blank(root, view.songbook.orEmpty(), titleForNew)
        edits.add(library.writeNew(blank))
        refresh()
    }

    fun deleteSelected(): SaveOutcome = delete(selectedSongs())

    fun delete(songs: List<SongItem>): SaveOutcome {
        val outcome = library.delete(songs)
        edits.remove(songs.map { it.sourceFile })
        songs.forEach { selected.remove(it.sourceFile) }
        refresh()
        lastOutcome = outcome
        return outcome
    }

    fun createSongbook(name: String, assignSelected: Boolean): Boolean {
        if (!library.createSongbook(name)) return false
        if (assignSelected) editAll(mapOf(SongField.SONGBOOK to name))
        return true
    }

    fun selectedSongs(): List<SongItem> = songs.filter { it.sourceFile in selected }

    private fun refresh() {
        songs = edits.songs
        isDirty = edits.isDirty
    }
}
