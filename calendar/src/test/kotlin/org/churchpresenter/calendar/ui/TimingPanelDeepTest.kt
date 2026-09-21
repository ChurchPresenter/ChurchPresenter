@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The row editor's timing panel — every way a row can be made to run by itself.
 *
 * Each chip writes one field of [RowTiming] and the panel's last line says what the combination
 * means, which is the only place a planner sees the four fields read as a sentence. Driven through
 * the window and asserted against `calendar.json`, because "saved as you change it" is a claim
 * about the file rather than about the panel.
 */
class TimingPanelDeepTest {

    private fun stored(folder: File) = CalendarStore(folder).load().document
    private fun timingOf(folder: File) = stored(folder).services.single().timing["a"]

    /**
     * The row's length, which the panel's **Runs** chips write.
     *
     * Not `RowTiming.runSeconds`: a row's length is its planned length, the same number the
     * duration cell edits, and the timing carries only when it starts, how often and what then --
     * see [TimingDraft.toTiming].
     */
    private fun lengthOf(folder: File) = stored(folder).services.single().plannedSeconds["a"]

    /** Clicks a chip of the open editor — exact text, and only inside the sheet. */
    private fun ComposeUiTest.chip(label: String) = clickInSheet(label, anchor = "Editing")

    private fun withRow(body: ComposeUiTest.(folder: File) -> Unit) =
        withCalendar(documentWith(service())) { folder ->
            awaitText("Amazing Grace")
            clickFirst("Amazing Grace")
            awaitText("Editing")
            body(folder)
        }

    // ── Starts ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a row starts on the service's own time`() = withRow { folder ->
        chip("On time")

        assertEquals("10:00", timingOf(folder)?.startAt)
    }

    @Test
    fun `a row starts a set number of minutes before the service`() = withRow { folder ->
        chip("−15")

        assertEquals("09:45", timingOf(folder)?.startAt, "a quarter of an hour before 10:00")
    }

    @Test
    fun `a row waits its turn instead of taking a time`() = withRow { folder ->
        chip("After previous")

        assertTrue(timingOf(folder)?.followsPrevious == true)
        assertTrue(timingOf(folder)?.startAt.isNullOrEmpty(), "and holds no clock time of its own")
    }

    @Test
    fun `a row set back to cued forgets its time`() = withRow { folder ->
        chip("On time")
        chip("Cued")

        assertNull(timingOf(folder), "nothing left to say is nothing stored")
    }

    /** The clock field beside the offset chips: search box, minutes-before, then this one. */
    @Test
    fun `a row's start can be typed as a clock time`() = withRow { folder ->
        typeIntoFieldAt(index = 2, text = "09:30")
        chip("RUNS")

        assertEquals("09:30", timingOf(folder)?.startAt)
    }

    // ── Runs, repeats and the end ───────────────────────────────────────────────────────────────

    @Test
    fun `a run length can be picked from the chips`() = withRow { folder ->
        chip("15m")

        assertEquals(900, lengthOf(folder))
    }

    @Test
    fun `a row can run for as long as the item itself lasts`() = withRow { folder ->
        chip("15m")
        chip("Its own")

        assertNull(lengthOf(folder), "no number to count means the item decides")
    }

    @Test
    fun `a row can be set to loop, to play twice, or to play a typed number of times`() = withRow { folder ->
        chip("Loop")
        assertEquals(0, timingOf(folder)?.repeats, "0 plays is loop")

        chip("2")
        assertEquals(2, timingOf(folder)?.repeats)

        chip("Once")
        assertNull(timingOf(folder), "playing once is the default, and a default is not stored")
    }

    @Test
    fun `what happens at the end is one of three things`() = withRow { folder ->
        chip("Next item")
        assertEquals(RowEnd.NEXT, timingOf(folder)?.atEnd)

        chip("Blank")
        assertEquals(RowEnd.BLANK, timingOf(folder)?.atEnd)

        chip("Hold")
        assertNull(timingOf(folder), "holding once is the default, and a default is not stored")
    }

    // ── What the panel says it all means ────────────────────────────────────────────────────────

    @Test
    fun `the panel reads the four fields back as a sentence`() = withRow {
        chip("On time")
        chip("15m")
        chip("Next item")

        assertTrue(shows("Starts on its own"), "when it starts")
        assertTrue(shows("runs"), "how long it runs")
        assertTrue(shows("then advances"), "and what it does at the end")
    }

    @Test
    fun `a looping row says it loops rather than giving a number`() = withRow {
        chip("Loop")

        assertTrue(shows("loops"))
    }

    @Test
    fun `a row nothing hands to is marked as stranded`() =
        withCalendar(
            documentWith(
                service(
                    items = listOf(song("a", "Amazing Grace"), song("b", "Be Thou My Vision")),
                    timing = mapOf("b" to RowTiming(followsPrevious = true)),
                )
            )
        ) {
            awaitText("Be Thou My Vision")

            // The chip is drawn in the error tone and its tooltip says nothing hands to this row;
            // the tooltip is a hover popup, so what a test can see is the chip itself. That the
            // row *is* stranded is `followsWithoutHandoff`, pinned in RunClockLayoutTest.
            assertTrue(shows("After previous"))
        }
}
