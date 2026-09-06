@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Driving the song list from the arrow keys.
 *
 * This is how the tab is actually used mid-service — the operator's hand is on the arrow keys, not the
 * mouse — and none of it was tested. What the keys do depends on two things at once: whether the song
 * is being displayed one *line* at a time or a whole section at a time, and whether the tab is what is
 * currently live.
 *
 * The rule that matters is the asymmetry between them. While **not** presenting, left and right walk
 * between songs so the operator can look ahead without touching the screen. While presenting, they
 * must **not** — walking off the live song would swap what the congregation is reading — so in line
 * mode they step through lines and push each step out, and in section mode they do nothing at all.
 *
 * Up and down step through sections either way, falling through to the previous or next song only when
 * there are no more sections and nothing is live.
 */
class SongsTabKeyboardTest {

    /**
     * The idle window these tests run the tab with, short enough to wait out for real.
     *
     * Not the test clock: `mainClock.advanceTimeBy` does not reliably drive that `delay`, so tests
     * written that way passed or failed on how much wall time the run happened to take. Each wait
     * below ends on a positive signal via [waitForKeys] instead.
     */
    private val IDLE_MS = 60L

    /** Bound on [waitForKeys] — generous, and only ever reached by a genuine failure. */
    private val KEYS_BACK_TIMEOUT_MS = 3_000L

    /**
     * Waits for the tab to actually take the caret back, then presses [key].
     *
     * The signal is the banner going away: it is rendered exactly while the search field holds
     * focus, so its absence is the tab reporting that the keys are its own again.
     */
    private fun ComposeUiTest.waitForKeys(key: Key) {
        waitUntil(timeoutMillis = KEYS_BACK_TIMEOUT_MS) {
            onAllNodes(hasText(searchFocusHint, substring = true))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty()
        }
        press(key)
    }

    /** The hint banner's words, which are also the "the caret is still in the box" signal. */
    private val searchFocusHint = "Keyboard is in the search box"

    /** The clear button carries no test tag, so it is addressed by its content description. */
    private val clearSearchLabel = "Clear search"

    private fun lineMode() =
        SongSettings(fullscreenDisplayMode = Constants.SONG_DISPLAY_MODE_LINE)

    // verseMode() lives in SongsTabTestSupport.kt, shared with SongsTabLyricsClickTest.

    /**
     * Presses [key] on the tab.
     *
     * The handler is an `onPreviewKeyEvent` on the tab's own focusable root, which the tab focuses
     * itself on composition, so the press is sent to the root rather than to a particular control.
     */
    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    /** Selects the first song so there is something to navigate within. */
    private fun ComposeUiTest.selectFirstSong(vm: org.churchpresenter.app.churchpresenter.viewmodel.SongsViewModel) {
        vm.selectSong(0)
        waitForIdle()
    }

    // ── Walking between songs while nothing is live ─────────────────────────────

    @Test
    fun `right moves to the next song when nothing is live`() {
        songsTab(songSettings = verseMode()) { vm, _ ->
            selectFirstSong(vm)
            val before = vm.selectedSongIndex.value

            press(Key.DirectionRight)

            assertTrue(
                vm.selectedSongIndex.value != before,
                "the operator has to be able to look ahead without touching the mouse",
            )
        }
    }

    @Test
    fun `left comes back again`() {
        songsTab(songSettings = verseMode()) { vm, _ ->
            selectFirstSong(vm)
            press(Key.DirectionRight)
            val afterRight = vm.selectedSongIndex.value

            press(Key.DirectionLeft)

            assertTrue(vm.selectedSongIndex.value != afterRight)
        }
    }

    @Test
    fun `right does not leave the live song while presenting`() {
        songsTab(songSettings = verseMode(), isPresenting = true) { vm, _ ->
            selectFirstSong(vm)
            val before = vm.selectedSongIndex.value

            press(Key.DirectionRight)

            assertEquals(
                before,
                vm.selectedSongIndex.value,
                "walking off the live song would swap what the congregation is reading",
            )
        }
    }

    @Test
    fun `left does not leave the live song while presenting`() {
        songsTab(songSettings = verseMode(), isPresenting = true) { vm, _ ->
            selectFirstSong(vm)
            val before = vm.selectedSongIndex.value

            press(Key.DirectionLeft)

            assertEquals(before, vm.selectedSongIndex.value)
        }
    }

