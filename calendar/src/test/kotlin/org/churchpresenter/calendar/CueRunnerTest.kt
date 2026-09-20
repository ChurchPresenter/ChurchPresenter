package org.churchpresenter.calendar

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowEnd
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CueRunnerTest {

    private val today = LocalDate.of(2026, 9, 20)

    private fun at(hour: Int, minute: Int, second: Int = 0) =
        LocalDateTime.of(today, LocalTime.of(hour, minute, second))

    private fun song(id: String) = ScheduleItem.SongItem(
        id = id, songNumber = 1, title = "Song $id", songbook = "", songId = "",
    )

    /** What the engine did, in order: `project:<id>` and `blank`. */
    private class Outputs {
        val done = mutableListOf<String>()

        fun host(): CalendarHost = CalendarHost(
            projectItem = { item, plays -> done += "project:${item.id}x$plays" },
            blankOutputs = { done += "blank" },
        )
    }

    private fun runner(
        rows: List<ScheduleItem>,
        timing: Map<String, RowTiming>,
        outputs: Outputs,
        now: () -> LocalDateTime,
        armed: Boolean = true,
        operatorLive: () -> Boolean = { false },
    ) = CueRunner(
        items = { rows },
        armed = { armed },
        host = outputs.host(),
        timing = { timing },
        now = now,
        operatorLive = operatorLive,
    )

    private fun cue(id: String, minute: Int) = ScheduleItem.CueItem(
        id = id, action = CueAction.BLANK, absoluteTime = "10:%02d".format(minute),
    )

    @Test
    fun `a due row and cue are skipped, not fired, while the operator is live`() {
        val rows = listOf(song("a"), cue("c", 0))
        val timing = mapOf("a" to RowTiming(startAt = "10:00", runSeconds = 60, atEnd = RowEnd.NEXT))
        val outputs = Outputs()
        val runner = runner(rows, timing, outputs, { at(10, 0) }, operatorLive = { true })

        runner.tick()

        assertTrue(outputs.done.isEmpty(), "nothing fired over the operator")
        val skipped = CueFeed.fired.value.filter { it.skipped }.map { it.row.id }
        assertTrue("a" in skipped && "c" in skipped, "both reported skipped: $skipped")
    }

    @Test
    fun `a skipped row stays skipped once the operator clears, and its end never runs`() {
        val rows = listOf(song("a"), song("b"))
        val timing = mapOf("a" to RowTiming(startAt = "10:00", runSeconds = 60, atEnd = RowEnd.NEXT))
        val outputs = Outputs()
        var busy = true
        var clock = at(10, 0)
        val runner = runner(rows, timing, outputs, { clock }, operatorLive = { busy })

        runner.tick()
        busy = false
        clock = at(10, 0, 30)
        runner.tick()
        clock = at(10, 2)
        runner.tick()

        assertTrue(outputs.done.isEmpty(), "a moment that passed does not come back: ${outputs.done}")
    }

    @Test
    fun `an item finishing says nothing about a row that is no longer live`() {
        val rows = listOf(song("a"), song("b"), song("c"))
        val timing = mapOf(
            "a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT),
            "c" to RowTiming(startAt = "10:01"),
        )
        val outputs = Outputs()
        var clock = at(10, 0)
        val runner = runner(rows, timing, outputs, { clock })

        runner.tick()
        clock = at(10, 1)
        runner.tick()
        runner.liveItemFinished()

        assertEquals(listOf("project:ax1", "project:cx1"), outputs.done, "a's own-length end must not fire b over c")
    }

    @Test
    fun `the engine's own picture is not in its way`() {
        val rows = listOf(song("a"), cue("c", 1))
        val timing = mapOf("a" to RowTiming(startAt = "10:00"))
        val outputs = Outputs()
        var clock = at(10, 0)
        // What the app answers once the engine has projected: the screen shows the engine's row.
        val runner = runner(rows, timing, outputs, { clock }, operatorLive = { false })

        runner.tick()
        clock = at(10, 1)
        runner.tick()

        assertEquals(listOf("project:ax1", "blank"), outputs.done)
    }

    @Test
    fun `a pinned row fires at its time and hands on when its run is up`() {
        val rows = listOf(song("a"), song("b"))
        val timing = mapOf(
            "a" to RowTiming(startAt = "10:00", runSeconds = 300, atEnd = RowEnd.NEXT),
        )
        val outputs = Outputs()
        var clock = at(9, 59)
        val runner = runner(rows, timing, outputs, { clock })

        runner.tick()
        assertTrue(outputs.done.isEmpty(), "not yet")

        clock = at(10, 0)
        runner.tick()
        assertEquals(listOf("project:ax1"), outputs.done)

        clock = at(10, 4)
        runner.tick()
        assertEquals(listOf("project:ax1"), outputs.done, "its five minutes are not up")

        clock = at(10, 5)
        runner.tick()
        assertEquals(listOf("project:ax1", "project:bx1"), outputs.done)
    }

    @Test
    fun `a row fires once, however often the engine looks`() {
        val rows = listOf(song("a"))
        val timing = mapOf("a" to RowTiming(startAt = "10:00"))
        val outputs = Outputs()
        val runner = runner(rows, timing, outputs, { at(10, 0) })

        repeat(4) { runner.tick() }

        assertEquals(listOf("project:ax1"), outputs.done)
    }

    @Test
    fun `nothing fires while the schedule is not armed`() {
        val rows = listOf(song("a"))
        val timing = mapOf("a" to RowTiming(startAt = "10:00"))
        val outputs = Outputs()

        runner(rows, timing, outputs, { at(10, 0) }, armed = false).tick()

        assertTrue(outputs.done.isEmpty())
    }

    @Test
    fun `a row whose time passed long ago is left alone`() {
        val rows = listOf(song("a"))
        val timing = mapOf("a" to RowTiming(startAt = "10:00"))
        val outputs = Outputs()

        // Loading a service at 11:40 must not fire its 10:00 rows one after another.
        runner(rows, timing, outputs, { at(11, 40) }).tick()

        assertTrue(outputs.done.isEmpty())
    }

    @Test
    fun `an end action is dropped once its row is no longer live`() {
        val rows = listOf(song("a"), song("b"), song("c"))
        val timing = mapOf(
            // `a` runs five minutes and would hand to `b` at 10:05...
            "a" to RowTiming(startAt = "10:00", runSeconds = 300, atEnd = RowEnd.NEXT),
            // ...but `c` takes the screen at 10:02.
            "c" to RowTiming(startAt = "10:02"),
        )
        val outputs = Outputs()
        var clock = at(10, 0)
        val runner = runner(rows, timing, outputs, { clock })

        runner.tick()
        clock = at(10, 2)
        runner.tick()
        clock = at(10, 5)
        runner.tick()

        assertEquals(
            listOf("project:ax1", "project:cx1"),
            outputs.done,
            "the stale hand-off must not replace what is on screen",
        )
    }

    @Test
    fun `a row with no length waits for the item to finish`() {
        val rows = listOf(song("a"), song("b"))
        val timing = mapOf(
            "a" to RowTiming(startAt = "10:00", runSeconds = null, atEnd = RowEnd.NEXT),
        )
        val outputs = Outputs()
        var clock = at(10, 0)
        val runner = runner(rows, timing, outputs, { clock })

        runner.tick()
        clock = at(10, 30)
        runner.tick()
        assertEquals(listOf("project:ax1"), outputs.done, "nothing counts it down")

        runner.liveItemFinished()

        assertEquals(listOf("project:ax1", "project:bx1"), outputs.done)
    }

    @Test
    fun `an item finishing after something else went live changes nothing`() {
        val rows = listOf(song("a"), song("b"), song("c"))
        val timing = mapOf(
            "a" to RowTiming(startAt = "10:00", atEnd = RowEnd.NEXT),
            "c" to RowTiming(startAt = "10:01"),
        )
        val outputs = Outputs()
        var clock = at(10, 0)
        val runner = runner(rows, timing, outputs, { clock })
        runner.tick()
        clock = at(10, 1)
        runner.tick()

        runner.liveItemFinished()

        assertEquals(listOf("project:ax1", "project:cx1"), outputs.done)
    }

    @Test
    fun `blank at the end clears the outputs`() {
        val rows = listOf(song("a"), song("b"))
        val timing = mapOf("a" to RowTiming(startAt = "10:00", runSeconds = 60, atEnd = RowEnd.BLANK))
        val outputs = Outputs()
        var clock = at(10, 0)
        val runner = runner(rows, timing, outputs, { clock })

        runner.tick()
        clock = at(10, 1)
        runner.tick()

        assertEquals(listOf("project:ax1", "blank"), outputs.done)
    }

    @Test
    fun `a looping row runs its stated length before handing on`() {
        val rows = listOf(song("a"), song("b"))
        val timing = mapOf(
            "a" to RowTiming(startAt = "10:00", runSeconds = 120, repeats = 0, atEnd = RowEnd.NEXT),
        )
        val outputs = Outputs()
        var clock = at(10, 0)
        val runner = runner(rows, timing, outputs, { clock })

        runner.tick()
        assertEquals(listOf("project:ax0"), outputs.done, "0 plays is `loop` all the way down")

        clock = at(10, 2)
        runner.tick()

        assertEquals(listOf("project:ax0", "project:bx1"), outputs.done)
    }

    @Test
    fun `a row played twice runs twice as long`() {
        val rows = listOf(song("a"), song("b"))
        val timing = mapOf(
            "a" to RowTiming(startAt = "10:00", runSeconds = 60, repeats = 2, atEnd = RowEnd.NEXT),
        )
        val outputs = Outputs()
        var clock = at(10, 0)
        val runner = runner(rows, timing, outputs, { clock })

        runner.tick()
        clock = at(10, 1)
        runner.tick()
        assertEquals(listOf("project:ax2"), outputs.done, "one play in, one to go")

        clock = at(10, 2)
        runner.tick()

        assertEquals(listOf("project:ax2", "project:bx1"), outputs.done)
    }

    @Test
    fun `a heading is never what the next item means`() {
        val rows = listOf(
            song("a"),
            ScheduleItem.LabelItem(id = "h", text = "Worship", textColor = "#FFF", backgroundColor = "#000"),
            song("b"),
        )
        val timing = mapOf("a" to RowTiming(startAt = "10:00", runSeconds = 60, atEnd = RowEnd.NEXT))
        val outputs = Outputs()
        var clock = at(10, 0)
        val runner = runner(rows, timing, outputs, { clock })

        runner.tick()
        clock = at(10, 1)
        runner.tick()

        assertEquals(listOf("project:ax1", "project:bx1"), outputs.done)
    }
}
