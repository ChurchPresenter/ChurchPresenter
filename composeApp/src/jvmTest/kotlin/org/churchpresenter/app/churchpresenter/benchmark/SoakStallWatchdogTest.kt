package org.churchpresenter.app.churchpresenter.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The soak's stall watchdog, ticked by hand on a clock the test moves. */
class SoakStallWatchdogTest {

    private var now = 0L
    private val stalls = mutableListOf<Long>()
    private val watchdog = SoakStallWatchdog(limitNanos = LIMIT, onStall = { stalls += it }, clock = { now })

    @Test
    fun `nothing is reported between frames, however long the gap`() {
        watchdog.step { now += LIMIT / 2 }
        now += LIMIT * 10
        assertFalse(watchdog.checkOnce())
        assertTrue(stalls.isEmpty())
    }

    @Test
    fun `a frame within the limit is not a stall`() {
        watchdog.step {
            now += LIMIT
            assertFalse(watchdog.checkOnce())
        }
        assertTrue(stalls.isEmpty())
    }

    @Test
    fun `a frame past the limit is reported once, with how long it has run`() {
        watchdog.step {
            now += LIMIT + 1
            assertTrue(watchdog.checkOnce())
            now += LIMIT
            assertFalse(watchdog.checkOnce(), "a stall is reported once")
        }
        assertEquals(listOf(LIMIT + 1), stalls)
    }

    @Test
    fun `the frame's own result comes back through the watch`() {
        assertEquals("drawn", watchdog.step { "drawn" })
    }

    private companion object {
        const val LIMIT = 60_000_000_000L
    }
}
