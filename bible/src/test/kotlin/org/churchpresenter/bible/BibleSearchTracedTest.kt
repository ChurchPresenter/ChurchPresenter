package org.churchpresenter.bible

import io.sentry.NoOpTransportFactory
import io.sentry.Sentry
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BibleSearchTracedTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-traced").toFile()
        Sentry.init { options ->
            options.dsn = "https://key@localhost/1"
            options.setTransportFactory(NoOpTransportFactory.getInstance())
            options.isEnableUncaughtExceptionHandler = false
            options.isEnableAutoSessionTracking = false
            options.tracesSampleRate = 1.0
        }
    }

    @AfterTest
    fun tearDown() {
        Sentry.close()
        dir.deleteRecursively()
    }

    private fun bible(): Bible = SpbFixture.loadedBible(dir)

    private fun anyWord(word: String) = Regex(word, RegexOption.IGNORE_CASE)

    private fun allWords(vararg words: String) =
        Regex("\\b(${words.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

    @Test
    fun `a traced search still returns its results`() {
        val results = bible().searchBible(false, anyWord("God"))

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { "god" in it.verseText.lowercase() })
    }

    @Test
    fun `a traced search narrowed to a book returns only that book`() {
        val results = bible().searchBible(false, anyWord("the"), book = 19)

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.book == "Psalms" }, results.map { it.book }.toString())
    }

    @Test
    fun `a traced search narrowed to a chapter returns only that chapter`() {
        val results = bible().searchBible(false, anyWord("the"), book = 1, chapter = 1)

        assertTrue(results.isNotEmpty())
        assertTrue(results.all { it.chapter == "1" })
    }

    @Test
    fun `a traced all-words search keeps only verses carrying every word`() {
        val results = bible().searchBible(true, allWords("heaven", "earth"))

        assertTrue(
            results.all { "heaven" in it.verseText.lowercase() && "earth" in it.verseText.lowercase() },
            results.map { it.verseText }.toString(),
        )
    }

    @Test
    fun `a traced all-words search inside one chapter`() {
        val results = bible().searchBible(true, allWords("heaven", "earth"), book = 1, chapter = 1)

        assertEquals(1, results.size, "only Genesis 1:1 carries both words")
    }

    @Test
    fun `a traced search that matches nothing returns nothing`() {
        assertTrue(bible().searchBible(false, anyWord("aardvark")).isEmpty())
    }

    @Test
    fun `a traced all-words search that matches nothing returns nothing`() {
        assertTrue(bible().searchBible(true, allWords("aardvark", "wombat")).isEmpty())
    }

    @Test
    fun `a traced search of an empty bible returns nothing`() {
        assertTrue(Bible().searchBible(false, anyWord("God")).isEmpty())
    }

    @Test
    fun `a traced result names the verse it came from`() {
        val result = bible().searchBible(false, anyWord("shepherd")).single()

        assertEquals("Psalms", result.book)
        assertEquals("23", result.chapter)
        assertEquals("1", result.verse)
        assertTrue(result.verseText.startsWith("Psalms 23:1 "))
    }

    @Test
    fun `a book with no match returns nothing rather than the whole bible`() {
        assertTrue(bible().searchBible(false, anyWord("shepherd"), book = 1).isEmpty())
    }
}