    @Test
    fun `by default the arrow keys are already in line mode`() {
        // Nothing was configured for lines here, but `lowerThirdDisplayMode` defaults to it — so
        // right steps a line rather than moving to the next song. This is the out-of-the-box
        // behaviour, and it is the reason every song-walking test above has to spell out verse mode.
        songsTab { vm, _ ->
            selectFirstSong(vm)
            val song = vm.selectedSongIndex.value

            press(Key.DirectionRight)

            assertEquals(song, vm.selectedSongIndex.value, "the default is line stepping, not song walking")
        }
    }

    // ── Stepping through lines while presenting ─────────────────────────────────

    @Test
    fun `in line mode right steps to the next line and pushes it out`() {
        songsTab(songSettings = lineMode(), isPresenting = true) { vm, reports ->
            selectFirstSong(vm)

            press(Key.DirectionRight)

            // The step has to reach the output, not just the tab's own state.
            assertNotNull(reports.lineIndex, "the presenter must be told which line to show")
            assertEquals(vm.selectedLineIndex.value, reports.lineIndex)
        }
    }

    @Test
    fun `in line mode left steps back`() {
        songsTab(songSettings = lineMode(), isPresenting = true) { vm, reports ->
            selectFirstSong(vm)
            press(Key.DirectionRight)
            val forward = vm.selectedLineIndex.value

            press(Key.DirectionLeft)

            assertTrue(vm.selectedLineIndex.value <= forward)
            assertEquals(vm.selectedLineIndex.value, reports.lineIndex)
        }
    }

    @Test
    fun `in line mode the keys stay within the song even while presenting`() {
        songsTab(songSettings = lineMode(), isPresenting = true) { vm, _ ->
            selectFirstSong(vm)
            val song = vm.selectedSongIndex.value

            repeat(4) { press(Key.DirectionRight) }

            assertEquals(song, vm.selectedSongIndex.value, "line stepping must not change song")
        }
    }

    // ── Sections, up and down ───────────────────────────────────────────────────

    @Test
    fun `down steps through the sections and pushes each one out`() {
        songsTab { vm, reports ->
            selectFirstSong(vm)

            press(Key.DirectionDown)

            assertNotNull(reports.selectedSection, "the presenter must be handed the new section")
        }
    }

    @Test
    fun `up steps back through the sections`() {
        songsTab { vm, reports ->
            selectFirstSong(vm)
            press(Key.DirectionDown)
            val down = vm.selectedSectionIndex.value

            press(Key.DirectionUp)

            assertTrue(vm.selectedSectionIndex.value <= down)
            assertNotNull(reports.selectedSection)
        }
    }

    @Test
    fun `down past the last section moves to the next song when nothing is live`() {
        songsTab(songSettings = verseMode()) { vm, _ ->
            selectFirstSong(vm)
            val song = vm.selectedSongIndex.value

            // The fixture songs are short, so a handful of presses runs out of sections.
            repeat(8) { press(Key.DirectionDown) }

            assertTrue(
                vm.selectedSongIndex.value != song,
                "running out of sections should carry on into the next song",
            )
        }
    }

    @Test
    fun `down past the last section stays put while presenting`() {
        songsTab(isPresenting = true) { vm, _ ->
            selectFirstSong(vm)
            val song = vm.selectedSongIndex.value

            repeat(8) { press(Key.DirectionDown) }

            assertEquals(
                song,
                vm.selectedSongIndex.value,
                "the live song must not change under the congregation",
            )
        }
    }

    // ── Typing in the search box ────────────────────────────────────────────────

    /**
     * One song with a verse long enough for a line step to be visible.
     *
     * The shared fixtures give every song a single line, so stepping a line there immediately falls
     * through to the next section and leaves the line index back at 0 — which cannot tell a key that
     * worked from a key that was swallowed.
     */
    private val multiLineSong = listOf(
        SongFixture(
            number = "1",
            title = "Amazing Grace",
            lyrics = listOf("[Verse 1]", "Amazing grace", "how sweet the sound", "that saved a wretch"),
        )
    )

