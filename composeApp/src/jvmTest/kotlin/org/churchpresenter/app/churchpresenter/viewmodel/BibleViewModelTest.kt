package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BibleEngineSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [BibleViewModel]'s Bible-independent behaviour: verse-range formatting, recent-passage history,
 * and the engine tuning settings that drive speech auto-follow.
 *
 * Anything reading actual scripture (chapter loading, verse selection, search) needs a loaded
 * `.spb` SQLite Bible and is not covered here — with no bible file configured the view model loads
 * nothing, which is exactly the state these tests run in.
 */
class BibleViewModelTest {

    private val created = mutableListOf<BibleViewModel>()

    @AfterTest
    fun cleanup() {
        created.clear()
    }

    private fun vm(settings: AppSettings = AppSettings()): BibleViewModel =
        BibleViewModel(settings).also { created.add(it) }

    // ── Verse range formatting ──────────────────────────────────────────────────

    @Test
    fun `a single verse formats as its own number`() {
        assertEquals("16", vm().formatVerseRange(listOf(16)))
    }

    @Test
    fun `no verses formats as empty`() {
        assertEquals("", vm().formatVerseRange(emptyList()))
    }

    @Test
    fun `a contiguous run collapses to a dash range`() {
        assertEquals("1-3", vm().formatVerseRange(listOf(1, 2, 3)))
        assertEquals("16-18", vm().formatVerseRange(listOf(16, 17, 18)))
    }

    @Test
    fun `a gap is listed rather than collapsed`() {
        // "1-5" would claim verses 2 and 4 are on screen when they are not.
        assertEquals("1,3,5", vm().formatVerseRange(listOf(1, 3, 5)))
    }

    @Test
    fun `verses are sorted before formatting`() {
        // Ctrl-clicking bottom-up produces an out-of-order list.
        assertEquals("1-3", vm().formatVerseRange(listOf(3, 1, 2)))
        assertEquals("1,3,5", vm().formatVerseRange(listOf(5, 1, 3)))
    }

    @Test
    fun `a partly contiguous selection is listed in full`() {
        // 1,2,3,7 is not fully contiguous, so every number is spelled out.
        assertEquals("1,2,3,7", vm().formatVerseRange(listOf(1, 2, 3, 7)))
    }

    @Test
    fun `two adjacent verses collapse, two separated verses do not`() {
        assertEquals("4-5", vm().formatVerseRange(listOf(4, 5)))
        assertEquals("4,6", vm().formatVerseRange(listOf(4, 6)))
    }

    // ── History ─────────────────────────────────────────────────────────────────

    @Test
    fun `history starts empty`() {
        assertTrue(vm().history.isEmpty())
    }

    @Test
    fun `the most recent passage is first`() {
        val b = vm()
        b.addToHistory("John", 3, 16, "For God so loved…")
        b.addToHistory("Psalm", 23, 1, "The Lord is my shepherd")

        assertEquals(2, b.history.size)
        assertEquals("Psalm", b.history.first().bookName, "newest first — the operator scans from the top")
    }

    @Test
    fun `re-visiting a passage moves it to the front instead of duplicating`() {
        val b = vm()
        b.addToHistory("John", 3, 16, "text")
        b.addToHistory("Psalm", 23, 1, "text")
        b.addToHistory("John", 3, 16, "text")

        assertEquals(2, b.history.size, "the repeat must not create a second row")
        assertEquals("John", b.history.first().bookName)
    }

    @Test
    fun `passages differing only by range are separate entries`() {
        val b = vm()
        b.addToHistory("John", 3, 16, "text", verseRange = "16")
        b.addToHistory("John", 3, 16, "text", verseRange = "16-18")
        assertEquals(2, b.history.size, "a single verse and a range are different passages")
    }

    @Test
    fun `history is capped at fifty entries, dropping the oldest`() {
        val b = vm()
        for (verse in 1..60) b.addToHistory("Psalm", 119, verse, "verse $verse")

        assertEquals(50, b.history.size)
        assertEquals(60, b.history.first().verseNumber, "newest kept")
        assertEquals(11, b.history.last().verseNumber, "the first ten fell off the end")
    }

    @Test
    fun `history can be cleared`() {
        val b = vm()
        b.addToHistory("John", 3, 16, "text")
        b.clearHistory()
        assertTrue(b.history.isEmpty())
    }

    @Test
    fun `a history entry displays as a reference`() {
        val single = BibleViewModel.HistoryEntry("John", 3, 16, "text")
        assertEquals("John 3:16", single.displayText)

        val range = BibleViewModel.HistoryEntry("John", 3, 16, "text", verseRange = "16-18")
        assertEquals("John 3:16-18", range.displayText, "a range displays the range, not just the start")
    }

