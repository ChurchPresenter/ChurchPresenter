package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.ndi.FakeNdiLibrary
import org.churchpresenter.ndi.NdiFinder
import org.churchpresenter.ndi.NdiSourceInfo
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val CAMERA = NdiSourceInfo("BOOTH (Camera 1)", "192.168.1.20:5961")
private val GRAPHICS = NdiSourceInfo("BOOTH (Graphics)")

/**
 * Discovery for the Canvas source picker, over `:ndi`'s [FakeNdiLibrary].
 *
 * The behaviour worth pinning is the finder's *lifetime*: the SDK's picture of the network is built
 * up inside one finder and lost when it is destroyed, so a class that opened one per query would
 * show an empty list every time and nobody would notice until it was on a real network.
 */
class NdiSourceDirectoryTest {

    private fun directory(lib: FakeNdiLibrary, openings: MutableList<NdiFinder> = mutableListOf()) =
        NdiSourceDirectory { NdiFinder(lib).also { openings += it } }

    @Test
    fun `nothing is discovered, and no finder opened, before anyone is looking`() {
        val lib = FakeNdiLibrary().apply { discoverable += CAMERA }
        val directory = directory(lib)

        assertEquals(emptyList(), directory.sources())
        assertFalse(directory.isRunning)
        assertTrue(lib.findersCreated.isEmpty())
    }

    @Test
    fun `acquiring starts discovery and reports what the network is advertising`() {
        val lib = FakeNdiLibrary().apply { discoverable += listOf(CAMERA, GRAPHICS) }
        val directory = directory(lib)

        directory.acquire()

        assertTrue(directory.isRunning)
        assertEquals(listOf(CAMERA, GRAPHICS), directory.sources())
        directory.release()
    }

    @Test
    fun `the same finder answers every look, because discovery is cumulative`() {
        val lib = FakeNdiLibrary()
        val directory = directory(lib)
        directory.acquire()

        assertEquals(emptyList(), directory.sources(), "a young finder knows nothing yet")

        lib.discoverable += CAMERA
        assertEquals(listOf(CAMERA), directory.sources())
        assertEquals(1, lib.findersCreated.size, "a second finder would have forgotten the first's answers")
        directory.release()
    }

    @Test
    fun `a source with no name is not offered, whatever the runtime reports`() {
        val lib = FakeNdiLibrary().apply { discoverable += listOf(CAMERA, NdiSourceInfo("", "10.0.0.5:5961")) }
        val directory = directory(lib)
        directory.acquire()

        assertEquals(listOf(CAMERA), directory.sources(), "a blank row in the picker cannot be chosen")
        directory.release()
    }

    @Test
    fun `two panels looking at once share one finder, and the second release stops it`() {
        val lib = FakeNdiLibrary().apply { discoverable += CAMERA }
        val directory = directory(lib)

        directory.acquire()
        directory.acquire()
        assertEquals(1, lib.findersCreated.size)

        directory.release()
        assertTrue(directory.isRunning, "the other panel is still looking")
        assertTrue(lib.findersDestroyed.isEmpty())

        directory.release()
        assertFalse(directory.isRunning, "nothing may keep answering mDNS for a panel that closed")
        assertEquals(1, lib.findersDestroyed.size)
        assertEquals(emptyList(), directory.sources())
    }

    @Test
    fun `looking again after everyone left starts a fresh finder`() {
        val lib = FakeNdiLibrary().apply { discoverable += CAMERA }
        val directory = directory(lib)

        directory.acquire()
        directory.release()
        directory.acquire()

        assertEquals(2, lib.findersCreated.size)
        assertEquals(listOf(CAMERA), directory.sources())
        directory.release()
    }

    @Test
    fun `an unbalanced release cannot drive the count below zero`() {
        val lib = FakeNdiLibrary().apply { discoverable += CAMERA }
        val directory = directory(lib)

        directory.release()
        directory.acquire()

        assertTrue(directory.isRunning, "one acquire must be enough to start looking again")
        assertEquals(listOf(CAMERA), directory.sources())
        directory.release()
        assertFalse(directory.isRunning)
    }

    @Test
    fun `the caller's wait is what reaches the runtime`() {
        val lib = FakeNdiLibrary()
        val directory = directory(lib)
        directory.acquire()

        directory.sources(waitMs = 300)

        assertEquals(300, lib.lastFindTimeoutMs)
        directory.release()
    }

