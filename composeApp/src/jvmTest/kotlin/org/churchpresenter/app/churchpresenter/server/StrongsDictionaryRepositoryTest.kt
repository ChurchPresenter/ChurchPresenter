package org.churchpresenter.app.churchpresenter.server

import churchpresenter.composeapp.generated.resources.Res
import io.mockk.coEvery
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.viewmodel.DictionaryFixture
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure lookup/formatting helpers behind the dictionary REST endpoints, plus the suspend loaders
 * ([StrongsDictionaryRepository.all]/[lookup]/[search]/[versesFor]) — driven with
 * [DictionaryFixture]'s stubbed `Res.readBytes` (the same fixture `DictionaryViewModel`'s tests use,
 * since it is the same bundled dictionary) plus a small stubbed interlinear index, rather than the
 * real ~14k-entry files.
 *
 * [StrongsDictionaryRepository] is a singleton with its own load-once [StrongsDictionaryRepository.cache]
 * — cleared before and after every test here so this class can neither read another test's real data
 * nor leave stubbed data behind for one.
 */
class StrongsDictionaryRepositoryTest {

    private val repo = StrongsDictionaryRepository

    private val greekInterlinear = """
        [
          {"r":"043003016","w":[{"t":"ἀγάπη","s":"G26"},{"t":"θεός","s":"G2316"}]},
          {"r":"040005003","w":[{"t":"ἀγάπη","s":"G26"}]}
        ]
    """.trimIndent()

    private val hebrewInterlinear = """
        [
          {"r":"001001001","w":[{"t":"אֱלֹהִים","s":"H430"},{"t":"רֵאשִׁית","s":"H7225"}]},
          {"r":"019023001","w":[{"t":"אֱלֹהִים","s":"H430"}]}
        ]
    """.trimIndent()

    @BeforeTest
    fun stubDictionary() {
        repo.cache.clear()
        repo.interlinear.resetForTest()
        DictionaryFixture.stubResources()
        coEvery { Res.readBytes("files/dictionary/interlinear_g.json") } returns greekInterlinear.toByteArray()
        coEvery { Res.readBytes("files/dictionary/interlinear_h.json") } returns hebrewInterlinear.toByteArray()
    }

    @AfterTest
    fun tearDown() {
        repo.cache.clear()
        repo.interlinear.resetForTest()
        unmockkObject(Res)
    }

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
    fun `all is cached -- a second call does not re-read resources`() = runBlocking {
        repo.all("en")
        unmockkObject(Res) // if a second call re-read, it would now throw instead of returning stale data
        val entries = repo.all("en")
        assertEquals(4, entries.size)
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
        assertEquals("ru", repo.normalizeLang("ru"))
        assertEquals("ru", repo.normalizeLang("RU"))
        assertEquals("en", repo.normalizeLang("en"))
        assertEquals("en", repo.normalizeLang("fr"))
        assertEquals("en", repo.normalizeLang(null))
        assertEquals("en", repo.normalizeLang(""))
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
        assertEquals(dto, json.decodeFromString<StrongsEntryDto>(json.encodeToString(dto)))
    }

    @Test
    fun `DictionaryVerseDto and DictionaryVersesResponse round-trip`() {
        val json = Json { encodeDefaults = true }
        val verse = DictionaryVerseDto("Genesis", 1, 1, "Genesis 1:1", "In the beginning")
        assertEquals(verse, json.decodeFromString<DictionaryVerseDto>(json.encodeToString(verse)))

        val response = DictionaryVersesResponse("H430", total = 2606, verses = listOf(verse))
        assertEquals(response, json.decodeFromString<DictionaryVersesResponse>(json.encodeToString(response)))
    }
}
