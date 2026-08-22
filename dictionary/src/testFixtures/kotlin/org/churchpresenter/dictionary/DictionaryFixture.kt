package org.churchpresenter.dictionary

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A miniature Strong's dictionary and interlinear index, standing in for the bundled files.
 *
 * The real data is six files and 18 MB — ~14k entries plus every occurrence of every one of them —
 * so nothing here reads it. Both the module's own suite and the app's dictionary tab, view-model
 * and server suites build their fixtures from this instead: the production classes do their real
 * parsing, indexing, filtering and sorting, over a corpus small enough that each assertion can name
 * the entries it expects.
 *
 * The entries are deliberately varied — Hebrew and Greek, distinct transliterations, pronunciations
 * and definitions — so a test can prove *which* field a query matched on.
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

    /** John 3:16, John 3:17, Matthew 5:3 — two books, so the book index has something to sort. */
    val greekInterlinear = """
        [
          {"r":"043003016","w":[{"t":"ἀγάπη","s":"G26"},{"t":"θεός","s":"G2316"}]},
          {"r":"043003017","w":[{"t":"θεός","s":"G2316"}]},
          {"r":"040005003","w":[{"t":"ἀγάπη","s":"G26"}]}
        ]
    """.trimIndent()

    /** Genesis 1:1 and Psalm 23:1. */
    val hebrewInterlinear = """
        [
          {"r":"001001001","w":[{"t":"אֱלֹהִים","s":"H430"},{"t":"רֵאשִׁית","s":"H7225"}]},
          {"r":"019023001","w":[{"t":"אֱלֹהִים","s":"H430"}]}
        ]
    """.trimIndent()

    private val json = Json { ignoreUnknownKeys = true }

    private fun bytes(entries: List<StrongsEntry>): ByteArray =
        json.encodeToString(ListSerializer(StrongsEntry.serializer()), entries).toByteArray()

    /**
     * A catalogue serving the fixture entries for all four bundled dictionary files.
     *
     * [extraGreek] appends entries to the Greek side for a test that needs a shape the standing
     * corpus does not have — a definition long enough to be truncated, for instance. The corpus is
     * deliberately tiny and every other suite asserts against it by name, so this adds rather than
     * replaces, and defaults to empty.
     */
    fun catalog(extraGreek: List<StrongsEntry> = emptyList()): StrongsCatalog =
        StrongsCatalog(loader = { name -> catalogBytes(name, extraGreek) })

    /** What [catalog] answers for one bundled file — for a test that counts reads of its own. */
    fun catalogBytes(name: String, extraGreek: List<StrongsEntry> = emptyList()): ByteArray = when (name) {
        StrongsCatalog.HEBREW_FILE -> bytes(hebrewEntries)
        StrongsCatalog.GREEK_FILE -> bytes(greekEntries + extraGreek)
        StrongsCatalog.HEBREW_FILE_RU -> bytes(hebrewEntriesRu)
        StrongsCatalog.GREEK_FILE_RU -> bytes(greekEntriesRu + extraGreek)
        else -> error("no such bundled file: $name")
    }

    /** An interlinear index over [greekInterlinear] and [hebrewInterlinear], nothing loaded yet. */
    fun interlinear(): InterlinearRepository = InterlinearRepository(loader = RecordingFiles()::read)

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
 * The two interlinear files, counting reads.
 *
 * The counts are how "each testament is read once" and "a Greek lookup does not touch the Hebrew
 * file" can be asserted at all — the production class has no other way of saying what it read.
 */
class RecordingFiles(
    private val greek: String = DictionaryFixture.greekInterlinear,
    private val hebrew: String = DictionaryFixture.hebrewInterlinear,
) {
    var greekReads = 0
        private set
    var hebrewReads = 0
        private set

    /** Set to make the next Greek read throw, for the failure paths. */
    var greekFailure: Throwable? = null

    fun read(name: String): ByteArray = when (name) {
        GREEK_INTERLINEAR_FILE -> {
            greekReads++
            greekFailure?.let { throw it }
            greek.toByteArray()
        }
        HEBREW_INTERLINEAR_FILE -> {
            hebrewReads++
            hebrew.toByteArray()
        }
        else -> error("no such bundled file: $name")
    }

    fun repository() = InterlinearRepository(loader = ::read)
}
