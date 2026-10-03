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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BibleViewModelExposedStateTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-state").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        vm = BibleViewModel(
            AppSettings(bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb")),
            dispatcher = Dispatchers.Unconfined,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val deadline = System.currentTimeMillis() + 5_000
        while (!(vm.isFullyLoaded && vm.verses.value.isNotEmpty())) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out")
            Thread.yield()
        }
    }

    @AfterTest
    fun tearDown() {
        vm.dispose()
        dir.deleteRecursively()
    }

    @Test
    fun `each column filter keeps the text typed into it`() {
        vm.updateBookSearchQuery("ps")
        vm.updateChapterSearchQuery("2")
        vm.updateVerseSearchQuery("light")

        assertEquals("ps", vm.bookSearchQuery.value)
        assertEquals("2", vm.chapterSearchQuery.value)
        assertEquals("light", vm.verseSearchQuery.value)
        assertEquals(listOf("Psalms"), vm.filteredBooks.value)
    }

    @Test
    fun `the next verses follow the selection`() {
        vm.selectVerse(0)

        assertEquals(2, vm.nextVerses.value.first().verseNumber)
    }

    @Test
    fun `changing the engine tuning reaches the listeners that were set`() {
        val levels = mutableListOf<TextMatchLevel>()
        val speeds = mutableListOf<ContinuationSpeed>()
        vm.onTextMatchLevelChanged = { levels += it }
        vm.onContinuationSpeedChanged = { speeds += it }

        vm.setTextMatchLevel(TextMatchLevel.AGGRESSIVE)
        vm.setContinuationSpeed(ContinuationSpeed.FAST)

        assertEquals(listOf(TextMatchLevel.AGGRESSIVE), levels)
        assertEquals(listOf(ContinuationSpeed.FAST), speeds)
        assertTrue(vm.onTextMatchLevelChanged != null && vm.onContinuationSpeedChanged != null)
    }

    @Test
    fun `a detection remembers the segment and session it came from`() {
        vm.onEngineScripture(
            EngineScripture(43, 3, 16, null, "", "explicit", segmentId = "seg-7", sessionId = "service-1"),
        )

        assertEquals("seg-7", vm.lastDetectionSegmentId)
        assertEquals("service-1", vm.lastSessionId)
    }

    @Test
    fun `a loaded module is no longer loading and names its books`() {
        assertFalse(vm.isLoading.value)
        assertSame(vm.bookNameMapping.value, vm.bookNameMapping.value)
        vm.addToHistory("Genesis", 1, 1, "In the beginning")
        assertEquals(1, vm._history.size)
    }
}
