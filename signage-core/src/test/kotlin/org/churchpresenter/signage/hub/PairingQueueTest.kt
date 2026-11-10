package org.churchpresenter.signage.hub

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PairingQueueTest {
    private var now = 1_000L
    private val queue = PairingQueue { now }

    @Test
    fun aWaitingPlayerIsListedUntilDecided() = runTest {
        val decision = async { queue.await(hello(), "192.168.1.30") }
        runCurrent()
        val listed = queue.pending.value.single()
        assertEquals("4821" to "192.168.1.30", listed.code to listed.address)

        assertTrue(queue.decide("player-1", PairingDecision.Approved("token")))
        assertEquals(PairingDecision.Approved("token"), decision.await())
        assertEquals(emptyList(), queue.pending.value)
    }

    @Test
    fun aPlayerThatLeavesStopsBeingListed() = runTest {
        val waiting = async { queue.await(hello(), "192.168.1.30") }
        runCurrent()
        waiting.cancel()
        runCurrent()
        assertEquals(emptyList(), queue.pending.value)
        assertFalse(queue.decide("player-1", PairingDecision.Rejected), "nobody is waiting any more")
    }

    @Test
    fun aPlayerThatReconnectsIsListedOnceWithItsNewCode() = runTest {
        val first = async { runCatching { queue.await(hello(code = "1111"), "a") } }
        runCurrent()
        val second = async { queue.await(hello(code = "2222"), "b") }
        runCurrent()
        assertTrue(first.await().isFailure, "the first wait is called off")
        assertEquals(listOf("2222"), queue.pending.value.map { it.code })

        queue.decide("player-1", PairingDecision.Rejected)
        assertEquals(PairingDecision.Rejected, second.await())
    }

    @Test
    fun aHeldPlayerIsTurnedAwayUntilTheHoldRunsOut() {
        queue.hold("player-1", "Lobby")
        assertTrue(queue.isHeld("player-1"))
        assertFalse(queue.isHeld("player-2"))
        assertEquals("Lobby", queue.held.value.single().name)

        now += PairingQueue.HOLD_MS - 1
        assertTrue(queue.isHeld("player-1"))
        now += 1
        assertFalse(queue.isHeld("player-1"))
        assertEquals(emptyList(), queue.held.value)
    }

    @Test
    fun releasingAHoldSaysWhetherThereWasOne() {
        queue.hold("player-1", "Lobby")
        assertTrue(queue.release("player-1"))
        assertFalse(queue.isHeld("player-1"))
        assertFalse(queue.release("player-1"))
    }

    @Test
    fun holdingAPlayerAgainRestartsItsTimeWithoutListingItTwice() {
        queue.hold("player-1", "Lobby")
        now += PairingQueue.HOLD_MS / 2
        queue.hold("player-1", "Lobby")
        now += PairingQueue.HOLD_MS / 2 + 1
        assertTrue(queue.isHeld("player-1"))
        assertEquals(1, queue.held.value.size)
    }

    @Test
    fun pairHereTakesAHeldScreenOffHoldAndAsksItBackUntilItComesOrTimeRunsOut() {
        queue.hold("player-1", "Lobby", "192.168.1.30")
        assertTrue(queue.invite("player-1"))
        assertFalse(queue.isHeld("player-1"))
        assertEquals(listOf("192.168.1.30"), queue.activeInvites().map { it.address })
        queue.arrived("player-1")
        assertEquals(emptyList(), queue.activeInvites())

        queue.hold("player-1", "Lobby", "192.168.1.30")
        queue.invite("player-1")
        now += PairingQueue.INVITE_MS
        assertEquals(emptyList(), queue.activeInvites())
        assertFalse(queue.invite("player-2"), "never held")
    }

    @Test
    fun pairHereOnAScreenWithNoKnownAddressOnlyReleasesIt() {
        queue.hold("player-1", "Lobby")
        assertTrue(queue.invite("player-1"))
        assertFalse(queue.isHeld("player-1"))
        assertEquals(emptyList(), queue.activeInvites())
    }
}
