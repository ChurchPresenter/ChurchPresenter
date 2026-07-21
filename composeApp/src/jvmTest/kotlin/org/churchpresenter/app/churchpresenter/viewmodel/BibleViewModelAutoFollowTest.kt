package org.churchpresenter.app.churchpresenter.viewmodel

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
 * Speech-driven auto-follow: what the detection engine reports, and what actually reaches the
 * congregation as a result.
 *
 * The rule under test is the **tiering**. `explicit` (the reference was spoken outright) and
 * `continuation` (simply the next verse of the passage being read) go live immediately — that is
 * the ordinary case while following a reading. `chapter-scan`, `chapter-history` and `reverse` are
 * inferred from the text with no reference actually spoken, so they only *stage* the passage in
 * the browse view; a wrong guess must never overwrite what is on screen.
 *
 * `autoFollowLiveToken` is the signal that something was pushed live, so these tests distinguish
 * "went live" from "was merely selected" by watching it.
 */
class BibleViewModelAutoFollowTest {

    private lateinit var dir: File
    private lateinit var vm: BibleViewModel

    @BeforeTest
    fun loadBible() {
        dir = Files.createTempDirectory("cp-bible-autofollow-test").toFile()
        SpbFixture.spbFile(dir, name = "test.spb")
        vm = newViewModel(autoFollow = true)
    }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun newViewModel(autoFollow: Boolean): BibleViewModel {
        val model = BibleViewModel(
            AppSettings(
                bibleSettings = BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "test.spb"),
                bibleEngineSettings = BibleEngineSettings(autoFollow = autoFollow),
            ),
        )
        awaitUntil("books to load") { model.books.value.isNotEmpty() }
        awaitUntil("verse data to load") { model.isFullyLoaded }
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

    /** True once [condition] holds; false if it never does within [timeoutMs]. Only ever used for
     *  checks that EXPECT it to become true — a false result costs the full timeout, so a
     *  "must NOT happen" check uses [awaitNavigation] plus a plain assert instead. */
    private fun settlesTo(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return false
    }

    /**
     * Waits for the detection's navigation to finish, which is the positive signal that the
     * go-live decision has been made: `navigateToReference` bumps `verseSelectionToken` and then —
     * in the same coroutine, with no suspension between — bumps `autoFollowLiveToken` if and only
     * if the match qualified. Once the first token moves, the second either moved with it or never
     * will, so a "must not go live" assertion can run immediately instead of waiting out a timeout.
     */
    private fun awaitNavigation(model: BibleViewModel, before: Int) =
        awaitUntil("the detection to finish navigating") { model.verseSelectionToken.value > before }

    /** Reports a detection of John 3:16 with the given engine match type. */
    private fun detect(
        matchType: String,
        model: BibleViewModel = vm,
        bookId: Int = 43,
        chapter: Int = 3,
        verseStart: Int = 16,
        verseEnd: Int? = null,
        tracks: List<String> = listOf("transcription"),
    ) = model.onEngineScripture(
        bookId = bookId,
        chapter = chapter,
        verseStart = verseStart,
        verseEnd = verseEnd,
        verseText = "For God so loved the world.",
        matchType = matchType,
        tracks = tracks,
    )

    // ── Detection list ──────────────────────────────────────────────────────────

    @Test
    fun `a detection appears in the chip list`() {
        detect("explicit")
        assertEquals(1, vm.detectedReferences.value.size)
        with(vm.detectedReferences.value.single()) {
            assertEquals(2, bookIndex, "John is at display index 2")
            assertEquals(3, chapter)
            assertEquals(16, verseStart)
            assertTrue(DetectionSource.EXPLICIT in sources)
        }
    }

    @Test
    fun `every engine match type maps to its own source marker`() {
        val expected = mapOf(
            "explicit" to DetectionSource.EXPLICIT,
            "continuation" to DetectionSource.CONTINUATION,
            "chapter-scan" to DetectionSource.CHAPTER_SCAN,
            "chapter-history" to DetectionSource.CHAPTER_HISTORY,
            "reverse" to DetectionSource.REVERSE,
        )
        expected.entries.forEachIndexed { i, (matchType, source) ->
            val model = newViewModel(autoFollow = false)
            detect(matchType, model = model, verseStart = i + 1)
            assertTrue(source in model.detectedReferences.value.single().sources, "match type $matchType")
        }
    }

    @Test
    fun `an unknown match type is treated as a reverse lookup`() {
        // The engine may add types; the app must not drop them, and must treat them as inferred.
        val model = newViewModel(autoFollow = false)
        detect("some-future-type", model = model)
        assertTrue(DetectionSource.REVERSE in model.detectedReferences.value.single().sources)
    }

    @Test
    fun `the same reference detected twice does not duplicate the chip`() {
        detect("explicit")
        detect("explicit")
        assertEquals(1, vm.detectedReferences.value.size, "repeated engine events must not stack up")
    }

    @Test
    fun `corroboration from a second track merges into the existing chip`() {
        // The translation track arrives later than the transcription track for the same verse.
        detect("explicit", tracks = listOf("transcription"))
        detect("explicit", tracks = listOf("translation"))

        val chip = vm.detectedReferences.value.single()
        assertTrue(DetectionTrack.TRANSCRIPTION in chip.tracks)
        assertTrue(DetectionTrack.TRANSLATION in chip.tracks, "late corroboration should show on the chip")
    }

    @Test
    fun `different references produce separate chips, newest first`() {
        detect("explicit", verseStart = 16)
        detect("continuation", verseStart = 17)
        assertEquals(2, vm.detectedReferences.value.size)
        assertEquals(17, vm.detectedReferences.value.first().verseStart, "the newest detection leads the list")
    }

