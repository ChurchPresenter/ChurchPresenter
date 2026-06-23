package org.churchpresenter.app.churchpresenter.data

import churchpresenter.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi

class InterlinearRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private val greekIndex  = HashMap<String, MutableList<InterlinearVerse>>()
    private val hebrewIndex = HashMap<String, MutableList<InterlinearVerse>>()

    private var greekLoaded  = false
    private var hebrewLoaded = false

    @OptIn(ExperimentalResourceApi::class)
    suspend fun ensureGreekLoaded() {
        if (greekLoaded) return
        withContext(Dispatchers.IO) {
            val bytes = Res.readBytes("files/dictionary/interlinear_g.json")
            val verses = json.decodeFromString<List<InterlinearVerse>>(bytes.decodeToString())
            for (verse in verses) {
                for (word in verse.words) {
                    greekIndex.getOrPut(word.strongsNumber) { mutableListOf() }.add(verse)
                }
            }
            greekLoaded = true
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun ensureHebrewLoaded() {
        if (hebrewLoaded) return
        withContext(Dispatchers.IO) {
            val bytes = Res.readBytes("files/dictionary/interlinear_h.json")
            val verses = json.decodeFromString<List<InterlinearVerse>>(bytes.decodeToString())
            for (verse in verses) {
                for (word in verse.words) {
                    hebrewIndex.getOrPut(word.strongsNumber) { mutableListOf() }.add(verse)
                }
            }
            hebrewLoaded = true
        }
    }

    fun getVersesForEntry(number: String): List<InterlinearVerse> {
        val index = if (number.startsWith("G")) greekIndex else hebrewIndex
        return index[number] ?: emptyList()
    }
}
