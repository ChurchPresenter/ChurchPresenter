@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Songs tab's keyboard from search to live (#797, #798, #801).
 *
 * The search box is where the keyboard browses: up and down walk the results and nothing reaches
 * the output until Go Live, which sends the highlighted song in one press. Ctrl+Tab moves between
 * the search box and what is live, and opening the tab puts the caret in the search box unless a
 * song from here is live or the schedule opened it.
 */
class SongsTabSearchKeysTest {

    private fun ComposeUiTest.pressInSearch(key: Key) {
        searchBox().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    private fun ComposeUiTest.switchSearchLive() {
        onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Tab) } }
        waitForIdle()
    }

    private fun ComposeUiTest.selectedTitle(vm: SongsViewModel) =
        vm.filteredSongItems.value.getOrNull(vm.selectedSongIndex.value)?.title

    /** Puts [title] live through the search box, the way an operator would. */
    private fun ComposeUiTest.goLiveFromSearch(vm: SongsViewModel, title: String) {
        searchBox().requestFocus()
        search(title)
        pressInSearch(Key.Enter)
        assertEquals(title, selectedTitle(vm))
    }

    // ── Opening the tab ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `opening the tab puts the caret in the search box`() =
        songsTab(focusSearchOnOpen = true) { _, _ ->
            waitForIdle()
            searchBox().assertIsFocused()
        }

    @Test
    fun `with the setting off the tab keeps the keyboard`() =
        songsTab(focusSearchOnOpen = false) { _, _ ->
            waitForIdle()
            searchBox().assertIsNotFocused()
        }

    @Test
    fun `a tab whose song is live opens on the song, not the search box`() =
        songsTab(focusSearchOnOpen = true, isPresenting = true) { _, _ ->
            waitForIdle()
            searchBox().assertIsNotFocused()
        }

    @Test
    fun `a tab opened from the schedule keeps the keyboard on that song`() {
        val row = ScheduleItem.SongItem(id = "s", songNumber = 2, title = "Be Thou My Vision", songbook = "Hymnal")
        songsTab(focusSearchOnOpen = true, scheduleSelection = mutableStateOf(row)) { _, _ ->
            waitForIdle()
            searchBox().assertIsNotFocused()
        }
    }

    // ── In the search box ────────────────────────────────────────────────────────────────────────

    @Test
    fun `up and down in the search box walk the results and send nothing`() =
        songsTab(focusSearchOnOpen = true, isPresenting = true) { vm, reports ->
            vm.selectSong(0)
            waitForIdle()
            searchBox().requestFocus()
            val pushes = reports.allSections.size
            val first = vm.selectedSongIndex.value

            pressInSearch(Key.DirectionDown)
            assertEquals(first + 1, vm.selectedSongIndex.value, "down moves to the next song")
            pressInSearch(Key.DirectionUp)
            assertEquals(first, vm.selectedSongIndex.value, "up comes back")

            assertEquals(pushes, reports.allSections.size, "browsing never reaches the output")
            searchBox().assertIsFocused()
        }

    @Test
    fun `enter in the search box puts the highlighted song live in one press`() =
        songsTab(focusSearchOnOpen = true) { vm, reports ->
            goLiveFromSearch(vm, "Be Thou My Vision")

            assertEquals(listOf(Presenting.LYRICS), reports.presenting)
            assertEquals("Be Thou My Vision", reports.selectedSection?.title)
            assertTrue(reports.selectedSection?.lines.orEmpty().isNotEmpty())
            searchBox().assertIsNotFocused()
        }

    @Test
    fun `go live on a song already live changes nothing`() =
        songsTab(isPresenting = true) { vm, reports ->
            goLiveFromSearch(vm, "Be Thou My Vision")
            val pushes = reports.allSections.size

            press(Key.Enter)

            assertEquals(pushes, reports.allSections.size, "a second Enter must not restart the song")
            assertEquals(1, reports.presenting.count { it == Presenting.LYRICS })
        }

    // ── While a song is live ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the section keys hold back while another song is selected, and say so`() =
        songsTab(isPresenting = true, songSettings = verseMode()) { vm, reports ->
            goLiveFromSearch(vm, "Amazing Grace")
            vm.selectSong(vm.filteredSongItems.value.indexOfFirst { it.title == "Be Thou My Vision" })
            waitForIdle()
            val pushes = reports.allSections.size

            press(Key.DirectionDown)

            assertEquals(pushes, reports.allSections.size, "the browsed song must not reach the screen")
            assertTrue(
                onAllNodes(hasText("Arrow keys paused", substring = true))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty(),
            )
        }

    @Test
    fun `ctrl tab goes from the search box back to the live song, and back again`() =
        songsTab(isPresenting = true) { vm, _ ->
            goLiveFromSearch(vm, "Amazing Grace")
            searchBox().requestFocus()
            search("Amazing")
            if (selectedTitle(vm) != "Amazing Grace") pressInSearch(Key.DirectionUp)
            pressInSearch(Key.DirectionDown)
            assertEquals("Amazing Love", selectedTitle(vm), "browsing moved the highlight off the live song")

            switchSearchLive()
            assertEquals("Amazing Grace", selectedTitle(vm), "back on the live song")
            searchBox().assertIsNotFocused()

            switchSearchLive()
            searchBox().assertIsFocused()
        }
}
