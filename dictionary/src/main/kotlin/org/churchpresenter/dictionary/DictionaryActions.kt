package org.churchpresenter.dictionary

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.bible.Bible
import org.churchpresenter.bible.MAX_BIBLE_SCAN_DEPTH
import org.churchpresenter.bible.readTranslationTitle
import org.churchpresenter.dictionary.data.StrongsEntry
import java.io.File

/** Choosing an entry, and moving back and forward through the ones chosen. */
interface DictionarySelectionActions {
    fun onEntrySelected(entry: StrongsEntry?, addToHistory: Boolean = true, scrollToEntry: Boolean = true)
    fun goBack()
    fun goForward()
    fun showMoreInterlinear()
    fun selectByNumber(number: String)
}

/** The passage filters: one over the entry list, one over the detail pane's verse cards. */
interface DictionaryPassageActions {
    fun filterCardsByBook(bookId: Int?)
    fun filterCardsByChapter(chapter: Int?)
    fun filterEntryListByBook(bookId: Int?)
    fun filterEntryListByChapter(chapter: Int?)
    fun filterEntryListByVerse(verse: Int?)
}

/** The Bible the dictionary quotes verses from, chosen from the translations installed. */
interface DictionaryBibleActions {
    /**
     * Lists the modules under [directory] for the dictionary's bible picker; returns the scan's job,
     * or null when there is none to run.
     *
     * [customNames] is the operator's own name per module, keyed the way the Bible settings key it
     * — by path *relative* to the storage folder — while this list is keyed by absolute path, so
     * each file is relativised to look its rename up. Renaming a translation in one place and
     * having it still read as its old self here is the kind of half-applied setting that reads as a
     * bug.
     */
    fun loadAvailableBibles(directory: String, customNames: Map<String, String> = emptyMap()): Job?
    fun setDictBible(filePath: String)
}

internal class DictionarySelection(
    private val state: DictionaryState,
    private val scope: CoroutineScope,
) : DictionarySelectionActions {
    private var interlinearJob: Job? = null

    override fun onEntrySelected(entry: StrongsEntry?, addToHistory: Boolean, scrollToEntry: Boolean) =
        with(state) {
            selectedEntry = entry
            if (scrollToEntry && entry != null) scrollRequestToken++
            interlinearJob?.cancel()
            interlinearVerses = emptyList()
            interlinearDisplayLimit = DictionaryViewModel.INTERLINEAR_PAGE_SIZE
            cardBookFilter = null
            cardChapterFilter = null
            if (entry == null) return@with
            if (addToHistory) {
                // Truncate any forward history before pushing the new entry
                if (historyIdx < history.lastIndex) {
                    history.subList(historyIdx + 1, history.size).clear()
                }
                history.add(entry.number)
                historyIdx = history.lastIndex
            }
            interlinearJob = scope.launch {
                isInterlinearLoading = true
                try {
                    if (entry.isGreek) interlinear.ensureGreekLoaded()
                    else interlinear.ensureHebrewLoaded()
                    interlinearVerses = interlinear.getVersesForEntry(entry.number)
                } finally {
                    isInterlinearLoading = false
                }
            }
        }

    override fun goBack() = with(state) {
        if (historyIdx <= 0) return@with
        historyIdx--
        selectFromHistory()
    }

    override fun goForward() = with(state) {
        if (historyIdx >= history.lastIndex) return@with
        historyIdx++
        selectFromHistory()
    }

    private fun selectFromHistory() {
        val number = state.history[state.historyIdx]
        onEntrySelected(state.entries.find { it.number == number }, addToHistory = false)
    }

    override fun showMoreInterlinear() {
        state.interlinearDisplayLimit += DictionaryViewModel.INTERLINEAR_PAGE_SIZE
    }

    override fun selectByNumber(number: String) {
        val normalized = number.uppercase()
        val found = state.entries.find { it.number == normalized }
        if (found != null) {
            // If navigating to an entry not visible under the current passage filter, clear it
            val visible = state.passageNumbers()
            if (visible != null && found.number !in visible) state.clearPassageFilter()
            onEntrySelected(found, scrollToEntry = false)
        } else if (normalized.isNotEmpty()) {
            state.pendingSelectionNumber = normalized
        }
    }
}