    @Test
    fun `a runtime that will not open a finder leaves the directory empty rather than failing`() {
        val lib = FakeNdiLibrary(refuseFinder = true)
        val directory = directory(lib)

        directory.acquire()

        assertFalse(directory.isRunning)
        assertEquals(emptyList(), directory.sources())
        directory.release()
    }

    @Test
    fun `no runtime at all is an empty picker, not a crash`() {
        val directory = NdiSourceDirectory { null }

        directory.acquire()

        assertFalse(directory.isRunning)
        assertEquals(emptyList(), directory.sources())
        directory.release()
    }

    // ── The finder's lifetime under a live look ─────────────────────────────────

    /**
     * The crash this class exists to stop: Sentry CHURCH-PRESENTER-DESKTOP-66.
     *
     * A look blocks inside the runtime for up to a second and JNA cannot be interrupted, so the
     * composition disposing a properties panel could destroy the handle under it -- a fatal native
     * fault on Windows, not an exception. The latch stands in for that second.
     */
    @Test
    fun `a finder is not destroyed while a look is still inside it`() {
        val lib = FakeNdiLibrary().apply { discoverable += CAMERA }
        val directory = directory(lib)
        val inside = CountDownLatch(1)
        val letGo = CountDownLatch(1)
        lib.duringFindSources = {
            inside.countDown()
            assertTrue(letGo.await(1, TimeUnit.SECONDS), "the test never released the look")
        }
        directory.acquire()

        val look = thread { directory.sources() }
        assertTrue(inside.await(1, TimeUnit.SECONDS), "the look never reached the runtime")

        directory.release()
        assertFalse(directory.isRunning, "the panel let go, so no new look may start")
        assertTrue(
            lib.findersDestroyed.isEmpty(),
            "destroying a finder a look is parked inside is the native crash this exists to stop",
        )

        letGo.countDown()
        look.join(1_000)
        assertFalse(look.isAlive, "the look never came back")
        assertEquals(lib.findersCreated, lib.findersDestroyed, "the last look out closes the finder")
    }

    /** The reason the lock and the count belong to the finder rather than to the directory. */
    @Test
    fun `a look inside a retired finder never reaches the one that replaced it`() {
        val lib = FakeNdiLibrary().apply { discoverable += CAMERA }
        val directory = directory(lib)
        val inside = CountDownLatch(1)
        val letGo = CountDownLatch(1)
        lib.duringFindSources = {
            inside.countDown()
            assertTrue(letGo.await(1, TimeUnit.SECONDS), "the test never released the look")
        }
        directory.acquire()
        val look = thread { directory.sources() }
        assertTrue(inside.await(1, TimeUnit.SECONDS), "the look never reached the runtime")

        // Another panel opens before the old look has come back.
        directory.release()
        directory.acquire()
        assertEquals(2, lib.findersCreated.size, "the second panel gets a finder of its own")
        assertTrue(lib.findersDestroyed.isEmpty(), "the live finder is not the retired one")

        letGo.countDown()
        look.join(1_000)
        assertFalse(look.isAlive, "the look never came back")
        assertEquals(
            listOf(lib.findersCreated.first()),
            lib.findersDestroyed,
            "the retired finder, exactly once -- and never the one now answering",
        )

        directory.release()
        assertEquals(lib.findersCreated, lib.findersDestroyed, "and both are closed in the end")
    }

    /**
     * One look at a time through one finder.
     *
     * The runtime owns the array it hands back until the next call on the same finder, so two looks
     * overlapping would leave the first reading what the second had already replaced.
     */
    @Test
    fun `two panels looking at once do not enter the finder together`() {
        val lib = FakeNdiLibrary().apply { discoverable += CAMERA }
        val directory = directory(lib)
        val entered = AtomicInteger()
        val order = Collections.synchronizedList(mutableListOf<Int>())
        val second = CountDownLatch(1)
        lib.duringFindSources = {
            val n = entered.incrementAndGet()
            order += n
            // The first look waits for a second to arrive. Serialised, none can, and it carries on
            // after the bound; unserialised, the second walks straight in and the order records it.
            if (n == 1) second.await(200, TimeUnit.MILLISECONDS) else second.countDown()
            order += -n
        }
        directory.acquire()

        val looks = listOf(thread { directory.sources() }, thread { directory.sources() })
        looks.forEach { it.join(1_000) }
        looks.forEach { assertFalse(it.isAlive, "a look never came back") }

        assertEquals(listOf(1, -1, 2, -2), order.toList(), "one look at a time through one finder")
        directory.release()
    }
}
