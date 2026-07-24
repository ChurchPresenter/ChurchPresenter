package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Book-name matching for the unified search box: scoring a candidate name against a typed token,
 * and resolving a bare book name to a single book for live navigation.
 *
 * The live-nav resolver deliberately navigates only on an unambiguous match — typing "cor" (1 & 2
 * Corinthians tie) must NOT jump anywhere and flicker, while "genesis"/"gen" (a unique best) should
 * resolve. The scorer and resolver are pure / near-pure and are reached directly as internal
 * members over a small real bible; no mocking.
 */
class BibleViewModelBookMatchTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bible-bookmatch-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = SpbFixture.buildContent(
            title = "Book Match Bible",
            books = listOf(
                SpbFixture.Book(1, "Genesis", 1),
                SpbFixture.Book(2, "Exodus", 1),
                SpbFixture.Book(43, "John", 1),
                SpbFixture.Book(46, "1 Corinthians", 1),
                SpbFixture.Book(47, "2 Corinthians", 1),
            ),
            verses = listOf(
                SpbFixture.Verse(1, 1, 1, "In the beginning"),
                SpbFixture.Verse(2, 1, 1, "These are the names"),
                SpbFixture.Verse(43, 1, 1, "In the beginning was the Word"),
                SpbFixture.Verse(46, 1, 1, "Paul called"),
                SpbFixture.Verse(47, 1, 1, "Paul an apostle"),
            ),
        ))
        vm = BibleViewModel(
            AppSettings(bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb")),
        )
        awaitUntil("the bible to load") { vm.books.value.size >= 5 && vm.isFullyLoaded }
    }

    @AfterTest
    fun cleanUp() {
        runCatching { vm.dispose() }
        dir.deleteRecursively()
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun bookIndex(name: String) = vm.books.value.indexOfFirst { it == name }

    // ── scoreNameMatch: exact > prefix > substring > none ────────────────────────

    @Test
    fun `an exact name scores highest`() {
        assertEquals(100, vm.scoreNameMatch("genesis", "genesis", "genesis"))
    }

    @Test
    fun `a prefix scores below an exact match`() {
        assertEquals(80, vm.scoreNameMatch("genesis", "gen", "gen"))
    }

    @Test
    fun `a substring scores below a prefix`() {
        assertEquals(60, vm.scoreNameMatch("1 corinthians", "cor", "cor"))
    }

    @Test
    fun `no overlap scores zero`() {
        assertEquals(0, vm.scoreNameMatch("genesis", "xyz", "xyz"))
    }

    @Test
    fun `spaces are ignored so a spaceless token still prefix-matches`() {
        // "1co" has no space; "1 corinthians" does — the no-space pass lets it prefix-match anyway.
        assertEquals(80, vm.scoreNameMatch("1 corinthians", "1co", "1co"))
    }

    // ── resolveBookForLiveNav: navigate only on a single best match ──────────────

    @Test
    fun `an empty token resolves to nothing`() {
        assertEquals(-1, vm.resolveBookForLiveNav(""))
    }

    @Test
    fun `an exact book name resolves to that book`() {
        assertEquals(bookIndex("Genesis"), vm.resolveBookForLiveNav("genesis"))
    }

    @Test
    fun `a unique prefix resolves to the one book that matches`() {
        assertEquals(bookIndex("Genesis"), vm.resolveBookForLiveNav("gen"))
    }

    @Test
    fun `an ambiguous token resolves to nothing rather than flickering`() {
        assertEquals(-1, vm.resolveBookForLiveNav("cor"), "1 and 2 Corinthians tie; live nav must not jump")
    }

    @Test
    fun `an unmatched token resolves to nothing`() {
        assertEquals(-1, vm.resolveBookForLiveNav("zzz"))
    }

    // ── rankedBookMatches: ordering and empties ─────────────────────────────────

    @Test
    fun `an empty token ranks nothing`() {
        assertTrue(vm.rankedBookMatches("").isEmpty())
    }

    @Test
    fun `a tie lists every equally-good book`() {
        val ranked = vm.rankedBookMatches("cor")

        assertEquals(
            setOf(bookIndex("1 Corinthians"), bookIndex("2 Corinthians")),
            ranked.map { it.first }.toSet(),
            "both Corinthians rank as substring matches",
        )
        assertTrue(ranked.all { it.second == 60 }, "both are substring matches, scored 60")
    }
}
