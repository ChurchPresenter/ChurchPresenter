package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.ndi.FakeNdiLibrary
import org.churchpresenter.ndi.NdiFinder
import org.churchpresenter.ndi.NdiSourceInfo
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
}
