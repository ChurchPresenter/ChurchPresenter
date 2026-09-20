package org.churchpresenter.calendar

import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.schedule.TimerModes
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What each cue action actually does, through the one function all three callers go through.
 *
 * The engine fires on the clock, the Schedule tab's go-live fires by hand and the run of show's ▶
 * fires from the planner — all through [fireCue], so that what a cue does cannot depend on who
 * asked. These are the host calls each action is supposed to make, and the ones it is supposed
 * not to.
 */
class FireCueTest {

    /** What the host was told to do, in order. */
    private class Outputs {
        val done = mutableListOf<String>()
        val loaded = mutableListOf<List<ScheduleItem>>()

        fun host() = CalendarHost(
            loadIntoSchedule = { items, _, replace, armed, _ ->
                loaded += items
                done += "load:${items.size}:replace=$replace:armed=$armed"
            },
            projectItem = { item, plays -> done += "project:${item.id}x$plays" },
            blankOutputs = { done += "blank" },
        )
    }

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song", "Hymns", "Hymns::1")

    private fun cue(action: String, payload: ScheduleItem? = null, plays: Int = 1) =
        ScheduleItem.CueItem(id = "c", action = action, payload = payload, plays = plays)

    private fun fire(
        cue: ScheduleItem.CueItem,
        rows: List<ScheduleItem> = emptyList(),
        loadRows: Boolean = true,
        startTime: String? = null,
    ): Outputs {
        val outputs = Outputs()
        fireCue(outputs.host(), rows, cue, at = LocalTime.of(10, 0), loadRows = loadRows, startTime = startTime)
        return outputs
    }

    @Test
    fun `a project cue puts its payload on screen, as many times as it says`() {
        val outputs = fire(cue(CueAction.PROJECT, payload = song("a"), plays = 3))

        assertEquals(listOf("project:ax3"), outputs.done)
    }

    @Test
    fun `a scene cue is a project cue with a scene in it`() {
        val scene = ScheduleItem.SceneItem("s", "scene-1", "Welcome")

        val outputs = fire(cue(CueAction.SCENE, payload = scene))

        assertEquals(listOf("project:sx1"), outputs.done)
    }

    @Test
    fun `a project cue with nothing to project does nothing`() {
        assertTrue(fire(cue(CueAction.PROJECT)).done.isEmpty())
    }

    @Test
    fun `a cue cannot project a heading, however it was set`() {
        val heading = ScheduleItem.LabelItem("h", "Worship", "#FFFFFF", "#5B9DF5")

        assertTrue(fire(cue(CueAction.PROJECT, payload = heading)).done.isEmpty())
    }

    @Test
    fun `a blank cue clears the outputs and shows nothing`() {
        assertEquals(listOf("blank"), fire(cue(CueAction.BLANK)).done)
    }

    @Test
    fun `an action this version does not know is simply not fired`() {
        assertTrue(fire(cue(CueAction.OBS_SCENE)).done.isEmpty())
        assertTrue(fire(cue("somethingLater")).done.isEmpty())
    }

    // ── Countdown ───────────────────────────────────────────────────────────────

    @Test
    fun `a countdown with no timer of its own counts to the service start`() {
        val outputs = fire(cue(CueAction.COUNTDOWN), startTime = "10:30")

        assertEquals(1, outputs.done.size)
        val timer = countdownItemFired(outputs)
        assertTrue(timer.isTimer)
        assertEquals(TimerModes.CLOCK, timer.timerMode)
        assertEquals(10, timer.targetHour)
        assertEquals(30, timer.targetMinute)
    }

    @Test
    fun `a countdown fires the timer it was given rather than building one`() {
        val timer = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMinutes = 5)

        val outputs = fire(cue(CueAction.COUNTDOWN, payload = timer), startTime = "10:30")

        assertEquals(listOf("project:tx1"), outputs.done)
    }

    @Test
    fun `a countdown with no start time and no timer has nothing to count`() {
        assertTrue(fire(cue(CueAction.COUNTDOWN), startTime = null).done.isEmpty())
        assertTrue(fire(cue(CueAction.COUNTDOWN), startTime = "not a time").done.isEmpty())
    }

    // ── Go live ─────────────────────────────────────────────────────────────────

    @Test
    fun `a go-live cue from the calendar loads the run of show and starts it`() {
        val rows = listOf(ScheduleItem.LabelItem("h", "Worship", "#FFF", "#000"), song("a"), song("b"))

        val outputs = fire(cue(CueAction.GO_LIVE), rows = rows, loadRows = true)

        assertEquals(listOf("load:3:replace=true:armed=true", "project:ax1"), outputs.done)
        assertEquals(rows, outputs.loaded.single(), "the whole list, headings and all")
    }

    @Test
    fun `a go-live cue fired from the schedule has nothing to load`() {
        val outputs = fire(cue(CueAction.GO_LIVE), rows = listOf(song("a")), loadRows = false)

        assertEquals(listOf("project:ax1"), outputs.done)
    }

    @Test
    fun `a go-live cue starts what it was pointed at, not the first row`() {
        val outputs = fire(
            cue(CueAction.GO_LIVE, payload = song("chosen"), plays = 0),
            rows = listOf(song("a")),
            loadRows = false,
        )

        assertEquals(listOf("project:chosenx0"), outputs.done)
    }

    @Test
    fun `a go-live cue on a run of show with nothing showable loads it and stops there`() {
        val rows = listOf(ScheduleItem.LabelItem("h", "Worship", "#FFF", "#000"))

        val outputs = fire(cue(CueAction.GO_LIVE), rows = rows)

        assertEquals(listOf("load:1:replace=true:armed=true"), outputs.done)
    }

    // ── What the window is told ─────────────────────────────────────────────────

    @Test
    fun `a cue that shows nothing is still posted`() {
        // Newest first, and the log is capped -- so the head is what this test just fired, and
        // counting the whole feed would only measure what the rest of the suite left in it.
        fire(cue(CueAction.BLANK))
        fire(cue(CueAction.PROJECT))

        val newest = CueFeed.fired.value.take(2).map { (it.row as ScheduleItem.CueItem).action }
        assertEquals(listOf(CueAction.PROJECT, CueAction.BLANK), newest, "the toast is how anyone knows")
    }

    @Test
    fun `the rows a cue loads carry no timing of their own`() {
        // A cue's own rows are already pinned by `rowsForSchedule`; the timing map is the
        // service's, which a bare cue has no access to.
        var timing: Map<String, RowTiming>? = null
        val host = CalendarHost(loadIntoSchedule = { _, rowTiming, _, _, _ -> timing = rowTiming })

        fireCue(host, listOf(song("a")), cue(CueAction.GO_LIVE), at = LocalTime.NOON)

        assertEquals(emptyMap(), timing)
    }

    private fun countdownItemFired(outputs: Outputs): ScheduleItem.AnnouncementItem {
        var fired: ScheduleItem.AnnouncementItem? = null
        val host = CalendarHost(projectItem = { item, _ -> fired = item as ScheduleItem.AnnouncementItem })
        fireCue(host, emptyList(), cue(CueAction.COUNTDOWN), at = LocalTime.of(10, 0), startTime = "10:30")
        return requireNotNull(fired) { "nothing was projected: ${outputs.done}" }
    }
}
