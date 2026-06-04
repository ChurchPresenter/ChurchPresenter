package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
    var selectedEntry by mutableStateOf<StrongsEntry?>(null)
    private var pendingSelectionNumber: String? = null

    val searchResults: List<StrongsEntry>
        get() {
            val pool = when (filterLanguage) {
                DictionaryLanguageFilter.HEBREW -> entries.filter { it.isHebrew }
                DictionaryLanguageFilter.GREEK -> entries.filter { it.isGreek }
                DictionaryLanguageFilter.ALL -> entries
            }
            val q = searchQuery.trim()
            if (q.isEmpty()) return pool
            val lower = q.lowercase()
            return pool.filter { entry ->
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
                    selectedEntry = entries.find { it.number == num }
                    pendingSelectionNumber = null
                }
            } catch (_: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    fun selectByNumber(number: String) {
        val found = entries.find { it.number == number }
        if (found != null) {
            selectedEntry = found
        } else if (number.isNotEmpty()) {
            pendingSelectionNumber = number
        }
    }

    fun dispose() {
        viewModelScope.cancel()
    }
}
