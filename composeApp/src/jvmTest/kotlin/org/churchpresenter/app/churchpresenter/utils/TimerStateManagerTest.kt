package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TimerStateManager] is a process-wide singleton shared by the canvas renderer and the
 * properties panel, so each test uses its own sourceId rather than resetting global state.
 */
class TimerStateManagerTest {

    /** Timers are process-wide and their tickers are real coroutines — see [TimerStateManager.clear]. */
    @AfterTest
    fun stopTimers() = TimerStateManager.clear()

    private var counter = 0
    private fun id() = "test-source-${counter++}-${this.hashCode()}"

    @Test
    fun `first read seeds the timer at its full duration, stopped`() {
        val s = TimerStateManager.getState(id(), totalSeconds = 60)
        assertEquals(60, s.remainingSeconds)
        assertFalse(s.isRunning)
    }

    @Test
    fun `reading again does not re-seed a timer that has already counted down`() {
        val id = id()
        TimerStateManager.getState(id, 60)
        TimerStateManager.setRunning(id, 60, true)
        TimerStateManager.tick(id)
        // A second read with the same duration must NOT reset progress back to 60.
        assertEquals(59, TimerStateManager.getState(id, 60).remainingSeconds)
    }

    @Test
    fun `tick only counts down while running`() {
        val id = id()
        TimerStateManager.getState(id, 10)
        TimerStateManager.tick(id)
        assertEquals(10, TimerStateManager.getState(id, 10).remainingSeconds, "stopped timer must not tick")

        TimerStateManager.setRunning(id, 10, true)
        repeat(3) { TimerStateManager.tick(id) }
        assertEquals(7, TimerStateManager.getState(id, 10).remainingSeconds)
    }

    @Test
    fun `tick on an unknown source is a no-op and does not create state`() {
        val id = id()
        TimerStateManager.tick(id) // never read/seeded first
        assertEquals(30, TimerStateManager.getState(id, 30).remainingSeconds)
    }

    @Test
    fun `reaching zero stops the timer and it never goes negative`() {
        val id = id()
        TimerStateManager.getState(id, 3)
        TimerStateManager.setRunning(id, 3, true)
        repeat(10) { TimerStateManager.tick(id) } // far more ticks than seconds
        val s = TimerStateManager.getState(id, 3)
        assertEquals(0, s.remainingSeconds, "countdown must clamp at zero, never go negative")
        assertFalse(s.isRunning, "hitting zero must stop the timer")
    }

    @Test
    fun `reset and onDurationChanged both return to a stopped full duration`() {
        val id = id()
        TimerStateManager.getState(id, 60)
        TimerStateManager.setRunning(id, 60, true)
        repeat(5) { TimerStateManager.tick(id) }

        TimerStateManager.reset(id, 60)
        assertEquals(TimerStateManager.TimerState(60, false), TimerStateManager.getState(id, 60))

        TimerStateManager.setRunning(id, 60, true)
        TimerStateManager.tick(id)
        TimerStateManager.onDurationChanged(id, 90) // duration edited in the properties panel
        assertEquals(TimerStateManager.TimerState(90, false), TimerStateManager.getState(id, 90))
    }

    @Test
    fun `tickUp counts up while running, from wherever it was seeded`() {
        val id = id()
        TimerStateManager.getState(id, 0)
        TimerStateManager.tickUp(id)
        assertEquals(0, TimerStateManager.getState(id, 0).remainingSeconds, "a stopped stopwatch must not move")

        TimerStateManager.setRunning(id, 0, true)
        repeat(3) { TimerStateManager.tickUp(id) }
        assertEquals(3, TimerStateManager.getState(id, 0).remainingSeconds)
    }

    @Test
    fun `tickUp has no ceiling and never stops itself`() {
        val id = id()
        TimerStateManager.getState(id, 0)
        TimerStateManager.setRunning(id, 0, true)
        repeat(100) { TimerStateManager.tickUp(id) }

        val state = TimerStateManager.getState(id, 0)
        assertEquals(100, state.remainingSeconds)
        assertTrue(state.isRunning, "a stopwatch runs until it is paused")
    }

    @Test
    fun `tickUp on an unknown source is a no-op and does not create state`() {
        val id = id()
        TimerStateManager.tickUp(id)
        assertEquals(0, TimerStateManager.getState(id, 0).remainingSeconds)
    }

    @Test
    fun `starting a stopwatch records that it counts up`() {
        val id = id()
        TimerStateManager.setRunning(id, 0, running = true, countUp = true)

        val state = TimerStateManager.getState(id, 0)
        assertTrue(state.isRunning)
        assertTrue(state.countUp, "the direction is settled when it starts, not by whoever is drawing it")
    }

    @Test
    fun `a countdown is what starting one without a direction gets`() {
        val id = id()
        TimerStateManager.setRunning(id, 60, running = true)

        assertFalse(TimerStateManager.getState(id, 60).countUp)
    }

    @Test
    fun `pausing leaves the value where it stood`() {
        val id = id()
        TimerStateManager.setRunning(id, 10, running = true)
        TimerStateManager.tick(id)
        TimerStateManager.setRunning(id, 10, running = false)

        val state = TimerStateManager.getState(id, 10)
        assertFalse(state.isRunning)
        assertEquals(9, state.remainingSeconds)
    }

    @Test
    fun `timers are isolated per source id`() {
        val a = id()
        val b = id()
        TimerStateManager.getState(a, 10)
        TimerStateManager.getState(b, 10)
        TimerStateManager.setRunning(a, 10, true)
        TimerStateManager.tick(a)
        assertEquals(9, TimerStateManager.getState(a, 10).remainingSeconds)
        assertEquals(10, TimerStateManager.getState(b, 10).remainingSeconds, "sources must not share state")
    }

    /**
     * Documents CURRENT behaviour of an edge case rather than asserting it is desirable:
     * starting an already-expired timer leaves it flagged isRunning with nothing to count, and
     * tick() can never clear the flag (its guard requires remainingSeconds > 0). A UI binding
     * that shows a "running" indicator off this flag would sit there lit forever.
     *
     * Reachable by pressing start on a finished countdown without resetting it first.
     */
    @Test
    fun `starting an expired timer leaves isRunning stuck true -- known edge case`() {
        val id = id()
        TimerStateManager.getState(id, 1)
        TimerStateManager.setRunning(id, 1, true)
        TimerStateManager.tick(id)
        assertEquals(0, TimerStateManager.getState(id, 1).remainingSeconds)

        TimerStateManager.setRunning(id, 1, true) // "start" pressed again at zero
        TimerStateManager.tick(id)
        assertTrue(TimerStateManager.getState(id, 1).isRunning, "current behaviour: flag stays set")
        assertEquals(0, TimerStateManager.getState(id, 1).remainingSeconds)
    }
}
