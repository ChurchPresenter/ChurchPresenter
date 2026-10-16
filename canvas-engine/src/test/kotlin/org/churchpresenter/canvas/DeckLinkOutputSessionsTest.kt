package org.churchpresenter.canvas

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.churchpresenter.canvas.DeckLinkOutputSessions.DeckLinkOutputSession
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every wait here is on the card's or the session's own signal; the timeouts only fail a stuck test. */
class DeckLinkOutputSessionsTest {

    private val card = FakeDeckLinkCard()
    private val bridge = bridgeOver(card)
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val outputs = DeckLinkOutputSessions(bridge, worker)

    @AfterTest
    fun stopWorker() {
        worker.shutdownNow()
    }

    private fun CountDownLatch.awaitOrFail() = assertTrue(await(WAIT_S, TimeUnit.SECONDS), "never signalled")

    private fun DeckLinkOutputSession.opened() = runBlocking { withTimeout(WAIT_MS) { awaitOpened() } }

    /** Closes [session] and returns once its close -- black frames, wait and all -- has run. */
    private fun closeAndWait(session: DeckLinkOutputSession, capture: Job? = null) {
        val done = CountDownLatch(1)
        session.close(capture) { done.countDown() }
        done.awaitOrFail()
    }

    /** Returns once everything queued on the output thread before now has run. */
    private fun drainWorker() {
        val done = CountDownLatch(1)
        worker.execute { done.countDown() }
        done.awaitOrFail()
    }

    /** A gate the card stops at until [release]; [reached] counts down each time something arrives. */
    private class Gate(arrivals: Int = 1) {
        val reached = CountDownLatch(arrivals)
        private val open = CountDownLatch(1)
        fun pass() {
            reached.countDown()
            open.await(WAIT_S, TimeUnit.SECONDS)
        }
        fun release() = open.countDown()
    }

    @Test
    fun `an output opens at the card's own size`() {
        val info = assertNotNull(outputs.open(0).opened())

        assertEquals(1280, info.width)
        assertTrue(bridge.isOutputActive(0))
    }

    @Test
    fun `an output that cannot say its mode is drawn at 1080p`() {
        card.outputInfo = intArrayOf()

        val info = assertNotNull(outputs.open(0).opened())

        assertEquals(1920 to 1080, info.width to info.height)
    }

    @Test
    fun `an output the card refuses says so and closes to nothing`() {
        card.opens = false
        val session = outputs.open(0)

        assertNull(session.opened())
        session.sendFrame(intArrayOf(1), 1, 1)
        closeAndWait(session)

        assertEquals(emptyList(), card.eventsNow())
    }

    @Test
    fun `closing sends black frames, then closes`() {
        val session = outputs.open(0)
        session.opened()
        session.sendFrame(intArrayOf(1), 1, 1)

        closeAndWait(session)

        assertEquals(listOf("open 0", "content 0", "black 0", "black 0", "black 0", "close 0"), card.eventsNow())
        assertFalse(bridge.isOutputActive(0))
    }

    @Test
    fun `the capture loop is joined before the black frames, so no content lands after them`() {
        val session = outputs.open(0)
        session.opened()
        val firstFrame = CompletableDeferred<Unit>()
        val capture = CoroutineScopeOnDefault.launch {
            while (isActive) {
                session.sendFrame(intArrayOf(1), 1, 1)
                firstFrame.complete(Unit)
            }
        }
        runBlocking { withTimeout(WAIT_MS) { firstFrame.await() } }

        capture.cancel()
        closeAndWait(session, capture)

        assertTrue(capture.isCompleted)
        assertNoContentAfterBlack(card.eventsNow(), 0)
    }

    @Test
    fun `a frame sent by a loop that ignores cancellation never lands after the black ones`() {
        val session = outputs.open(0)
        session.opened()
        val running = CountDownLatch(1)
        val stop = AtomicBoolean(false)
        val stray = thread {
            while (!stop.get()) {
                session.sendFrame(intArrayOf(1), 1, 1)
                running.countDown()
            }
        }
        running.awaitOrFail()

        closeAndWait(session)
        session.sendFrame(intArrayOf(1), 1, 1)
        stop.set(true)
        stray.join()

        assertNoContentAfterBlack(card.eventsNow(), 0)
    }

    @Test
    fun `a capture loop still running at the close is not waited on past the limit`() {
        val impatient = DeckLinkOutputSessions(bridge, worker, captureJoinTimeoutMs = 0)
        val session = impatient.open(0)
        session.opened()

        closeAndWait(session, capture = Job())

        assertEquals(listOf("open 0", "black 0", "black 0", "black 0", "close 0"), card.eventsNow())
    }

    @Test
    fun `opening and closing return while the card is still busy`() {
        val opening = Gate()
        card.onOpen = { opening.pass() }

        val session = outputs.open(0)
        opening.reached.awaitOrFail()
        session.close()

        // The close came back while the card is still held inside its open.
        assertEquals(emptyList(), card.eventsNow())
        opening.release()
        drainWorker()
        assertEquals(listOf("open 0", "black 0", "black 0", "black 0", "close 0"), card.eventsNow())
    }

