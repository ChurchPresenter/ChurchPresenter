package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.churchpresenter.app.churchpresenter.data.BibleLoadError
import org.churchpresenter.app.churchpresenter.data.BibleSearch
import org.churchpresenter.app.churchpresenter.models.bible.SelectedVerse
import androidx.compose.runtime.derivedStateOf
import java.io.File
import org.churchpresenter.app.churchpresenter.data.settings.BibleSyncMode

private const val MAX_HISTORY_ENTRIES = 50
private const val ASCII_LIMIT = 128

class BibleViewModel(
    internal var appSettings: AppSettings,
    internal val onBibleLoaded: ((bible: Bible, translation: String) -> Unit)? = null,

    internal val onSecondaryBibleFilePathChanged: ((filePath: String) -> Unit)? = null,
    internal val onBibleFilePathsChanged: ((filePaths: List<String>) -> Unit)? = null,

    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    internal val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    data class LoadedTranslation(val fileName: String, val bible: Bible)

    internal val _primaryBible = mutableStateOf<Bible?>(null)
    val primaryBible: State<Bible?> = _primaryBible

    internal val _secondaryBible = mutableStateOf<Bible?>(null)
    val secondaryBible: State<Bible?> = _secondaryBible

    internal val _loadedTranslations = mutableStateOf<List<LoadedTranslation>>(emptyList())

    val loadedTranslations: State<List<LoadedTranslation>> = _loadedTranslations

    internal val _loadedBibles = mutableStateOf<List<Bible>>(emptyList())

    val loadedBibles: State<List<Bible>> = _loadedBibles

    internal val _books = mutableStateOf<List<String>>(emptyList())
    val books: State<List<String>> = _books

    internal val _selectedBookIndex = mutableStateOf(0)
    val selectedBookIndex: State<Int> = _selectedBookIndex

    internal val _selectedChapter = mutableStateOf(1)
    val selectedChapter: State<Int> = _selectedChapter

    internal val _selectedVerseIndex = mutableStateOf(0)
    val selectedVerseIndex: State<Int> = _selectedVerseIndex

    internal val _verses = mutableStateOf<List<String>>(emptyList())
    val verses: State<List<String>> = _verses

    internal val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> = _searchQuery

    private val _selectedScopeIndex = mutableStateOf(0)
    val selectedScopeIndex: State<Int> = _selectedScopeIndex

    private val _selectedModeIndex = mutableStateOf(0)
    val selectedModeIndex: State<Int> = _selectedModeIndex

    private val _bookSearchQuery = mutableStateOf("")
    val bookSearchQuery: State<String> = _bookSearchQuery

    private val _chapterSearchQuery = mutableStateOf("")
    val chapterSearchQuery: State<String> = _chapterSearchQuery

    private val _verseSearchQuery = mutableStateOf("")
    val verseSearchQuery: State<String> = _verseSearchQuery

    private val _filteredBooks = mutableStateOf<List<String>>(emptyList())
    val filteredBooks: State<List<String>> = _filteredBooks

    private val _filteredChapters = mutableStateOf<List<String>>(emptyList())
    val filteredChapters: State<List<String>> = _filteredChapters

    private val _filteredVerses = mutableStateOf<List<String>>(emptyList())
    val filteredVerses: State<List<String>> = _filteredVerses

    internal val _searchResults = mutableStateOf<List<BibleSearch>>(emptyList())
    val searchResults: State<List<BibleSearch>> = _searchResults

    internal val _isSearchMode = mutableStateOf(false)
    val isSearchMode: State<Boolean> = _isSearchMode

    internal val _searchMode = mutableStateOf(BibleSearchMode.AUTO)
    val searchMode: State<BibleSearchMode> = _searchMode

    internal val _bookNameMapping = mutableStateOf<Map<String, String>>(emptyMap())
    val bookNameMapping: State<Map<String, String>> = _bookNameMapping

    internal val _englishBookNames = mutableStateOf<List<String>>(emptyList())

    internal val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    internal val _loadErrors = mutableStateOf<List<BibleLoadError>>(emptyList())
    val loadErrors: State<List<BibleLoadError>> = _loadErrors

    internal val _verseSelectionToken = mutableStateOf(0)
    val verseSelectionToken: State<Int> = _verseSelectionToken

    internal val _multiVerseEnabled = mutableStateOf(false)
    val multiVerseEnabled: State<Boolean> = _multiVerseEnabled

    internal val _selectedVerseIndices = mutableStateListOf<Int>()
    val selectedVerseIndices: List<Int> get() = _selectedVerseIndices

    internal val _isFullyLoadedFlow = MutableStateFlow(false)
    val isFullyLoadedFlow: StateFlow<Boolean> = _isFullyLoadedFlow.asStateFlow()
    val isFullyLoaded: Boolean get() = _isFullyLoadedFlow.value

    internal val _detectedReferences = mutableStateOf<List<DetectedReference>>(emptyList())
    val detectedReferences: State<List<DetectedReference>> = _detectedReferences

    @Volatile internal var _lastDetectionSegmentId: String? = null
    val lastDetectionSegmentId: String? get() = _lastDetectionSegmentId

    @Volatile internal var _lastSessionId: String? = null
    val lastSessionId: String? get() = _lastSessionId

    internal val _autoFollowEnabled = mutableStateOf(appSettings.bibleEngineSettings.autoFollow)
    val autoFollowEnabled: State<Boolean> = _autoFollowEnabled

    internal val _autoFollowLiveToken = mutableStateOf(0)
    val autoFollowLiveToken: State<Int> = _autoFollowLiveToken

    internal val _autoFollowLiveSource = mutableStateOf("auto")
    val autoFollowLiveSource: State<String> get() = _autoFollowLiveSource

    internal val _autoFollowLiveMatchType = mutableStateOf<String?>(null)
    val autoFollowLiveMatchType: State<String?> get() = _autoFollowLiveMatchType

    fun setAutoFollow(enabled: Boolean) {
        _autoFollowEnabled.value = enabled

        if (enabled) {
            _detectedReferences.value.firstOrNull()
                ?.let {
                    navigateToReference(
                        SmartReference(it.bookIndex, it.chapter, it.verseStart, verseEnd = null),
                        goLive = true,
                        matchType = it.matchTypeLabel(),
                    )
                }
        }
    }

    private val _textMatchLevel = mutableStateOf(
        runCatching { TextMatchLevel.valueOf(appSettings.bibleEngineSettings.textMatchLevel.uppercase()) }
            .getOrDefault(TextMatchLevel.OFF)
    )
    val textMatchLevel: State<TextMatchLevel> = _textMatchLevel

    var onTextMatchLevelChanged: ((TextMatchLevel) -> Unit)? = null
    fun setTextMatchLevel(level: TextMatchLevel) {
        _textMatchLevel.value = level
        onTextMatchLevelChanged?.invoke(level)
    }

    private val _continuationSpeed = mutableStateOf(
        runCatching { ContinuationSpeed.valueOf(appSettings.bibleEngineSettings.continuationSpeed.uppercase()) }
            .getOrDefault(ContinuationSpeed.BALANCED)
    )
    val continuationSpeed: State<ContinuationSpeed> = _continuationSpeed

    var onContinuationSpeedChanged: ((ContinuationSpeed) -> Unit)? = null
    fun setContinuationSpeed(speed: ContinuationSpeed) {
        _continuationSpeed.value = speed
        onContinuationSpeedChanged?.invoke(speed)
    }

    internal val recentDetectionKeys = ArrayDeque<String>()

    data class ModuleRef(
        val abbreviation: String,
        val chapter: Int,
        val verse: Int,
        val text: String,
    )

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

        _history.removeAll { it.bookName == bookName && it.chapter == chapter && it.verseNumber == verseNumber && it.verseRange == verseRange }

        _history.add(0, entry)

        while (_history.size > MAX_HISTORY_ENTRIES) _history.removeLast()
    }

    fun clearHistory() { _history.clear() }

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

    fun ctrlClickVerse(verseIndex: Int) {
        if (verseIndex < 0 || verseIndex >= _verses.value.size) return
        if (_selectedVerseIndices.contains(verseIndex)) {
            _selectedVerseIndices.remove(verseIndex)
            if (_selectedVerseIndices.isEmpty()) {

                _selectedVerseIndex.value = verseIndex
            }
        } else {

            if (_selectedVerseIndices.isEmpty()) {
                val anchor = _selectedVerseIndex.value
                if (anchor >= 0 && anchor < _verses.value.size && anchor != verseIndex) {
                    _selectedVerseIndices.add(anchor)
                }
            }
            _selectedVerseIndices.add(verseIndex)
            _selectedVerseIndex.value = verseIndex
        }
        _multiVerseEnabled.value = _selectedVerseIndices.isNotEmpty()
        _verseSelectionToken.value++
    }

    fun shiftClickVerse(targetIndex: Int) {
        if (targetIndex < 0 || targetIndex >= _verses.value.size) return
        val anchor = _selectedVerseIndex.value.coerceIn(0, _verses.value.size - 1)
        val from = minOf(anchor, targetIndex)
        val to   = maxOf(anchor, targetIndex)
        _selectedVerseIndices.clear()
        for (index in from..to) _selectedVerseIndices.add(index)
        _multiVerseEnabled.value = _selectedVerseIndices.size > 1
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
        internal const val CANONICAL_BOOK_COUNT = 66

        internal const val MODULE_FILE_MISSING = "Module file not found"

        internal const val MODULE_LOAD_THREW = "Module could not be loaded"
        private const val CLICK_DEBOUNCE_MS = 300L
        private const val LIVE_SEARCH_DEBOUNCE_MS = 300L

        internal const val MAX_DETECTED = 20
        internal const val DETECTION_DEDUPE_WINDOW = 32
        internal val STANDARD_ENGLISH_BOOKS = listOf(
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

    internal val viewModelScope = CoroutineScope(dispatcher + SupervisorJob())
    internal var loadChapterJob: kotlinx.coroutines.Job? = null
    internal var searchJob: kotlinx.coroutines.Job? = null
    private var lastChapterSelectTime = 0L
    private var lastBookSelectTime = 0L

    internal fun Bible.getCanonicalBooks(): List<String> = getBooks().take(CANONICAL_BOOK_COUNT)

    init {
        _selectedScopeIndex.value = 0
        _selectedModeIndex.value = 0
        loadBibles()
    }

    suspend fun getVersesForDisplay(bookName: String, chapter: Int, verseNum: Int): List<SelectedVerse> {
        val primaryBible = _primaryBible.value ?: return emptyList()
        val bookIndex = _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }
        if (bookIndex < 0) return emptyList()
        val bookId = primaryBible.getBookId(bookIndex)
        return withContext(ioDispatcher) {
            val verseList = mutableListOf<SelectedVerse>()
            val chapterVerses = primaryBible.getChapter(bookId, chapter).verses
            val verseStr = chapterVerses.firstOrNull { v ->
                verseNumberOf(v) == verseNum
            }
            val primaryVerseText = verseStr?.let { verseTextOf(it, "") } ?: ""
            val primaryBookName = primaryBible.getBookName(bookId) ?: bookName
            if (primaryVerseText.isNotEmpty()) {
                verseList.add(
                    SelectedVerse(
                        translationFileName = _loadedTranslations.value.firstOrNull()?.fileName.orEmpty(),
                        bibleAbbreviation = primaryBible.getBibleAbbreviation(),
                        bibleName = primaryBible.getBibleTitle(),
                        bookName = primaryBookName,
                        chapter = chapter,
                        verseNumber = verseNum,
                        verseText = primaryVerseText,

                        bookId = bookId,
                    )
                )
            }
            val codeRef = primaryBible.getCodeReference(bookId, chapter, verseNum)
            val targetBook = codeRef?.first ?: bookId
            val targetChapter = codeRef?.second ?: chapter
            val targetVerse = codeRef?.third ?: verseNum
            _loadedTranslations.value.drop(1).forEach { loadedTranslation ->
                val bible = loadedTranslation.bible
                bible.getVerseDetailsByCode(targetBook, targetChapter, targetVerse)?.let { result ->
                    verseList.add(
                        SelectedVerse(
                            translationFileName = loadedTranslation.fileName,
                            bibleAbbreviation = bible.getBibleAbbreviation(),
                            bibleName = bible.getBibleTitle(),
                            bookName = result.bookName,
                            chapter = result.displayChapter,
                            verseNumber = result.displayVerse,
                            verseText = result.verseText,
                            bookId = targetBook,
                        )
                    )
                }
            }
            verseList
        }
    }

    suspend fun getChapterVerses(bookName: String, chapter: Int): List<String> {
        val bible = _primaryBible.value ?: return emptyList()
        val bookIndex = _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }
        if (bookIndex < 0) return emptyList()
        val bookId = bible.getBookId(bookIndex)
        return withContext(ioDispatcher) {
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
                    val chapterResult = withContext(ioDispatcher) {
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

    internal fun parseVerseNumbers(rangeStr: String): List<Int> {
        val result = mutableListOf<Int>()
        rangeStr.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val bounds = trimmed.split("-")
                val from = bounds.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach
                val to   = bounds.getOrNull(1)?.trim()?.toIntOrNull() ?: return@forEach
                for (index in from..to) result.add(index)
            } else {
                trimmed.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    fun selectVerseByDetails(bookName: String, chapter: Int, verseNumber: Int, verseRange: String = "", goLiveSource: String? = null, bookId: Int = 0): Boolean {
        val bookIndex = resolveBookIndex(bookName, bookId)
        if (bookIndex < 0) return false

        beginVerseSelection(bookIndex, chapter)
        viewModelScope.launch {
            awaitFullyLoaded()
            applyVerseSelection(bookIndex, chapter, verseNumber, verseRange, goLiveSource)
        }
        return true
    }

    /**
     * Selects the same verse as [selectVerseByDetails] but waits for it, and hands back the verses
     * that are now selected — empty when this Bible does not have the reference.
     *
     * The difference from [selectVerseByDetails] is the **order**: the Bible is awaited *before*
     * the book is looked up, not after. At startup `loadBibles` publishes a books-only module first
     * and the full text second, so a reference resolved during that window either misses the book
     * list entirely or finds a book whose chapters hold no verses — and the caller is told `true`
     * either way. That is the blank output a schedule item produced on the first click of a cold
     * start.
     *
     * Returning the verses is the other half: the caller pushes them to the presenter itself rather
     * than leaving it to a token effect that may not be composed by the time the token moves.
     */
    internal suspend fun resolveVerseSelection(
        bookName: String,
        chapter: Int,
        verseNumber: Int,
        verseRange: String = "",
        goLiveSource: String? = null,
        bookId: Int = 0,
    ): List<SelectedVerse> {
        awaitFullyLoaded()
        val bookIndex = resolveBookIndex(bookName, bookId)
        if (bookIndex < 0) return emptyList()

        beginVerseSelection(bookIndex, chapter)
        applyVerseSelection(bookIndex, chapter, verseNumber, verseRange, goLiveSource)
        return getSelectedVerses()
    }

    /** Suspends until the full Bible text is loaded; returns at once when it already is. */
    internal suspend fun awaitFullyLoaded() {
        if (!_isFullyLoadedFlow.value) {
            _isFullyLoadedFlow.first { it }
        }
    }

    /**
     * The display index of the book [bookId] (preferred) or [bookName] names, or -1.
     *
     * The id is tried first because it survives a translation change; the name is the fallback for
     * schedules written before ids were stored, and for callers that only have one.
     */
    internal fun resolveBookIndex(bookName: String, bookId: Int): Int {
        val byId = if (bookId > 0) {
            _primaryBible.value?.getDisplayIndexForBookId(bookId)?.takeIf { it in _books.value.indices }
        } else {
            null
        }
        return byId ?: _books.value.indexOfFirst { it.equals(bookName, ignoreCase = true) }
    }

    /** Points the selection at a book and chapter, before the chapter's text has been read. */
    private fun beginVerseSelection(bookIndex: Int, chapter: Int) {
        _selectedBookIndex.value = bookIndex
        _selectedChapter.value = chapter
        _selectedVerseIndex.value = 0

        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false
    }

    /**
     * Turns a saved range ("16-18", "16,18,20") into a multi-verse selection within [chapterVerses].
     *
     * A range naming one verse leaves the single-verse selection alone: multi-verse mode changes how
     * the verse is drawn and how the reference reads, and one verse is not that.
     */
    private fun applyVerseRange(verseRange: String, chapterVerses: List<String>) {
        if (verseRange.isEmpty()) return
        val verseNumbers = parseVerseNumbers(verseRange)
        if (verseNumbers.size <= 1) return

        _selectedVerseIndices.clear()
        for (vNum in verseNumbers) {
            val vIdx = chapterVerses.indexOfFirst { it.startsWith("$vNum. ") }
            if (vIdx >= 0) _selectedVerseIndices.add(vIdx)
        }
        _multiVerseEnabled.value = _selectedVerseIndices.size > 1
    }

    /** Reads the chapter and settles the selection inside it. Assumes the Bible is fully loaded. */
    private suspend fun applyVerseSelection(
        bookIndex: Int,
        chapter: Int,
        verseNumber: Int,
        verseRange: String,
        goLiveSource: String?,
    ) {
        val bible = _primaryBible.value ?: return
        val bookCount = minOf(bible.getBookCount(), CANONICAL_BOOK_COUNT)
        if (bookCount == 0) return

        val clampedIndex = bookIndex.coerceIn(0, bookCount - 1)
        val bookId = bible.getBookId(clampedIndex)

        val chapterResult = withContext(ioDispatcher) {
            bible.getChapter(bookId, chapter)
        }
        val chapterVerses = chapterResult.verses
        _verses.value = chapterVerses

        val verseIndex = chapterVerses.indexOfFirst { it.startsWith("$verseNumber. ") }
        _selectedVerseIndex.value = if (verseIndex >= 0) verseIndex else 0

        applyVerseRange(verseRange, chapterVerses)

        _verseSelectionToken.value++

        refreshFilteredLists()

        if (goLiveSource != null) {
            _autoFollowLiveSource.value = goLiveSource

            _autoFollowLiveMatchType.value = null
            _autoFollowLiveToken.value++
        }
    }

    fun selectVerseByBookId(bookId: Int, chapter: Int, verseNumber: Int) {
        val bible = _primaryBible.value ?: return
        val displayIndex = bible.getDisplayIndexForBookId(bookId)
        if (displayIndex < 0) return
        val bookCount = minOf(bible.getBookCount(), CANONICAL_BOOK_COUNT)
        val clamped = displayIndex.coerceIn(0, bookCount - 1)

        searchJob?.cancel()
        _isSearchMode.value = false
        _searchResults.value = emptyList()
        _selectedBookIndex.value = clamped
        _selectedChapter.value = chapter
        _selectedVerseIndex.value = 0
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false

        loadChapterJob?.cancel()
        loadChapterJob = viewModelScope.launch {
            if (!_isFullyLoadedFlow.value) _isFullyLoadedFlow.first { it }
            val bId = bible.getBookId(clamped)
            val chapterVerses = withContext(ioDispatcher) { bible.getChapter(bId, chapter).verses }
            _verses.value = chapterVerses
            val verseIdx = chapterVerses.indexOfFirst { it.startsWith("$verseNumber. ") }
            _selectedVerseIndex.value = if (verseIdx >= 0) verseIdx else 0
            _verseSelectionToken.value++
            refreshFilteredLists()
        }
    }

    fun selectVerseByCanonicalRef(
        bookId: Int,
        chapter: Int,
        verse: Int,
        goLiveSource: String? = null,
    ): Boolean {
        val bible = _primaryBible.value ?: return false

        val details = bible.getVerseDetailsByCode(bookId, chapter, verse) ?: return false

        return selectVerseByDetails(
            bookName = details.bookName,
            chapter = details.displayChapter,
            verseNumber = details.displayVerse,
            goLiveSource = goLiveSource,
            bookId = bookId,
        )
    }

    fun moduleRefFor(bookId: Int, chapter: Int, verse: Int): ModuleRef? {
        val bible = _primaryBible.value ?: return null
        val details = bible.getVerseDetailsByCode(bookId, chapter, verse) ?: return null
        return ModuleRef(
            abbreviation = bible.getBookAbbreviation(bookId) ?: details.bookName,
            chapter = details.displayChapter,
            verse = details.displayVerse,
            text = details.verseText,
        )
    }

    fun getChaptersForCurrentBook(): List<String> {
        _primaryBible.value?.let { bible ->

            val bookIndex = _selectedBookIndex.value
            val chapterCount = bible.getChapterCount(bookIndex)
            val count = if (chapterCount > 0) chapterCount else 1
            return (1..count).map { it.toString() }
        }
        return emptyList()
    }

    fun getFilteredBooks(): List<String> {
        val query = _bookSearchQuery.value
        if (query.isEmpty()) return _books.value

        return _books.value.filter { it.contains(query, ignoreCase = true) }
            .ifEmpty { booksNamedInEnglish(query) }
    }

    private fun booksNamedInEnglish(query: String): List<String> {
        if (!query.all { it.isLetter() && it.code < ASCII_LIMIT }) return emptyList()
        val bible = _primaryBible.value ?: return emptyList()
        return STANDARD_ENGLISH_BOOKS
            .mapIndexedNotNull { index, englishName ->
                (index + 1).takeIf { englishName.contains(query, ignoreCase = true) }
            }
            .mapNotNull { bookId -> bible.getBookName(bookId) }
            .filter { it in _books.value }
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

    fun getSelectedVerseNumbers(): List<Int> {
        return _selectedVerseIndices.sorted().mapNotNull { idx ->
            _verses.value.getOrNull(idx)?.let { verseNumberOf(it) }
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
        if (_verses.value.isEmpty()) return false
        if (_selectedVerseIndex.value < _verses.value.size - 1) {
            _selectedVerseIndices.clear()
            _multiVerseEnabled.value = false
            _selectedVerseIndex.value++
            _verseSelectionToken.value++
            return true
        }

        val bible = _primaryBible.value ?: return false
        var nextBookIndex = _selectedBookIndex.value
        var nextChapter = _selectedChapter.value + 1
        if (nextChapter > bible.getChapterCount(nextBookIndex)) {
            nextBookIndex += 1
            nextChapter = 1
            if (nextBookIndex >= _books.value.size) return false
        }
        _sequentialChapterAdvance = true
        _selectedVerseIndices.clear()
        _multiVerseEnabled.value = false
        loadChapter(nextBookIndex, nextChapter)
        return true
    }

    fun navigatePreviousChapter(): Boolean {
        if (_selectedChapter.value > 1) {
            _sequentialChapterAdvance = true
            selectChapter(_selectedChapter.value - 1)
            return true
        }
        return false
    }

    private var _sequentialChapterAdvance = false

    fun navigateNextChapter(): Boolean {
        _primaryBible.value?.let { bible ->

            val maxChapter = bible.getChapterCount(_selectedBookIndex.value)
            if (_selectedChapter.value < maxChapter) {
                _sequentialChapterAdvance = true
                selectChapter(_selectedChapter.value + 1)
                return true
            }
        }
        return false
    }

    fun consumeSequentialChapterAdvance(): Boolean {
        val wasSequentialAdvance = _sequentialChapterAdvance
        _sequentialChapterAdvance = false
        return wasSequentialAdvance
    }

    internal fun refreshFilteredLists() {
        _filteredBooks.value = getFilteredBooks()
        _filteredChapters.value = getFilteredChapters()
        _filteredVerses.value = getFilteredVerses()
    }

    fun addCurrentVerseToSchedule(
        onAdd: (bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String, bookId: Int) -> Unit
    ): Boolean {
        if (_verses.value.isEmpty()) return false
        val idx = _selectedVerseIndex.value
        if (idx < 0 || idx >= _verses.value.size) return false
        val selectedVerses = getSelectedVerses()
        if (selectedVerses.isEmpty()) return false
        val verse = selectedVerses[0]
        onAdd(verse.bookName, verse.chapter, verse.verseNumber, verse.verseText, verse.verseRange, verse.bookId)

        if (_multiVerseEnabled.value) {
            clearMultiVerseSelection()
        }
        return true
    }

    fun addCanonicalRefToSchedule(
        bookId: Int,
        chapter: Int,
        verse: Int,
        onAdd: (bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String, bookId: Int) -> Unit,
    ): Boolean {
        val bible = _primaryBible.value ?: return false
        val details = bible.getVerseDetailsByCode(bookId, chapter, verse) ?: return false
        onAdd(details.bookName, details.displayChapter, details.displayVerse, details.verseText, "", bookId)
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

    fun performSearch() = launchSearch(debounceMs = 0L)

    internal fun scheduleLiveSearch() = launchSearch(debounceMs = LIVE_SEARCH_DEBOUNCE_MS)

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
            val results = withContext(ioDispatcher) {
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

    internal var remoteModeActive = false
    internal var syncMode = BibleSyncMode.FULL_REPLICA
    internal var remoteBibleCacheFile: File? = null
    internal var remoteSecondaryBibleCacheFile: File? = null
    internal var remoteTranslationCacheFiles: List<Pair<String, File>> = emptyList()
    internal val remoteBibleCacheDir = File(System.getProperty("user.home"), ".churchpresenter/instance-link/cache/bibles")
    internal val actedDetectionKeys = HashSet<String>()

    val nextVerses: State<List<SelectedVerse>> = derivedStateOf { getNextVerses() }

    fun dispose() {
        viewModelScope.cancel()
    }
}
