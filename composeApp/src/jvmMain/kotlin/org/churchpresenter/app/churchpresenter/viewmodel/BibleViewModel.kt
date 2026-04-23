package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.AppSettings
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.BibleBookNames
import org.churchpresenter.app.churchpresenter.data.BibleSearch
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import java.io.File

class BibleViewModel(
    private var appSettings: AppSettings,
    private val onBibleLoaded: ((bible: Bible, translation: String) -> Unit)? = null
) {
    private val _primaryBible = mutableStateOf<Bible?>(null)
    val primaryBible: State<Bible?> = _primaryBible

    private val _secondaryBible = mutableStateOf<Bible?>(null)
    val secondaryBible: State<Bible?> = _secondaryBible

    private val _books = mutableStateOf<List<String>>(emptyList())
    val books: State<List<String>> = _books

    private val _selectedBookIndex = mutableStateOf(0)
    val selectedBookIndex: State<Int> = _selectedBookIndex

    private val _selectedChapter = mutableStateOf(1)
    val selectedChapter: State<Int> = _selectedChapter

    private val _selectedVerseIndex = mutableStateOf(0)
    val selectedVerseIndex: State<Int> = _selectedVerseIndex

    private val _verses = mutableStateOf<List<String>>(emptyList())
    val verses: State<List<String>> = _verses

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _selectedScopeIndex = mutableStateOf(0)  // 0 = Entire Bible, 1 = Current Book
    val selectedScopeIndex: State<Int> = _selectedScopeIndex

    private val _selectedModeIndex = mutableStateOf(0)  // 0 = Contains, 1 = Exact Match
    val selectedModeIndex: State<Int> = _selectedModeIndex

    private val _bookSearchQuery = mutableStateOf("")
    val bookSearchQuery: State<String> = _bookSearchQuery

    private val _chapterSearchQuery = mutableStateOf("")
    val chapterSearchQuery: State<String> = _chapterSearchQuery

    private val _verseSearchQuery = mutableStateOf("")
    val verseSearchQuery: State<String> = _verseSearchQuery

    // Filtered list states — updated whenever underlying data or queries change
    private val _filteredBooks = mutableStateOf<List<String>>(emptyList())
    val filteredBooks: State<List<String>> = _filteredBooks

    private val _filteredChapters = mutableStateOf<List<String>>(emptyList())
    val filteredChapters: State<List<String>> = _filteredChapters

    private val _filteredVerses = mutableStateOf<List<String>>(emptyList())
    val filteredVerses: State<List<String>> = _filteredVerses

    private val _searchResults = mutableStateOf<List<BibleSearch>>(emptyList())
    val searchResults: State<List<BibleSearch>> = _searchResults

    private val _isSearchMode = mutableStateOf(false)
    val isSearchMode: State<Boolean> = _isSearchMode

    // Dynamic book name mapping for cross-language search
    private val _bookNameMapping = mutableStateOf<Map<String, String>>(emptyMap())
    val bookNameMapping: State<Map<String, String>> = _bookNameMapping

    private val _englishBookNames = mutableStateOf<List<String>>(emptyList())

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    // Increments only when the user explicitly selects a verse — never on book/chapter/load resets.
    // BibleTab keys its onVerseSelected LaunchedEffect on this so presenter is not updated
    // when the user is just browsing books/chapters while presenting.
    private val _verseSelectionToken = mutableStateOf(0)
    val verseSelectionToken: State<Int> = _verseSelectionToken

    // Multi-verse selection mode
    private val _multiVerseEnabled = mutableStateOf(false)
    val multiVerseEnabled: State<Boolean> = _multiVerseEnabled

    private val _selectedVerseIndices = mutableStateListOf<Int>()
    val selectedVerseIndices: List<Int> get() = _selectedVerseIndices

    // True only after the full verse index (phase 3) is loaded — not just book names
    // MutableStateFlow so coroutines can suspend on it with .first { it }
    private val _isFullyLoadedFlow = MutableStateFlow(false)
    val isFullyLoadedFlow: StateFlow<Boolean> = _isFullyLoadedFlow.asStateFlow()
    val isFullyLoaded: Boolean get() = _isFullyLoadedFlow.value

    // History of presented verses (most recent first)
    data class HistoryEntry(
        val bookName: String,
        val chapter: Int,
        val verseNumber: Int,
        val verseText: String,
        val verseRange: String = ""
    ) {
        val displayText: String get() = if (verseRange.isNotEmpty()) "$bookName $chapter:$verseRange" else "$bookName $chapter:$verseNumber"
    }

    private val _history = mutableStateListOf<HistoryEntry>()
    val history: List<HistoryEntry> get() = _history

    fun addToHistory(bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String = "") {
        val entry = HistoryEntry(bookName, chapter, verseNumber, verseText, verseRange)
        // Remove duplicate if exists
        _history.removeAll { it.bookName == bookName && it.chapter == chapter && it.verseNumber == verseNumber && it.verseRange == verseRange }
        // Add to front
        _history.add(0, entry)
        // Keep max 50 entries
        while (_history.size > 50) _history.removeLast()
    }

    fun clearHistory() { _history.clear() }

    /** No longer driven by a checkbox — kept for any legacy call sites. */
    fun toggleMultiVerse(enabled: Boolean) {
        if (!enabled) {
            _selectedVerseIndices.clear()
            _multiVerseEnabled.value = false
        }
    }

    fun clearMultiVerseSelection() {
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false
    }

    /**
     * Ctrl/Cmd + Click — toggle the individual verse in the multi-selection.
     * On the first ctrl-click the current single selection is also included so
     * the user can start a multi-select from wherever they are.
     */
    fun ctrlClickVerse(verseIndex: Int) {
        if (verseIndex < 0 || verseIndex >= _verses.value.size) return
        if (_selectedVerseIndices.contains(verseIndex)) {
            _selectedVerseIndices.remove(verseIndex)
            if (_selectedVerseIndices.isEmpty()) {
                // Deselected last item — fall back to plain single selection
                _selectedVerseIndex.value = verseIndex
            }
        } else {
            // On the very first ctrl-click include the current anchor too
            if (_selectedVerseIndices.isEmpty()) {
                val anchor = _selectedVerseIndex.value
                if (anchor >= 0 && anchor < _verses.value.size && anchor != verseIndex) {
                    _selectedVerseIndices.add(anchor)
                }
            }
            _selectedVerseIndices.add(verseIndex)
            _selectedVerseIndex.value = verseIndex   // update anchor
        }
        _multiVerseEnabled.value = _selectedVerseIndices.isNotEmpty()
        _verseSelectionToken.value++
    }

    /**
     * Shift + Click — range-select from the current anchor to [targetIndex].
     * The anchor stays fixed; repeated shift-clicks extend/shrink from it.
     */
    fun shiftClickVerse(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex >= _verses.value.size) return
        val anchor = _selectedVerseIndex.value.coerceIn(0, _verses.value.size - 1)
        val from = minOf(anchor, targetIndex)
        val to   = maxOf(anchor, targetIndex)
        _selectedVerseIndices.clear()
        (from..to).forEach { _selectedVerseIndices.add(it) }
        _multiVerseEnabled.value = _selectedVerseIndices.size > 1
        _verseSelectionToken.value++
    }

    /** @deprecated Use [ctrlClickVerse] or [shiftClickVerse]. Kept for internal use. */
    private fun toggleVerseInSelection(verseIndex: Int) {
        if (verseIndex < 0 || verseIndex >= _verses.value.size) return
        if (_selectedVerseIndices.contains(verseIndex)) {
            _selectedVerseIndices.remove(verseIndex)
        } else {
            _selectedVerseIndices.add(verseIndex)
        }
        _multiVerseEnabled.value = _selectedVerseIndices.isNotEmpty()
        _verseSelectionToken.value++
    }

    fun formatVerseRange(numbers: List<Int>): String {
        if (numbers.isEmpty()) return ""
        if (numbers.size == 1) return numbers.first().toString()
        val sorted = numbers.sorted()
        val isContiguous = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
        return if (isContiguous) "${sorted.first()}-${sorted.last()}"
        else sorted.joinToString(",")
    }

    companion object {
        private const val CANONICAL_BOOK_COUNT = 66
    }

    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Returns at most 66 canonical books from a loaded Bible. */
    private fun Bible.getCanonicalBooks(): List<String> = getBooks().take(CANONICAL_BOOK_COUNT)

    init {
        _selectedScopeIndex.value = 0
        _selectedModeIndex.value = 0
        loadBibles()
    }

    fun updateSettings(newSettings: AppSettings) {
        appSettings = newSettings
        loadBibles()
    }

    fun loadBibles() {
        viewModelScope.launch {
            _isLoading.value = true
            _isFullyLoadedFlow.value = false
            try {
                val primaryPath = if (appSettings.bibleSettings.primaryBible.isNotEmpty() &&
                    appSettings.bibleSettings.storageDirectory.isNotEmpty()
                ) File(appSettings.bibleSettings.storageDirectory, appSettings.bibleSettings.primaryBible)
                    .takeIf { it.exists() }
                else null

                val secondaryPath = if (appSettings.bibleSettings.secondaryBible.isNotEmpty() &&
                    appSettings.bibleSettings.storageDirectory.isNotEmpty()
                ) File(appSettings.bibleSettings.storageDirectory, appSettings.bibleSettings.secondaryBible)
                    .takeIf { it.exists() }
                else null

                // ── Phase 1: load book names only (header scan — very fast) ──────────
                val bookNameMappingDeferred = async(Dispatchers.IO) {
                    try { BibleBookNames.getBookNameMapping() } catch (_: Exception) { emptyMap() }
                }
                val englishBookNamesDeferred = async(Dispatchers.IO) {
                    try { BibleBookNames.getEnglishBookNames() } catch (_: Exception) { emptyList() }
                }
                val quickPrimary = primaryPath?.let { path ->
                    async(Dispatchers.IO) {
                        try { Bible().apply { loadBooksOnly(path.absolutePath) } }
                        catch (_: Exception) { null }
                    }
                }

                // Show book names as soon as the header scan finishes
                val booksOnlyBible = quickPrimary?.await()
                _bookNameMapping.value = bookNameMappingDeferred.await()
                _englishBookNames.value = englishBookNamesDeferred.await()

                if (booksOnlyBible != null && booksOnlyBible.getBookCount() > 0) {
                    _primaryBible.value = booksOnlyBible
                    _books.value = booksOnlyBible.getCanonicalBooks()
                    refreshFilteredLists()
                }

                // ── Phase 2: load full verse data in background ────────────────────
                val primaryDeferred = primaryPath?.let { path ->
                    async(Dispatchers.IO) {
                        try { Bible().apply { loadFromSpb(path.absolutePath) } }
                        catch (e: Exception) { e.printStackTrace(); null }
                    }
                }
                val secondaryDeferred = secondaryPath?.let { path ->
                    async(Dispatchers.IO) {
                        try { Bible().apply { loadFromSpb(path.absolutePath) } }
                        catch (e: Exception) { e.printStackTrace(); null }
                    }
                }

                val primary = primaryDeferred?.await()
                val secondary = secondaryDeferred?.await()

                // ── Phase 3: update state with full data and load first chapter ─────
                _primaryBible.value = primary
                _secondaryBible.value = secondary

                if (primary != null) {
                    _books.value = primary.getCanonicalBooks()
                    val bookId = primary.getBookId(_selectedBookIndex.value)
                    val chapterResult = withContext(Dispatchers.IO) {
                        primary.getChapter(bookId, _selectedChapter.value)
                    }
                    _verses.value = chapterResult.verses
                    _selectedVerseIndex.value = 0
                    refreshFilteredLists()
                    onBibleLoaded?.invoke(primary, appSettings.bibleSettings.primaryBible)
                } else if (booksOnlyBible == null) {
                    _books.value = emptyList()
                    _verses.value = emptyList()
                    refreshFilteredLists()
                }
            } finally {
                _isLoading.value = false
                _isFullyLoadedFlow.value = true
            }
        }
    }

    fun loadChapter(bookIndex: Int, chapter: Int) {
        _primaryBible.value?.let { bible ->
            val bookCount = minOf(bible.getBookCount(), CANONICAL_BOOK_COUNT)
            if (bookCount > 0) {
                val clampedIndex = bookIndex.coerceIn(0, bookCount - 1)
                _selectedBookIndex.value = clampedIndex
                _selectedChapter.value = chapter
                _selectedVerseIndex.value = 0
                viewModelScope.launch {
                    val bookId = bible.getBookId(clampedIndex)
                    val chapterResult = withContext(Dispatchers.IO) {
                        bible.getChapter(bookId, chapter)
                    }
                    _verses.value = chapterResult.verses
                    refreshFilteredLists()
                }
            }
        }
    }

    fun selectBook(bookIndex: Int) {
        _selectedBookIndex.value = bookIndex
        _selectedChapter.value = 1
        _selectedVerseIndex.value = 0
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false
        loadChapter(bookIndex, 1)
    }

    fun selectChapter(chapter: Int) {
        _selectedChapter.value = chapter
        _selectedVerseIndex.value = 0
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false
        loadChapter(_selectedBookIndex.value, chapter)
    }

    /** Plain click — always selects a single verse and clears any multi-selection. */
    fun selectVerse(verseIndex: Int) {
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false
        if (verseIndex >= 0 && verseIndex < _verses.value.size) {
            _selectedVerseIndex.value = verseIndex
            _verseSelectionToken.value++
        } else {
            _selectedVerseIndex.value = 0
        }
    }

    /**
     * Parses a verse range string (e.g. "1-3", "2,4", "1-3,5") into a list of verse numbers.
     * Handles both hyphen ranges and comma-separated lists, including mixed formats.
     */
    private fun parseVerseNumbers(rangeStr: String): List<Int> {
        val result = mutableListOf<Int>()
        rangeStr.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val bounds = trimmed.split("-")
                val from = bounds.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach
                val to   = bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: return@forEach
                (from..to).forEach { result.add(it) }
            } else {
                trimmed.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    fun selectVerseByDetails(bookName: String, chapter: Int, verseNumber: Int, verseRange: String = ""): Boolean {
        val bookIndex = _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }
        if (bookIndex < 0) return false

        _selectedBookIndex.value = bookIndex
        _selectedChapter.value = chapter
        _selectedVerseIndex.value = 0
        // Always clear multi-selection when navigating to a specific verse (e.g. from schedule)
        // so stale indices from a different chapter don't highlight wrong verses
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false

        viewModelScope.launch {
            // Wait for full verse data (phase 3) — books-only bible has no chapter index
            if (!_isFullyLoadedFlow.value) {
                _isFullyLoadedFlow.first { it }
            }

            val bible = _primaryBible.value ?: return@launch
            val bookCount = minOf(bible.getBookCount(), CANONICAL_BOOK_COUNT)
            if (bookCount == 0) return@launch

            val clampedIndex = bookIndex.coerceIn(0, bookCount - 1)
            val bookId = bible.getBookId(clampedIndex)

            val chapterResult = withContext(Dispatchers.IO) {
                bible.getChapter(bookId, chapter)
            }
            val chapterVerses = chapterResult.verses
            _verses.value = chapterVerses

            // Use "N. " (with trailing space) to avoid "3." matching "13." or "23."
            val verseIndex = chapterVerses.indexOfFirst { it.startsWith("$verseNumber. ") }
            _selectedVerseIndex.value = if (verseIndex >= 0) verseIndex else 0

            // Restore multi-verse selection when a range is provided (e.g. from schedule click)
            if (verseRange.isNotEmpty()) {
                val verseNumbers = parseVerseNumbers(verseRange)
                if (verseNumbers.size > 1) {
                    _selectedVerseIndices.clear()
                    for (vNum in verseNumbers) {
                        val vIdx = chapterVerses.indexOfFirst { it.startsWith("$vNum. ") }
                        if (vIdx >= 0) _selectedVerseIndices.add(vIdx)
                    }
                    _multiVerseEnabled.value = _selectedVerseIndices.size > 1
                }
            }

            _verseSelectionToken.value++

            refreshFilteredLists()
        }
        return true
    }

    fun getChaptersForCurrentBook(): List<String> {
        _primaryBible.value?.let { bible ->
            // getChapterCount expects 0-based book index
            val bookIndex = _selectedBookIndex.value
            val chapterCount = bible.getChapterCount(bookIndex)
            val count = if (chapterCount > 0) chapterCount else 1
            return (1..count).map { it.toString() }
        }
        return emptyList()
    }

    fun getFilteredBooks(): List<String> {
        val query = _bookSearchQuery.value

        if (query.isEmpty()) {
            return _books.value
        }

        // STEP 1: Try direct match (case-insensitive) against actual book names
        val directMatch = _books.value.filter {
            it.contains(query, ignoreCase = true)
        }

        if (directMatch.isNotEmpty()) {
            return directMatch
        }

        // STEP 2: Try cross-language search using English book names
        val isLatin = query.all { it.isLetter() && it.code < 128 }

        if (!isLatin) {
            return emptyList()
        }

        // Create a list of standard English book order (hardcoded for reliability)
        // This must match the order of books in Bible files (66 books in standard order)
        val standardEnglishBooks = listOf(
            "genesis", "exodus", "leviticus", "numbers", "deuteronomy", "joshua", "judges", "ruth",
            "1 samuel", "2 samuel", "1 kings", "2 kings", "1 chronicles", "2 chronicles",
            "ezra", "nehemiah", "esther", "job", "psalms", "proverbs", "ecclesiastes", "song of solomon",
            "isaiah", "jeremiah", "lamentations", "ezekiel", "daniel", "hosea", "joel", "amos",
            "obadiah", "jonah", "micah", "nahum", "habakkuk", "zephaniah", "haggai", "zechariah", "malachi",
            "matthew", "mark", "luke", "john", "acts", "romans",
            "1 corinthians", "2 corinthians", "galatians", "ephesians", "philippians", "colossians",
            "1 thessalonians", "2 thessalonians", "1 timothy", "2 timothy", "titus", "philemon",
            "hebrews", "james", "1 peter", "2 peter", "1 john", "2 john", "3 john", "jude", "revelation"
        )

        // Find book IDs of matching English books (index+1 = standard book ID)
        val matchingBookIds = standardEnglishBooks.mapIndexedNotNull { index, englishName ->
            if (englishName.contains(query, ignoreCase = true)) {
                index + 1
            } else {
                null
            }
        }

        if (matchingBookIds.isEmpty()) {
            return emptyList()
        }

        // Look up display names by book ID (works regardless of display order)
        val bible = _primaryBible.value
        val mappedResults = matchingBookIds.mapNotNull { bookId ->
            bible?.getBookName(bookId)
        }.filter { it in _books.value }

        return mappedResults
    }

    fun getFilteredChapters(): List<String> {
        val chapters = getChaptersForCurrentBook()
        val query = _chapterSearchQuery.value
        if (query.isEmpty()) {
            return chapters
        }
        return chapters.filter { it.contains(query, ignoreCase = true) }
    }

    fun getFilteredVerses(): List<String> {
        val query = _verseSearchQuery.value
        if (query.isEmpty()) {
            return _verses.value
        }
        return _verses.value.filter { it.contains(query, ignoreCase = true) }
    }

    fun getSelectedVerses(): List<SelectedVerse> {
        val verseList = mutableListOf<SelectedVerse>()

        // Safety checks: ensure we have verses
        if (_verses.value.isEmpty()) {
            return verseList
        }

        val bookId = _primaryBible.value?.getBookId(_selectedBookIndex.value) ?: (_selectedBookIndex.value + 1)

        // ── Multi-verse mode: combine selected verses into one SelectedVerse per bible ──
        if (_multiVerseEnabled.value && _selectedVerseIndices.isNotEmpty()) {
            val sortedIndices = _selectedVerseIndices.sorted()
            val primaryTexts = mutableListOf<String>()
            val secondaryTexts = mutableListOf<String>()
            val verseNumbers = mutableListOf<Int>()
            var bookName = ""
            var secondaryBookName = ""

            for (idx in sortedIndices) {
                val verse = _verses.value.getOrNull(idx) ?: continue
                val vNum = verse.substringBefore(". ").toIntOrNull() ?: continue
                verseNumbers.add(vNum)

                _primaryBible.value?.getVerseDetails(bookId, _selectedChapter.value, vNum)?.let { (bk, text, _) ->
                    if (bookName.isEmpty()) bookName = bk
                    primaryTexts.add(text)
                }
                _secondaryBible.value?.getVerseDetails(bookId, _selectedChapter.value, vNum)?.let { (bk, text, _) ->
                    if (secondaryBookName.isEmpty()) secondaryBookName = bk
                    secondaryTexts.add(text)
                }
            }

            val rangeStr = formatVerseRange(verseNumbers)

            if (primaryTexts.isNotEmpty()) {
                verseList.add(
                    SelectedVerse(
                        bibleAbbreviation = _primaryBible.value?.getBibleAbbreviation() ?: "",
                        bibleName = _primaryBible.value?.getBibleTitle() ?: "",
                        bookName = bookName,
                        chapter = _selectedChapter.value,
                        verseNumber = verseNumbers.first(),
                        verseText = primaryTexts.joinToString(" "),
                        verseRange = rangeStr
                    )
                )
            }
            if (secondaryTexts.isNotEmpty()) {
                verseList.add(
                    SelectedVerse(
                        bibleAbbreviation = _secondaryBible.value?.getBibleAbbreviation() ?: "",
                        bibleName = _secondaryBible.value?.getBibleTitle() ?: "",
                        bookName = secondaryBookName,
                        chapter = _selectedChapter.value,
                        verseNumber = verseNumbers.first(),
                        verseText = secondaryTexts.joinToString(" "),
                        verseRange = rangeStr
                    )
                )
            }
            return verseList
        }

        // ── Single-verse mode ──
        // Clamp the index to valid range
        val safeIndex = _selectedVerseIndex.value.coerceIn(0, _verses.value.size - 1)

        // Update index if it was clamped
        if (safeIndex != _selectedVerseIndex.value) {
            _selectedVerseIndex.value = safeIndex
        }

        val verse = _verses.value[safeIndex]
        val verseNumber = verse.substringBefore(". ").toIntOrNull() ?: 1

        // Add primary Bible verse
        _primaryBible.value?.getVerseDetails(bookId, _selectedChapter.value, verseNumber)?.let { (bookName, verseText, _) ->
            val abbreviation = _primaryBible.value?.getBibleAbbreviation() ?: ""
            verseList.add(
                SelectedVerse(
                    bibleAbbreviation = abbreviation,
                    bibleName = _primaryBible.value?.getBibleTitle() ?: "",
                    bookName = bookName,
                    chapter = _selectedChapter.value,
                    verseNumber = verseNumber,
                    verseText = verseText
                )
            )
        }

        // Add secondary Bible verse if available
        _secondaryBible.value?.getVerseDetails(bookId, _selectedChapter.value, verseNumber)?.let { (bookName, verseText, _) ->
            val abbreviation = _secondaryBible.value?.getBibleAbbreviation() ?: ""
            verseList.add(
                SelectedVerse(
                    bibleAbbreviation = abbreviation,
                    bibleName = _secondaryBible.value?.getBibleTitle() ?: "",
                    bookName = bookName,
                    chapter = _selectedChapter.value,
                    verseNumber = verseNumber,
                    verseText = verseText
                )
            )
        }

        return verseList
    }

    /** Returns verse numbers currently selected in multi-verse mode. */
    fun getSelectedVerseNumbers(): List<Int> {
        return _selectedVerseIndices.sorted().mapNotNull { idx ->
            _verses.value.getOrNull(idx)?.substringBefore(". ")?.toIntOrNull()
        }
    }

    fun navigatePreviousVerse(): Boolean {
        if (_verses.value.isNotEmpty() && _selectedVerseIndex.value > 0) {
            _selectedVerseIndices.clear()
            _multiVerseEnabled.value = false
            _selectedVerseIndex.value--
            _verseSelectionToken.value++
            return true
        }
        return false
    }

    fun navigateNextVerse(): Boolean {
        if (_verses.value.isNotEmpty() && _selectedVerseIndex.value < _verses.value.size - 1) {
            _selectedVerseIndices.clear()
            _multiVerseEnabled.value = false
            _selectedVerseIndex.value++
            _verseSelectionToken.value++
            return true
        }
        return false
    }

    fun navigatePreviousChapter(): Boolean {
        if (_selectedChapter.value > 1) {
            selectChapter(_selectedChapter.value - 1)
            return true
        }
        return false
    }

    fun navigateNextChapter(): Boolean {
        _primaryBible.value?.let { bible ->
            // getChapterCount expects 0-based book index
            val maxChapter = bible.getChapterCount(_selectedBookIndex.value)
            if (_selectedChapter.value < maxChapter) {
                selectChapter(_selectedChapter.value + 1)
                return true
            }
        }
        return false
    }

    private fun refreshFilteredLists() {
        _filteredBooks.value = getFilteredBooks()
        _filteredChapters.value = getFilteredChapters()
        _filteredVerses.value = getFilteredVerses()
    }

    /**
     * Adds the currently selected Bible verse(s) to the given schedule.
     * When multiple verses are selected the joined text and range are forwarded.
     * The multi-verse selection is cleared after a successful add.
     * Returns true if the verse was successfully added, false otherwise.
     */
    fun addCurrentVerseToSchedule(
        onAdd: (bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String) -> Unit
    ): Boolean {
        if (_verses.value.isEmpty()) return false
        val idx = _selectedVerseIndex.value
        if (idx < 0 || idx >= _verses.value.size) return false
        val selectedVerses = getSelectedVerses()
        if (selectedVerses.isEmpty()) return false
        val verse = selectedVerses[0]
        onAdd(verse.bookName, verse.chapter, verse.verseNumber, verse.verseText, verse.verseRange)
        // Clear multi-selection so the next pick starts clean
        if (_multiVerseEnabled.value) {
            clearMultiVerseSelection()
        }
        return true
    }

    // Keep legacy overload for callers that still pass a ScheduleViewModel
    fun addCurrentVerseToSchedule(scheduleViewModel: ScheduleViewModel): Boolean =
        addCurrentVerseToSchedule { bookName, chapter, verseNumber, verseText, verseRange ->
            scheduleViewModel.addBibleVerse(bookName, chapter, verseNumber, verseText, verseRange)
        }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectedScopeIndex(index: Int) {
        _selectedScopeIndex.value = index
    }

    fun updateSelectedModeIndex(index: Int) {
        _selectedModeIndex.value = index
    }

    fun updateBookSearchQuery(query: String) {
        _bookSearchQuery.value = query
        refreshFilteredLists()
    }

    fun updateChapterSearchQuery(query: String) {
        _chapterSearchQuery.value = query
        refreshFilteredLists()
    }

    fun updateVerseSearchQuery(query: String) {
        _verseSearchQuery.value = query
        refreshFilteredLists()
    }

    fun performSearch() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) {
            // Clear search results
            _searchResults.value = emptyList()
            _isSearchMode.value = false
            return
        }

        _primaryBible.value?.let { bible ->
            try {
                // Determine search mode based on selectedModeIndex
                // 0 = Contains, 1 = Exact Match
                val isExactMatch = _selectedModeIndex.value == 1

                // Build regex pattern
                val pattern = if (isExactMatch) {
                    "\\b${Regex.escape(query)}\\b"
                } else {
                    Regex.escape(query)
                }
                val searchRegex = Regex(pattern, RegexOption.IGNORE_CASE)

                // Determine scope based on selectedScopeIndex
                // 0 = Entire Bible, 1 = Current Book
                val results = when (_selectedScopeIndex.value) {
                    1 -> {
                        // Search in current book only
                        val bookId = bible.getBookId(_selectedBookIndex.value)
                        bible.searchBible(allWords = false, searchExp = searchRegex, book = bookId)
                    }
                    else -> {
                        // Search entire Bible
                        bible.searchBible(allWords = false, searchExp = searchRegex)
                    }
                }

                _searchResults.value = results
                _isSearchMode.value = true

            } catch (e: Exception) {
                e.printStackTrace()
                _searchResults.value = emptyList()
                _isSearchMode.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchMode.value = false
    }

    fun selectSearchResult(result: BibleSearch) {
        val bookIndex = _books.value.indexOf(result.book)
        if (bookIndex < 0) return
        val chapter = result.chapter.toIntOrNull() ?: 1
        val verse = result.verse.toIntOrNull() ?: 1

        val bible = _primaryBible.value ?: return
        val bookCount = minOf(bible.getBookCount(), CANONICAL_BOOK_COUNT)
        if (bookCount == 0) return

        val clampedIndex = bookIndex.coerceIn(0, bookCount - 1)
        val bookId = bible.getBookId(clampedIndex)

        _selectedBookIndex.value = clampedIndex
        _selectedChapter.value = chapter
        _selectedVerseIndex.value = 0

        viewModelScope.launch {
            val chapterResult = withContext(Dispatchers.IO) {
                bible.getChapter(bookId, chapter)
            }
            val chapterVerses = chapterResult.verses
            _verses.value = chapterVerses

            val verseIndex = chapterVerses.indexOfFirst { it.startsWith("$verse. ") }
            _selectedVerseIndex.value = if (verseIndex >= 0) verseIndex else 0
            _verseSelectionToken.value++

            refreshFilteredLists()
        }
    }

    fun dispose() {
        viewModelScope.cancel()
    }
}
