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
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.InterlinearRepository
import org.churchpresenter.app.churchpresenter.data.InterlinearVerse
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.io.File
import java.nio.charset.StandardCharsets

enum class DictionaryLanguageFilter { ALL, HEBREW, GREEK }

class DictionaryViewModel {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var dictLanguage by mutableStateOf("en")
        private set
    var dictBibleFile by mutableStateOf("")
        private set
    var dictBible by mutableStateOf<Bible?>(null)
        private set
    var isDictBibleLoading by mutableStateOf(false)
        private set
    var availableDictBibles by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var entries: List<StrongsEntry> by mutableStateOf(emptyList())
        private set
    var searchQuery by mutableStateOf("")
    var filterLanguage by mutableStateOf(DictionaryLanguageFilter.ALL)
        private set
    var selectedEntry by mutableStateOf<StrongsEntry?>(null)
        private set

    // Back/Forward navigation history (stores Strong's numbers)
    private val history = mutableListOf<String>()
    private var historyIdx by mutableStateOf(-1)
    val canGoBack: Boolean get() = historyIdx > 0
    val canGoForward: Boolean get() = historyIdx < history.lastIndex
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
    var scrollRequestToken by mutableStateOf(0)
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
                val hFile = if (dictLanguage == "ru") "files/dictionary/strongs_h_ru.json"
                            else "files/dictionary/strongs_h.json"
                val gFile = if (dictLanguage == "ru") "files/dictionary/strongs_g_ru.json"
                            else "files/dictionary/strongs_g.json"
                val hBytes = withContext(Dispatchers.IO) { Res.readBytes(hFile) }
                val gBytes = withContext(Dispatchers.IO) { Res.readBytes(gFile) }
                val hEntries = json.decodeFromString<List<StrongsEntry>>(hBytes.decodeToString())
                val gEntries = json.decodeFromString<List<StrongsEntry>>(gBytes.decodeToString())
                entries = hEntries.sortedBy { it.numericValue } + gEntries.sortedBy { it.numericValue }
                pendingSelectionNumber?.let { num ->
                    onEntrySelected(entries.find { it.number == num }, addToHistory = false)
                    pendingSelectionNumber = null
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
        // Pre-load interlinear data in background so passage filtering is available (once only)
        if (!isInterlinearDataLoaded) {
            viewModelScope.launch {
                try {
                    interlinearRepository.ensureGreekLoaded()
                    interlinearRepository.ensureHebrewLoaded()
                    isInterlinearDataLoaded = true
                } catch (_: Exception) { }
            }
        }
    }

    fun loadAvailableBibles(directory: String) {
        if (directory.isEmpty()) { availableDictBibles = emptyList(); return }
        viewModelScope.launch {
            val dir = File(directory)
            if (!dir.exists() || !dir.isDirectory) { availableDictBibles = emptyList(); return@launch }
            availableDictBibles = withContext(Dispatchers.IO) {
                dir.listFiles { f -> f.extension.lowercase() == "spb" }
                    ?.sortedBy { it.name }
                    ?.map { f -> f.absolutePath to extractBibleTitle(f) }
                    ?: emptyList()
            }
        }
    }

    fun setDictBible(filePath: String) {
        if (filePath == dictBibleFile) return
        dictBibleFile = filePath
        if (filePath.isEmpty()) { dictBible = null; return }
        isDictBibleLoading = true
        viewModelScope.launch {
            try {
                dictBible = withContext(Dispatchers.IO) { Bible().apply { loadFromSpb(filePath) } }
            } catch (_: Exception) {
                dictBible = null
            } finally {
                isDictBibleLoading = false
            }
        }
    }

    private fun extractBibleTitle(file: File): String {
        return try {
            file.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                repeat(10) {
                    val line = reader.readLine() ?: return@use file.nameWithoutExtension
                    if (line.startsWith("##Title:")) return@use line.substring(8).trim()
                }
                file.nameWithoutExtension
            }
        } catch (_: Exception) {
            file.nameWithoutExtension
        }
    }

    fun toggleDictLanguage() {
        dictLanguage = if (dictLanguage == "en") "ru" else "en"
        reload()
    }

    fun reload() {
        // Preserve the current selection across a language swap so the user keeps
        // viewing the same word — load() re-selects it with the new-language text.
        val preserve = selectedEntry?.number
        entries = emptyList()
        if (preserve == null) onEntrySelected(null, addToHistory = false)
        pendingSelectionNumber = preserve
        load()
    }

    fun goBack() {
        if (!canGoBack) return
        historyIdx--
        val number = history[historyIdx]
        val entry = entries.find { it.number == number }
        onEntrySelected(entry, addToHistory = false)
    }

    fun goForward() {
        if (!canGoForward) return
        historyIdx++
        val number = history[historyIdx]
        val entry = entries.find { it.number == number }
        onEntrySelected(entry, addToHistory = false)
    }

    fun setLanguageFilter(filter: DictionaryLanguageFilter) {
        filterLanguage = filter
        clearPassageFilter()
        onEntrySelected(null, addToHistory = false)
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
        if (first != null) onEntrySelected(first, addToHistory = false)
    }

    private fun clearPassageFilter() {
        entryBookFilter = null
        entryChapterFilter = null
        entryVerseFilter = null
    }

    fun onEntrySelected(entry: StrongsEntry?, addToHistory: Boolean = true, scrollToEntry: Boolean = true) {
        selectedEntry = entry
        if (scrollToEntry && entry != null) scrollRequestToken++
        interlinearJob?.cancel()
        interlinearVerses = emptyList()
        interlinearDisplayLimit = INTERLINEAR_PAGE_SIZE
        if (entry == null) return
        if (addToHistory) {
            // Truncate any forward history before pushing the new entry
            if (historyIdx < history.lastIndex) {
                history.subList(historyIdx + 1, history.size).clear()
            }
            history.add(entry.number)
            historyIdx = history.lastIndex
        }
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
            onEntrySelected(found, scrollToEntry = false)
        } else if (normalized.isNotEmpty()) {
            pendingSelectionNumber = normalized
        }
    }

    fun dispose() {
        viewModelScope.cancel()
    }
}
