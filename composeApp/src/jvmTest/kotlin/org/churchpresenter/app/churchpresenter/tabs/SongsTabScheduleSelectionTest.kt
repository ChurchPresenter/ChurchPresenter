@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.songs.SongItem
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Clicking a song in the schedule, and what the Songs tab does about it.
 *
 * The schedule is where a service is driven from, so this is the ordinary way a song is put on
 * screen: the operator clicks the row, the tab finds that song in the library and hands its first
 * section to the presenter. Getting it wrong is quiet — the row highlights, and nothing reaches the
 * output.
 *
 * Two details of the wiring are the reason this is worth pinning:
 *
 * - The row can be clicked **before the library has finished loading**, which is normal on a cold
 *   start. The tab waits for the load rather than looking the song up in an empty catalogue and
 *   giving up.
 * - The same row can be clicked **twice**. Compose would skip the second, so the caller bumps a
 *   version alongside the item; without it, taking a song live, moving away and coming back to the
 *   same row would do nothing the second time.
 */
class SongsTabScheduleSelectionTest {

    /** A schedule row for one of [defaultSongs], as the schedule builds them. */
    private fun row(number: Int, title: String, songbook: String = "Hymnal") =
        ScheduleItem.SongItem(
            id = "schedule-$number",
            songNumber = number,
            title = title,
            songbook = songbook,
        )

    @Test
    fun `clicking a scheduled song hands its lyrics to the presenter`() {
        val selection = mutableStateOf<ScheduleItem.SongItem?>(null)
        songsTab(scheduleSelection = selection) { vm, reports ->
            waitForIdle()
            assertNull(reports.selectedSection, "nothing is live until a row is clicked")

            selection.value = row(1, "Amazing Grace")
            waitForIdle()

            val selected = vm.filteredSongItems.value.getOrNull(vm.selectedSongIndex.value)
            assertEquals("Amazing Grace", selected?.title, "the library selection follows")
            assertEquals("Amazing Grace", reports.selectedSection?.title, "and its lyrics go to the presenter")
        }
    }

    @Test
    fun `a song in another songbook is found by its own book, not the first match on number`() {
        val selection = mutableStateOf<ScheduleItem.SongItem?>(null)
        songsTab(scheduleSelection = selection) { vm, _ ->
            waitForIdle()

            // "3" exists only in Chorus Book; the Hymnal has 1, 2 and 12. Resolving by number alone
            // would be ambiguous the moment two books number their songs the same way.
            selection.value = row(3, "How Great Thou Art", songbook = "Chorus Book")
            waitForIdle()

            val selected = vm.filteredSongItems.value.getOrNull(vm.selectedSongIndex.value)
            assertEquals("How Great Thou Art", selected?.title)
            assertEquals("Chorus Book", selected?.songbook)
        }
    }

    @Test
    fun `clicking the same row again re-fires it`() {
        val selection = mutableStateOf<ScheduleItem.SongItem?>(null)
        val version = mutableStateOf(0)
        songsTab(scheduleSelection = selection, scheduleSelectionVersion = version) { _, reports ->
            waitForIdle()
            selection.value = row(1, "Amazing Grace")
            waitForIdle()
            val afterFirst = reports.allSections.size

            // The item is identical, so only the version differs — which is the whole reason it is
            // passed. Without it Compose skips the effect and the second click does nothing.
            version.value = 1
            waitForIdle()

            assertEquals(afterFirst + 1, reports.allSections.size, "the second click reached the presenter too")
        }
    }

    @Test
    fun `a scheduled song the library does not have reaches the presenter as nothing`() {
        val selection = mutableStateOf<ScheduleItem.SongItem?>(null)
        songsTab(scheduleSelection = selection) { _, reports ->
            waitForIdle()

            selection.value = row(999, "A Song Removed From The Library")
            waitForIdle()

            // A song deleted from the library after the service was planned must leave the output
            // alone rather than push whatever happened to be selected.
            assertNull(reports.selectedSection, "no match means nothing is sent")
        }
    }
}
