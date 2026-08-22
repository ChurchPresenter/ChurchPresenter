package org.churchpresenter.dictionary

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Search and lookup over the dictionary, as the REST endpoints ask for them.
 *
 * Driven with [DictionaryFixture] rather than the real ~14k-entry files: everything asserted here
 * is filtering, ordering, scoping and capping, and a four-entry corpus makes each of those a
 * one-line expectation. Each test builds its own repository, so nothing is shared and nothing has
 * to be reset.
 */
class StrongsDictionaryRepositoryTest {

    private val files = RecordingFiles()

    private val repo = StrongsDictionaryRepository(
        catalog = DictionaryFixture.catalog(),
        interlinear = files.repository(),
    )

    // ── all() ──────────────────────────────────────────────────────────────────

    @Test
    fun `all combines Hebrew and Greek entries for the requested language`() = runBlocking {
        val entries = repo.all("en")
        assertEquals(
            (DictionaryFixture.hebrewEntries + DictionaryFixture.greekEntries).map { it.number }.toSet(),
            entries.map { it.number }.toSet(),
        )
    }

    @Test
    fun `all is cached -- a second call does not re-read the files`() = runBlocking {
        var reads = 0
        val counted = StrongsDictionaryRepository(
            catalog = StrongsCatalog(loader = { name -> reads++; DictionaryFixture.catalogBytes(name) }),
            interlinear = files.repository(),
        )

        counted.all("en")
        val entries = counted.all("en")

        assertEquals(4, entries.size)
        assertEquals(2, reads, "one read per half, however many times the language is asked for")
    }

    @Test
    fun `all serves the Russian files under the ru language key`() = runBlocking {
        val entries = repo.all("ru")
        assertTrue(entries.any { it.definition == "Бог, судьи" }, "expected the Russian fixture text, got $entries")
    }

    // ── lookup() ───────────────────────────────────────────────────────────────

    @Test
    fun `lookup finds an entry by number, case-insensitively, with occurrences and root filled in`() = runBlocking {
        val dto = repo.lookup("h430", "en")
        assertEquals("H430", dto?.number)
        assertEquals(2, dto?.occurrences, "H430 appears in two stubbed interlinear verses")
    }

    @Test
    fun `lookup returns null for a number not in the dictionary`() = runBlocking {
        assertNull(repo.lookup("H9999999", "en"))
    }

    // ── search() ───────────────────────────────────────────────────────────────

    @Test
    fun `an empty query returns entries up to the limit`() = runBlocking {
        val results = repo.search("", "en", filter = "all", limit = 2)
        assertEquals(2, results.size)
    }

    @Test
    fun `search matches by word, transliteration, definition or KJV usage`() = runBlocking {
        assertTrue(repo.search("elohiym", "en", "all", 10).any { it.number == "H430" }, "transliteration match")
        assertTrue(repo.search("brotherly", "en", "all", 10).any { it.number == "G26" }, "definition match")
        assertTrue(repo.search("favour", "en", "all", 10).any { it.number == "G5485" }, "KJV usage match")
    }

    @Test
    fun `search filter narrows to Hebrew or Greek only`() = runBlocking {
        assertTrue(repo.search("", "en", "hebrew", 10).all { it.number.startsWith("H") })
        assertTrue(repo.search("", "en", "greek", 10).all { it.number.startsWith("G") })
    }

    @Test
    fun `an exact number match is returned exactly`() = runBlocking {
        val results = repo.search("G26", "en", "all", 10)
        assertEquals(listOf("G26"), results.map { it.number })
    }

    @Test
    fun `with no exact match, results fall back to ascending Strong's number order`() = runBlocking {
        // "the" matches H7225's definition ("the first, in place...") and G26's ("broTHErly love"),
        // neither exactly — so this pins the comparator's tie-break, `.thenBy { numericValue }`.
        val results = repo.search("the", "en", "all", 10)
        assertEquals(listOf("G26", "H7225"), results.map { it.number })
    }

    @Test
    fun `search scoped to a book only returns Strong's numbers occurring in that book`() = runBlocking {
        // Book 1 (Genesis) in the stubbed Hebrew interlinear only cites H430 and H7225.
        val results = repo.search("", "en", "all", 10, book = 1)
        assertEquals(setOf("H430", "H7225"), results.map { it.number }.toSet())
    }

    @Test
    fun `search scoped to a book with no matching verses returns nothing`() = runBlocking {
        assertEquals(emptyList(), repo.search("", "en", "all", 10, book = 66))
    }

    @Test
    fun `the search limit is coerced into 1 through 500`() = runBlocking {
        assertEquals(1, repo.search("", "en", "all", limit = 0).size)
        assertEquals(4, repo.search("", "en", "all", limit = 10_000).size)
    }

    // ── versesFor() ────────────────────────────────────────────────────────────

    @Test
    fun `versesFor returns the total occurrence count and the distinct verse references`() = runBlocking {
        val (total, refs) = repo.versesFor("G26", limit = 10)
        assertEquals(2, total)
        assertEquals(listOf("043003016", "040005003"), refs)
    }

    @Test
    fun `versesFor caps the returned list without changing the reported total`() = runBlocking {
        val (total, refs) = repo.versesFor("G26", limit = 1)
        assertEquals(2, total)
        assertEquals(1, refs.size)
    }

