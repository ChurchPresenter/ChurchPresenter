package org.churchpresenter.ndi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val CAMERA = NdiSourceInfo("BOOTH (Camera 1)", "192.168.1.20:5961")
private val GRAPHICS = NdiSourceInfo("BOOTH (Graphics)")

class NdiFinderTest {

    @Test
    fun `an unopened finder discovers nothing rather than reaching the runtime`() {
        val lib = FakeNdiLibrary().apply { discoverable += CAMERA }
        val finder = NdiFinder(lib)

        assertEquals(emptyList(), finder.sources())
        assertFalse(finder.isOpen)
        assertEquals(-1, lib.lastFindTimeoutMs, "the runtime should not have been asked at all")
    }

    @Test
    fun `an open finder reports what the network is advertising`() {
        val lib = FakeNdiLibrary().apply { discoverable += listOf(CAMERA, GRAPHICS) }
        val finder = NdiFinder(lib)

        assertTrue(finder.open())
        assertTrue(finder.isOpen)
        assertEquals(listOf(CAMERA, GRAPHICS), finder.sources())
    }

    @Test
    fun `a source appearing later shows up without reopening the finder`() {
        val lib = FakeNdiLibrary()
        val finder = NdiFinder(lib)
        finder.open()

        assertEquals(emptyList(), finder.sources(), "discovery starts empty, which is not an error")

        lib.discoverable += CAMERA
        assertEquals(listOf(CAMERA), finder.sources())
    }

    @Test
    fun `the wait passed to the runtime is the caller's, and defaults to not waiting`() {
        val lib = FakeNdiLibrary()
        val finder = NdiFinder(lib)
        finder.open()

        finder.sources()
        assertEquals(0, lib.lastFindTimeoutMs)

        finder.sources(timeoutMs = 250)
        assertEquals(250, lib.lastFindTimeoutMs)
    }

    @Test
    fun `opening twice creates one finder, so a redraw does not restart discovery`() {
        val lib = FakeNdiLibrary()
        val finder = NdiFinder(lib)

        assertTrue(finder.open())
        assertTrue(finder.open())
        assertEquals(1, lib.findersCreated.size)
    }

    @Test
    fun `closing destroys the handle and can be undone by opening again`() {
        val lib = FakeNdiLibrary()
        val finder = NdiFinder(lib)
        finder.open()
        val first = lib.findersCreated.single()

        finder.close()
        assertFalse(finder.isOpen)
        assertEquals(listOf(first), lib.findersDestroyed)

        assertTrue(finder.open())
        assertEquals(2, lib.findersCreated.size)
    }

    @Test
    fun `a runtime that refuses discovery leaves the finder closed and answering empty`() {
        val lib = FakeNdiLibrary(refuseFinder = true).apply { discoverable += CAMERA }
        val finder = NdiFinder(lib)

        assertFalse(finder.open())
        assertFalse(finder.isOpen)
        assertEquals(emptyList(), finder.sources())
    }

    @Test
    fun `closing an unopened finder does nothing`() {
        val lib = FakeNdiLibrary()
        NdiFinder(lib).close()
        assertEquals(emptyList(), lib.findersDestroyed)
    }
}
