package org.churchpresenter.calendar.ui

import org.churchpresenter.calendar.model.RowSamples.EVERY_KIND
import org.churchpresenter.calendar.model.RowSamples.heading
import org.churchpresenter.calendar.model.RowSamples.song
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window's per-type passes over every kind of row: the second line a row shows and whether it
 * is a section heading. `RowShapesTest` holds the model's.
 */
class RowSubtitlesTest {

    @Test
    fun `every kind of row has a second line of its own`() {
        // Not what it says -- that a `when` over the hierarchy answers at all, for each of them.
        EVERY_KIND.forEach { row -> row.subtitle() }

        assertEquals("Hymns", song().subtitle())
        assertEquals("Welcome", ScheduleItem.SceneItem("s", "scene-1", "Welcome").subtitle())
        assertEquals("", heading().subtitle(), "a heading is its own title and nothing else")
    }

    @Test
    fun `a cue's second line is what it fires, or what it does`() {
        val withPayload = ScheduleItem.CueItem(id = "c", action = CueAction.PROJECT, payload = song())
        val bare = ScheduleItem.CueItem(id = "c", action = CueAction.BLANK)

        assertEquals(song().displayText, withPayload.subtitle())
        assertEquals(CueAction.BLANK, bare.subtitle())
    }

    @Test
    fun `a long verse is cut down to a second line`() {
        val verse = ScheduleItem.BibleVerseItem(
            id = "v", bookName = "John", chapter = 3, verseNumber = 16, verseText = "x".repeat(200),
        )

        assertEquals(60, verse.subtitle().length)
    }

    @Test
    fun `only a label is a section heading`() {
        assertTrue(heading().isSection())
        assertTrue(EVERY_KIND.filterNot { it is ScheduleItem.LabelItem }.none { it.isSection() })
    }
}
