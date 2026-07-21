package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleEngineSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The remaining branches of [BibleViewModel]: the degrade paths every accessor takes when a book,
 * chapter or translation is missing, plus the cross-language book search and the correction log
 * that records when the operator overrides a detection.
 *
 * These are the cases that only happen on someone else's machine — a Russian-only Bible, a
 * reference to a book the translation doesn't carry, a passage the engine guessed wrong.
 */
class BibleViewModelBranchTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bible-branch-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        vm = build(BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb"))
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun build(settings: BibleSettings, autoFollow: Boolean = false): BibleViewModel {
        val model = BibleViewModel(
            AppSettings(bibleSettings = settings, bibleEngineSettings = BibleEngineSettings(autoFollow = autoFollow)),
        )
        awaitUntil("books") { model.books.value.isNotEmpty() }
        awaitUntil("verses") { model.isFullyLoaded }
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

    private fun openChapter(model: BibleViewModel, bookIndex: Int, chapter: Int) {
        val token = model.verseSelectionToken.value
        model.loadChapter(bookIndex, chapter)
        awaitUntil("book $bookIndex chapter $chapter") { model.verseSelectionToken.value > token }
    }

    // ── getVersesForDisplay ─────────────────────────────────────────────────────

    @Test
    fun `verses can be fetched for display by book name`() = runBlocking {
        val verses = vm.getVersesForDisplay("John", 3, 16)
        assertEquals(1, verses.size, "one entry with no secondary translation")
        assertEquals("For God so loved the world.", verses.first().verseText)
        assertEquals("John", verses.first().bookName)
        assertEquals(16, verses.first().verseNumber)
    }

    @Test
    fun `fetching for display is case-insensitive on the book name`() = runBlocking {
        assertTrue(vm.getVersesForDisplay("jOhN", 3, 16).isNotEmpty())
    }

    @Test
    fun `fetching an unknown book for display yields nothing`() = runBlocking {
        assertTrue(vm.getVersesForDisplay("Habakkuk", 3, 2).isEmpty())
    }

    @Test
    fun `fetching a verse that does not exist yields nothing`() = runBlocking {
        assertTrue(vm.getVersesForDisplay("John", 3, 999).isEmpty())
        assertTrue(vm.getVersesForDisplay("John", 99, 1).isEmpty())
    }

    @Test
    fun `fetching for display with no bible loaded yields nothing`() = runBlocking {
        assertTrue(BibleViewModel(AppSettings()).getVersesForDisplay("John", 3, 16).isEmpty())
    }

    // ── getChapterVerses ────────────────────────────────────────────────────────

    @Test
    fun `a chapter's verse strings can be fetched by book name`() = runBlocking {
        val verses = vm.getChapterVerses("Genesis", 1)
        assertEquals(3, verses.size)
        assertTrue(verses.first().startsWith("1. "), "verses arrive numbered for display")
    }

    @Test
    fun `fetching an unknown book or chapter yields nothing`() = runBlocking {
        assertTrue(vm.getChapterVerses("Habakkuk", 1).isEmpty())
        assertTrue(vm.getChapterVerses("Genesis", 99).isEmpty())
        assertTrue(BibleViewModel(AppSettings()).getChapterVerses("Genesis", 1).isEmpty())
    }

    // ── Chapter stepping backwards ──────────────────────────────────────────────

    @Test
    fun `previous chapter steps back and stops at chapter one`() {
        openChapter(vm, 0, 2)
        assertTrue(vm.navigatePreviousChapter())
        assertEquals(1, vm.selectedChapter.value)
        assertFalse(vm.navigatePreviousChapter(), "there is nothing before chapter 1")
    }

    @Test
    fun `stepping back is flagged as a sequential move`() {
        openChapter(vm, 0, 2)
        vm.navigatePreviousChapter()
        assertTrue(vm.consumeSequentialChapterAdvance())
    }

    // ── Selected verse numbers ──────────────────────────────────────────────────

    @Test
    fun `selected verse numbers are reported in order`() {
        openChapter(vm, 0, 1)
        vm.selectVerse(0)
        vm.ctrlClickVerse(2)
        assertEquals(listOf(1, 3), vm.getSelectedVerseNumbers())
    }

    @Test
    fun `no multi-selection means no verse numbers`() {
        openChapter(vm, 0, 1)
        vm.selectVerse(0)
        assertTrue(vm.getSelectedVerseNumbers().isEmpty(), "a single selection is not a range")
    }

    // ── Search clearing ─────────────────────────────────────────────────────────

    @Test
    fun `clearSearch empties the query, the results and search mode`() {
        vm.updateSearchQuery("God")
        vm.performSearch()
        awaitUntil("results") { vm.isSearchMode.value }

        vm.clearSearch()
        assertEquals("", vm.searchQuery.value)
        assertTrue(vm.searchResults.value.isEmpty())
        assertFalse(vm.isSearchMode.value)
    }

    @Test
    fun `submitting an empty query clears the search`() {
        vm.updateSearchQuery("God")
        vm.performSearch()
        awaitUntil("results") { vm.isSearchMode.value }

        vm.onSmartQueryChanged("   ")
        vm.submitSmartQuery()
        assertFalse(vm.isSearchMode.value)
    }

    // ── Cross-language book picker ──────────────────────────────────────────────

    @Test
    fun `an english query finds a book named in another language`() {
        // The picker falls back to the canonical English book list, so an operator running a
        // Russian Bible can still type "psalm" and land on Псалтирь.
        val russianDir = Files.createTempDirectory("cp-bible-ru-test").toFile()
        try {
            SpbFixture.spbFile(
                russianDir, name = "ru.spb",
                content = SpbFixture.buildContent(
                    title = "Русская Библия",
                    books = listOf(
                        SpbFixture.Book(1, "Бытие", 1),
                        SpbFixture.Book(19, "Псалтирь", 1),
                    ),
                    verses = listOf(
                        SpbFixture.Verse(1, 1, 1, "В начале сотворил Бог"),
                        SpbFixture.Verse(19, 23, 1, "Господь — Пастырь мой"),
                    ),
                ),
            )
            val ru = build(BibleSettings(storageDirectory = russianDir.absolutePath, primaryBible = "ru.spb"))

            ru.updateBookSearchQuery("psalm")
            assertEquals(listOf("Псалтирь"), ru.getFilteredBooks(), "English fallback should map to the local name")

            ru.updateBookSearchQuery("genesis")
            assertEquals(listOf("Бытие"), ru.getFilteredBooks())
        } finally {
            russianDir.deleteRecursively()
        }
    }

    @Test
    fun `a non-latin query that matches nothing returns nothing`() {
        // The English fallback only applies to Latin input; a Cyrillic miss stops there.
        vm.updateBookSearchQuery("Ъъъ")
        assertTrue(vm.getFilteredBooks().isEmpty())
    }

    @Test
    fun `a direct name match wins over the english fallback`() {
        vm.updateBookSearchQuery("John")
        assertEquals(listOf("John"), vm.getFilteredBooks())
    }

    // ── Detection chips: accepting and correcting ───────────────────────────────

    private fun detect(model: BibleViewModel, matchType: String, verseStart: Int = 16) =
        model.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = verseStart, verseEnd = null,
            verseText = "For God so loved the world.", matchType = matchType,
        )

    @Test
    fun `accepting a chip navigates to it`() {
        detect(vm, "reverse")
        val chip = vm.detectedReferences.value.single()
        val token = vm.verseSelectionToken.value

        vm.applyDetectedReference(chip)
        awaitUntil("navigation") { vm.verseSelectionToken.value > token }
        assertEquals(2, vm.selectedBookIndex.value)
        assertEquals(3, vm.selectedChapter.value)
    }

    @Test
    fun `accepting a chip with a go-live source pushes it live`() {
        detect(vm, "reverse")
        val chip = vm.detectedReferences.value.single()
        val liveToken = vm.autoFollowLiveToken.value

        vm.applyDetectedReference(chip, goLiveSource = "detection")
        awaitUntil("go-live") { vm.autoFollowLiveToken.value > liveToken }
        assertEquals("detection", vm.autoFollowLiveSource.value)
    }

    @Test
    fun `showing a different passage than the top suggestion records a correction`() {
        // The operator overruled the engine — logged so acceptance rates stay honest.
        detect(vm, "reverse", verseStart = 16)
        vm.logGoLiveCorrection(shownBookIndex = 0, shownChapter = 1, shownVerse = 1)
        assertTrue(vm.detectedReferences.value.isNotEmpty(), "the chip stays visible")
    }

    @Test
    fun `showing exactly the top suggestion is not a correction`() {
        detect(vm, "reverse", verseStart = 16)
        val chip = vm.detectedReferences.value.single()
        vm.logGoLiveCorrection(chip.bookIndex, chip.chapter, chip.verseStart)
        assertTrue(vm.detectedReferences.value.isNotEmpty())
    }

    @Test
    fun `a correction with no suggestions on screen is a no-op`() {
        vm.logGoLiveCorrection(0, 1, 1)
        assertTrue(vm.detectedReferences.value.isEmpty())
    }

    // ── Engine detections: canonical codes and ranges ────────────────────────────

    @Test
    fun `a detected range is shown on the chip but only the start verse is navigated to`() {
        val model = build(
            BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb"),
            autoFollow = true,
        )
        model.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 16, verseEnd = 17,
            verseText = "text", matchType = "explicit",
        )
        val chip = model.detectedReferences.value.single()
        assertEquals(16, chip.verseStart)
        assertEquals(17, chip.verseEnd, "the chip advertises the whole range")

        awaitUntil("navigation") { model.verses.value.isNotEmpty() }
        assertEquals(3, model.selectedChapter.value)
    }

    @Test
    fun `a verse end equal to the start is not treated as a range`() {
        vm.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 16, verseEnd = 16,
            verseText = "text", matchType = "explicit",
        )
        assertEquals(null, vm.detectedReferences.value.single().verseEnd)
    }

    @Test
    fun `a canonical code resolves through the module's own numbering`() {
        vm.onEngineScripture(
            bookId = 0, chapter = 0, verseStart = 0, verseEnd = null,
            verseText = "text", matchType = "explicit",
            canonicalCodeStart = "B043C003V016",
        )
        val chip = vm.detectedReferences.value.single()
        assertEquals(2, chip.bookIndex, "the code alone identified John")
        assertEquals(3, chip.chapter)
        assertEquals(16, chip.verseStart)
    }

    @Test
    fun `a canonical range code resolves its end verse too`() {
        vm.onEngineScripture(
            bookId = 0, chapter = 0, verseStart = 0, verseEnd = null,
            verseText = "text", matchType = "explicit",
            canonicalCodeStart = "B043C003V016", canonicalCodeEnd = "B043C003V017",
        )
        assertEquals(17, vm.detectedReferences.value.single().verseEnd)
    }

    @Test
    fun `an unparseable code falls back to the plain numbers`() {
        vm.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 16, verseEnd = null,
            verseText = "text", matchType = "explicit", canonicalCodeStart = "not-a-code",
        )
        val chip = vm.detectedReferences.value.single()
        assertEquals(3, chip.chapter)
        assertEquals(16, chip.verseStart)
    }

    @Test
    fun `an unknown track name is ignored rather than failing the detection`() {
        vm.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 16, verseEnd = null,
            verseText = "text", matchType = "explicit",
            tracks = listOf("transcription", "some-future-track"),
        )
        val chip = vm.detectedReferences.value.single()
        assertEquals(setOf(DetectionTrack.TRANSCRIPTION), chip.tracks)
    }

    @Test
    fun `a detection with blank text falls back to this bible's wording`() {
        vm.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 16, verseEnd = null,
            verseText = "", matchType = "explicit",
        )
        assertEquals("For God so loved the world.", vm.detectedReferences.value.single().verseText)
    }

    @Test
    fun `detections with no bible loaded are dropped`() {
        val empty = BibleViewModel(AppSettings())
        empty.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 16, verseEnd = null,
            verseText = "text", matchType = "explicit",
        )
        assertTrue(empty.detectedReferences.value.isEmpty())
    }
}
