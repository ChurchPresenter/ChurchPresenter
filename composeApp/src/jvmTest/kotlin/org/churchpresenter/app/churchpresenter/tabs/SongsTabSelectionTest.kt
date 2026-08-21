@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Choosing a song in the Songs tab, and what the tab hands the rest of the app when you do.
 *
 * Picking a row is the moment everything downstream depends on: the lyric pane fills, the schedule
 * gets the right song, the presenter is handed a section. A row that selects the *wrong* song is not
 * a cosmetic fault — it puts the wrong words on the screen. So these assert the identity of what
 * comes back, not merely that a callback fired.
 *
 * `SongsTab` reports the chosen section through `onSongItemSelected`; the harness captures it, which
 * is why the assertions can name a title and a verse line rather than a call count.
 */
class SongsTabSelectionTest {

    /** Clicks the row showing [title]. Rows are addressed by the title they display. */
    private fun ComposeUiTest.clickRow(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
    }

    /**
     * Presses Go Live.
     *
     * Addressed by the icon's content description, not by a text match: "Go Live" as *text* is the
     * tooltip, which exists only while hovered, so matching it finds a node that cannot take a click.
     * (Note the capital L — `PrimaryActionButtonsTest` uses "Go live" because it passes its own
     * tooltip text; here the description comes from the `go_live` string resource.)
     */
    private fun ComposeUiTest.goLive() {
        onAllNodes(hasContentDescription("Go Live"))[0].performClick()
        waitForIdle()
    }

    // ── Selecting a song ────────────────────────────────────────────────────────

    @Test
    fun `a song is selected as soon as the tab opens, so the lyric pane is never blank`() =
        songsTab { vm, _ ->
            assertEquals(0, vm.selectedSongIndex.value, "the first row is selected for you")
            assertNotNull(vm.getSelectedLyricSection(), "and it has a section ready to show")
        }

    @Test
    fun `clicking a row selects that song for preview`() = songsTab { vm, _ ->
        clickRow("Be Thou My Vision")

        assertEquals(
            "Be Thou My Vision",
            vm.filteredSongItems.value[vm.selectedSongIndex.value].title,
            "the clicked row becomes the current song",
        )
        assertEquals(
            "Be Thou My Vision",
            vm.getSelectedLyricSection()?.title,
            "and the lyric pane follows it, carrying that song's own section",
        )
    }

    @Test
    fun `the title-slide card omits the number when that setting is disabled`() =
        songsTab(
            songSettings = SongSettings(
                titleSlideEnabled = true,
                titleSlideShowSongNumber = false,
            ),
        ) { _, _ ->
            assertTrue(shows("Amazing Grace"), "the stored song title must still be shown")
            assertTrue(
                !shows("1 – Amazing Grace"),
                "the preview must match the number-free title slide sent to the presenter",
            )
        }

    @Test
    fun `selecting a row does NOT push anything to the presenter`() = songsTab { _, reports ->
        // The safety property behind the whole tab: browsing the library during a service must not
        // change what the congregation is looking at. Going live is a separate, deliberate action
        // (Go Live, a lyric-line click, or keyboard navigation) — all of which route through
        // sendToPresenter(); merely selecting a row does not.
        clickRow("Be Thou My Vision")
        clickRow("Amazing Love")

        assertEquals(null, reports.selectedSection, "no section may reach the presenter from a preview")
        assertEquals(emptyList(), reports.allSections, "and no section list either")
    }

    @Test
    fun `going live hands the presenter the selected song's section`() = songsTab { _, reports ->
        clickRow("Amazing Love")
        goLive()

        val section = reports.selectedSection
        assertNotNull(section, "Go Live is what pushes to the presenter")
        assertEquals("Amazing Love", section.title, "and it must be the song that was selected")
        assertEquals(12, section.songNumber, "carrying the number the schedule and statistics key on")
    }

    @Test
    fun `selecting a different song replaces the previous selection rather than adding to it`() =
        songsTab { vm, _ ->
            clickRow("Be Thou My Vision")
            clickRow("Amazing Grace")

            assertEquals(
                "Amazing Grace",
                vm.filteredSongItems.value[vm.selectedSongIndex.value].title,
                "only the last clicked row is current",
            )
            assertEquals("Amazing Grace", vm.getSelectedLyricSection()?.title)
        }

    @Test
    fun `a song selected before a search survives it being filtered away and back`() =
        songsTab { vm, _ ->
            clickRow("Be Thou My Vision")
            search("Amazing")
            // "Be Thou My Vision" is no longer listed; whatever the tab does it must not crash and
            // must leave a valid selection behind for the lyric pane.
            search("")
            assertTrue(
                vm.selectedSongIndex.value in vm.filteredSongItems.value.indices,
                "the selection index must stay inside the list it points at",
            )
        }

    @Test
    fun `clicking a different row while presenting live drops the live section highlight`() =
        songsTab(isPresenting = true) { vm, _ ->
            clickRow("Amazing Grace")
            goLive()
            assertTrue(vm.selectedSectionIndex.value >= 0, "a section is live to begin with")

            clickRow("Be Thou My Vision")

            assertEquals(
                "Be Thou My Vision",
                vm.filteredSongItems.value[vm.selectedSongIndex.value].title,
                "the click must still move the preview to the row that was clicked",
            )
            assertEquals(
                -1,
                vm.selectedSectionIndex.value,
                "browsing away from the live song while presenting must not leave a stale section highlighted",
            )
        }

