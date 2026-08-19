package engine

import engine.bible.BibleIndex
import engine.bible.EngineBook
import engine.bible.EngineTranslation
import engine.bible.EngineVerse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleIndexEdgeTest {

    private fun fixture(verses: List<EngineVerse>, id: String = "TEST") = EngineTranslation(
        id = id, title = "Test", abbreviation = id, language = "ENG", numbering = "standard",
        books = listOf(EngineBook(40, "Matthew", 28)),
        byBCV = verses.associateBy { Triple(it.bookNum, it.chapter, it.verse) },
        byChapter = verses.groupBy { it.bookNum to it.chapter },
        byCode = verses.associateBy { it.code },
    )

    private val verses = listOf(
        EngineVerse("40-11-28", 40, 11, 28, "придите ко мне все труждающиеся и обремененные и я успокою вас", false),
        EngineVerse("40-11-29", 40, 11, 29, "возьмите иго мое на себя и научитесь от меня", false),
        EngineVerse("40-11-30", 40, 11, 30, "ибо иго мое благо и бремя мое легко", false),
        EngineVerse("40-05-03", 40, 5, 3, "blessed are the poor in spirit for theirs is the kingdom", false),
    )

    private val index = BibleIndex(listOf(fixture(verses)))

    @Test
    fun `an empty query matches nothing`() {
        assertTrue(index.search("").isEmpty())
        assertTrue(index.searchAllTerms("").isEmpty())
    }

    @Test
    fun `a query of only punctuation matches nothing`() {
        assertTrue(index.search("--- ,,, ...").isEmpty())
        assertTrue(index.searchAllTerms("!!! ???").isEmpty())
    }

    @Test
    fun `topK bounds the number of results`() {
        assertTrue(index.search("иго мое", topK = 1).size <= 1)
    }

    @Test
    fun `a header verse is not indexed`() {
        val withHeader = fixture(
            verses + EngineVerse("40-11-00", 40, 11, 0, "заголовок главы уникальноеслово", true),
        )

        assertTrue(BibleIndex(listOf(withHeader)).search("уникальноеслово").isEmpty())
    }

    @Test
    fun `a blank verse is not indexed`() {
        val withBlank = fixture(verses + EngineVerse("40-11-31", 40, 11, 31, "   ", false))

        assertTrue(BibleIndex(listOf(withBlank)).search("иго").isNotEmpty())
    }

    @Test
    fun `an index over no verses at all searches cleanly`() {
        val empty = BibleIndex(listOf(fixture(emptyList())))

        assertTrue(empty.search("anything").isEmpty())
        assertTrue(empty.searchAllTerms("anything").isEmpty())
    }

    @Test
    fun `a token too short to stem is not fuzzy-expanded`() {
        assertTrue(index.search("зз").isEmpty())
    }

    @Test
    fun `a token containing digits is not fuzzy-expanded`() {
        assertTrue(index.search("труждающ1еся").isEmpty())
    }

    @Test
    fun `a garbled token is rescued by a one-edit stem match`() {
        val hit = index.search("туждающиеся").firstOrNull()

        assertEquals("40-11-28", hit?.verse?.code)
    }

    @Test
    fun `an all-terms search needs every token to be matchable`() {
        assertTrue(index.searchAllTerms("иго мое совершенноотсутствующее").isEmpty())
    }

    @Test
    fun `an all-terms search returns the verse holding every token`() {
        assertEquals("40-11-30", index.searchAllTerms("бремя легко").firstOrNull()?.verse?.code)
    }

    @Test
    fun `english and russian verses live in the same index`() {
        assertEquals("40-05-03", index.search("blessed poor spirit").firstOrNull()?.verse?.code)
        assertEquals("40-11-30", index.search("бремя мое легко").firstOrNull()?.verse?.code)
    }

    @Test
    fun `two translations both contribute postings`() {
        val other = fixture(
            listOf(EngineVerse("40-11-28", 40, 11, 28, "come unto me all ye that labour", false)),
            id = "OTHER",
        )
        val both = BibleIndex(listOf(fixture(verses), other))

        assertTrue(both.search("labour").isNotEmpty())
        assertTrue(both.search("иго").isNotEmpty())
    }
}
