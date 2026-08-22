package org.churchpresenter.dictionary

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Which of the six bundled files a load reads, and what it makes of them.
 *
 * The dictionary ships in two languages and two alphabets, so a load is four decisions: normalise
 * whatever language string arrived, pick a Hebrew file and a Greek file from it, and parse each.
 * Everything above this — the tab's sorted entry list, the REST layer's flat cache — is built from
 * the result, so a wrong file here is a whole dictionary in the wrong language.
 */
class StrongsCatalogTest {

    private val reads = mutableListOf<String>()

    private fun catalog() = StrongsCatalog(loader = { name ->
        reads += name
        DictionaryFixture.catalogBytes(name)
    })

    // ── Choosing the language ───────────────────────────────────────────────────

    @Test
    fun `only Russian maps to ru, everything else falls back to en`() {
        assertEquals("ru", StrongsCatalog.normalizeLanguage("ru"))
        assertEquals("ru", StrongsCatalog.normalizeLanguage("RU"))
        assertEquals("en", StrongsCatalog.normalizeLanguage("en"))
        assertEquals("en", StrongsCatalog.normalizeLanguage("fr"))
        assertEquals("en", StrongsCatalog.normalizeLanguage(null))
        assertEquals("en", StrongsCatalog.normalizeLanguage(""))
    }

    @Test
    fun `english reads the two english files`() = runBlocking {
        catalog().load("en")

        assertEquals(listOf(StrongsCatalog.HEBREW_FILE, StrongsCatalog.GREEK_FILE), reads)
    }

    @Test
    fun `russian reads the two russian files`() = runBlocking {
        catalog().load("ru")

        assertEquals(listOf(StrongsCatalog.HEBREW_FILE_RU, StrongsCatalog.GREEK_FILE_RU), reads)
    }

    @Test
    fun `an unknown language reads the english files rather than none`() = runBlocking {
        catalog().load("kk")

        assertEquals(listOf(StrongsCatalog.HEBREW_FILE, StrongsCatalog.GREEK_FILE), reads)
    }

    // ── What comes back ─────────────────────────────────────────────────────────

    @Test
    fun `the two halves stay apart, and each is parsed`() = runBlocking {
        val loaded = catalog().load("en")

        assertEquals(listOf("H7225", "H430"), loaded.hebrew.map { it.number })
        assertEquals(listOf("G5485", "G26"), loaded.greek.map { it.number })
        assertEquals("elohiym", loaded.hebrew.first { it.number == "H430" }.transliteration)
    }

    @Test
    fun `all joins the halves with Hebrew first`() = runBlocking {
        val loaded = catalog().load("en")

        assertEquals(listOf("H7225", "H430", "G5485", "G26"), loaded.all.map { it.number })
    }

    @Test
    fun `the russian files carry the russian text`() = runBlocking {
        val loaded = catalog().load("ru")

        assertTrue(loaded.hebrew.any { it.definition == "Бог, судьи" }, "got ${loaded.hebrew}")
    }

    @Test
    fun `nothing is read until a load is asked for`() {
        catalog()

        assertTrue(reads.isEmpty())
    }

    /** An unknown key is not an error to recover from: the load stops rather than half-answering. */
    @Test
    fun `a file the loader cannot serve fails the load`() {
        val broken = StrongsCatalog(loader = { error("no such file") })

        assertFailsWith<IllegalStateException> { runBlocking { broken.load("en") } }
    }

    // ── The packaged files ──────────────────────────────────────────────────────

    /**
     * The one test that touches the real resources, and the only one that proves the module's data
     * is actually packaged with it: everything else here reads a fixture, which would go on passing
     * with all six files deleted.
     */
    @Test
    fun `a default catalogue loads the packaged dictionary`() = runBlocking {
        val loaded = StrongsCatalog().load("en")

        assertTrue(loaded.hebrew.size > 8_000, "Strong's has ~8,600 Hebrew entries, got ${loaded.hebrew.size}")
        assertTrue(loaded.greek.size > 5_000, "and ~5,500 Greek ones, got ${loaded.greek.size}")
        assertTrue(loaded.hebrew.all { it.isHebrew }, "the Hebrew file must hold only H numbers")
        assertTrue(loaded.greek.all { it.isGreek }, "and the Greek file only G numbers")
        // The shipped transliterations carry their diacritics; the fixture's do not, which is
        // exactly why this test reads the real file.
        assertEquals("\u02BC\u0115l\u00F4h\u00EEym", loaded.hebrew.first { it.number == "H430" }.transliteration)
    }

    @Test
    fun `the bundled files are on the classpath`() {
        assertTrue(readBundledDictionaryFile(StrongsCatalog.GREEK_FILE_RU).isNotEmpty())
    }

    /** Constructed with the packaged loader, an index reads nothing until a testament is asked for. */
    @Test
    fun `a default interlinear index reads nothing on construction`() {
        assertTrue(InterlinearRepository().getVersesForEntry("G26").isEmpty())
    }

    @Test
    fun `a bundled file that is not there says so`() {
        val failure = assertFailsWith<IllegalStateException> { readBundledDictionaryFile("strongs_xx.json") }

        assertTrue("strongs_xx.json" in failure.message.orEmpty(), failure.message.orEmpty())
    }
}