    @Test
    fun `closing returns while the card is still showing its black frames`() {
        val settling = Gate()
        card.onSettle = { settling.pass() }
        val session = outputs.open(0)
        session.opened()

        session.close()
        settling.reached.awaitOrFail()

        assertFalse("close 0" in card.eventsNow())
        settling.release()
        drainWorker()
        assertEquals("close 0", card.eventsNow().last())
    }

    @Test
    fun `a reopen waits for the pending close, and the old close never closes the new output`() {
        val settling = Gate()
        card.onSettle = { settling.pass() }
        val old = outputs.open(0)
        old.opened()
        old.close()
        settling.reached.awaitOrFail()

        val reopened = outputs.open(0)
        // The old close is held between its black frames and the close: the reopen has not run.
        assertEquals(1, card.eventsNow().count { it == "open 0" })
        settling.release()
        assertNotNull(reopened.opened())
        old.sendFrame(intArrayOf(1), 1, 1)
        reopened.sendFrame(intArrayOf(2), 1, 1)

        assertEquals(
            listOf("open 0", "black 0", "black 0", "black 0", "close 0", "open 0", "content 0"),
            card.eventsNow(),
        )
        assertTrue(bridge.isOutputActive(0))
    }

    @Test
    fun `an output closed before its open ran never opens`() {
        val busy = Gate()
        card.onOpen = { if (it == 1) busy.pass() }
        outputs.open(1)
        busy.reached.awaitOrFail()

        val session = outputs.open(0)
        session.close()
        busy.release()

        assertNull(session.opened())
        drainWorker()
        assertFalse("open 0" in card.eventsNow())
    }

    @Test
    fun `a second close does nothing`() {
        val session = outputs.open(0)
        session.opened()
        closeAndWait(session)

        var again = false
        session.close { again = true }
        drainWorker()

        assertFalse(again)
        assertEquals(1, card.eventsNow().count { it == "close 0" })
    }

    @Test
    fun `fill and key close independently`() {
        val fill = outputs.open(0)
        val key = outputs.open(1)
        fill.opened()
        key.opened()

        closeAndWait(fill)
        key.sendFrame(intArrayOf(1), 1, 1)

        assertTrue(bridge.isOutputActive(1))
        assertEquals("content 1", card.eventsNow().last())
    }

    @Test
    fun `quitting with a close in flight closes every device once, black first`() {
        val settling = Gate()
        card.onSettle = { settling.pass() }
        val fill = outputs.open(0)
        val key = outputs.open(1)
        fill.opened()
        key.opened()
        fill.close()
        settling.reached.awaitOrFail()

        val quitting = thread { bridge.closeAllOutputs() }
        awaitState(quitting, Thread.State.BLOCKED)
        settling.release()
        quitting.join(WAIT_MS)
        drainWorker()

        assertFalse(quitting.isAlive)
        val events = card.eventsNow()
        assertEquals(1, events.count { it == "close 0" })
        assertEquals(1, events.count { it == "close 1" })
        for (device in 0..1) {
            val afterOpen = events.filter { it.endsWith(" $device") }.drop(1)
            assertEquals(List(3) { "black $device" } + "close $device", afterOpen)
        }
    }

    @Test
    fun `nothing opens once the app is quitting`() {
        bridge.closeAllOutputs()

        assertNull(outputs.open(0).opened())
        assertFalse(bridge.isOutputActive(0))
    }

    @Test
    fun `quitting waits for an open in flight, then closes it`() {
        val opening = Gate()
        card.onOpen = { opening.pass() }
        val session = outputs.open(0)
        opening.reached.awaitOrFail()

        val quitting = thread { bridge.closeAllOutputs() }
        awaitState(quitting, Thread.State.BLOCKED)
        opening.release()
        quitting.join(WAIT_MS)

        assertFalse(quitting.isAlive)
        assertNotNull(session.opened())
        assertEquals(listOf("open 0", "black 0", "black 0", "black 0", "close 0"), card.eventsNow())
        assertFalse(bridge.isOutputActive(0))
    }

    /** Polls [thread]'s state, which is what the test is waiting for; the limit only fails it. */
    private fun awaitState(thread: Thread, state: Thread.State) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS)
        while (thread.state != state) {
            assertTrue(System.nanoTime() < deadline, "${thread.name} never reached $state")
            Thread.onSpinWait()
        }
    }

    private fun assertNoContentAfterBlack(events: List<String>, device: Int) {
        val firstBlack = events.indexOf("black $device")
        assertTrue(firstBlack >= 0, "no black frame in $events")
        assertEquals(List(3) { "black $device" } + "close $device", events.drop(firstBlack))
    }

    private companion object {
        const val WAIT_S = 5L
        const val WAIT_MS = WAIT_S * 1000
        val CoroutineScopeOnDefault = CoroutineScope(Dispatchers.Default)
    }
}
