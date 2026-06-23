package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.data.InterlinearRepository
import org.churchpresenter.app.churchpresenter.data.InterlinearVerse
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.jetbrains.compose.resources.ExperimentalResourceApi

enum class DictionaryLanguageFilter { ALL, HEBREW, GREEK }

class DictionaryViewModel {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var isLoading by mutableStateOf(false)
        private set
    var entries: List<StrongsEntry> by mutableStateOf(emptyList())
        private set
    var searchQuery by mutableStateOf("")
    var filterLanguage by mutableStateOf(DictionaryLanguageFilter.ALL)
        private set
    var selectedEntry by mutableStateOf<StrongsEntry?>(null)
        private set
    var entryBookFilter by mutableStateOf<Int?>(null)
    var entryChapterFilter by mutableStateOf<Int?>(null)
    var entryVerseFilter by mutableStateOf<Int?>(null)
    var isInterlinearDataLoaded by mutableStateOf(false)
        private set
    private var pendingSelectionNumber: String? = null

    // Interlinear state
    private val interlinearRepository = InterlinearRepository()
    private var interlinearJob: Job? = null

    var interlinearVerses by mutableStateOf<List<InterlinearVerse>>(emptyList())
        private set
    var isInterlinearLoading by mutableStateOf(false)
        private set
    var interlinearDisplayLimit by mutableStateOf(INTERLINEAR_PAGE_SIZE)
        private set

    // Verses sorted so entries matching the current left-pane filter appear first
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

    // Entry-list passage filter (filters the left-pane list)
    val entryAvailableBooks: List<Int>
        get() {
            if (!isInterlinearDataLoaded) return emptyList()
            return when (filterLanguage) {
                DictionaryLanguageFilter.HEBREW -> interlinearRepository.getBooksWithHebrewData()
                DictionaryLanguageFilter.GREEK  -> interlinearRepository.getBooksWithGreekData()
                DictionaryLanguageFilter.ALL    ->
                    (interlinearRepository.getBooksWithHebrewData() + interlinearRepository.getBooksWithGreekData()).sorted()
            }
        }

    val entryAvailableChapters: List<Int>
        get() {
            val bookId = entryBookFilter ?: return emptyList()
            return interlinearRepository.getChaptersForBook(bookId)
        }

    val entryAvailableVerses: List<Int>
        get() {
            val bookId = entryBookFilter ?: return emptyList()
            val chapter = entryChapterFilter ?: return emptyList()
            return interlinearRepository.getVersesInChapter(bookId, chapter)
        }

    companion object {
        const val INTERLINEAR_PAGE_SIZE = 50
    }

    val searchResults: List<StrongsEntry>
        get() {
            val pool = when (filterLanguage) {
                DictionaryLanguageFilter.HEBREW -> entries.filter { it.isHebrew }
                DictionaryLanguageFilter.GREEK  -> entries.filter { it.isGreek }
                DictionaryLanguageFilter.ALL    -> entries
            }
            val passageFiltered = if (entryBookFilter != null) {
                val validNumbers = interlinearRepository.getStrongsForBookChapter(
                    entryBookFilter!!, entryChapterFilter, entryVerseFilter
                )
                pool.filter { it.number in validNumbers }
            } else pool
            val q = searchQuery.trim()
            if (q.isEmpty()) return passageFiltered
            val lower = q.lowercase()
            return passageFiltered.filter { entry ->
                entry.number.lowercase().contains(lower) ||
                entry.word.contains(q) ||
                entry.transliteration.lowercase().contains(lower) ||
                entry.pronunciation.lowercase().contains(lower) ||
                entry.definition.lowercase().contains(lower)
            }
        }

    @OptIn(ExperimentalResourceApi::class)
    fun load() {
        if (entries.isNotEmpty() || isLoading) return
        isLoading = true
        viewModelScope.launch {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val hBytes = withContext(Dispatchers.IO) {
                    Res.readBytes("files/dictionary/strongs_h.json")
                }
                val gBytes = withContext(Dispatchers.IO) {
                    Res.readBytes("files/dictionary/strongs_g.json")
                }
                val hEntries = json.decodeFromString<List<StrongsEntry>>(hBytes.decodeToString())
                val gEntries = json.decodeFromString<List<StrongsEntry>>(gBytes.decodeToString())
                entries = hEntries.sortedBy { it.numericValue } + gEntries.sortedBy { it.numericValue }
                pendingSelectionNumber?.let { num ->
                    onEntrySelected(entries.find { it.number == num })
                    pendingSelectionNumber = null
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
        // Pre-load interlinear data in background so passage filtering is available
        viewModelScope.launch {
            try {
                interlinearRepository.ensureGreekLoaded()
                interlinearRepository.ensureHebrewLoaded()
                isInterlinearDataLoaded = true
            } catch (_: Exception) { }
        }
    }

    fun setLanguageFilter(filter: DictionaryLanguageFilter) {
        filterLanguage = filter
        clearPassageFilter()
        onEntrySelected(null)
    }

    fun filterEntryListByBook(bookId: Int?) {
        entryBookFilter = bookId
        entryChapterFilter = null
        entryVerseFilter = null
        if (bookId != null) reselectIfNeeded()
    }

    fun filterEntryListByChapter(chapter: Int?) {
        entryChapterFilter = chapter
        entryVerseFilter = null
        if (chapter != null) reselectIfNeeded()
    }

    fun filterEntryListByVerse(verse: Int?) {
        entryVerseFilter = verse
        if (verse != null) reselectIfNeeded()
    }

    // If the current selection is no longer in the filtered list, pick the first visible entry.
    private fun reselectIfNeeded() {
        val results = searchResults
        val current = selectedEntry
        if (current != null && results.any { it.number == current.number }) return
        val first = results.firstOrNull()
        if (first != null) onEntrySelected(first)
    }

    private fun clearPassageFilter() {
        entryBookFilter = null
        entryChapterFilter = null
        entryVerseFilter = null
    }

    fun onEntrySelected(entry: StrongsEntry?) {
        selectedEntry = entry
        interlinearJob?.cancel()
        interlinearVerses = emptyList()
        interlinearDisplayLimit = INTERLINEAR_PAGE_SIZE
        if (entry == null) return
        interlinearJob = viewModelScope.launch {
            isInterlinearLoading = true
            try {
                if (entry.isGreek) interlinearRepository.ensureGreekLoaded()
                else interlinearRepository.ensureHebrewLoaded()
                interlinearVerses = interlinearRepository.getVersesForEntry(entry.number)
            } finally {
                isInterlinearLoading = false
            }
        }
    }

    fun showMoreInterlinear() {
        interlinearDisplayLimit += INTERLINEAR_PAGE_SIZE
    }

    fun selectByNumber(number: String) {
        val normalized = number.uppercase()
        val found = entries.find { it.number == normalized }
        if (found != null) {
            // If navigating to an entry not visible under the current passage filter, clear it
            if (entryBookFilter != null) {
                val visible = interlinearRepository.getStrongsForBookChapter(
                    entryBookFilter!!, entryChapterFilter, entryVerseFilter
                )
                if (found.number !in visible) clearPassageFilter()
            }
            onEntrySelected(found)
        } else if (normalized.isNotEmpty()) {
            pendingSelectionNumber = normalized
        }
    }

    fun dispose() {
        viewModelScope.cancel()
    }
}
