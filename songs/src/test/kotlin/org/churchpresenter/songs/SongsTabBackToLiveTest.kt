@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "Back to Live" button — the way back to whatever is on the screen after searching away from it.
 *
 * Mid-service the operator searches for the *next* song while the current one is still projected.
 * That leaves the tab showing one song and the audience seeing another, and the lyric pane following
 * the selection rather than the output. This button is the only route back: it reselects the live
 * song and restores the exact section and line that are live, not merely the song.
 *
 * It appears only when all three hold — the tab is presenting, something is live, and the row the
 * selection now points at is a *different* song. Each of those is its own reason to hide it, and the
 * button appearing when nothing is live would be an invitation to jump somewhere arbitrary.
 *
 * The live song is put out of view with a **search**, which is what the production comment names as
 * the case: `liveSongId` is re-stamped on every push, so a plain selection change cannot separate the
 * two — filtering the list underneath the selection is what does.
 *
 * See `SongsTabTestSupport.kt` for the harness.
 */
class SongsTabBackToLiveTest {

    private val backToLive = "Back to Live"

    private fun ComposeUiTest.hasBackToLive(): Boolean =
        onAllNodes(hasText(backToLive)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** Puts [title] live, which is what stamps it as the live song. */
    private fun ComposeUiTest.goLiveWith(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
        onAllNodes(hasContentDescription("Go Live"))[0].performClick()
        waitForIdle()
    }

    // ── When it is offered ──────────────────────────────────────────────────────

    @Test
    fun `nothing live means no way back`() = songsTab(isPresenting = true) { _, _ ->
        // Presenting but with nothing pushed yet: there is no "live" to return to.
        assertTrue(!hasBackToLive(), "the button must not offer a jump to nothing")
    }

    @Test
    fun `while the live song is the selected one there is nothing to go back to`() =
        songsTab(isPresenting = true) { _, _ ->
            goLiveWith("Amazing Grace")

            assertTrue(!hasBackToLive(), "the selection already is the live song")
        }

    @Test
    fun `searching the live song out of view offers the way back`() =
        songsTab(isPresenting = true) { _, _ ->
            goLiveWith("Amazing Grace")

            // "Be Thou" matches only the other song, so the selection now points at a row that is
            // not what the congregation is looking at.
            search("Be Thou")

            assertTrue(hasBackToLive(), "the operator must be able to get back to what is live")
        }

    @Test
    fun `the button goes away once it has been used`() =
        songsTab(isPresenting = true) { _, _ ->
            goLiveWith("Amazing Grace")
            search("Be Thou")
            assertTrue(hasBackToLive())

            onNodeWithText(backToLive).performClick()
            waitForIdle()

            assertTrue(!hasBackToLive(), "with the live song selected again there is nowhere to go")
        }

    @Test
    fun `clearing the search does not by itself put the selection back`() =
        songsTab(isPresenting = true) { _, _ ->
            goLiveWith("Amazing Grace")
            search("Be Thou")

            search("")

            // Worth pinning because it is the opposite of what one might assume: restoring the full
            // list does not move the selection back onto the live song, so the offer to return
            // stands until the operator takes it. The alternative — silently reselecting on every
            // search clear — would yank the tab away from a song they were deliberately queueing up.
            assertTrue(hasBackToLive(), "the way back must stay offered until it is taken")
        }

    @Test
    fun `not presenting means the button stays away even with the live song out of view`() =
        songsTab(isPresenting = false) { _, _ ->
            goLiveWith("Amazing Grace")
            search("Be Thou")

            // Nothing is on the screen, so there is nothing to be "back" to — the lyric pane
            // following the selection is simply what the operator asked for.
            assertTrue(!hasBackToLive(), "the button belongs to presenting, not to browsing")
        }

    // ── What it does ────────────────────────────────────────────────────────────

    @Test
    fun `pressing it reselects the live song`() = songsTab(isPresenting = true) { vm, _ ->
        goLiveWith("Amazing Grace")
        val liveId = vm.filteredSongItems.value[vm.selectedSongIndex.value].songId

        search("Be Thou")
        onNodeWithText(backToLive).performClick()
        waitForIdle()

        assertEquals(
            liveId,
            vm.filteredSongItems.value.getOrNull(vm.selectedSongIndex.value)?.songId,
            "the selection must land back on the song that is live",
        )
    }

    @Test
    fun `pressing it restores the live section and line, not just the song`() =
        songsTab(isPresenting = true) { vm, _ ->
            goLiveWith("Amazing Grace")
            // Step within the live song so the live position is not the song's first line — that is
            // the whole point of restoring position rather than just reselecting.
            vm.selectSection(0)
            vm.setLineIndex(0)
            waitForIdle()
            val liveSection = vm.selectedSectionIndex.value
            val liveLine = vm.selectedLineIndex.value

            search("Be Thou")
            waitForIdle()
            onNodeWithText(backToLive).performClick()
            waitForIdle()

            assertEquals(liveSection, vm.selectedSectionIndex.value, "the live section must come back")
            assertEquals(liveLine, vm.selectedLineIndex.value, "and the live line with it")
        }
}
