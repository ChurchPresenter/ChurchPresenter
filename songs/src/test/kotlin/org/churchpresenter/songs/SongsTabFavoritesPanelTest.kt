@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The favorites panel — the expandable strip at the bottom of the song list, grouping every starred
 * song for one-click reach regardless of what the search box currently filters to.
 *
 * `SongsTabSelectionTest`/`SongsTabContextMenuTest` cover starring itself; this covers the panel the
 * star actually populates, which no existing test opens or clicks into.
 *
 * See `SongsTabTestSupport.kt` for the harness.
 */
class SongsTabFavoritesPanelTest {

    @Test
    fun `starring a song makes the favorites panel appear`() = songsTab { vm, _ ->
        assertTrue(!shows("Favorites"), "nothing to show until something is starred")

        vm.toggleFavorite(vm.filteredSongItems.value.first { it.title == "Amazing Grace" }.songId)
        waitForIdle()

        assertTrue(shows("Favorites"), "the panel appears, expanded, as soon as there is something in it")
    }

    @Test
    fun `clicking a favorites-panel row selects that song`() = songsTab { vm, _ ->
        vm.toggleFavorite(vm.filteredSongItems.value.first { it.title == "Amazing Grace" }.songId)
        vm.toggleFavorite(vm.filteredSongItems.value.first { it.title == "How Great Thou Art" }.songId)
        waitForIdle()

        // The panel's own row text is "<number>. <title>", distinct from the plain title cell the
        // main list shows — "3. How Great Thou Art" identifies the favorites row uniquely.
        onNodeWithText("3. How Great Thou Art").performClick()
        waitForIdle()

        assertEquals(
            "How Great Thou Art",
            vm.filteredSongItems.value[vm.selectedSongIndex.value].title,
            "the favorites row must select the song it names",
        )
    }

    @Test
    fun `the favorites-panel add-to-schedule icon schedules that song`() = songsTab { vm, reports ->
        vm.toggleFavorite(vm.filteredSongItems.value.first { it.title == "Be Thou My Vision" }.songId)
        waitForIdle()

        // "Add to Schedule" also labels the toolbar's own button, always on screen above the list —
        // the favorites panel sits at the bottom, so its icon is whichever match has the larger top.
        val matches = onAllNodes(hasContentDescription(SongsLabel.ADD_TO_SCHEDULE)).fetchSemanticsNodes()
        val lowest = matches.indices.maxByOrNull { matches[it].boundsInRoot.top }
            ?: error("no \"${SongsLabel.ADD_TO_SCHEDULE}\" control is on screen")
        onAllNodes(hasContentDescription(SongsLabel.ADD_TO_SCHEDULE))[lowest].performClick()
        waitForIdle()

        assertEquals(listOf("Be Thou My Vision"), reports.scheduled)
    }

    @Test
    fun `with no schedule to add to, the favorites panel offers no add-to-schedule icon`() =
        songsTab(withOnAddToSchedule = false) { vm, _ ->
            vm.toggleFavorite(vm.filteredSongItems.value.first().songId)
            waitForIdle()

            assertTrue(
                onAllNodes(hasContentDescription(SongsLabel.ADD_TO_SCHEDULE))
                    .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
            )
        }

    @Test
    fun `clearing favorites empties the panel and the model`() = songsTab { vm, reports ->
        vm.toggleFavorite(vm.filteredSongItems.value.first().songId)
        vm.toggleFavorite(vm.filteredSongItems.value[1].songId)
        waitForIdle()
        assertEquals(2, vm.favorites.value.size)

        onNodeWithContentDescription("Clear favorites").performClick()
        waitForIdle()

        assertTrue(vm.favorites.value.isEmpty())
        assertEquals(emptyList(), reports.settingsAfterChange?.songFavorites)
        assertTrue(!shows("Favorites"), "the panel itself must go away with nothing left to show")
    }
}
