package org.churchpresenter.bibletab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reconnect backoff [retryDelayMs] uses when the engine link goes down.
 *
 * [BibleEngineClientLinkTest] drives the real loop but injects a millisecond floor so its tests fit
 * the time budget, which means nothing there pins the *shape* or the two-second production default.
 * This does, and needs no server, no client and no wall clock: the schedule is a pure function of
 * the attempt number.
 */
class BibleEngineRetryDelayTest {

    /** Every wait for one floor, across enough attempts to reach the cap and stay there. */
    private fun schedule(floorMs: Long, attempts: Int = 12): List<Long> =
        (0 until attempts).map { retryDelayMs(it, floorMs) }

    @Test
    fun `the app waits two seconds before its first retry`() {
        assertEquals(2_000L, DEFAULT_RETRY_FLOOR_MS, "the shipped floor; shortening it hammers a restarting engine")
        repeat(50) {
            val first = retryDelayMs(attempt = 0)
            assertTrue(
                first in 2_000L..2_400L,
                "the first retry waits the floor plus at most +20% jitter, was ${first}ms"
            )
        }
    }

    @Test
    fun `each successive failure waits longer than the one before`() {
        // Compared floor-to-floor rather than sample-to-sample: consecutive draws overlap by design
        // (±20% of a doubling is a 1.2x vs 1.6x band), so it is the *schedule* that climbs.
        repeat(20) {
            val waits = schedule(floorMs = 100L, attempts = 5)
            waits.zipWithNext().forEach { (earlier, later) ->
                assertTrue(later > earlier, "attempt waits must climb, got $waits")
            }
        }
    }

    @Test
    fun `the wait doubles once per failure until it is capped`() {
        // Written out rather than recomputed, so restating the production expression cannot make
        // this pass: 2s, then double per failure, then held at the 30s cap.
        val expected = listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L, 30_000L)
        repeat(20) {
            schedule(floorMs = 2_000L, attempts = expected.size).forEachIndexed { attempt, wait ->
                val target = expected[attempt]
                assertTrue(
                    wait >= (target * 0.8).toLong() && wait <= (target * 1.2).toLong(),
                    "attempt $attempt should sit within ±20% of ${target}ms, was ${wait}ms"
                )
            }
        }
    }

    @Test
    fun `no wait grows past the cap however long the engine stays down`() {
        assertEquals(30_000L, MAX_RETRY_DELAY_MS)
        repeat(20) {
            val waits = schedule(floorMs = 2_000L, attempts = 40)
            assertTrue(
                waits.all { it <= (MAX_RETRY_DELAY_MS * 1.2).toLong() },
                "an engine down for an hour must still be retried; the wait cannot run away, got ${waits.max()}ms"
            )
            assertTrue(
                waits.any { it > 20_000L },
                "and it must actually reach the cap rather than stalling low, got ${waits.max()}ms"
            )
        }
    }

    @Test
    fun `jitter spreads the waits so reconnecting clients do not come back in lockstep`() {
        // 200 draws of the same attempt: an unjittered implementation returns one value 200 times.
        val draws = (1..200).map { retryDelayMs(attempt = 2, floorMs = 2_000L) }.toSet()
        assertTrue(draws.size > 50, "the wait must vary between clients, saw ${draws.size} distinct values")
    }

    @Test
    fun `jitter never dips below the floor`() {
        // The -20% side of a floor-sized base would undercut the floor, so it is clamped. Without
        // the clamp an injected 25ms floor could fire at 20ms — and the shipped one at 1.6s.
        listOf(25L, 100L, 2_000L).forEach { floor ->
            repeat(200) {
                val wait = retryDelayMs(attempt = 0, floorMs = floor)
                assertTrue(wait >= floor, "a ${floor}ms floor must never wait less, was ${wait}ms")
            }
        }
    }

    @Test
    fun `an injected floor scales the whole schedule, not just the first wait`() {
        val fast = schedule(floorMs = 25L, attempts = 5)
        val shipped = schedule(floorMs = 2_000L, attempts = 5)
        fast.zip(shipped).forEachIndexed { attempt, (quick, slow) ->
            assertTrue(quick < slow, "attempt $attempt: ${quick}ms should undercut the shipped ${slow}ms")
        }
        assertTrue(
            fast.sum() < 1_000L,
            "three failed connects on an injected floor must fit a test budget, summed to ${fast.sum()}ms"
        )
    }
}