internal class DictionaryPassages(
    private val state: DictionaryState,
    private val selection: DictionarySelectionActions,
) : DictionaryPassageActions {

    override fun filterCardsByBook(bookId: Int?) {
        state.cardBookFilter = bookId
        state.cardChapterFilter = null
    }

    override fun filterCardsByChapter(chapter: Int?) {
        state.cardChapterFilter = chapter
    }

    override fun filterEntryListByBook(bookId: Int?) {
        state.entryBookFilter = bookId
        state.entryChapterFilter = null
        state.entryVerseFilter = null
        if (bookId != null) reselectIfNeeded()
    }

    override fun filterEntryListByChapter(chapter: Int?) {
        state.entryChapterFilter = chapter
        state.entryVerseFilter = null
        if (chapter != null) reselectIfNeeded()
    }

    override fun filterEntryListByVerse(verse: Int?) {
        state.entryVerseFilter = verse
        if (verse != null) reselectIfNeeded()
    }

    /** If the current selection is no longer in the filtered list, pick the first visible entry. */
    private fun reselectIfNeeded() {
        val results = state.searchResults
        val current = state.selectedEntry
        if (current != null && results.any { it.number == current.number }) return
        val first = results.firstOrNull()
        if (first != null) selection.onEntrySelected(first, addToHistory = false)
    }
}

internal class DictionaryBibles(
    private val state: DictionaryState,
    private val scope: CoroutineScope,
) : DictionaryBibleActions {

    /** The scan of the Bible folder in flight, so a newer one can cancel it -- see [loadAvailableBibles]. */
    private var availableBiblesJob: Job? = null

    override fun loadAvailableBibles(directory: String, customNames: Map<String, String>): Job? {
        // One scan at a time. Each runs on the IO pool and finishes in its own time, so two folders
        // named in quick succession could answer out of order and the stale scan's list would win
        // -- an empty folder's `emptyList()` landing after the next folder's translations.
        availableBiblesJob?.cancel()
        if (directory.isEmpty()) {
            state.availableDictBibles = emptyList()
            return null
        }
        return scope.launch {
            val dir = File(directory)
            if (!dir.exists() || !dir.isDirectory) {
                state.availableDictBibles = emptyList()
                return@launch
            }
            state.availableDictBibles = withContext(Dispatchers.IO) {
                // Subfolders included: collections nest a folder per language and translation, so a
                // flat listing sees only whatever happens to sit at the top level. This one keys on
                // absolute paths already, so nesting needs no other change.
                dir.walkTopDown().maxDepth(MAX_BIBLE_SCAN_DEPTH)
                    .filter { it.isFile && it.extension.lowercase() == "spb" }
                    .sortedBy { it.absolutePath }
                    .map { f ->
                        val key = f.relativeToOrNull(dir)?.invariantSeparatorsPath
                        f.absolutePath to readTranslationTitle(f, key?.let(customNames::get))
                    }
                    .toList()
            }
        }.also { availableBiblesJob = it }
    }

    override fun setDictBible(filePath: String) = with(state) {
        if (filePath == dictBibleFile) return@with
        dictBibleFile = filePath
        if (filePath.isEmpty()) {
            dictBible = null
            return@with
        }
        isDictBibleLoading = true
        scope.launch {
            try {
                dictBible = withContext(Dispatchers.IO) { Bible().apply { loadFromSpb(filePath) } }
            } catch (_: Exception) {
                dictBible = null
            } finally {
                isDictBibleLoading = false
            }
        }
        Unit
    }
}
