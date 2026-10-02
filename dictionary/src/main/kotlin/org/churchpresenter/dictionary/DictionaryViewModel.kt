package org.churchpresenter.dictionary

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.churchpresenter.dictionary.data.DictionaryFiles
import org.churchpresenter.dictionary.data.InterlinearRepository
import org.churchpresenter.dictionary.data.InterlinearVerse
import org.churchpresenter.dictionary.data.StrongsEntry

enum class DictionaryLanguageFilter { ALL, HEBREW, GREEK }

/**
 * The Strong's dictionary: its entries, the one chosen and where it occurs in scripture, the
 * passage filters, and the Bible verses are quoted from.
 *
 * The work is split across parts sharing one [DictionaryState]: choosing entries and the history
 * ([DictionarySelection]), the passage filters ([DictionaryPassages]) and the Bible picker
 * ([DictionaryBibles]). Their functions are this class's own, by delegation.
 *
 * Stable: every property reads snapshot state in [DictionaryState], so a composable handed the same
 * view model can skip and still hears of every change.
 */
@Stable
class DictionaryViewModel private constructor(
    private val files: DictionaryFiles,
    private val state: DictionaryState,
    private val viewModelScope: CoroutineScope,
    selection: DictionarySelection,
) : DictionarySelectionActions by selection,
    DictionaryPassageActions by DictionaryPassages(state, selection),
    DictionaryBibleActions by DictionaryBibles(state, viewModelScope) {

    constructor(files: DictionaryFiles = DictionaryFiles.Bundled) :
        this(files, DictionaryState(InterlinearRepository(files)), CoroutineScope(Dispatchers.Main + SupervisorJob()))

    private constructor(files: DictionaryFiles, state: DictionaryState, scope: CoroutineScope) :
        this(files, state, scope, DictionarySelection(state, scope))

    val dictLanguage get() = state.dictLanguage
    val dictBibleFile get() = state.dictBibleFile
    val dictBible get() = state.dictBible
    val isDictBibleLoading get() = state.isDictBibleLoading
    val availableDictBibles get() = state.availableDictBibles
    val isLoading get() = state.isLoading
    val entries get() = state.entries
    var searchQuery by state::searchQuery
    val filterLanguage get() = state.filterLanguage
    val selectedEntry get() = state.selectedEntry
    val canGoBack: Boolean get() = state.historyIdx > 0
    val canGoForward: Boolean get() = state.historyIdx < state.history.lastIndex
    val entryBookFilter get() = state.entryBookFilter
    val entryChapterFilter get() = state.entryChapterFilter
    val entryVerseFilter get() = state.entryVerseFilter
    val isInterlinearDataLoaded get() = state.isInterlinearDataLoaded
    val interlinearVerses get() = state.interlinearVerses
    val isInterlinearLoading get() = state.isInterlinearLoading
    val interlinearDisplayLimit get() = state.interlinearDisplayLimit
    val scrollRequestToken get() = state.scrollRequestToken
    val cardBookFilter get() = state.cardBookFilter
    val cardChapterFilter get() = state.cardChapterFilter
    val searchResults: List<StrongsEntry> get() = state.searchResults

    val cardAvailableBooks: List<Int>
        get() = interlinearVerses.map { it.bookId }.distinct().sorted()

    val cardAvailableChapters: List<Int>
        get() {
            val bookId = cardBookFilter ?: return emptyList()
            return interlinearVerses.filter { it.bookId == bookId }.map { it.chapter }.distinct().sorted()
        }

    /** The verses, those matching the entry-list passage filter first. */
    val sortedInterlinearVerses: List<InterlinearVerse>
        get() {
            val bookId = entryBookFilter ?: return interlinearVerses
            val chapter = entryChapterFilter
            val verse = entryVerseFilter
            val (matching, rest) = interlinearVerses.partition { v ->
                v.bookId == bookId &&
                    (chapter == null || v.chapter == chapter) &&
                    (verse == null || v.verseNumber == verse)
            }
            return matching + rest
        }

    /** [sortedInterlinearVerses], narrowed by the detail pane's card filter. */
    val filteredSortedInterlinearVerses: List<InterlinearVerse>
        get() {
            val sorted = sortedInterlinearVerses
            val book = cardBookFilter ?: return sorted
            val chapter = cardChapterFilter
            return sorted.filter { v -> v.bookId == book && (chapter == null || v.chapter == chapter) }
        }

    /** The books the entry-list passage filter offers: those with tagged words in the filtered language. */
    val entryAvailableBooks: List<Int>
        get() {
            if (!isInterlinearDataLoaded) return emptyList()
            val interlinear = state.interlinear
            return when (filterLanguage) {
                DictionaryLanguageFilter.HEBREW -> interlinear.getBooksWithHebrewData()
                DictionaryLanguageFilter.GREEK -> interlinear.getBooksWithGreekData()
                DictionaryLanguageFilter.ALL ->
                    (interlinear.getBooksWithHebrewData() + interlinear.getBooksWithGreekData()).sorted()
            }
        }

    val entryAvailableChapters: List<Int>
        get() {
            val bookId = entryBookFilter ?: return emptyList()
            return state.interlinear.getChaptersForBook(bookId)
        }

    val entryAvailableVerses: List<Int>
        get() {
            val bookId = entryBookFilter ?: return emptyList()
            val chapter = entryChapterFilter ?: return emptyList()
            return state.interlinear.getVersesInChapter(bookId, chapter)
        }

    companion object {
        const val INTERLINEAR_PAGE_SIZE = 50
    }

    fun load() {
        if (state.entries.isNotEmpty() || state.isLoading) return
        state.isLoading = true
        viewModelScope.launch {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val (hFile, gFile) = DictionaryFiles.strongsFor(state.dictLanguage)
                val hEntries = json.decodeFromString<List<StrongsEntry>>(files.read(hFile).decodeToString())
                val gEntries = json.decodeFromString<List<StrongsEntry>>(files.read(gFile).decodeToString())
                state.entries = hEntries.sortedBy { it.numericValue } + gEntries.sortedBy { it.numericValue }
                state.pendingSelectionNumber?.let { num ->
                    onEntrySelected(state.entries.find { it.number == num }, addToHistory = false)
                    state.pendingSelectionNumber = null
                }
            } catch (_: Exception) {
            } finally {
                state.isLoading = false
            }
        }
        // Pre-load interlinear data in background so passage filtering is available (once only)
        if (!state.isInterlinearDataLoaded) {
            viewModelScope.launch {
                try {
                    state.interlinear.ensureGreekLoaded()
                    state.interlinear.ensureHebrewLoaded()
                    state.isInterlinearDataLoaded = true
                } catch (_: Exception) { }
            }
        }
    }

    fun toggleDictLanguage() {
        state.dictLanguage = if (state.dictLanguage == "en") "ru" else "en"
        reload()
    }

    fun reload() {
        // Preserve the current selection across a language swap so the user keeps
        // viewing the same word — load() re-selects it with the new-language text.
        val preserve = state.selectedEntry?.number
        state.entries = emptyList()
        if (preserve == null) onEntrySelected(null, addToHistory = false)
        state.pendingSelectionNumber = preserve
        load()
    }

    fun setLanguageFilter(filter: DictionaryLanguageFilter) {
        state.filterLanguage = filter
        state.clearPassageFilter()
        onEntrySelected(null, addToHistory = false)
    }

    fun dispose() {
        viewModelScope.cancel()
    }
}
