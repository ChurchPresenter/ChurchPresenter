@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a run-of-show row draws, over every kind of row there is.
 *
 * A row's icon, its second line, its chips and its clock are each decided by a `when` over the whole
 * `ScheduleItem` hierarchy, and a row drawn for a kind nobody exercised is a row nobody would notice
 * was wrong. [everyKind] puts one of each into a service so all of them are drawn at once.
 */
class RunOfShowRowsTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document

    private fun everyKind() = service(
        items = listOf(
            heading("h1", "Pre-Service"),
            ScheduleItem.PictureItem("pics", "/photos", "Welcome loop", 24),
            ScheduleItem.CueItem(id = "cue", action = CueAction.GO_LIVE, absoluteTime = "09:58"),
            song("a", "Amazing Grace"),
            ScheduleItem.BibleVerseItem("v", "Psalms", 100, 1, "Make a joyful noise", verseRange = "1-5"),
            ScheduleItem.PresentationItem("deck", "/deck.pptx", "Sermon", 18, "pptx"),
            ScheduleItem.MediaItem("clip", "/clip.mp4", "Testimony", "local"),
            ScheduleItem.SceneItem("scene", "scene-1", "Bible with Background"),
            ScheduleItem.AnnouncementItem("ann", "Fellowship lunch"),
            ScheduleItem.WebsiteItem("web", "https://example.org/give", "Giving page"),
            ScheduleItem.LowerThirdItem("third", "preset-1", "Pastor Ruth", false, 0),
            ScheduleItem.DictionaryItem("dict", "G26", "agapē", "agape", "love"),
        ),
        planned = mapOf("pics" to 600, "a" to 300, "deck" to 1920),
        timing = mapOf(
            "pics" to RowTiming(startAt = "09:45", repeats = 0, atEnd = RowEnd.NEXT),
            "a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT),
            "deck" to RowTiming(followsPrevious = true, atEnd = RowEnd.BLANK),
            "clip" to RowTiming(repeats = 2),
        ),
    )

    @Test
    fun `every kind of row is drawn with its own second line`() = withCalendar(documentWith(everyKind())) {
        awaitText("Amazing Grace")

        listOf(
            "Welcome loop", "Psalms 100:1-5", "Sermon", "Testimony", "Bible with Background",
            "Fellowship lunch", "Giving page", "Pastor Ruth", "agape",
        ).forEach { assertTrue(shows(it), "$it is missing from the run of show") }
    }

    @Test
    fun `a row says how it runs, in chips`() = withCalendar(documentWith(everyKind())) {
        awaitText("Amazing Grace")

        assertTrue(shows("Loop"), "a row set to loop")
        assertTrue(shows("2×"), "and one set to play twice")
        assertTrue(shows("Next"), "what happens at the end")
        assertTrue(shows("Blank"))
        assertTrue(shows("After previous"), "and the row that waits its turn")
    }

    @Test
    fun `a section heading is a divider, and can be taken out on its own`() =
        withCalendar(documentWith(everyKind())) { folder ->
            awaitText("Pre-Service")

            clickIcon("Remove from the run of show")

            val items = stored(folder).services.single().items
            assertTrue(items.none { it is ScheduleItem.LabelItem }, "the heading went")
            assertTrue(items.any { it.id == "pics" }, "the rows under it stayed")
        }

    @Test
    fun `the header adds up what the service will do on its own`() = withCalendar(documentWith(everyKind())) {
        awaitText("Amazing Grace")

        assertTrue(shows("auto start"), "two rows start themselves, and the header says so")
    }

    // ── The duration cell ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a length typed onto a row is saved`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")

        clickFirst("5:00")
        clearFirstField()
        typeIntoFirstField("6:30")
        clickFirst("Sunday Morning")
        waitForIdle()

        assertEquals(390, stored(folder).services.single().plannedSeconds["a"])
    }

    /**
     * An unreadable length clears the estimate rather than keeping the old one.
     *
     * The cell is the one place a length is set, so there is nowhere for a half-typed value to
     * live: `finish` parses what is there and stores the answer, and `null` is a row with no
     * estimate — which is also what an empty cell means.
     */
    @Test
    fun `a length that cannot be read clears the estimate`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")

        clickFirst("5:00")
        clearFirstField()
        typeIntoFirstField("soon")
        clickFirst("Sunday Morning")
        waitForIdle()

        assertNull(stored(folder).services.single().plannedSeconds["a"])
    }

    @Test
    fun `clearing the cell drops the estimate`() = withCalendar(documentWith(service())) { folder ->
        awaitText("Amazing Grace")

        clickFirst("5:00")
        clearFirstField()
        clickFirst("Sunday Morning")
        waitForIdle()

        assertNull(stored(folder).services.single().plannedSeconds["a"])
    }

    // ── Moving rows ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a row can be moved down and back up`() = withCalendar(
        documentWith(service(items = listOf(song("a", "Amazing Grace"), song("b", "Be Thou My Vision"))))
    ) { folder ->
        awaitText("Amazing Grace")

        clickIcon("Move up")
        assertEquals(
            listOf("a", "b"), stored(folder).services.single().items.map { it.id },
            "the first row has nothing above it to swap with",
        )

        clickIcon("Move down")
        assertEquals(listOf("b", "a"), stored(folder).services.single().items.map { it.id })

        clickIconAt("Move up", index = 1)
        assertEquals(listOf("a", "b"), stored(folder).services.single().items.map { it.id })
    }

    @Test
    fun `a service with nothing in it still totals to nothing rather than to a guess`() =
        withCalendar(documentWith(service(items = emptyList(), planned = emptyMap()))) {
            awaitText("Sunday Morning")

            assertTrue(shows("0 items") || shows("Add song, verse or section"), "an empty plan reads as empty")
        }
}