    @Test
    /**
     * The keys belong to the text while the caret is in the search field.
     *
     * The handler is an `onPreviewKeyEvent` on the tab root, so it sees every key before the field
     * does. Unguarded, it swallowed left and right — the caret could not be moved through a query at
     * all — and each keystroke navigated the song underneath instead, on a list the same keystrokes
     * were re-filtering. That is what the reported crash walked off the end of.
     */
    fun `arrow keys in the search box leave the song alone`() {
        songsTab(songs = multiLineSong, songSettings = lineMode()) { vm, _ ->
            selectFirstSong(vm)
            press(Key.DirectionRight)
            val song = vm.selectedSongIndex.value
            val section = vm.selectedSectionIndex.value
            val line = vm.selectedLineIndex.value

            search("grace")
            listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)
                .forEach { key ->
                    searchBox().performKeyInput { pressKey(key) }
                    waitForIdle()
                }

            assertEquals(song, vm.selectedSongIndex.value, "typing must not walk the song list")
            assertEquals(section, vm.selectedSectionIndex.value)
            assertEquals(line, vm.selectedLineIndex.value)
        }
    }

    /**
     * And has them back the moment the caret leaves it — the guard is a state, not a one-way door.
     *
     * Clicking a scheduled song is one of four routes out of the field now; the other three — the
     * idle window, Enter, and the clear button — are covered in the section below.
     */
    @Test
    fun `the keys navigate again once the search box loses focus`() {
        val selection = mutableStateOf<ScheduleItem.SongItem?>(null)
        songsTab(
            songs = multiLineSong,
            songSettings = lineMode(),
            isPresenting = true,
            scheduleSelection = selection,
        ) { vm, _ ->
            selectFirstSong(vm)
            search("grace")
            searchBox().performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
            assertEquals(0, vm.selectedLineIndex.value, "the caret is in the field, so nothing moved")

            // Clicking a scheduled song is how the operator goes back to navigating: the tab takes
            // keyboard focus for itself, which is what takes the caret out of the field.
            selection.value = ScheduleItem.SongItem(
                id = "schedule-1", songNumber = 1, title = "Amazing Grace", songbook = "Hymnal",
            )
            waitForIdle()
            press(Key.DirectionRight)

            assertTrue(vm.selectedLineIndex.value > 0, "was ${vm.selectedLineIndex.value}")
        }
    }

    // ── Getting the keys back from the search box ───────────────────────────────

    /**
     * Typing previews the first hit with no click, but leaves the caret in the search field, where
     * the tab's key handler stands down — so the verse and line keys did nothing until the operator
     * clicked a row. After typing stops the tab takes focus back on its own.
     *
     * The wait is elapsed on the test clock, so none of these pay a real second.
     */
    @Test
    fun `typing that stops hands the keys back`() {
        songsTab(songs = multiLineSong, songSettings = lineMode(), searchIdleFocusMs = IDLE_MS) { vm, _ ->
            selectFirstSong(vm)
            search("grace")
            searchBox().performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
            assertEquals(0, vm.selectedLineIndex.value, "still typing, so the key belongs to the text")

            waitForKeys(Key.DirectionRight)

            assertTrue(vm.selectedLineIndex.value > 0, "was ${vm.selectedLineIndex.value}")
        }
    }

    /** Only the caret moves: the query and the filtered list are left exactly as they were. */
    @Test
    fun `the query survives the keys coming back`() {
        songsTab(songs = multiLineSong, songSettings = lineMode(), searchIdleFocusMs = IDLE_MS) { vm, _ ->
            selectFirstSong(vm)
            search("grace")
            waitForKeys(Key.DirectionRight)

            assertEquals("grace", vm.searchQuery.value, "the query must not be cleared")
            assertEquals(listOf("Amazing Grace"), listedTitles(multiLineSong), "nor the filter dropped")
        }
    }

    /**
     * The wait is on *quiet*, not on time since the first keystroke.
     *
     * Two gaps that each fall short of the window must not add up to one that clears it — otherwise
     * a slow typist loses the caret mid-query.
     */
    @Test
    fun `each keystroke restarts the wait`() {
        // A window long enough that the two keystrokes below land well inside it, so the test is
        // about the restart and not about how fast the machine is.
        songsTab(songs = multiLineSong, songSettings = lineMode(), searchIdleFocusMs = 2_000L) { vm, _ ->
            selectFirstSong(vm)
            search("g")
            search("gr")

            searchBox().performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
            assertEquals(0, vm.selectedLineIndex.value, "the second keystroke restarted the wait")

            // The banner is still up, which is the tab saying the caret is still in the box.
            assertTrue(
                onAllNodes(hasText(searchFocusHint, substring = true))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty(),
                "the banner must still say the caret is in the box",
            )
        }
    }

    /**
     * While lyrics are live the tab never takes the caret back on its own.
     *
     * Every navigation branch pushes to the output while presenting, so a pause mid-service must not
     * leave one stray keypress able to change what the congregation is reading. Enter is still
     * allowed, because it is the operator asking for it.
     */
    @Test
    fun `while a song is live the wait never fires but Enter still does`() {
        songsTab(
            songs = multiLineSong,
            songSettings = lineMode(),
            isPresenting = true,
            searchIdleFocusMs = IDLE_MS,
        ) { vm, reports ->
            selectFirstSong(vm)
            search("grace")
            val pushedBefore = reports.allSections.size

            // Well past the window, and it must still not have fired.
            Thread.sleep(IDLE_MS * 5)
            waitForIdle()
            press(Key.DirectionRight)
            assertEquals(0, vm.selectedLineIndex.value, "the wait must not fire while live")
            assertEquals(pushedBefore, reports.allSections.size, "and nothing may reach the output")

            searchBox().performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            press(Key.DirectionRight)
            assertTrue(vm.selectedLineIndex.value > 0, "was ${vm.selectedLineIndex.value}")
        }
    }

    /** Enter is the operator saying "that is the song" — no wait, and the query stays put. */
    @Test
    fun `Enter hands the keys back at once`() {
        // The app's own three seconds: Enter must not be waiting for any window at all.
        songsTab(songs = multiLineSong, songSettings = lineMode()) { vm, _ ->
            selectFirstSong(vm)
            search("grace")
            searchBox().performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            press(Key.DirectionRight)

            assertTrue(vm.selectedLineIndex.value > 0, "was ${vm.selectedLineIndex.value}")
            assertEquals("grace", vm.searchQuery.value, "Enter must not clear the query")
        }
    }

    /** Clearing the box leaves focus on the tab, not on the clear button it was pressed with. */
    @Test
    fun `clearing the box hands the keys back`() {
        songsTab(songs = multiLineSong, songSettings = lineMode()) { vm, _ ->
            selectFirstSong(vm)
            search("grace")
            onNodeWithContentDescription(clearSearchLabel).performClick()
            waitForIdle()
            press(Key.DirectionRight)

            assertTrue(vm.selectedLineIndex.value > 0, "was ${vm.selectedLineIndex.value}")
        }
    }

    // ── Keys the tab does not claim ─────────────────────────────────────────────

    @Test
    fun `an unrelated key changes nothing`() {
        songsTab { vm, _ ->
            selectFirstSong(vm)
            val song = vm.selectedSongIndex.value
            val section = vm.selectedSectionIndex.value

            press(Key.Spacebar)

            assertEquals(song, vm.selectedSongIndex.value)
            assertEquals(section, vm.selectedSectionIndex.value)
        }
    }

    // ── Rebound keys ────────────────────────────────────────────────────────────

    @Test
    fun `a rebound next-line key steps the line and the shipped key stops doing so`() {
        val remapped = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = mapOf(ShortcutAction.SONGS_NEXT.name to listOf(KeyChord.of(Key.N)))
            )
        )
        songsTab(songSettings = lineMode(), isPresenting = true, shortcuts = remapped) { vm, reports ->
            selectFirstSong(vm)

            press(Key.N)
            // Asserted through what reached the presenter, the same way the shipped-key cases above
            // do — the line index alone can legitimately stay put at the end of a song.
            assertNotNull(reports.lineIndex, "the rebound key must push a line to the presenter")

            val afterRebound = vm.selectedLineIndex.value
            press(Key.DirectionRight)
            assertEquals(afterRebound, vm.selectedLineIndex.value, "the shipped key must stop working")
        }
    }

    // ── The on-screen navigation hint ───────────────────────────────────────────

    /**
     * The hint names the keys that are actually bound.
     *
     * It shipped as the literal "Use ← → to navigate lines, ↑ ↓ for verses" and went on saying that
     * after the keys became rebindable — the defect this suite's hint cases exist for.
     */
    @Test
    fun `the line-mode hint is shown`() {
        songsTab(songSettings = lineMode()) { _, _ ->
            onNodeWithText("navigate lines", substring = true).assertExists()
        }
    }

    @Test
    fun `the hint follows a rebind`() {
        val remapped = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = mapOf(
                    ShortcutAction.SONGS_PREVIOUS.name to listOf(KeyChord.of(Key.N)),
                    ShortcutAction.SONGS_NEXT.name to listOf(KeyChord.of(Key.P)),
                )
            )
        )
        songsTab(songSettings = lineMode(), shortcuts = remapped) { _, _ ->
            onNodeWithText("Use N  P to navigate lines", substring = true).assertExists()
        }
    }

    @Test
    fun `the hint disappears when every navigation key is unbound`() {
        val cleared = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = listOf(
                    ShortcutAction.SONGS_PREVIOUS, ShortcutAction.SONGS_NEXT,
                    ShortcutAction.SONGS_PREVIOUS_SECTION, ShortcutAction.SONGS_NEXT_SECTION,
                ).associate { it.name to emptyList<KeyChord>() }
            )
        )
        songsTab(songSettings = lineMode(), shortcuts = cleared) { _, _ ->
            // A sentence with holes where the keys should be is worse than no sentence.
            onNodeWithText("navigate lines", substring = true).assertDoesNotExist()
        }
    }
}
