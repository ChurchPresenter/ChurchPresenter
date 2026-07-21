package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSyncMode
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogSide
import org.churchpresenter.app.churchpresenter.utils.InstanceLinkLogger
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Instance Link follower mode for the Bible, plus the bilingual look-ahead.
 *
 * A follower can either mirror the primary's translation byte-for-byte (`FULL_REPLICA`, which
 * downloads the `.spb` and caches it) or keep its own (`REFERENCE_ONLY`, where only references
 * cross the wire). Getting this wrong means a second campus showing a different translation than
 * the one being read from the platform.
 *
 * The cache lives under the app data directory; the test task points `user.home` at
 * `build/test-home`, and each test clears the cache so a download actually happens.
 */
class BibleViewModelFollowerTest {

    private lateinit var dir: File
    private lateinit var testHome: File
    private var realHome: String? = null
    private lateinit var localBible: File
    private lateinit var remoteBible: File

    /** Must match BibleViewModel's own path — note the plural "bibles". */
    private val cacheDir: File
        get() = File(System.getProperty("user.home"), ".churchpresenter/instance-link/cache/bibles")

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-follower-test").toFile()

        // Each test gets its own home, so the replica cache is genuinely empty AND a coroutine
        // still finishing from a previous test cannot write into this one's cache directory.
        // BibleViewModel resolves the cache path once, at construction, so this must be set before
        // any view model is built.
        // Bind InstanceLinkLogger's lazily-resolved log directory to the standard test home
        // BEFORE swapping user.home below. setInstanceLinkSource writes to that logger, and if the
        // lazy first resolved under this test's temporary home it would stay pointed at a
        // directory that tearDown deletes — breaking InstanceLinkLoggerTest for the rest of the run.
        InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "follower_test_warmup")

        realHome = System.getProperty("user.home")
        testHome = Files.createTempDirectory("cp-bible-follower-home").toFile()
        System.setProperty("user.home", testHome.absolutePath)

        // The follower's own translation.
        localBible = SpbFixture.spbFile(
            dir, name = "local.spb",
            content = SpbFixture.buildContent(
                title = "Local Translation",
                books = listOf(SpbFixture.Book(43, "John", 3)),
                verses = listOf(SpbFixture.Verse(43, 3, 16, "LOCAL wording of the verse")),
            ),
        )
        // What the primary would send down the wire.
        remoteBible = SpbFixture.spbFile(
            dir, name = "remote.spb",
            content = SpbFixture.buildContent(
                title = "Primary Translation",
                books = listOf(SpbFixture.Book(43, "John", 3)),
                verses = listOf(SpbFixture.Verse(43, 3, 16, "PRIMARY wording of the verse")),
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        realHome?.let { System.setProperty("user.home", it) }
        dir.deleteRecursively()
        testHome.deleteRecursively()
    }

    private fun follower(): BibleViewModel {
        val model = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "local.spb"),
            ),
        )
        awaitUntil("local bible") { model.books.value.isNotEmpty() && model.isFullyLoaded }
        return model
    }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun BibleViewModel.verseTextAt(bookIndex: Int, chapter: Int): String {
        val token = verseSelectionToken.value
        loadChapter(bookIndex, chapter)
        awaitUntil("chapter load") { verseSelectionToken.value > token }
        return verses.value.first()
    }

    // ── Reference-only mode ─────────────────────────────────────────────────────

    @Test
    fun `reference-only mode keeps the follower's own translation`() {
        val vm = follower()
        var fetched = false

        vm.setInstanceLinkSource(
            active = true,
            mode = BibleSyncMode.REFERENCE_ONLY,
            fetchBibleFile = { fetched = true; remoteBible.readBytes() },
            fetchSecondaryBibleFile = null,
        )
        awaitUntil("reload") { vm.isFullyLoaded }

        assertTrue(vm.verseTextAt(0, 3).contains("LOCAL"), "reference-only must not replace the translation")
        assertTrue(!fetched, "nothing should be downloaded in reference-only mode")
    }

    // ── Full replica ────────────────────────────────────────────────────────────

    @Test
    fun `full replica downloads and uses the primary's translation`() {
        val vm = follower()
        assertTrue(vm.verseTextAt(0, 3).contains("LOCAL"), "starts on the local translation")

        vm.setInstanceLinkSource(
            active = true,
            mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { remoteBible.readBytes() },
            fetchSecondaryBibleFile = null,
        )
        awaitUntil("the replica to load") { vm.verses.value.isEmpty() || vm.isFullyLoaded }
        awaitUntil("the primary's wording") { vm.verseTextAt(0, 3).contains("PRIMARY") }
    }

    @Test
    fun `a cached replica is reused rather than downloaded again`() {
        val vm = follower()
        var downloads = 0
        vm.setInstanceLinkSource(
            active = true, mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { downloads++; remoteBible.readBytes() },
            fetchSecondaryBibleFile = null,
        )
        awaitUntil("first download") { downloads == 1 }
        awaitUntil("replica loaded") { vm.verseTextAt(0, 3).contains("PRIMARY") }

        val second = follower()
        second.setInstanceLinkSource(
            active = true, mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { downloads++; remoteBible.readBytes() },
            fetchSecondaryBibleFile = null,
        )
        // Wait for proof the replica was actually applied, rather than sleeping and hoping:
        // once the primary's wording is on screen, the sync has run to completion.
        awaitUntil("the cached replica to load") { second.verseTextAt(0, 3).contains("PRIMARY") }
        assertEquals(1, downloads, "a cached bible must not be re-fetched on every reconnect")
    }

    @Test
    fun `a failed download leaves the follower on its own translation`() {
        val vm = follower()
        var attempted = false
        vm.setInstanceLinkSource(
            active = true, mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { attempted = true; null }, // the primary is unreachable
            fetchSecondaryBibleFile = null,
        )
        // Wait for the fetch itself rather than a fixed delay — the sync gives up as soon as it
        // returns null, so once this is true the outcome is settled.
        awaitUntil("the failed fetch") { attempted }
        assertTrue(vm.verseTextAt(0, 3).contains("LOCAL"), "a failed sync must not blank the Bible")
    }

    @Test
    fun `a replica with a secondary translation downloads both`() {
        val vm = follower()
        var secondaryFetched = false
        vm.setInstanceLinkSource(
            active = true, mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { remoteBible.readBytes() },
            fetchSecondaryBibleFile = { secondaryFetched = true; remoteBible.readBytes() },
        )
        awaitUntil("both downloads") { secondaryFetched }
        awaitUntil("replica loaded") { vm.verseTextAt(0, 3).contains("PRIMARY") }
    }

    @Test
    fun `a missing secondary on the primary is not an error`() {
        val vm = follower()
        vm.setInstanceLinkSource(
            active = true, mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { remoteBible.readBytes() },
            fetchSecondaryBibleFile = { null }, // the primary has no second translation
        )
        awaitUntil("replica loaded") { vm.verseTextAt(0, 3).contains("PRIMARY") }
    }

    // ── Disconnecting ───────────────────────────────────────────────────────────

    @Test
    fun `disconnecting restores the follower's own translation`() {
        val vm = follower()
        vm.setInstanceLinkSource(
            active = true, mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { remoteBible.readBytes() },
            fetchSecondaryBibleFile = null,
        )
        awaitUntil("replica loaded") { vm.verseTextAt(0, 3).contains("PRIMARY") }

        vm.setInstanceLinkSource(active = false, mode = BibleSyncMode.FULL_REPLICA, null, null)
        awaitUntil("local bible restored") { vm.verseTextAt(0, 3).contains("LOCAL") }
    }

    @Test
    fun `disconnecting when never connected is a no-op`() {
        val vm = follower()
        vm.setInstanceLinkSource(active = false, mode = BibleSyncMode.FULL_REPLICA, null, null)
        assertTrue(vm.verseTextAt(0, 3).contains("LOCAL"))
    }

    @Test
    fun `invalidating the cache forces a fresh download`() {
        val vm = follower()
        var downloads = 0
        vm.setInstanceLinkSource(
            active = true, mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { downloads++; remoteBible.readBytes() },
            fetchSecondaryBibleFile = null,
        )
        // Wait for the bytes to actually land on disk: the counter increments inside the fetch
        // lambda, before the file is written, so invalidating on the counter alone races the write.
        awaitUntil("the cache file to be written") { File(cacheDir, "primary.spb").exists() }
        assertEquals(1, downloads)

        vm.invalidateInstanceLinkBibleCache()
        vm.setInstanceLinkSource(
            active = true, mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = { downloads++; remoteBible.readBytes() },
            fetchSecondaryBibleFile = null,
        )
        awaitUntil("re-download after invalidation") { downloads == 2 }
    }

    // ── Bilingual look-ahead ────────────────────────────────────────────────────

    @Test
    fun `the look-ahead includes the secondary translation`() {
        val bilingualDir = Files.createTempDirectory("cp-bible-lookahead-test").toFile()
        try {
            SpbFixture.spbFile(
                bilingualDir, name = "p.spb",
                content = SpbFixture.buildContent(
                    title = "Primary",
                    books = listOf(SpbFixture.Book(43, "John", 3)),
                    verses = (1..3).map { SpbFixture.Verse(43, 3, it, "English $it") },
                ),
            )
            SpbFixture.spbFile(
                bilingualDir, name = "s.spb",
                content = SpbFixture.buildContent(
                    title = "Secondary",
                    books = listOf(SpbFixture.Book(43, "Иоанна", 3)),
                    verses = (1..3).map { SpbFixture.Verse(43, 3, it, "Русский $it") },
                ),
            )
            val vm = BibleViewModel(
                AppSettings(
                    bibleSettings = BibleSettings(
                        storageDirectory = bilingualDir.absolutePath,
                        primaryBible = "p.spb",
                        secondaryBible = "s.spb",
                    ),
                ),
            )
            awaitUntil("bilingual load") { vm.books.value.isNotEmpty() && vm.isFullyLoaded }

            val token = vm.verseSelectionToken.value
            vm.loadChapter(0, 3)
            awaitUntil("chapter") { vm.verseSelectionToken.value > token }
            vm.selectVerse(0)

            val next = vm.getNextVerses()
            assertEquals(2, next.size, "the stage monitor shows both languages ahead")
            assertTrue(next[0].verseText.contains("English 2"))
            assertTrue(next[1].verseText.contains("Русский 2"))
        } finally {
            bilingualDir.deleteRecursively()
        }
    }

    // ── Verse-range parsing via navigation ──────────────────────────────────────

    @Test
    fun `mixed range formats all restore a multi-selection`() {
        val rangeDir = Files.createTempDirectory("cp-bible-range-test").toFile()
        try {
            SpbFixture.spbFile(
                rangeDir, name = "r.spb",
                content = SpbFixture.buildContent(
                    title = "Range",
                    books = listOf(SpbFixture.Book(43, "John", 3)),
                    verses = (1..10).map { SpbFixture.Verse(43, 3, it, "verse $it") },
                ),
            )
            val vm = BibleViewModel(
                AppSettings(bibleSettings = BibleSettings(storageDirectory = rangeDir.absolutePath, primaryBible = "r.spb")),
            )
            awaitUntil("load") { vm.books.value.isNotEmpty() && vm.isFullyLoaded }

            fun numbersFor(range: String): List<Int> {
                val token = vm.verseSelectionToken.value
                vm.selectVerseByDetails("John", 3, 1, verseRange = range)
                awaitUntil("range $range") { vm.verseSelectionToken.value > token }
                return vm.getSelectedVerseNumbers()
            }

            assertEquals(listOf(1, 2, 3), numbersFor("1-3"), "a plain range")
            assertEquals(listOf(2, 4), numbersFor("2,4"), "a comma list")
            assertEquals(listOf(1, 2, 3, 5), numbersFor("1-3,5"), "mixed forms")
            assertEquals(listOf(2, 4), numbersFor(" 2 , 4 "), "whitespace is tolerated")
            assertEquals(listOf(1, 2), numbersFor("1-2,nonsense"), "an unparseable part is skipped")
        } finally {
            rangeDir.deleteRecursively()
        }
    }
}
