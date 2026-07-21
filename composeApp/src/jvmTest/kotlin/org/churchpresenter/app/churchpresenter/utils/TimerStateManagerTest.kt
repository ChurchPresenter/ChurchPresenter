package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [TimerStateManager] is a process-wide singleton shared by the canvas renderer and the
 * properties panel, so each test uses its own sourceId rather than resetting global state.
 */
class TimerStateManagerTest {

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
        TimerStateManager.tick(id, 60)
        // A second read with the same duration must NOT reset progress back to 60.
        assertEquals(59, TimerStateManager.getState(id, 60).remainingSeconds)
    }

    @Test
    fun `tick only counts down while running`() {
        val id = id()
        TimerStateManager.getState(id, 10)
        TimerStateManager.tick(id, 10)
        assertEquals(10, TimerStateManager.getState(id, 10).remainingSeconds, "stopped timer must not tick")

        TimerStateManager.setRunning(id, 10, true)
        repeat(3) { TimerStateManager.tick(id, 10) }
        assertEquals(7, TimerStateManager.getState(id, 10).remainingSeconds)
    }

    @Test
    fun `tick on an unknown source is a no-op and does not create state`() {
        val id = id()
        TimerStateManager.tick(id, 30) // never read/seeded first
        assertEquals(30, TimerStateManager.getState(id, 30).remainingSeconds)
    }

    @Test
    fun `reaching zero stops the timer and it never goes negative`() {
        val id = id()
        TimerStateManager.getState(id, 3)
        TimerStateManager.setRunning(id, 3, true)
        repeat(10) { TimerStateManager.tick(id, 3) } // far more ticks than seconds
        val s = TimerStateManager.getState(id, 3)
        assertEquals(0, s.remainingSeconds, "countdown must clamp at zero, never go negative")
        assertFalse(s.isRunning, "hitting zero must stop the timer")
    }

    @Test
    fun `reset and onDurationChanged both return to a stopped full duration`() {
        val id = id()
        TimerStateManager.getState(id, 60)
        TimerStateManager.setRunning(id, 60, true)
        repeat(5) { TimerStateManager.tick(id, 60) }

        TimerStateManager.reset(id, 60)
        assertEquals(TimerStateManager.TimerState(60, false), TimerStateManager.getState(id, 60))

        TimerStateManager.setRunning(id, 60, true)
        TimerStateManager.tick(id, 60)
        TimerStateManager.onDurationChanged(id, 90) // duration edited in the properties panel
        assertEquals(TimerStateManager.TimerState(90, false), TimerStateManager.getState(id, 90))
    }

    @Test
    fun `timers are isolated per source id`() {
        val a = id()
        val b = id()
        TimerStateManager.getState(a, 10)
        TimerStateManager.getState(b, 10)
        TimerStateManager.setRunning(a, 10, true)
        TimerStateManager.tick(a, 10)
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
        TimerStateManager.tick(id, 1)
        assertEquals(0, TimerStateManager.getState(id, 1).remainingSeconds)

        TimerStateManager.setRunning(id, 1, true) // "start" pressed again at zero
        TimerStateManager.tick(id, 1)
        assertTrue(TimerStateManager.getState(id, 1).isRunning, "current behaviour: flag stays set")
        assertEquals(0, TimerStateManager.getState(id, 1).remainingSeconds)
    }
}
