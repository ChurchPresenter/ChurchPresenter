package org.churchpresenter.dictionary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.bible.Bible
import org.churchpresenter.dictionary.data.InterlinearRepository
import org.churchpresenter.dictionary.data.InterlinearVerse
import org.churchpresenter.dictionary.data.StrongsEntry

/**
 * Everything [DictionaryViewModel] and its parts read and write. The parts are separate classes, but
 * the dictionary is one piece of state: the selection, the passage filters and the list all look at
 * the same entries.
 */
internal class DictionaryState(val interlinear: InterlinearRepository) {
    var dictLanguage by mutableStateOf("en")
    var dictBibleFile by mutableStateOf("")
    var dictBible by mutableStateOf<Bible?>(null)
    var isDictBibleLoading by mutableStateOf(false)
    var availableDictBibles by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var isLoading by mutableStateOf(false)
    var entries: List<StrongsEntry> by mutableStateOf(emptyList())
    var searchQuery by mutableStateOf("")
    var filterLanguage by mutableStateOf(DictionaryLanguageFilter.ALL)
    var selectedEntry by mutableStateOf<StrongsEntry?>(null)

    /** Back/Forward navigation history, as Strong's numbers. */
    val history = mutableListOf<String>()
    var historyIdx by mutableStateOf(-1)

    var entryBookFilter by mutableStateOf<Int?>(null)
    var entryChapterFilter by mutableStateOf<Int?>(null)
    var entryVerseFilter by mutableStateOf<Int?>(null)
    var isInterlinearDataLoaded by mutableStateOf(false)

    /** A number asked for before the entries had loaded, selected once they have. */
    var pendingSelectionNumber: String? = null

    var interlinearVerses by mutableStateOf<List<InterlinearVerse>>(emptyList())
    var isInterlinearLoading by mutableStateOf(false)
    var interlinearDisplayLimit by mutableStateOf(DictionaryViewModel.INTERLINEAR_PAGE_SIZE)
    var scrollRequestToken by mutableStateOf(0)

    /** Book/chapter filter for the detail pane's verse cards; reset on every entry switch. */
    var cardBookFilter by mutableStateOf<Int?>(null)
    var cardChapterFilter by mutableStateOf<Int?>(null)

    /** The Strong's numbers the entry-list passage filter keeps, or null when no book is chosen. */
    fun passageNumbers(): Set<String>? {
        val book = entryBookFilter ?: return null
        return interlinear.getStrongsForBookChapter(book, entryChapterFilter, entryVerseFilter)
    }

    fun clearPassageFilter() {
        entryBookFilter = null
        entryChapterFilter = null
        entryVerseFilter = null
    }

    /** The entry list: the language filter, then the passage filter, then the search box. */
    val searchResults: List<StrongsEntry>
        get() {
            val pool = when (filterLanguage) {
                DictionaryLanguageFilter.HEBREW -> entries.filter { it.isHebrew }
                DictionaryLanguageFilter.GREEK -> entries.filter { it.isGreek }
                DictionaryLanguageFilter.ALL -> entries
            }
            val passageFiltered = passageNumbers()?.let { valid -> pool.filter { it.number in valid } } ?: pool
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
}
