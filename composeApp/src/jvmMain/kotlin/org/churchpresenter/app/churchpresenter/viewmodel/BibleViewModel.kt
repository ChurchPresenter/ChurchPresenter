package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.data.BibleBookNames
import org.churchpresenter.app.churchpresenter.data.BibleSearch
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import java.io.File

/** Mode of the unified Bible search box. */
enum class BibleSearchMode { AUTO, REFERENCE, TEXT }

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

    // Unified search box mode:
    //  AUTO      — references navigate, anything else searches verse text
    //  REFERENCE — only navigation; non-references do nothing (never searches)
    //  TEXT      — everything is treated as verse text (nothing navigates)
    private val _searchMode = mutableStateOf(BibleSearchMode.AUTO)
    val searchMode: State<BibleSearchMode> = _searchMode

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
        private const val CLICK_DEBOUNCE_MS = 300L
        private const val LIVE_SEARCH_DEBOUNCE_MS = 300L
        private val STANDARD_ENGLISH_BOOKS = listOf(
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
    }

    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loadChapterJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var lastChapterSelectTime = 0L
    private var lastBookSelectTime = 0L

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
        loadChapterJob?.cancel()
        loadChapterJob = null
        val previousBookId = _primaryBible.value?.getBookId(_selectedBookIndex.value)
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
                    // Preserve current book by canonical ID so swapping bibles stays on the same book
                    val bookCount = minOf(primary.getBookCount(), CANONICAL_BOOK_COUNT)
                    val clampedBookIndex = if (previousBookId != null) {
                        (0 until bookCount).firstOrNull { primary.getBookId(it) == previousBookId }
                            ?: _selectedBookIndex.value.coerceIn(0, (bookCount - 1).coerceAtLeast(0))
                    } else {
                        _selectedBookIndex.value.coerceIn(0, (bookCount - 1).coerceAtLeast(0))
                    }
                    _selectedBookIndex.value = clampedBookIndex
                    val bookId = primary.getBookId(clampedBookIndex)
                    val chapterResult = withContext(Dispatchers.IO) {
                        primary.getChapter(bookId, _selectedChapter.value)
                    }
                    _verses.value = chapterResult.verses
                    _selectedVerseIndex.value = _selectedVerseIndex.value.coerceIn(0, (chapterResult.verses.size - 1).coerceAtLeast(0))
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

    suspend fun getVersesForDisplay(bookName: String, chapter: Int, verseNum: Int): List<SelectedVerse> {
        val primaryBible = _primaryBible.value ?: return emptyList()
        val bookIndex = _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }
        if (bookIndex < 0) return emptyList()
        val bookId = primaryBible.getBookId(bookIndex)
        return withContext(Dispatchers.IO) {
            val verseList = mutableListOf<SelectedVerse>()
            val chapterVerses = primaryBible.getChapter(bookId, chapter).verses
            val verseStr = chapterVerses.firstOrNull { v ->
                v.substringBefore(". ").toIntOrNull() == verseNum
            }
            val primaryVerseText = verseStr?.substringAfter(". ", "") ?: ""
            val primaryBookName = primaryBible.getBookName(bookId) ?: bookName
            if (primaryVerseText.isNotEmpty()) {
                verseList.add(
                    SelectedVerse(
                        bibleAbbreviation = primaryBible.getBibleAbbreviation() ?: "",
                        bibleName = primaryBible.getBibleTitle() ?: "",
                        bookName = primaryBookName,
                        chapter = chapter,
                        verseNumber = verseNum,
                        verseText = primaryVerseText
                    )
                )
            }
            val codeRef = primaryBible.getCodeReference(bookId, chapter, verseNum)
            val secBook = codeRef?.first ?: bookId
            val secChapter = codeRef?.second ?: chapter
            val secVerse = codeRef?.third ?: verseNum
            _secondaryBible.value?.getVerseDetailsByCode(secBook, secChapter, secVerse)?.let { result ->
                verseList.add(
                    SelectedVerse(
                        bibleAbbreviation = _secondaryBible.value?.getBibleAbbreviation() ?: "",
                        bibleName = _secondaryBible.value?.getBibleTitle() ?: "",
                        bookName = result.bookName,
                        chapter = result.displayChapter,
                        verseNumber = result.displayVerse,
                        verseText = result.verseText
                    )
                )
            }
            verseList
        }
    }

    suspend fun getChapterVerses(bookName: String, chapter: Int): List<String> {
        val bible = _primaryBible.value ?: return emptyList()
        val bookIndex = _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }
        if (bookIndex < 0) return emptyList()
        val bookId = bible.getBookId(bookIndex)
        return withContext(Dispatchers.IO) {
            bible.getChapter(bookId, chapter).verses
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
                loadChapterJob?.cancel()
                loadChapterJob = viewModelScope.launch {
                    val bookId = bible.getBookId(clampedIndex)
                    val chapterResult = withContext(Dispatchers.IO) {
                        bible.getChapter(bookId, chapter)
                    }
                    _verses.value = chapterResult.verses
                    refreshFilteredLists()
                    _verseSelectionToken.value++
                }
            }
        }
    }

    fun selectBook(bookIndex: Int) {
        val now = System.currentTimeMillis()
        if (now - lastBookSelectTime < CLICK_DEBOUNCE_MS) return
        lastBookSelectTime = now
        _selectedBookIndex.value = bookIndex
        _selectedChapter.value = 1
        _selectedVerseIndex.value = 0
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false
        loadChapter(bookIndex, 1)
    }

    fun selectChapter(chapter: Int) {
        val now = System.currentTimeMillis()
        if (now - lastChapterSelectTime < CLICK_DEBOUNCE_MS) return
        lastChapterSelectTime = now
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

        val matchingBookIds = STANDARD_ENGLISH_BOOKS.mapIndexedNotNull { index, englishName ->
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

                // Primary: use text from _verses.value to stay in sync with Bible tab
                val primaryText = verse.substringAfter(". ")
                if (primaryText.isNotEmpty()) {
                    if (bookName.isEmpty()) bookName = _primaryBible.value?.getBookName(bookId) ?: ""
                    primaryTexts.add(primaryText)
                }
                // Cross-reference via internal code for secondary Bible
                val codeRef = _primaryBible.value?.getCodeReference(bookId, _selectedChapter.value, vNum)
                val sB = codeRef?.first ?: bookId
                val sCh = codeRef?.second ?: _selectedChapter.value
                val sV = codeRef?.third ?: vNum
                _secondaryBible.value?.getVerseDetailsByCode(sB, sCh, sV)?.let { result ->
                    if (secondaryBookName.isEmpty()) secondaryBookName = result.bookName
                    secondaryTexts.add(result.verseText)
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

        // Add primary Bible verse — use text from _verses.value (the verse list displayed
        // in the Bible tab) to guarantee the presenter always matches what the user sees.
        // Re-querying via getVerseDetails could return different data if _selectedChapter
        // updated before _verses was reloaded.
        val primaryVerseText = verse.substringAfter(". ")
        val primaryBookName = _primaryBible.value?.getBookName(bookId) ?: ""
        if (primaryVerseText.isNotEmpty()) {
            verseList.add(
                SelectedVerse(
                    bibleAbbreviation = _primaryBible.value?.getBibleAbbreviation() ?: "",
                    bibleName = _primaryBible.value?.getBibleTitle() ?: "",
                    bookName = primaryBookName,
                    chapter = _selectedChapter.value,
                    verseNumber = verseNumber,
                    verseText = primaryVerseText
                )
            )
        }

        // Add secondary Bible verse if available.
        // Use the internal code reference from the primary verse to cross-reference
        // the secondary Bible, since they may use different numbering (e.g. LXX vs Hebrew Psalms).
        // getVerseDetailsByCode translates code numbers to the secondary Bible's display numbers.
        val codeRef = _primaryBible.value?.getCodeReference(bookId, _selectedChapter.value, verseNumber)
        val secBook = codeRef?.first ?: bookId
        val secChapter = codeRef?.second ?: _selectedChapter.value
        val secVerse = codeRef?.third ?: verseNumber
        _secondaryBible.value?.getVerseDetailsByCode(secBook, secChapter, secVerse)?.let { result ->
            val abbreviation = _secondaryBible.value?.getBibleAbbreviation() ?: ""
            verseList.add(
                SelectedVerse(
                    bibleAbbreviation = abbreviation,
                    bibleName = _secondaryBible.value?.getBibleTitle() ?: "",
                    bookName = result.bookName,
                    chapter = result.displayChapter,
                    verseNumber = result.displayVerse,
                    verseText = result.verseText
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

    /** Runs an immediate full-text search of the verse text (used by the 🔍 button and Enter). */
    fun performSearch() = launchSearch(debounceMs = 0L)

    /** Debounced live text search — runs as the user types in the unified box. */
    private fun scheduleLiveSearch() = launchSearch(debounceMs = LIVE_SEARCH_DEBOUNCE_MS)

    /**
     * Searches the verse text for the current query off the UI thread, latest-wins. Respects the
     * Scope (Entire Bible / Current Book) and Mode (Contains / Exact) selections. Queries shorter
     * than 2 chars are ignored so a single letter doesn't return the whole Bible.
     */
    private fun launchSearch(debounceMs: Long) {
        val query = _searchQuery.value.trim()
        searchJob?.cancel()
        if (query.length < 2) {
            _searchResults.value = emptyList()
            _isSearchMode.value = false
            return
        }
        val bible = _primaryBible.value ?: return
        val isExactMatch = _selectedModeIndex.value == 1
        val scopeIndex = _selectedScopeIndex.value
        val bookIndex = _selectedBookIndex.value

        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val results = withContext(Dispatchers.IO) {
                try {
                    val pattern = if (isExactMatch) "\\b${Regex.escape(query)}\\b" else Regex.escape(query)
                    val searchRegex = Regex(pattern, RegexOption.IGNORE_CASE)
                    if (scopeIndex == 1) {
                        bible.searchBible(allWords = false, searchExp = searchRegex, book = bible.getBookId(bookIndex))
                    } else {
                        bible.searchBible(allWords = false, searchExp = searchRegex)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
            if (isActive) {
                _searchResults.value = results
                _isSearchMode.value = true
            }
        }
    }

    /** Cycles Auto → Reference → Text → Auto and re-evaluates the current query under the new mode. */
    fun cycleSearchMode() {
        _searchMode.value = when (_searchMode.value) {
            BibleSearchMode.AUTO -> BibleSearchMode.REFERENCE
            BibleSearchMode.REFERENCE -> BibleSearchMode.TEXT
            BibleSearchMode.TEXT -> BibleSearchMode.AUTO
        }
        onSmartQueryChanged(_searchQuery.value)
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearchMode.value = false
    }

    // ── Smart (unified) search ────────────────────────────────────────────
    // A single box handles both Scripture references ("mat 1:6", "john 3", "ps 23:1-3")
    // and free-text content search ("love your enemies"). References navigate live as the
    // user types; free text runs a full-text search on Enter / the search button.

    private data class SmartReference(
        val bookIndex: Int,
        val chapter: Int?,
        val verseStart: Int?,
        val verseEnd: Int?
    )

    /** Maps a canonical book id (1-based) to its index in the displayed [_books] list. */
    private fun canonicalBookIdToIndex(canonicalId: Int): Int? {
        val localized = _primaryBible.value?.getBookName(canonicalId) ?: return null
        return _books.value.indexOf(localized).takeIf { it >= 0 }
    }

    /**
     * Scores how well [name] matches the query (both already lowercased). Spaces are ignored on a
     * second pass so e.g. "1cor"/"1 co" still match "1 corinthians". Higher is better; 0 = no match.
     */
    private fun scoreNameMatch(name: String, norm: String, normNoSpace: String): Int {
        val nameNoSpace = name.replace(" ", "")
        return when {
            name == norm || nameNoSpace == normNoSpace -> 100
            name.startsWith(norm) || nameNoSpace.startsWith(normNoSpace) -> 80
            name.contains(norm) || nameNoSpace.contains(normNoSpace) -> 60
            else -> 0
        }
    }

    /**
     * Returns `(bookIndex, score)` pairs for [token] ordered best-first, using a plain
     * contains/prefix/exact search (no abbreviation table). Matches against the localized
     * (displayed) names and, for cross-language support, the standard English names. Ties are
     * broken by the shortest book name, then canonical order (so "john" beats "1 john"/"2 john").
     */
    private fun rankedBookMatches(token: String): List<Pair<Int, Int>> {
        val norm = token.trim().lowercase().replace(Regex("\\s+"), " ")
        if (norm.isEmpty()) return emptyList()
        val normNoSpace = norm.replace(" ", "")
        val books = _books.value
        if (books.isEmpty()) return emptyList()

        val scored = linkedMapOf<Int, Int>()
        fun consider(index: Int?, score: Int) {
            if (index == null || index < 0 || score <= 0) return
            val prev = scored[index]
            if (prev == null || score > prev) scored[index] = score
        }

        // Localized (displayed) book names
        books.forEachIndexed { i, name ->
            consider(i, scoreNameMatch(name.lowercase(), norm, normNoSpace))
        }

        // Standard English names (cross-language search)
        STANDARD_ENGLISH_BOOKS.forEachIndexed { i, english ->
            consider(canonicalBookIdToIndex(i + 1), scoreNameMatch(english, norm, normNoSpace))
        }

        return scored.entries
            .sortedWith(
                compareByDescending<Map.Entry<Int, Int>> { it.value }
                    .thenBy { books.getOrNull(it.key)?.length ?: Int.MAX_VALUE }
                    .thenBy { it.key }
            )
            .map { it.key to it.value }
    }

    /** Best book match for a reference where a chapter/verse is also present. */
    private fun resolveBook(token: String): Int = rankedBookMatches(token).firstOrNull()?.first ?: -1

    /**
     * Book match for a bare book name (no chapter). Navigates only when there is a single best
     * match — i.e. an exact match, or a unique top score — so live typing doesn't flicker through
     * books on ambiguous input like "jo" (Joshua/Job/Joel/Jonah/John all tie) or "cor" (1 & 2 Cor).
     */
    private fun resolveBookForLiveNav(token: String): Int {
        val norm = token.trim().lowercase()
        if (norm.isEmpty()) return -1
        _books.value.indexOfFirst { it.lowercase() == norm }.takeIf { it >= 0 }?.let { return it }
        val ranked = rankedBookMatches(token)
        val top = ranked.firstOrNull() ?: return -1
        val topCount = ranked.count { it.second == top.second }
        return if (topCount == 1) top.first else -1
    }

    /**
     * Parses [input] as a Scripture reference. Recognizes an optional book token followed by a
     * chapter and an optional `:verse` (or `:verse-verseEnd`). When the book token is empty the
     * current book is used (e.g. "3:16" or "5"). Returns null when the input is not a reference.
     */
    private fun parseReference(input: String): SmartReference? {
        val refRegex = Regex("^(.*?)\\s*(\\d+)(?:\\s*[:. ]\\s*(\\d+)(?:\\s*-\\s*(\\d+))?)?\\s*$")
        val match = refRegex.find(input)
        if (match != null) {
            val bookToken = match.groupValues[1].trim()
            val chapter = match.groupValues[2].toIntOrNull()
            val verseStart = match.groupValues[3].toIntOrNull()
            val verseEnd = match.groupValues[4].toIntOrNull()
            val bookIndex = if (bookToken.isEmpty()) {
                _selectedBookIndex.value
            } else {
                resolveBook(bookToken).takeIf { it >= 0 } ?: return null
            }
            return SmartReference(bookIndex, chapter, verseStart, verseEnd)
        }
        // No trailing number — treat as a bare book name if it resolves unambiguously
        val bookIndex = resolveBookForLiveNav(input)
        return if (bookIndex >= 0) SmartReference(bookIndex, null, null, null) else null
    }

    /** True when [query] resolves to a Scripture reference (book / chapter / verse). */
    fun isReferenceQuery(query: String): Boolean = parseReference(query.trim()) != null

    /**
     * Navigates the browse columns to [bookIndex], [chapter] and optional verse range without the
     * click debounce, so it stays responsive while the user types a reference.
     */
    private fun navigateToReference(ref: SmartReference) {
        val bible = _primaryBible.value ?: return
        val bookCount = minOf(bible.getBookCount(), CANONICAL_BOOK_COUNT)
        if (bookCount == 0) return
        val idx = ref.bookIndex.coerceIn(0, bookCount - 1)
        val targetChapter = (ref.chapter ?: 1).coerceAtLeast(1)

        searchJob?.cancel()
        _isSearchMode.value = false
        _searchResults.value = emptyList()
        _selectedBookIndex.value = idx
        _selectedChapter.value = targetChapter
        _selectedVerseIndex.value = 0
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false

        loadChapterJob?.cancel()
        loadChapterJob = viewModelScope.launch {
            if (!_isFullyLoadedFlow.value) _isFullyLoadedFlow.first { it }
            val bookId = bible.getBookId(idx)
            val chapterVerses = withContext(Dispatchers.IO) {
                bible.getChapter(bookId, targetChapter).verses
            }
            _verses.value = chapterVerses

            val verseStart = ref.verseStart
            if (verseStart != null) {
                val startIdx = chapterVerses.indexOfFirst { it.startsWith("$verseStart. ") }
                _selectedVerseIndex.value = if (startIdx >= 0) startIdx else 0
                val verseEnd = ref.verseEnd
                if (verseEnd != null && verseEnd > verseStart) {
                    _selectedVerseIndices.clear()
                    for (v in verseStart..verseEnd) {
                        val vIdx = chapterVerses.indexOfFirst { it.startsWith("$v. ") }
                        if (vIdx >= 0) _selectedVerseIndices.add(vIdx)
                    }
                    _multiVerseEnabled.value = _selectedVerseIndices.size > 1
                }
            }
            _verseSelectionToken.value++
            refreshFilteredLists()
        }
    }

    /**
     * Called on every keystroke in the unified search box. In Auto mode a recognized reference
     * navigates live and anything else runs a debounced live text search; in Text mode everything
     * is treated as verse text and live-searched (so book-name words like "john" are searchable).
     */
    fun onSmartQueryChanged(query: String) {
        _searchQuery.value = query
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchJob?.cancel()
            _isSearchMode.value = false
            _searchResults.value = emptyList()
            return
        }
        when (_searchMode.value) {
            BibleSearchMode.TEXT -> scheduleLiveSearch()
            BibleSearchMode.REFERENCE -> {
                searchJob?.cancel()
                _isSearchMode.value = false
                _searchResults.value = emptyList()
                parseReference(trimmed)?.let { navigateToReference(it) }
            }
            BibleSearchMode.AUTO -> {
                val ref = parseReference(trimmed)
                if (ref != null) {
                    searchJob?.cancel()
                    navigateToReference(ref)
                } else {
                    scheduleLiveSearch()
                }
            }
        }
    }

    /** Called when the user presses Enter in the unified box. */
    fun submitSmartQuery() {
        val trimmed = _searchQuery.value.trim()
        if (trimmed.isEmpty()) {
            clearSearch()
            return
        }
        when (_searchMode.value) {
            BibleSearchMode.TEXT -> performSearch()
            BibleSearchMode.REFERENCE -> parseReference(trimmed)?.let { navigateToReference(it) }
            BibleSearchMode.AUTO -> {
                val ref = parseReference(trimmed)
                if (ref != null) navigateToReference(ref) else performSearch()
            }
        }
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
