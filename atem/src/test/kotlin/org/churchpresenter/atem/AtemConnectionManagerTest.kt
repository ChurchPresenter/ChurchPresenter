package org.churchpresenter.atem

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [AtemConnectionManager]'s caching and invalidation, driven against [FakeAtemSwitcher] over
 * loopback UDP rather than a stubbed client — the decisions under test are all about a *live*
 * socket (is it still alive, does it already hold a state dump, is it pointed at this endpoint),
 * so a stand-in that answers those from a field would test the stand-in.
 *
 * The manager is an `object`, so every test brackets itself with [AtemConnectionManager.invalidate]
 * — its own public reset, not a test-only seam — and the identity assertions below are what proves
 * the reset works.
 */
private const val LOOPBACK = "127.0.0.1"

class AtemConnectionManagerTest {

    @BeforeTest fun reset() = AtemConnectionManager.invalidate()

    @AfterTest fun tearDown() = AtemConnectionManager.invalidate()

    // ── Connecting and caching ────────────────────────────────────────────────

    @Test
    fun `use connects, runs the block and returns its value`() {
        FakeAtemSwitcher().use { fake ->
            val answer = runBlocking {
                AtemConnectionManager.use(LOOPBACK, fake.port) { client ->
                    assertTrue(client.isAlive(), "the block runs against an open socket")
                    "ran"
                }
            }
            assertEquals("ran", answer)
        }
    }

    @Test
    fun `a second use of the same endpoint reuses the cached client`() {
        FakeAtemSwitcher().use { fake ->
            runBlocking {
                val first = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                val second = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                assertSame(first, second, "the session is held open across calls, not re-established")
            }
        }
    }

    @Test
    fun `pointing at a different endpoint reconnects`() {
        FakeAtemSwitcher().use { first ->
            FakeAtemSwitcher().use { second ->
                runBlocking {
                    val a = AtemConnectionManager.use(LOOPBACK, first.port) { it }
                    val b = AtemConnectionManager.use(LOOPBACK, second.port) { it }
                    assertNotSame(a, b, "a changed endpoint invalidates the cache")
                    assertFalse(a.isAlive(), "the old client is disconnected rather than leaked")
                    assertTrue(b.isAlive())
                }
            }
        }
    }

    @Test
    fun `a client whose socket has closed is replaced on the next use`() {
        FakeAtemSwitcher().use { fake ->
            runBlocking {
                val first = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                // What the keepalive loop does when the switcher goes silent: tear down the socket
                // while the manager still holds the reference.
                first.disconnect()
                val second = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                assertNotSame(first, second)
                assertTrue(second.isAlive())
            }
        }
    }

    // ── The state dump ────────────────────────────────────────────────────────

    @Test
    fun `needsState collects the state dump`() {
        FakeAtemSwitcher(mixEffects = 4, downstreamKeyers = 2).use { fake ->
            runBlocking {
                val state = AtemConnectionManager.use(LOOPBACK, fake.port, needsState = true) {
                    it.lastKnownState
                }
                assertNotNull(state)
                assertEquals(4, state.mixEffectCount)
                assertEquals(2, state.downstreamKeyers)
            }
        }
    }

    @Test
    fun `a stateless connection is reconnected when state is later needed`() {
        FakeAtemSwitcher().use { fake ->
            runBlocking {
                val stateless = AtemConnectionManager.use(LOOPBACK, fake.port, needsState = false) { it }
                assertNull(stateless.lastKnownState, "a stateless connect skips the dump")

                val stateful = AtemConnectionManager.use(LOOPBACK, fake.port, needsState = true) { it }
                assertNotSame(stateless, stateful, "the dump cannot be added to a live session")
                assertNotNull(stateful.lastKnownState)

                // And once it has state, needsState reuses it rather than reconnecting again.
                assertSame(stateful, AtemConnectionManager.use(LOOPBACK, fake.port, needsState = true) { it })
            }
        }
    }

    // ── Failure invalidates the cache ─────────────────────────────────────────

