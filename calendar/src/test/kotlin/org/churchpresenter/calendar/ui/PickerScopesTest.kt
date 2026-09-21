@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the picker narrows what it offers: the song-book scope and the preset kinds.
 *
 * Both are the same idea — a row of chips over a list, each carrying how many it would leave — and
 * both are decided by a `when` over what an item *is*, so a kind nobody exercised is a chip that
 * quietly counts nothing. A library of several books and a preset of every kind put all of them on
 * screen at once.
 */
class PickerScopesTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private fun ComposeUiTest.openPicker() {
        awaitText("Sunday Morning")
        clickFirst("Add song, verse or section")
        awaitText("Songs")
    }

    // ── Song books ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the scope opens on every song book, with how many each holds`() {
        val songs = songFolderWith(
            libraSong("1", "Amazing Grace"),
            libraSong("2", "Be Thou My Vision"),
            libraSong("1", "Here I Am to Worship", songbook = "Chorus Book"),
            libraSong("7", "Silent Night", songbook = "Carols"),
        )
        try {
            withCalendar(documentWith(service()), songFolder = songs) {
                openPicker()
                awaitText("Amazing Grace")

                clickFirst("All song books")

                assertTrue(shows("Hymns"), "each book is offered")
                assertTrue(shows("Chorus Book"))
                assertTrue(shows("Carols"))
            }
        } finally {
            songs.deleteRecursively()
        }
    }

    @Test
    fun `picking one book leaves only its songs`() {
        val songs = songFolderWith(
            libraSong("1", "Amazing Grace"),
            libraSong("1", "Here I Am to Worship", songbook = "Chorus Book"),
        )
        try {
            withCalendar(documentWith(service()), songFolder = songs) {
                openPicker()
                awaitText("Here I Am to Worship")

                clickFirst("All song books")
                clickInSheetContaining("Chorus Book")

                assertTrue(showsInSheet("Here I Am to Worship"))
                // In the sheet: the run of show behind it holds an "Amazing Grace" row of its own.
                assertTrue(!showsInSheet("Amazing Grace"), "the other book is put away")
            }
        } finally {
            songs.deleteRecursively()
        }
    }

    @Test
    fun `a search that matches nothing says so, and says what to try`() {
        val songs = songFolderWith(libraSong("1", "Amazing Grace"))
        try {
            withCalendar(documentWith(service()), songFolder = songs) {
                openPicker()
                awaitText("Amazing Grace")

                typeIntoFirstField("bagpipes")

                assertTrue(shows("Nothing matches that"))
                assertTrue(shows("Try a different word"), "and what might work instead")
            }
        } finally {
            songs.deleteRecursively()
        }
    }

    @Test
    fun `with no song folder at all the tab says the library is empty rather than loading forever`() =
        withCalendar(documentWith(service()), songFolder = null) {
            openPicker()

            assertTrue(shows("Nothing matches that") || shows("Try a different word"))
        }

    // ── Preset kinds ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every kind of preset is counted under its own chip`() = withCalendar(documentWith(service())) { folder ->
        seedPresets(folder, *EVERY_KIND_OF_PRESET)
        openPicker()
        clickFirst("Presets")
        awaitText("Slideshow")

        listOf("Slideshows", "Presentations", "Media", "Timers", "Announcements", "Scenes", "Lower thirds", "Other")
            .forEach { assertTrue(shows(it), "$it has no chip") }
        assertTrue(shows("All"), "and one for the lot")
    }

    @Test
    fun `a kind chip leaves only that kind, and All brings the rest back`() =
        withCalendar(documentWith(service())) { folder ->
            seedPresets(folder, *EVERY_KIND_OF_PRESET)
            openPicker()
            clickFirst("Presets")
            awaitText("Slideshow")

            clickInSheetContaining("Scenes")
            assertTrue(showsInSheet("Scene preset"))
            assertTrue(!showsInSheet("Slideshow preset"), "the other kinds are put away")

            // In the sheet: the run of show's own footer says "All manual".
            clickInSheetContaining("All  ")

            // A preset of another kind is back. Not the *first* preset: the list keeps the scroll
            // it had while filtered, so what was at the top is above the fold once all eight
            // return -- which is a fact about the list, not about the filter.
            assertTrue(showsInSheet("Announcement preset"), "and All brings the rest back")
        }

    @Test
    fun `a preset goes into the run of show under the name it was saved as`() =
        withCalendar(documentWith(service())) { folder ->
            seedPresets(folder, *EVERY_KIND_OF_PRESET)
            openPicker()
            clickFirst("Presets")
            awaitText("Slideshow preset")

            // Searched for rather than scrolled to: eight presets do not all fit at once.
            typeIntoFirstField("Scene")
            awaitText("Scene preset")
            clickFirst("Scene preset")
            waitForIdle()

            val added = stored(folder).services.single().items.last()
            assertEquals("Scene preset", added.displayText, "and not `Scene: …`, which is what the item says")
        }

    // ── What the sheet is adding to ─────────────────────────────────────────────────────────────

    @Test
    fun `the sheet says which service a pick will land in`() = withCalendar(documentWith(service())) {
        openPicker()

        assertTrue(shows("Sunday Morning"), "the service it adds to, in the footer")
    }

    @Test
    fun `opening the picker on a row says it is replacing that row`() = withCalendar(documentWith(service())) {
        awaitText("Amazing Grace")

        clickFirst("Amazing Grace")
        awaitText("Editing")

        assertTrue(shows("Amazing Grace"), "the row being replaced is named in the header")
    }

    private companion object {
        /**
         * One preset of every kind the chips can count.
         *
         * `presetKindOf` is a `when` over the item, so this list is the argument for the chip row
         * being right: each entry here should produce its own chip with a count of one.
         */
        val EVERY_KIND_OF_PRESET = arrayOf(
            ItemPreset(
                id = "p1", name = "Slideshow preset",
                item = ScheduleItem.PictureItem("i1", "/pics", "Welcome", 12),
            ),
            ItemPreset(
                id = "p2", name = "Deck preset",
                item = ScheduleItem.PresentationItem("i2", "/deck.pptx", "Sermon", 8, "pptx"),
            ),
            ItemPreset(
                id = "p3", name = "Clip preset",
                item = ScheduleItem.MediaItem("i3", "/clip.mp4", "Testimony", "local"),
            ),
            ItemPreset(
                id = "p4", name = "Timer preset",
                item = ScheduleItem.AnnouncementItem("i4", "", isTimer = true, timerMinutes = 5),
            ),
            ItemPreset(
                id = "p5", name = "Announcement preset",
                item = ScheduleItem.AnnouncementItem("i5", "Fellowship lunch"),
            ),
            ItemPreset(
                id = "p6", name = "Scene preset",
                item = ScheduleItem.SceneItem("i6", "scene-1", "Bible with Background"),
            ),
            ItemPreset(
                id = "p7", name = "Lower third preset",
                item = ScheduleItem.LowerThirdItem("i7", "preset-1", "Pastor Ruth", false, 0),
            ),
            ItemPreset(
                id = "p8", name = "Website preset",
                item = ScheduleItem.WebsiteItem("i8", "https://example.org"),
            ),
        )
    }
}
