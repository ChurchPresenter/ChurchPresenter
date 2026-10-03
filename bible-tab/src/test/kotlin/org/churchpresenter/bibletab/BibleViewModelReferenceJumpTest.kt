package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BibleViewModelReferenceJumpTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-jump").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        vm = BibleViewModel(
            AppSettings(bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb")),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        awaitUntil { vm.isFullyLoaded && vm.books.value.isNotEmpty() }
    }

    @AfterTest
    fun tearDown() {
        vm.dispose()
        dir.deleteRecursively()
    }

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.yield()
        }
    }

    private fun jump(ref: SmartReference, goLive: Boolean = false) {
        val token = vm.verseSelectionToken.value
        vm.navigateToReference(ref, goLive = goLive)
        awaitUntil { vm.verseSelectionToken.value > token }
    }

    @Test
    fun `a range selects every verse of it as a passage`() {
        jump(SmartReference(bookIndex = 0, chapter = 1, verseStart = 1, verseEnd = 3))

        assertEquals(listOf(0, 1, 2), vm.selectedVerseIndices)
        assertTrue(vm.multiVerseEnabled.value)
    }

    @Test
    fun `a range running past the chapter keeps the verses that exist`() {
        jump(SmartReference(bookIndex = 0, chapter = 1, verseStart = 2, verseEnd = 9))

        assertEquals(listOf(1, 2), vm.selectedVerseIndices)
    }

    @Test
    fun `a range of one verse is not a passage`() {
        jump(SmartReference(bookIndex = 0, chapter = 1, verseStart = 3, verseEnd = 3))

        assertFalse(vm.multiVerseEnabled.value)
        assertEquals(2, vm.selectedVerseIndex.value)
    }

    @Test
    fun `a verse the chapter lacks lands on its first verse`() {
        jump(SmartReference(bookIndex = 0, chapter = 1, verseStart = 40, verseEnd = null))

        assertEquals(0, vm.selectedVerseIndex.value)
    }

    @Test
    fun `a jump that goes live raises the auto-follow signal`() {
        val before = vm.autoFollowLiveToken.value

        jump(SmartReference(bookIndex = 2, chapter = 3, verseStart = 16, verseEnd = null), goLive = true)

        assertEquals(before + 1, vm.autoFollowLiveToken.value)
    }

    @Test
    fun `there is no chapter after the last chapter of the last book`() {
        jump(SmartReference(bookIndex = 2, chapter = 3, verseStart = 17, verseEnd = null))

        assertFalse(vm.navigateNextChapter())
    }

    @Test
    fun `submitting a reference jumps to it`() {
        vm._searchQuery.value = "John 3:16"
        val token = vm.verseSelectionToken.value

        vm.submitSmartQuery()
        awaitUntil { vm.verseSelectionToken.value > token }

        assertEquals(2, vm.selectedBookIndex.value)
        assertEquals("16. For God so loved the world.", vm.verses.value[vm.selectedVerseIndex.value])
    }

    @Test
    fun `submitting words searches for them`() {
        vm._searchQuery.value = "shepherd"

        vm.submitSmartQuery()
        awaitUntil { vm.searchResults.value.isNotEmpty() }

        assertEquals("23", vm.searchResults.value.single().chapter)
    }

    @Test
    fun `a reference mode query that is not a reference does nothing`() {
        vm._searchMode.value = BibleSearchMode.REFERENCE
        vm._searchQuery.value = "zzzz"

        vm.submitSmartQuery()

        assertEquals(0, vm.selectedBookIndex.value)
        assertTrue(vm.searchResults.value.isEmpty())
    }

    @Test
    fun `a book name nothing resembles is not a live navigation target`() {
        assertEquals(-1, vm.resolveBookForLiveNav("zzzz"))
        assertEquals(-1, vm.resolveBookForLiveNav("  "))
        assertEquals(2, vm.resolveBookForLiveNav("JOHN"))
    }

    @Test
    fun `no book matches when the module lists none`() {
        vm._books.value = emptyList()

        assertTrue(vm.rankedBookMatches("john").isEmpty())
        assertTrue(vm.rankedBookMatches("   ").isEmpty())
    }
}
