package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The high-water marks of the native resources the app holds on someone else's behalf.
 *
 * Three leaks were found in this area in one week — an ffmpeg process holding a camera, a libvlc
 * decoder, and a headless browser leaked on every viewport resize — and every one of them leaks a
 * process or a native handle rather than heap. That is why the signal watched here is a count and
 * not memory: a JVM heap chart was flat through all three.
 *
 * [ResourceCensus] is process-global and the suite shares a JVM, so every test resets it.
 */
class ResourceCensusTest {

    @BeforeTest fun clear() = ResourceCensus.reset()

    @AfterTest fun clearAfter() = ResourceCensus.reset()

    @Test
    fun `the census remembers the highest count, not the last one`() {
        ResourceCensus.record(SharedResource.VIDEO_DECODE, 1)
        ResourceCensus.record(SharedResource.VIDEO_DECODE, 5)
        ResourceCensus.record(SharedResource.VIDEO_DECODE, 2)

        assertEquals(
            5, ResourceCensus.peak(SharedResource.VIDEO_DECODE),
            "a leak is visible in how high the count climbed, not in what it happens to be at shutdown",
        )
    }

    @Test
    fun `resources are counted apart from each other`() {
        ResourceCensus.record(SharedResource.CAMERA_CAPTURE, 2)
        ResourceCensus.record(SharedResource.BROWSER_SOURCE, 9)

        assertEquals(2, ResourceCensus.peak(SharedResource.CAMERA_CAPTURE))
        assertEquals(9, ResourceCensus.peak(SharedResource.BROWSER_SOURCE))
        assertEquals(0, ResourceCensus.peak(SharedResource.NDI_RECEIVER), "one never opened is zero, not absent")
    }

    @Test
    fun `a snapshot names every resource, including the ones never opened`() {
        ResourceCensus.record(SharedResource.SCREEN_GRAB, 1)

        assertEquals(
            SharedResource.entries.toSet(), ResourceCensus.snapshot().keys,
            "a missing key in a report reads as a resource that does not exist rather than one at zero",
        )
    }

    // ── What counts as a leak ─────────────────────────────────────────────────────────────────

    @Test
    fun `an ordinary booth is not reported`() {
        val ordinary = mapOf(
            SharedResource.CAMERA_CAPTURE to 2,
            SharedResource.VIDEO_DECODE to 3,
            SharedResource.SCREEN_GRAB to 1,
            SharedResource.NDI_RECEIVER to 2,
            SharedResource.BROWSER_SOURCE to 1,
        )

        assertFalse(
            looksLikeLeak(ordinary),
            "a report that arrives from every install is one nobody reads by the second week",
        )
    }

    @Test
    fun `a count a scene cannot account for is reported`() {
        assertTrue(looksLikeLeak(mapOf(SharedResource.VIDEO_DECODE to 8)))
    }

    @Test
    fun `one leaking resource is enough, whatever the others are doing`() {
        val onlyBrowsersLeaked = mapOf(
            SharedResource.CAMERA_CAPTURE to 1,
            SharedResource.BROWSER_SOURCE to 40,
        )

        assertTrue(
            looksLikeLeak(onlyBrowsersLeaked),
            "the browser leak fired on every resize, so its count outran everything else",
        )
    }

    @Test
    fun `an app that opened nothing is not a leak`() {
        assertFalse(looksLikeLeak(emptyMap()))
        assertFalse(looksLikeLeak(SharedResource.entries.associateWith { 0 }))
    }

    @Test
    fun `the rendered census names every resource and carries only counts`() {
        val rendered = renderCensus(mapOf(SharedResource.VIDEO_DECODE to 12))

        assertEquals(
            """
            camera_capture=0
            video_decode=12
            screen_grab=0
            ndi_receiver=0
            browser_source=0
            """.trimIndent(),
            rendered,
        )
    }
}