    // ── Auto-follow and engine tuning ───────────────────────────────────────────

    @Test
    fun `auto-follow is seeded from persisted settings`() {
        assertFalse(vm(AppSettings(bibleEngineSettings = BibleEngineSettings(autoFollow = false))).autoFollowEnabled.value)
        assertTrue(vm(AppSettings(bibleEngineSettings = BibleEngineSettings(autoFollow = true))).autoFollowEnabled.value)
    }

    @Test
    fun `auto-follow can be toggled`() {
        val b = vm()
        b.setAutoFollow(true)
        assertTrue(b.autoFollowEnabled.value)
        b.setAutoFollow(false)
        assertFalse(b.autoFollowEnabled.value)
    }

    @Test
    fun `enabling auto-follow with no detections does not fire a go-live`() {
        val b = vm()
        val before = b.autoFollowLiveToken.value
        b.setAutoFollow(true)
        assertEquals(before, b.autoFollowLiveToken.value, "nothing was detected, so nothing should go live")
    }

    @Test
    fun `the text match level is pushed to the engine when changed`() {
        val b = vm()
        val pushed = mutableListOf<TextMatchLevel>()
        b.onTextMatchLevelChanged = { pushed.add(it) }

        b.setTextMatchLevel(TextMatchLevel.AGGRESSIVE)
        assertEquals(TextMatchLevel.AGGRESSIVE, b.textMatchLevel.value)
        assertEquals(listOf(TextMatchLevel.AGGRESSIVE), pushed, "the engine must be told, or the UI lies")

        b.setTextMatchLevel(TextMatchLevel.OFF)
        assertEquals(listOf(TextMatchLevel.AGGRESSIVE, TextMatchLevel.OFF), pushed)
    }

    @Test
    fun `the continuation speed is pushed to the engine when changed`() {
        val b = vm()
        val pushed = mutableListOf<ContinuationSpeed>()
        b.onContinuationSpeedChanged = { pushed.add(it) }

        b.setContinuationSpeed(ContinuationSpeed.FAST)
        assertEquals(ContinuationSpeed.FAST, b.continuationSpeed.value)
        assertEquals(listOf(ContinuationSpeed.FAST), pushed)
    }

    @Test
    fun `the two engine tuning settings are independent`() {
        val b = vm()
        b.setTextMatchLevel(TextMatchLevel.CONSERVATIVE)
        b.setContinuationSpeed(ContinuationSpeed.FAST)
        assertEquals(TextMatchLevel.CONSERVATIVE, b.textMatchLevel.value)
        assertEquals(ContinuationSpeed.FAST, b.continuationSpeed.value, "one must not reset the other")
    }

    @Test
    fun `engine tuning is seeded from persisted settings`() {
        val b = vm(
            AppSettings(
                bibleEngineSettings = BibleEngineSettings(
                    textMatchLevel = "balanced",
                    continuationSpeed = "fast",
                ),
            ),
        )
        assertEquals(TextMatchLevel.BALANCED, b.textMatchLevel.value, "case-insensitive parse")
        assertEquals(ContinuationSpeed.FAST, b.continuationSpeed.value)
    }

    @Test
    fun `an unparseable persisted level falls back to a safe default`() {
        // Hand-edited or downgraded settings must not crash startup.
        val b = vm(
            AppSettings(
                bibleEngineSettings = BibleEngineSettings(
                    textMatchLevel = "not-a-level",
                    continuationSpeed = "nonsense",
                ),
            ),
        )
        assertEquals(TextMatchLevel.OFF, b.textMatchLevel.value, "reverse lookup stays off unless asked for")
        assertEquals(ContinuationSpeed.BALANCED, b.continuationSpeed.value)
    }

    // ── Multi-verse selection with no chapter loaded ─────────────────────────────

    @Test
    fun `verse clicks are ignored when no chapter is loaded`() {
        // Every click path is bounds-checked against the loaded verse list; with no bible
        // configured that list is empty, and clicking must be inert rather than throwing.
        val b = vm()
        b.ctrlClickVerse(0)
        b.ctrlClickVerse(-1)
        b.shiftClickVerse(5)
        assertTrue(b.selectedVerseIndices.isEmpty())
        assertFalse(b.multiVerseEnabled.value)
    }

    @Test
    fun `clearing a multi-verse selection resets the flag`() {
        val b = vm()
        b.clearMultiVerseSelection()
        assertTrue(b.selectedVerseIndices.isEmpty())
        assertFalse(b.multiVerseEnabled.value)

        b.toggleMultiVerse(false)
        assertFalse(b.multiVerseEnabled.value)
    }
}
