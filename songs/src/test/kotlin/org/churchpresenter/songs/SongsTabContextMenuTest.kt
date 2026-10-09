@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The song list's right-click menu.
 *
 * Everything the operator can do to a song without leaving the list lives here — schedule it, star
 * it, edit it, delete it, put it live — and none of it had been exercised, because the menu only
 * opens on a *secondary* mouse press and every existing test drives ordinary clicks.
 *
 * The two rules worth pinning are about which song the menu is acting on: it has to be the row that
 * was right-clicked, not the row that happens to be selected, and the favourite item has to name the
 * action it will perform rather than the state the song is in. Getting either wrong schedules or
 * deletes the wrong song, which is only discovered later.
 */
class SongsTabContextMenuTest {

    /** Right-clicks the row for [title], opening its context menu. */
    private fun ComposeUiTest.openMenuOn(title: String) {
        onNodeWithText(title).performMouseInput { rightClick() }
        waitForIdle()
    }

    /**
     * Clicks a menu item.
     *
     * Matched as the *last* node with that label: an item's text can repeat a label the list itself
     * already shows ("Go Live" is also a toolbar button), and the popup is composed after the list.
     */
    private fun ComposeUiTest.clickMenuItem(label: String) {
        val nodes = onAllNodesWithText(label)
        nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    // ── Opening it ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a right-click opens the menu with every action`() {
        songsTab { _, _ ->
            openMenuOn("Amazing Grace")

            assertTrue(showsItem(SongsMenu.GO_LIVE), rendered().toString())
            assertTrue(showsItem(SongsMenu.ADD_TO_SCHEDULE))
            assertTrue(showsItem(SongsMenu.ADD_TO_FAVORITES))
            assertTrue(showsItem(SongsMenu.EDIT))
            assertTrue(showsItem(SongsMenu.DELETE))
        }
    }

    @Test
    fun `the header names the song that was right-clicked`() {
        songsTab { _, _ ->
            openMenuOn("Be Thou My Vision")

            assertTrue(shows("Hymnal · 2"), rendered().toString())
        }
    }

    @Test
    fun `Go Live leads the menu and Delete closes it`() {
        songsTab { _, _ ->
            openMenuOn("Amazing Grace")

            val items = rendered().filter { line -> SongsMenu.ORDER.any { line.startsWith(it) } }
            assertTrue(items.first().startsWith(SongsMenu.GO_LIVE), items.toString())
            assertEquals(SongsMenu.DELETE, items.last())
        }
    }

    @Test
    fun `Go Live and Add to Schedule show their keys`() {
        songsTab { _, _ ->
            openMenuOn("Amazing Grace")

            assertTrue(shows(SongsMenu.GO_LIVE + "Enter"), rendered().toString())
            assertTrue(shows(SongsMenu.ADD_TO_SCHEDULE + "F2"))
        }
    }

    @Test
    fun `an ordinary click does not open it`() {
        songsTab { _, _ ->
            onNodeWithText("Amazing Grace").performClick()
            waitForIdle()

            assertFalse(showsItem(SongsMenu.EDIT), rendered().toString())
        }
    }

    // ── Scheduling ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the menu schedules the song it was opened on`() {
        songsTab { _, reports ->
            // Deliberately not the first row: the menu must act on the row that was right-clicked.
            openMenuOn("Be Thou My Vision")
            clickMenuItem(SongsMenu.ADD_TO_SCHEDULE)

            assertEquals(listOf("Be Thou My Vision"), reports.scheduled)
        }
    }

    @Test
    fun `scheduling closes the menu`() {
        songsTab { _, _ ->
            openMenuOn("Amazing Grace")
            clickMenuItem(SongsMenu.ADD_TO_SCHEDULE)

            assertFalse(showsItem(SongsMenu.EDIT), "the menu should be gone: ${rendered()}")
        }
    }

    // ── Favourites ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the menu stars a song`() {
        songsTab { vm, _ ->
            assertTrue(vm.favorites.value.isEmpty())

            openMenuOn("Amazing Grace")
            clickMenuItem(SongsMenu.ADD_TO_FAVORITES)

            assertEquals(1, vm.favorites.value.size, "the star has to stick to the song")
        }
    }

    @Test
    fun `a starred song is offered the opposite action`() {
        songsTab { vm, _ ->
            openMenuOn("Amazing Grace")
            clickMenuItem(SongsMenu.ADD_TO_FAVORITES)

            openMenuOn("Amazing Grace")

            // The item names what the click will do, not what the song currently is.
            assertTrue(shows(SongsMenu.REMOVE_FROM_FAVORITES), rendered().toString())
            assertFalse(shows(SongsMenu.ADD_TO_FAVORITES))

            clickMenuItem(SongsMenu.REMOVE_FROM_FAVORITES)
            assertTrue(vm.favorites.value.isEmpty())
        }
    }

    @Test
    fun `starring one song leaves the others alone`() {
        songsTab { vm, _ ->
            openMenuOn("Amazing Grace")
            clickMenuItem(SongsMenu.ADD_TO_FAVORITES)

            openMenuOn("Be Thou My Vision")

            assertTrue(shows(SongsMenu.ADD_TO_FAVORITES), "this one is not starred: ${rendered()}")
            assertEquals(1, vm.favorites.value.size)
        }
    }

    // ── Going live ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the menu puts the song live`() {
        songsTab { _, reports ->
            openMenuOn("Amazing Grace")
            clickMenuItem(SongsMenu.GO_LIVE)

            assertTrue(
                reports.allSections.isNotEmpty(),
                "going live has to hand the presenter the song's sections",
            )
            assertEquals(0, reports.sectionIndex, "and start at the first one")
        }
    }

    /** A menu row's text is its label, then its key when it has one ("Add to ScheduleF2"). */
    private fun ComposeUiTest.showsItem(label: String) = rendered().any { it.startsWith(label) }

    private object SongsMenu {
        const val ADD_TO_SCHEDULE = "Add to Schedule"
        const val ADD_TO_FAVORITES = "Add to favorites"
        const val REMOVE_FROM_FAVORITES = "Remove from favorites"
        const val EDIT = "Edit Song"
        const val DELETE = "Delete"
        const val GO_LIVE = "Go Live"
        val ORDER = listOf(GO_LIVE, ADD_TO_SCHEDULE, ADD_TO_FAVORITES, REMOVE_FROM_FAVORITES, EDIT, DELETE)
    }
}
