package org.churchpresenter.app.churchpresenter.server

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Driving the Bitfocus Companion lower-third sequence.
 *
 * One HTTP call fans out into a timed dance the app owns the timing for: key on → pre-roll →
 * lower third live (an [onShow] emission the UI layer collects) → hold → post-roll → key off →
 * clear (an [onClear] emission). This test exercises the app-side contract that survives without
 * any ATEM in the room — a lower third must still go live and still clear — so it configures a
 * blank ATEM host, which the sequencer treats as "no key control" and never opens a socket.
 *
 * All waits are on a positive signal (the request arriving on [onShow], the clear arriving on
 * [onClear], the observable `status`), never on the pre/post-roll delays: those are set to zero and
 * the sequence is run in "show" mode (`autoEnd = false`) so nothing hinges on wall-clock time. The
 * sequencer is a JVM singleton with its own scope, so each test stops it afterwards to return the
 * shared state to idle.
 */
class LowerThirdSequencerTest {

    /** Blank host => the sequencer skips all key control; pre/post-roll zeroed so nothing waits. */
    private val noAtem = AtemSettings(host = "", keyPreRollMs = 0, keyPostRollMs = 0)

    @AfterTest
    fun reset() = runBlocking { LowerThirdSequencer.stop() }

    /** Subscribe to [flow], guarantee the subscription is live, then run [block] and await one item. */
    private inline fun <T> captureFirst(
        flow: kotlinx.coroutines.flow.MutableSharedFlow<T>,
        crossinline block: suspend () -> Unit
    ): T = runBlocking {
        val received = Channel<T>(capacity = 1)
        val collector = launch { flow.collect { received.trySend(it) } }
        flow.subscriptionCount.first { it > 0 }   // no emit can be missed once this returns
        block()
        try {
            withTimeout(1_000) { received.receive() }
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `show mode puts the lower third live and marks the sequence running`() {
        val request = captureFirst(LowerThirdSequencer.onShow) {
            LowerThirdSequencer.run(
                name = "Welcome",
                json = "{\"lottie\":true}",
                durationMs = 0L,
                pauseAtFrame = false,
                pauseDurationMs = 0L,
                mixEffect = null,
                keyer = null,
                atem = noAtem,
                autoEnd = false
            )
        }
        assertEquals("{\"lottie\":true}", request.json, "the UI layer needs the lottie to render")
        assertEquals("Welcome", request.name)
        assertEquals("running:Welcome", LowerThirdSequencer.status.value, "status must name the live sequence")
    }

    @Test
    fun `the show request carries the pause-at-frame settings through`() {
        val request = captureFirst(LowerThirdSequencer.onShow) {
            LowerThirdSequencer.run(
                name = "Hold",
                json = "{}",
                durationMs = 0L,
                pauseAtFrame = true,
                pauseDurationMs = 1234L,
                mixEffect = null,
                keyer = null,
                atem = noAtem,
                autoEnd = false
            )
        }
        assertTrue(request.pauseAtFrame, "a hold-on-frame lower third must tell the player to pause")
        assertEquals(1234L, request.pauseDurationMs, "the hold duration must reach the player intact")
    }

    @Test
    fun `stopping a running sequence clears it and returns to idle`() = runBlocking {
        LowerThirdSequencer.run(
            name = "Live", json = "{}", durationMs = 0L, pauseAtFrame = false, pauseDurationMs = 0L,
            mixEffect = null, keyer = null, atem = noAtem, autoEnd = false
        )
        assertEquals("running:Live", LowerThirdSequencer.status.value)

        // stop() emits onClear; capture it to prove the UI is told to pull the lower third.
        val cleared = Channel<Unit>(capacity = 1)
        val collector = launch { LowerThirdSequencer.onClear.collect { cleared.trySend(Unit) } }
        LowerThirdSequencer.onClear.subscriptionCount.first { it > 0 }
        LowerThirdSequencer.stop()
        withTimeout(1_000) { cleared.receive() }
        collector.cancel()

        assertEquals("idle", LowerThirdSequencer.status.value, "a stopped sequence must leave the app idle")
    }

    @Test
    fun `a lower third with no ATEM configured reports no key error`() = runBlocking {
        val keyError = LowerThirdSequencer.run(
            name = "NoKey", json = "{}", durationMs = 0L, pauseAtFrame = false, pauseDurationMs = 0L,
            mixEffect = null, keyer = null, atem = noAtem, autoEnd = false
        )
        assertNull(keyError, "with no key control requested there is no ATEM error to report")
    }
}
