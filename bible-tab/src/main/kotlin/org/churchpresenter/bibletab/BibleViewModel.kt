package org.churchpresenter.bibletab

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.utils.UsageEventStore
import org.churchpresenter.sharedui.utils.UsageEvents
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.BibleLoadError
import org.churchpresenter.bible.BibleSearch
import org.churchpresenter.core.models.bible.SelectedVerse
import androidx.compose.runtime.derivedStateOf
import java.io.File
import org.churchpresenter.settings.BibleSyncMode

private const val MAX_HISTORY_ENTRIES = 50
internal const val ASCII_LIMIT = 128

class BibleViewModel(
    internal var appSettings: AppSettings,
    internal val onBibleLoaded: ((bible: Bible, translation: String) -> Unit)? = null,

    internal val onSecondaryBibleFilePathChanged: ((filePath: String) -> Unit)? = null,
    internal val onBibleFilePathsChanged: ((filePaths: List<String>) -> Unit)? = null,

    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    internal val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    internal val usage: UsageEventStore = UsageEvents,
    // Where the Instance Link follower's Bible sync events are logged; the app's InstanceLinkLogger.
    internal val remoteSyncLog: (event: String, fields: Map<String, Any?>) -> Unit = { _, _ -> },
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

    internal val _selectedScopeIndex = mutableStateOf(0)

    val selectedScopeIndex: State<Int> = _selectedScopeIndex

    internal val _selectedModeIndex = mutableStateOf(0)

    val selectedModeIndex: State<Int> = _selectedModeIndex

    internal val _bookSearchQuery = mutableStateOf("")

    val bookSearchQuery: State<String> = _bookSearchQuery

    internal val _chapterSearchQuery = mutableStateOf("")

    val chapterSearchQuery: State<String> = _chapterSearchQuery

    internal val _verseSearchQuery = mutableStateOf("")

    val verseSearchQuery: State<String> = _verseSearchQuery

    internal val _filteredBooks = mutableStateOf<List<String>>(emptyList())

    val filteredBooks: State<List<String>> = _filteredBooks

    internal val _filteredChapters = mutableStateOf<List<String>>(emptyList())

    val filteredChapters: State<List<String>> = _filteredChapters

    internal val _filteredVerses = mutableStateOf<List<String>>(emptyList())

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

    // Which half of a split verse is showing, and the selection publication it belongs to. Anything
    // that selects something else bumps the token above and so invalidates it. See VerseSplit.kt.
    internal val _versePage = mutableStateOf(VERSE_PAGE_FIRST)

    internal var _versePageToken = -1

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

    internal val _textMatchLevel = mutableStateOf(
        runCatching { TextMatchLevel.valueOf(appSettings.bibleEngineSettings.textMatchLevel.uppercase()) }
            .getOrDefault(TextMatchLevel.OFF)
    )

    val textMatchLevel: State<TextMatchLevel> = _textMatchLevel

    var onTextMatchLevelChanged: ((TextMatchLevel) -> Unit)? = null

    fun setTextMatchLevel(level: TextMatchLevel) {
        _textMatchLevel.value = level
        onTextMatchLevelChanged?.invoke(level)
    }

    internal val _continuationSpeed = mutableStateOf(
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
        val displayText: String
            get() = if (verseRange.isNotEmpty()) "$bookName $chapter:$verseRange" else "$bookName $chapter:$verseNumber"
    }

    internal val _history = mutableStateListOf<HistoryEntry>()

    val history: List<HistoryEntry> get() = _history

    fun addToHistory(bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String = "") {
        val entry = HistoryEntry(bookName, chapter, verseNumber, verseText, verseRange)

        _history.removeAll {
            it.bookName == bookName && it.chapter == chapter && it.verseNumber == verseNumber &&
                it.verseRange == verseRange
        }

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
        internal const val CLICK_DEBOUNCE_MS = 300L
        internal const val LIVE_SEARCH_DEBOUNCE_MS = 300L

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

    // The schedule hand-over (verse and version) the Bible tab last acted on. The tab is rebuilt on
    // every visit and the app keeps the last schedule verse, so this is how a visit tells a fresh
    // schedule click apart from one it has already handled.
    internal var scheduleSeen: Pair<ScheduleItem.BibleVerseItem?, Int>? = null

    internal var searchJob: kotlinx.coroutines.Job? = null

    internal var lastChapterSelectTime = 0L

    internal var lastBookSelectTime = 0L

    init {
        _selectedScopeIndex.value = 0
        _selectedModeIndex.value = 0
        loadBibles()
    }

    internal var _sequentialChapterAdvance = false

    internal var remoteModeActive = false

    internal var syncMode = BibleSyncMode.FULL_REPLICA

    internal var remoteBibleCacheFile: File? = null

    internal var remoteSecondaryBibleCacheFile: File? = null

    internal var remoteTranslationCacheFiles: List<Pair<String, File>> = emptyList()

    internal val remoteBibleCacheDir = File(
        System.getProperty("user.home"),
        ".churchpresenter/instance-link/cache/bibles"
    )

    internal val actedDetectionKeys = HashSet<String>()

    val nextVerses: State<List<SelectedVerse>> = derivedStateOf { getNextVerses() }

    fun dispose() {
        viewModelScope.cancel()
    }
}
