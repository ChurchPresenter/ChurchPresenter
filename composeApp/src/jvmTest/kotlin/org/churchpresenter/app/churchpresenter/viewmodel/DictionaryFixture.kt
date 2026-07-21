package org.churchpresenter.app.churchpresenter.viewmodel

import churchpresenter.composeapp.generated.resources.Res
import io.mockk.coEvery
import io.mockk.mockkObject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.data.InterlinearVerse
import org.churchpresenter.app.churchpresenter.data.InterlinearWord
import org.churchpresenter.app.churchpresenter.data.StrongsEntry

/**
 * A miniature Strong's dictionary for [DictionaryViewModel] tests.
 *
 * The real dictionary is ~14k entries across four bundled 1–2 MB JSON files that
 * [DictionaryViewModel.load] pulls through `Res.readBytes`. [stubResources] replaces those reads
 * with the handful of entries below, so tests get a known, tiny corpus — searching a real file
 * would make every assertion depend on data nobody in the test can see, and re-parsing megabytes
 * per test would dominate the run.
 *
 * Entries are deliberately varied: Hebrew and Greek, distinct transliterations, pronunciations and
 * definitions, so a test can prove *which* field a query matched on.
 */
object DictionaryFixture {

    const val HEBREW_EN = "files/dictionary/strongs_h.json"
    const val GREEK_EN = "files/dictionary/strongs_g.json"
    const val HEBREW_RU = "files/dictionary/strongs_h_ru.json"
    const val GREEK_RU = "files/dictionary/strongs_g_ru.json"

    val elohim = StrongsEntry(
        number = "H430",
        word = "אֱלֹהִים",
        transliteration = "elohiym",
        pronunciation = "el-o-heem'",
        definition = "God, gods, rulers, judges",
        kjvUsage = "God, god, judge, GOD"
    )

    val reshith = StrongsEntry(
        number = "H7225",
        word = "רֵאשִׁית",
        transliteration = "reshiyth",
        pronunciation = "ray-sheeth'",
        definition = "the first, in place, time, order or rank",
        kjvUsage = "beginning, chief"
    )

    val agape = StrongsEntry(
        number = "G26",
        word = "ἀγάπη",
        transliteration = "agape",
        pronunciation = "ag-ah'-pay",
        definition = "brotherly love, affection, benevolence",
        kjvUsage = "love, charity"
    )

    val charis = StrongsEntry(
        number = "G5485",
        word = "χάρις",
        transliteration = "charis",
        pronunciation = "khar'-ece",
        definition = "grace, that which affords joy and pleasure",
        kjvUsage = "grace, favour, thanks"
    )

    val hebrewEntries = listOf(reshith, elohim)   // deliberately not in number order
    val greekEntries = listOf(charis, agape)

    /** The same words as the Russian dictionary would return them — used to prove a language swap took. */
    val hebrewEntriesRu = hebrewEntries.map { it.copy(definition = "Бог, судьи") }
    val greekEntriesRu = greekEntries.map { it.copy(definition = "любовь") }

    private val json = Json { ignoreUnknownKeys = true }

    private fun bytes(entries: List<StrongsEntry>): ByteArray =
        json.encodeToString(ListSerializer(StrongsEntry.serializer()), entries).toByteArray()

    /**
     * Points `Res.readBytes` at the fixture for all four dictionary files. Callers are responsible
     * for `unmockkObject(Res)` in teardown.
     */
    fun stubResources() {
        mockkObject(Res)
        coEvery { Res.readBytes(HEBREW_EN) } returns bytes(hebrewEntries)
        coEvery { Res.readBytes(GREEK_EN) } returns bytes(greekEntries)
        coEvery { Res.readBytes(HEBREW_RU) } returns bytes(hebrewEntriesRu)
        coEvery { Res.readBytes(GREEK_RU) } returns bytes(greekEntriesRu)
    }

    /**
     * An interlinear verse at [book]/[chapter]/[verse]. The repository stores the reference as a
     * packed `BBBCCCVVV` string, which is what the view model's book/chapter accessors parse.
     */
    fun verse(book: Int, chapter: Int, verse: Int, strongsNumber: String = "G26"): InterlinearVerse =
        InterlinearVerse(
            ref = "%03d%03d%03d".format(book, chapter, verse),
            words = listOf(InterlinearWord(text = "λόγος", strongsNumber = strongsNumber))
        )
}
