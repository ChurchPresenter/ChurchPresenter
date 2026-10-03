package org.churchpresenter.bibletab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BibleViewModelBoundaryInputsTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-boundary").toFile()
        SpbFixture.spbFile(dir, name = "test.spb", content = SpbFixture.buildContent(
            title = "Boundary",
            books = listOf(SpbFixture.Book(1, "Genesis", 2), SpbFixture.Book(43, "John", 1)),
            verses = listOf(
                SpbFixture.Verse(1, 1, 1, "In the beginning God created the heaven and the earth."),
                SpbFixture.Verse(1, 1, 2, "And the earth was without form, and void."),
                SpbFixture.Verse(1, 2, 1, "Thus the heavens and the earth were finished."),
                SpbFixture.Verse(43, 1, 1, "In the beginning was the Word."),
                SpbFixture.Verse(43, 1, 2, "The same was in the beginning with God."),
            ),
        ))
        vm = BibleViewModel(
            AppSettings(bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb")),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        awaitUntil { vm.isFullyLoaded && vm.verses.value.isNotEmpty() }
        vm.selectVerse(0)
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

    @Test
    fun `down from the last verse of the last book stays where it is`() {
        vm.loadChapter(1, 1)
        awaitUntil { vm.verses.value.firstOrNull()?.contains("the Word") == true }
        vm.selectVerse(vm.verses.value.lastIndex)

        assertFalse(vm.navigateNextVerse())
        assertEquals(1, vm.selectedBookIndex.value)
    }

    @Test
    fun `a saved range keeps only the verses this chapter has`() {
        vm.applyVerseRange("1,9", vm.verses.value)

        assertFalse(vm.multiVerseEnabled.value, "one verse found is not a passage")
        assertEquals(listOf(1), vm.getSelectedVerseNumbers())
    }

    @Test
    fun `a book the module lacks has no display index`() {
        assertNull(vm.canonicalBookIdToIndex(10), "falls back to id - 1, which is past the two books")
        assertEquals(1, vm.canonicalBookIdToIndex(43))
    }

    @Test
    fun `a verse or book the module lacks has no canonical reference`() {
        assertNull(vm.canonicalRefForBookName("Genesis", 1, 99))
        assertNull(vm.canonicalRefForBookName("Exodus", 1, 1))
        assertEquals(Triple(43, 1, 1), vm.canonicalRefForBookName("john", 1, 1))
    }

    @Test
    fun `english book names match only plain letters`() {
        assertEquals(listOf("John"), vm.booksNamedInEnglish("joh"))
        assertTrue(vm.booksNamedInEnglish("jo1").isEmpty())
    }

    @Test
    fun `live navigation resolves no book for a name nothing matches`() {
        assertEquals(-1, vm.resolveBookForLiveNav("zzz"))
        assertEquals(-1, vm.resolveBookForLiveNav("   "))
        assertEquals(1, vm.resolveBookForLiveNav("JOHN"))
    }

    @Test
    fun `scheduling refuses a selection outside the chapter`() {
        val added = mutableListOf<String>()
        vm._selectedVerseIndex.value = 99

        assertFalse(vm.addCurrentVerseToSchedule { book, _, _, _, _, _ -> added += book })
        assertTrue(added.isEmpty())
    }

    @Test
    fun `ctrl-clicking the selected verse starts a passage of that verse alone`() {
        vm.selectVerse(1)

        vm.ctrlClickVerse(1)

        assertEquals(listOf(2), vm.getSelectedVerseNumbers())
        assertTrue(vm.multiVerseEnabled.value)
    }

    @Test
    fun `ctrl-clicking with no verse selected adds only the clicked one`() {
        vm._selectedVerseIndex.value = -1

        vm.ctrlClickVerse(1)

        assertEquals(listOf(2), vm.getSelectedVerseNumbers())
    }

    @Test
    fun `multi-verse with nothing ticked reads and looks ahead from the single selection`() {
        vm._multiVerseEnabled.value = true

        assertEquals(1, vm.getSelectedVerses().first().verseNumber)
        assertEquals(2, vm.getNextVerses().first().verseNumber)
    }

    @Test
    fun `without a module nothing is searched, loaded or stepped past`() {
        val empty = BibleViewModel(
            AppSettings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            empty.updateSearchQuery("beginning")
            empty.performSearch()
            assertFalse(empty.isSearchMode.value)
            assertTrue(empty.searchResults.value.isEmpty())

            empty.loadChapter(0, 1)
            assertTrue(empty.verses.value.isEmpty())
            assertTrue(empty.booksNamedInEnglish("gen").isEmpty())

            empty._verses.value = listOf("1. A lone verse.")
            empty._selectedVerseIndex.value = 0
            assertFalse(empty.navigateNextVerse())
        } finally {
            empty.dispose()
        }
    }

    @Test
    fun `the live panel steps from the top when the verse is not listed`() {
        assertEquals(2, nextLiveVerseNumber(listOf("1. a", "2. b", "3. c"), refVerse = 9, moveUp = false))
    }

    @Test
    fun `an empty selection has no page to cut`() {
        assertEquals(emptyList(), emptyList<SelectedVerse>().versePage(VERSE_PAGE_SECOND, true, 25))
    }

    @Test
    fun `blank runs between words do not count toward a long verse`() {
        val thirty = (1..30).joinToString(" ") { "w$it" }
        assertTrue(isLongVerse("   $thirty", 25))
        assertFalse(isLongVerse("   one two ", 25))
    }

    @Test
    fun `a verse the chapter lacks is not put on screen`() {
        assertTrue(runBlocking { vm.getVersesForDisplay("Genesis", 1, 99) }.isEmpty())
        assertTrue(runBlocking { vm.getVersesForDisplay("Leviticus", 1, 1) }.isEmpty())
        assertEquals(2, runBlocking { vm.getVersesForDisplay("genesis", 1, 2) }.single().verseNumber)
    }

    @Test
    fun `scheduling refuses a selection before the first verse`() {
        vm._selectedVerseIndex.value = -1

        assertFalse(vm.addCurrentVerseToSchedule { _, _, _, _, _, _ -> })
    }

    @Test
    fun `a book id the module cannot place falls back to the book's name`() {
        assertEquals(1, vm.resolveBookIndex("John", 10))
        assertEquals(-1, vm.resolveBookIndex("Leviticus", 10))
    }

    @Test
    fun `up from blank chapter lines still steps back a line`() {
        vm._verses.value = listOf("", "")
        vm._selectedVerseIndex.value = 1

        assertTrue(vm.navigatePreviousVerse())
        assertEquals(0, vm.selectedVerseIndex.value)
    }

    @Test
    fun `without a module there is no book to place or chapter to open`() {
        val empty = BibleViewModel(
            AppSettings(),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            assertNull(empty.canonicalBookIdToIndex(1))
            runBlocking { empty.applyVerseSelection(0, 1, 1, "", null) }
            assertTrue(empty.verses.value.isEmpty())
        } finally {
            empty.dispose()
        }
    }

    @Test
    fun `a typed range that runs past the chapter selects only the verse it has`() {
        vm.navigateToReference(SmartReference(bookIndex = 0, chapter = 1, verseStart = 2, verseEnd = 9))
        awaitUntil { vm.selectedVerseIndex.value == 1 }

        assertFalse(vm.multiVerseEnabled.value, "verse 2 is the only one of 2-9 Genesis 1 has here")
        assertEquals(2, vm.getSelectedVerses().first().verseNumber)
    }

    @Test
    fun `clearing the box stops a search under way`() {
        vm.updateSearchQuery("beginning")
        vm.performSearch()
        awaitUntil { vm.isSearchMode.value }

        vm.onSmartQueryChanged("   ")

        assertFalse(vm.isSearchMode.value)
        assertTrue(vm.searchResults.value.isEmpty())
    }

    @Test
    fun `a module with no book abbreviations labels references by book name`() {
        assertEquals("John", vm.moduleRefFor(43, 1, 1)?.abbreviation)
        assertNull(vm.moduleRefFor(43, 1, 9))
    }

    @Test
    fun `with splitting on a blank last line looks ahead into the next chapter`() {
        val splitting = BibleViewModel(
            AppSettings().withBibleEverywhere(
                BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb", splitLongVerses = true),
            ),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        try {
            awaitUntil { splitting.isFullyLoaded && splitting.verses.value.isNotEmpty() }
            splitting._verses.value = listOf("")
            splitting._selectedVerseIndex.value = 0

            val next = splitting.getNextVerses().first()
            assertEquals(2, next.chapter)
            assertEquals(1, next.verseNumber)
        } finally {
            splitting.dispose()
        }
    }
}