    @Test
    fun `versesFor orders the requested book's verse first when scoped`() = runBlocking {
        val (_, refs) = repo.versesFor("H430", limit = 10, book = 19, chapter = 23, verse = 1)
        assertEquals("019023001", refs.first())
    }

    @Test
    fun `only Russian maps to ru, everything else falls back to en`() {
        assertEquals("ru", StrongsCatalog.normalizeLanguage("ru"))
        assertEquals("ru", StrongsCatalog.normalizeLanguage("RU"))
        assertEquals("en", StrongsCatalog.normalizeLanguage("en"))
        assertEquals("en", StrongsCatalog.normalizeLanguage("fr"))
        assertEquals("en", StrongsCatalog.normalizeLanguage(null))
        assertEquals("en", StrongsCatalog.normalizeLanguage(""))
    }

    private fun entry(number: String, definition: String) = StrongsEntry(
        number = number, word = "w", transliteration = "t", pronunciation = "p", definition = definition,
    )

    @Test
    fun `rootOf returns the first Strong's reference in the definition other than the entry itself`() {
        assertEquals("H1234", repo.rootOf(entry("H430", "a form of H430; from H1234; God")))
        assertEquals("G2222", repo.rootOf(entry("G26", "from G2222 (life)")))
    }

    @Test
    fun `rootOf is empty when the definition cites no other number`() {
        assertEquals("", repo.rootOf(entry("H430", "plural of H430")), "the entry's own number is not its root")
        assertEquals("", repo.rootOf(entry("H1", "primitive root; a father")))
    }

    @Test
    fun `orderRefsByScope leaves the list untouched when no book is given`() {
        val refs = listOf("001001001", "043003016", "019023001")
        assertEquals(refs, repo.orderRefsByScope(refs, book = null, chapter = null, verse = null))
    }

    @Test
    fun `orderRefsByScope floats references in the requested book to the front, keeping order`() {
        val refs = listOf("001001001", "043003016", "001050020", "019023001")
        assertEquals(
            listOf("001001001", "001050020", "043003016", "019023001"),
            repo.orderRefsByScope(refs, book = 1, chapter = null, verse = null),
        )
    }

    @Test
    fun `orderRefsByScope narrows to a chapter`() {
        val refs = listOf("043003016", "043003017", "043004001", "001001001")
        assertEquals(
            listOf("043003016", "043003017", "043004001", "001001001"),
            repo.orderRefsByScope(refs, book = 43, chapter = 3, verse = null),
        )
    }

    @Test
    fun `orderRefsByScope narrows to a single verse that then leads the list`() {
        val refs = listOf("043003017", "043003016", "043004001")
        val ordered = repo.orderRefsByScope(refs, book = 43, chapter = 3, verse = 16)
        assertEquals(listOf("043003016", "043003017", "043004001"), ordered)
    }

    @Test
    fun `StrongsEntryDto round-trips`() {
        val json = Json { encodeDefaults = true }
        val dto = StrongsEntryDto("H430", "elohiym", "el-o-heem'", "el-o-HEEM", "God", "God", 2606, "H433")

        val restored = json.decodeFromString<StrongsEntryDto>(json.encodeToString(dto))

        assertEquals(dto, restored)
        // Field by field as well as whole: every one of these is read by name on the phone, so a
        // reordering of the constructor would round-trip perfectly while swapping two columns.
        assertEquals("H430", restored.number)
        assertEquals("elohiym", restored.word)
        assertEquals("el-o-heem'", restored.transliteration)
        assertEquals("el-o-HEEM", restored.pronunciation)
        assertEquals("God", restored.definition)
        assertEquals("God", restored.kjvUsage)
        assertEquals(2606, restored.occurrences)
        assertEquals("H433", restored.root)
    }

    @Test
    fun `DictionaryVerseDto and DictionaryVersesResponse round-trip`() {
        val json = Json { encodeDefaults = true }
        val verse = DictionaryVerseDto("Genesis", 1, 1, "Genesis 1:1", "In the beginning")

        val restoredVerse = json.decodeFromString<DictionaryVerseDto>(json.encodeToString(verse))

        assertEquals(verse, restoredVerse)
        assertEquals("Genesis", restoredVerse.bookName)
        assertEquals(1, restoredVerse.chapter)
        assertEquals(1, restoredVerse.verse)
        assertEquals("Genesis 1:1", restoredVerse.reference)
        assertEquals("In the beginning", restoredVerse.text)

        val response = DictionaryVersesResponse("H430", total = 2606, verses = listOf(verse))
        val restored = json.decodeFromString<DictionaryVersesResponse>(json.encodeToString(response))

        assertEquals(response, restored)
        assertEquals("H430", restored.number)
        assertEquals(2606, restored.total, "the total is the count before the list was capped")
        assertEquals(listOf(verse), restored.verses)
    }

    /**
     * The app holds one instance for the process, because the dictionary is 14k entries and every
     * connected phone searches the same copy. Nothing here loads it: [StrongsDictionaryRepository]
     * reads its files on the first request, not on construction.
     */
    @Test
    fun `the app-wide instance is one instance`() {
        assertSame(StrongsDictionaryRepository.shared, StrongsDictionaryRepository.shared)
    }
}