    @Test
    fun `clicking a different row while nothing is live keeps whatever section it lands on`() =
        songsTab(isPresenting = true) { vm, _ ->
            clickRow("Amazing Grace")
            // Deliberately not going live: liveSongId stays null, so the -1 reset below must not fire.

            clickRow("Be Thou My Vision")

            assertTrue(
                vm.selectedSectionIndex.value >= 0,
                "with nothing live yet, selecting a row must not blank out its section",
            )
        }

    // ── Favourites ──────────────────────────────────────────────────────────────

    @Test
    fun `no song is a favourite to begin with`() = songsTab { vm, _ ->
        assertEquals(emptySet(), vm.favorites.value)
    }

    @Test
    fun `clicking a row's star records that song as a favourite`() = songsTab { vm, _ ->
        // Driven through the UI rather than the model, so the wiring from the star to the view model
        // is covered too. The rows are in screen order, so the first star is the first row's.
        val firstRowTitle = listedTitles().first()
        val expectedId = vm.filteredSongItems.value.first { it.title == firstRowTitle }.songId

        onAllNodes(hasContentDescription("Add to favorites"))[0].performClick()
        waitForIdle()

        assertEquals(setOf(expectedId), vm.favorites.value, "the star belongs to the row it sits on")
    }

    @Test
    fun `starring a song records it by its stable id`() = songsTab { vm, _ ->
        val song = vm.filteredSongItems.value.first { it.title == "Amazing Grace" }
        vm.toggleFavorite(song.songId)

        assertEquals(
            setOf(song.songId),
            vm.favorites.value,
            "favourites key on songId so they survive a rename or a re-index",
        )
    }

    @Test
    fun `starring twice takes the star off again`() = songsTab { vm, _ ->
        val id = vm.filteredSongItems.value.first().songId
        vm.toggleFavorite(id)
        vm.toggleFavorite(id)
        assertEquals(emptySet(), vm.favorites.value, "the star is a toggle, not a one-way flag")
    }

    @Test
    fun `favouriting one song leaves the others alone`() = songsTab { vm, _ ->
        val songs = vm.filteredSongItems.value
        vm.toggleFavorite(songs[0].songId)
        assertEquals(setOf(songs[0].songId), vm.favorites.value)
        assertTrue(songs[1].songId !in vm.favorites.value)
    }

    // ── Adding to the schedule ──────────────────────────────────────────────────

    @Test
    fun `the add-to-schedule action sends the selected song to the schedule`() = songsTab { _, reports ->
        clickRow("Amazing Love")
        // By tag, not by label: several controls are correctly named "Add to Schedule" — the toolbar
        // button, one per table row, one per favourites entry — and only the tagged one adds the
        // song that is *selected*, which is what this asserts.
        onNodeWithTag(SONGS_ADD_SELECTED_TAG).performClick()
        waitForIdle()

        assertEquals(
            listOf("Amazing Love"),
            reports.scheduled,
            "the song added must be the one selected, not whichever was first",
        )
    }

    @Test
    fun `nothing reaches the schedule until it is asked for`() = songsTab { _, reports ->
        clickRow("Amazing Grace")
        assertEquals(
            emptyList(),
            reports.scheduled,
            "merely selecting a song to preview it must not add it to the service",
        )
    }

    // ── Sections handed onward ──────────────────────────────────────────────────

    @Test
    fun `going live publishes the full section list, not just the current section`() = songsTab { _, reports ->
        clickRow("Amazing Grace")
        goLive()

        val sections = reports.allSections.lastOrNull()
        assertNotNull(sections, "the stage monitor and the look-ahead need every section")
        assertTrue(sections.isNotEmpty())
        assertTrue(
            sections.all { it.title == "Amazing Grace" },
            "every section must belong to the live song; got ${sections.map { it.title }.distinct()}",
        )
    }

    @Test
    fun `a song with one verse still goes live with a section`() =
        songsTab(songs = listOf(SongFixture(
            number = "1",
            title = "Single",
            lyrics = listOf("[Verse 1]", "one line"),
        ))) { _, reports ->
            clickRow("Single")
            goLive()
            assertNotNull(reports.selectedSection, "a one-verse song is still a song")
        }

    @Test
    fun `Go Live is disabled until a section has actually been chosen`() =
        songsTab(songs = listOf(SongFixture(number = "1", title = "Single"))) { vm, reports ->
            // On open the tab pre-selects the first *song* and shows its lyrics, but no *section* —
            // `hasSongSelected` requires both. So Go Live is greyed out even though the pane looks
            // ready, and pressing it does nothing. That is a deliberate gate, not an oversight: it
            // takes a positive act to put words in front of a congregation, so the tab will not go
            // live off a selection the operator never made.
            assertNotNull(vm.getSelectedLyricSection(), "a song is pre-selected and its lyrics are shown")

            onAllNodes(hasContentDescription("Go Live"))[0].assertIsNotEnabled()
            goLive()
            assertEquals(null, reports.selectedSection, "a disabled Go Live pushes nothing")

            clickRow("Single")
            onAllNodes(hasContentDescription("Go Live"))[0].assertIsEnabled()
            goLive()
            assertNotNull(reports.selectedSection, "choosing the row is what arms it")
        }
}
