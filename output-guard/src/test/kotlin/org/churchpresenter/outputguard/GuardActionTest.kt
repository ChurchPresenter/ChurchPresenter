package org.churchpresenter.outputguard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardActionTest {

    private val now = 100_000L

    private fun state(
        closing: Boolean = false,
        live: Boolean = false,
        takeOverOnHang: Boolean = false,
        heartbeatAgo: Long = 100,
        tickAgo: Long = 100,
    ) = GuardState.inMemory().apply {
        reset(pid = 10, now = now)
        this.closing = closing
        this.live = live
        this.takeOverOnHang = takeOverOnHang
        heartbeatAt = now - heartbeatAgo
        uiTickAt = now - tickAgo
    }

    @Test
    fun `the app to watch is the one this build's state names, while it is still there`() {
        val asked = mutableListOf<Long>()
        val self = ProcessHandle.current()

        assertEquals(self, appToWatch(state()) { asked += it; self })
        assertEquals(listOf(10L), asked)
        assertEquals(null, appToWatch(GuardState.inMemory()) { self }, "no app of this build wrote it")
        assertEquals(null, appToWatch(state()) { null }, "the app is already gone")

        val mine = GuardState.inMemory().apply { reset(pid = self.pid(), now = 0) }
        assertEquals(self.pid(), appToWatch(mine)?.pid(), "looked up among the running processes")
    }

    @Test
    fun `a running app is followed`() {
        assertEquals(GuardAction.FOLLOW, decide(appAlive = true, state(), now))
    }

    @Test
    fun `a quit on purpose wins over everything, even a dead app`() {
        assertEquals(GuardAction.LEAVE, decide(appAlive = false, state(closing = true), now))
    }

    @Test
    fun `a dead app is covered, whatever it last said`() {
        assertEquals(GuardAction.COVER_DEATH, decide(appAlive = false, state(heartbeatAgo = 60_000), now))
    }

    @Test
    fun `a hang is covered only when asked, while live, with the app's other threads still beating`() {
        val stuck = HANG_MS + 1
        val asked = state(live = true, takeOverOnHang = true, tickAgo = stuck)
        assertEquals(GuardAction.COVER_HANG, decide(true, asked, now))

        assertEquals(GuardAction.FOLLOW, decide(true, state(live = true, tickAgo = stuck), now), "not asked")
        val nothingLive = state(takeOverOnHang = true, tickAgo = stuck)
        assertEquals(GuardAction.FOLLOW, decide(true, nothingLive, now), "nothing live")
        val frozen = state(live = true, takeOverOnHang = true, tickAgo = stuck, heartbeatAgo = HEARTBEAT_FRESH_MS)
        assertEquals(GuardAction.FOLLOW, decide(true, frozen, now), "the whole app is stuck, not only its UI")
        assertEquals(
            GuardAction.FOLLOW,
            decide(true, state(live = true, takeOverOnHang = true, tickAgo = HANG_MS), now),
            "exactly the hang time is not yet a hang",
        )
    }

    @Test
    fun `the relaunched app has taken over once its outputs are up`() {
        val state = state()
        state.reset(pid = 11, now = now)

        assertFalse(successorTookOver(state, deadPid = 11, firstSeenAt = now, now = now), "still the dead app's pid")
        assertFalse(successorTookOver(state, deadPid = 10, firstSeenAt = now, now = now), "its outputs are not up")

        state.outputsUp = true
        assertTrue(successorTookOver(state, deadPid = 10, firstSeenAt = now, now = now))
    }

    @Test
    fun `a relaunched app with no outputs has taken over after a grace, and a stranger's file never`() {
        val state = state()
        state.reset(pid = 11, now = now)

        assertFalse(successorTookOver(state, deadPid = 10, firstSeenAt = null, now = now))
        assertFalse(successorTookOver(state, deadPid = 10, firstSeenAt = now, now = now + SUCCESSOR_GRACE_MS - 1))
        assertTrue(successorTookOver(state, deadPid = 10, firstSeenAt = now, now = now + SUCCESSOR_GRACE_MS))

        val stranger = GuardState.inMemory()
        assertFalse(successorTookOver(stranger, deadPid = 10, firstSeenAt = now, now = now + SUCCESSOR_GRACE_MS))
        val noPid = GuardState.inMemory().apply { reset(pid = 0, now = now) }
        assertFalse(successorTookOver(noPid, deadPid = 10, firstSeenAt = now, now = now + SUCCESSOR_GRACE_MS))
    }
}
