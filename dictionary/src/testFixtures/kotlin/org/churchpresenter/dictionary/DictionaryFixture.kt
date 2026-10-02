package org.churchpresenter.dictionary

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.dictionary.data.DictionaryFiles
import org.churchpresenter.dictionary.data.InterlinearVerse
import org.churchpresenter.dictionary.data.InterlinearWord
import org.churchpresenter.dictionary.data.StrongsEntry
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A miniature Strong's dictionary for [DictionaryViewModel] tests.
 *
 * The real dictionary is ~14k entries across four bundled 1–2 MB JSON files that
 * [DictionaryViewModel.load] reads through [DictionaryFiles]. [files] stands in for them with the
 * handful of entries below, so tests get a known, tiny corpus — searching a real file
 * would make every assertion depend on data nobody in the test can see, and re-parsing megabytes
 * per test would dominate the run.
 *
 * Entries are deliberately varied: Hebrew and Greek, distinct transliterations, pronunciations and
 * definitions, so a test can prove *which* field a query matched on.
 */
object DictionaryFixture {

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
     * The four Strong's files as the fixture, and the two interlinear files as [interlinearGreek] and
     * [interlinearHebrew] (each a JSON list of verses; none by default).
     *
     * [extraGreek] appends entries to the Greek side for a test that needs a shape the standing
     * corpus does not have — a definition long enough to be truncated, for instance. The corpus is
     * deliberately tiny and every other suite asserts against it by name, so this adds rather than
     * replaces, and defaults to empty.
     */
    fun files(
        extraGreek: List<StrongsEntry> = emptyList(),
        interlinearGreek: String = "[]",
        interlinearHebrew: String = "[]",
    ): FixtureFiles = FixtureFiles(
        mapOf(
            DictionaryFiles.STRONGS_HEBREW to bytes(hebrewEntries),
            DictionaryFiles.STRONGS_GREEK to bytes(greekEntries + extraGreek),
            DictionaryFiles.STRONGS_HEBREW_RU to bytes(hebrewEntriesRu),
            DictionaryFiles.STRONGS_GREEK_RU to bytes(greekEntriesRu),
            DictionaryFiles.INTERLINEAR_GREEK to interlinearGreek.toByteArray(),
            DictionaryFiles.INTERLINEAR_HEBREW to interlinearHebrew.toByteArray(),
        )
    )

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

/**
 * A [DictionaryFiles] over fixed contents, which records every read so a test can say how often a
 * file was loaded. A file named in [failing] throws instead, as an unreadable file would.
 */
class FixtureFiles(private val contents: Map<String, ByteArray>) : DictionaryFiles {
    private val reads = CopyOnWriteArrayList<String>()

    @Volatile
    var failing: Set<String> = emptySet()

    /** When set, every read waits for it: a test holds a load open to see what shows meanwhile. */
    @Volatile
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun read(name: String): ByteArray {
        reads += name
        gate?.await()
        if (name in failing) throw IOException("$name is unreadable")
        return contents[name] ?: throw FileNotFoundException(name)
    }

    /** How many times [name] has been read. */
    fun readsOf(name: String): Int = reads.count { it == name }
}