    @Test
    fun `the chip carries the app's own verse text, not the engine's`() {
        detect("explicit")
        assertEquals(
            "For God so loved the world.",
            vm.detectedReferences.value.single().verseText,
            "the operator should see this Bible's wording",
        )
    }

    @Test
    fun `a detection for a book this bible lacks is ignored`() {
        detect("explicit", bookId = 66) // Revelation, not in the fixture
        assertTrue(vm.detectedReferences.value.isEmpty(), "nothing to navigate to")
    }

    @Test
    fun `clearing removes every chip`() {
        detect("explicit", verseStart = 16)
        detect("continuation", verseStart = 17)
        vm.clearDetectedReferences()
        assertTrue(vm.detectedReferences.value.isEmpty())
    }

    // ── Tiering: instant go-live ────────────────────────────────────────────────

    @Test
    fun `an explicit reference goes live immediately`() {
        val token = vm.autoFollowLiveToken.value
        detect("explicit")
        assertTrue(
            settlesTo { vm.autoFollowLiveToken.value > token },
            "a spoken reference should reach the screen without extra confirmation",
        )
    }

    @Test
    fun `a continuation goes live immediately`() {
        // Sequential next-verse reading is the expected case, not a risky jump.
        val token = vm.autoFollowLiveToken.value
        detect("continuation")
        assertTrue(settlesTo { vm.autoFollowLiveToken.value > token })
    }

    @Test
    fun `an instant go-live carries the match type for the training log`() {
        detect("explicit")
        awaitUntil("go-live") { vm.autoFollowLiveMatchType.value != null }
        assertEquals("explicit", vm.autoFollowLiveMatchType.value)
    }

    // ── Tiering: staged only ────────────────────────────────────────────────────

    @Test
    fun `an inferred match is staged, never pushed live`() {
        for (matchType in listOf("chapter-scan", "chapter-history", "reverse")) {
            val model = newViewModel(autoFollow = true)
            val token = model.autoFollowLiveToken.value
            val navToken = model.verseSelectionToken.value

            detect(matchType, model = model)

            awaitNavigation(model, navToken)
            assertEquals(
                token,
                model.autoFollowLiveToken.value,
                "$matchType was inferred from the text with no reference spoken — it must not go live",
            )
            assertEquals(1, model.detectedReferences.value.size, "$matchType should still offer a chip")
        }
    }

    @Test
    fun `a staged match still moves the browse view to the passage`() {
        val model = newViewModel(autoFollow = true)
        detect("reverse", model = model)
        awaitUntil("the browse view to move") { model.selectedBookIndex.value == 2 }

        assertEquals(3, model.selectedChapter.value, "the operator can accept it with one click")
    }

    @Test
    fun `a continuation after a staged guess confirms it and goes live`() {
        // The documented recovery path: an inferred match waits, then the next sequential verse
        // corroborates the passage and takes it live.
        val model = newViewModel(autoFollow = true)
        detect("reverse", model = model, verseStart = 16)
        val token = model.autoFollowLiveToken.value

        detect("continuation", model = model, verseStart = 17)
        assertTrue(settlesTo { model.autoFollowLiveToken.value > token })
    }

    // ── Auto-follow disabled ────────────────────────────────────────────────────

    @Test
    fun `with auto-follow off nothing goes live, whatever the match type`() {
        val model = newViewModel(autoFollow = false)
        val token = model.autoFollowLiveToken.value
        val navToken = model.verseSelectionToken.value

        detect("explicit", model = model)
        detect("continuation", model = model, verseStart = 17)

        // With auto-follow off the detections never navigate either, so there is no navigation to
        // wait on — turning it on is what catches up. A model that had gone live would have done so
        // synchronously-enough that the chips below could not have landed first.
        assertEquals(2, model.detectedReferences.value.size, "detections are still offered as chips")
        assertEquals(navToken, model.verseSelectionToken.value, "a detection must not even move the browse view")
        assertEquals(token, model.autoFollowLiveToken.value, "auto-follow is off")
    }

    @Test
    fun `turning auto-follow on jumps to the most recent detection`() {
        val model = newViewModel(autoFollow = false)
        detect("explicit", model = model)
        val token = model.autoFollowLiveToken.value

        model.setAutoFollow(true)
        assertTrue(
            settlesTo { model.autoFollowLiveToken.value > token },
            "enabling auto-follow should catch up to what was just detected",
        )
    }

    // ── Session correlation ─────────────────────────────────────────────────────

    @Test
    fun `the segment and session ids from the engine are remembered`() {
        // These are the join keys back to the STT transcript and the engine's own detection log.
        vm.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 16, verseEnd = null,
            verseText = "text", matchType = "explicit",
            segmentId = "seg-42", sessionId = "2026-07-21_100000",
        )
        assertEquals("seg-42", vm.lastDetectionSegmentId)
        assertEquals("2026-07-21_100000", vm.lastSessionId)
    }

    @Test
    fun `a detection without ids leaves the previous ones alone`() {
        vm.onEngineScripture(
            bookId = 43, chapter = 3, verseStart = 16, verseEnd = null,
            verseText = "t", matchType = "explicit", segmentId = "seg-1", sessionId = "sess-1",
        )
        detect("continuation", verseStart = 17) // no ids on this one
        assertEquals("seg-1", vm.lastDetectionSegmentId, "correlation must not be lost mid-session")
        assertEquals("sess-1", vm.lastSessionId)
    }
}