    @Test
    fun `a throwing block discards the cached client and rethrows`() {
        FakeAtemSwitcher().use { fake ->
            runBlocking {
                val first = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                assertFailsWith<IllegalStateException> {
                    AtemConnectionManager.use(LOOPBACK, fake.port) { error("stale session") }
                }
                assertFalse(first.isAlive(), "the failed session is closed, not left half-open")
                assertNotSame(first, AtemConnectionManager.use(LOOPBACK, fake.port) { it })
            }
        }
    }

    @Test
    fun `a throwing tryRun block discards the cached client and rethrows`() {
        FakeAtemSwitcher().use { fake ->
            runBlocking {
                val first = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                assertFailsWith<IllegalStateException> {
                    AtemConnectionManager.tryRun(LOOPBACK, fake.port) { error("stale session") }
                }
                assertFalse(first.isAlive())
                assertNotSame(first, AtemConnectionManager.use(LOOPBACK, fake.port) { it })
            }
        }
    }

    // ── tryRun's non-blocking contract ────────────────────────────────────────

    @Test
    fun `tryRun runs the block and reports true`() {
        FakeAtemSwitcher().use { fake ->
            var ran = false
            val result = runBlocking { AtemConnectionManager.tryRun(LOOPBACK, fake.port) { ran = true } }
            assertTrue(result)
            assertTrue(ran)
        }
    }

    @Test
    fun `tryRun reports false without waiting while the connection is held`() {
        FakeAtemSwitcher().use { fake ->
            runBlocking {
                val holding = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                // The upload case: a long operation owns the shared connection. The holder signals
                // that it is *inside* the lock, so the assertion below races nothing.
                val holder = launch(Dispatchers.Default) {
                    AtemConnectionManager.use(LOOPBACK, fake.port) {
                        holding.complete(Unit)
                        release.await()
                    }
                }
                holding.await()

                var ran = false
                assertFalse(
                    AtemConnectionManager.tryRun(LOOPBACK, fake.port) { ran = true },
                    "a busy connection is reported busy rather than queued behind the holder",
                )
                assertFalse(ran, "the block is not run when the lock could not be taken")

                release.complete(Unit)
                holder.join()
            }
        }
    }

    // ── invalidate ────────────────────────────────────────────────────────────

    @Test
    fun `invalidate closes the cached connection and the next use reconnects`() {
        FakeAtemSwitcher().use { fake ->
            runBlocking {
                val first = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                AtemConnectionManager.invalidate()
                assertFalse(first.isAlive(), "invalidate disconnects rather than only dropping the reference")

                val second = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                assertNotSame(first, second)
                assertTrue(second.isAlive())
            }
        }
    }

    @Test
    fun `a connection invalidated while it was still connecting is not cached`() {
        // The window this closes: invalidate() cannot take the manager's mutex -- it does not
        // suspend, and it is called from settings changes and from test teardown -- so it can land
        // while a connect is in flight. Before the generation check, that connect went on to cache
        // itself afterwards, leaving the manager holding a client for an endpoint that had already
        // been abandoned. Nothing later noticed, because AtemClient.isAlive() is `socket != null`,
        // which for UDP stays true whether or not anything is still listening at the other end.
        var invalidatedDuringConnect = false
        FakeAtemSwitcher(
            onHelloReceived = {
                // Exactly once, and strictly inside the connect: the client is waiting on this reply.
                if (!invalidatedDuringConnect) {
                    invalidatedDuringConnect = true
                    AtemConnectionManager.invalidate()
                }
            },
        ).use { fake ->
            runBlocking {
                val raced = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                assertTrue(invalidatedDuringConnect, "the switcher must have raced the connect")

                val next = AtemConnectionManager.use(LOOPBACK, fake.port) { it }
                assertNotSame(
                    raced,
                    next,
                    "a connection opened across an invalidate must not be handed to the next caller",
                )
            }
        }
    }

    @Test
    fun `invalidate with nothing cached is a no-op`() {
        AtemConnectionManager.invalidate()
        AtemConnectionManager.invalidate()
    }
}
