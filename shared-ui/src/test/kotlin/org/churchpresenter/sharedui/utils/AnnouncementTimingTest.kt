package org.churchpresenter.sharedui.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The seconds an announcement's Speed sets, and how a slide is timed from them (#991). */
class AnnouncementTimingTest {

    @Test
    fun `a slide starts just off one edge and ends just off the other`() {
        assertEquals(600f, slideTravelPx(containerPx = 1000f, contentPx = 200f))
        assertEquals(500f, slideTravelPx(containerPx = 1000f, contentPx = 0f), "nothing measured yet")
        assertEquals(500f, slideTravelPx(containerPx = 1000f, contentPx = -5f), "a negative size counts as none")
    }

    @Test
    fun `the text's leading edge crosses in the set seconds, so a longer line takes longer to leave`() {
        assertEquals(6_000, slidePassMs(crossingMs = 5_000, containerPx = 1000f, contentPx = 200f))
        assertEquals(10_000, slidePassMs(crossingMs = 5_000, containerPx = 1000f, contentPx = 1000f))
        assertEquals(5_000, slidePassMs(crossingMs = 5_000, containerPx = 1000f, contentPx = 0f))
    }

    @Test
    fun `the same seconds move every line at the same speed`() {
        val short = 1000f + 200f
        val long = 1000f + 1_600f
        val shortSpeed = short / slidePassMs(5_000, 1000f, 200f)
        val longSpeed = long / slidePassMs(5_000, 1000f, 1_600f)
        assertEquals(shortSpeed, longSpeed, 0.001f)
    }

    @Test
    fun `a container with no size yet passes in the set seconds`() {
        assertEquals(5_000, slidePassMs(crossingMs = 5_000, containerPx = 0f, contentPx = 200f))
    }

    @Test
    fun `a saved value is held to the slider's range`() {
        assertEquals(ANNOUNCEMENT_MIN_SECONDS_MS, announcementSecondsMs(500))
        assertEquals(12_000, announcementSecondsMs(12_000))
        assertEquals(ANNOUNCEMENT_MAX_SECONDS_MS, announcementSecondsMs(99_999))
    }

    @Test
    fun `the slider runs from the shortest time on the left to the longest on the right`() {
        assertEquals(1_000, announcementSecondsAt(0f))
        assertEquals(30_000, announcementSecondsAt(1f))
        assertEquals(0f, announcementSliderPosition(1_000))
        assertEquals(1f, announcementSliderPosition(30_000))
    }

    @Test
    fun `the slider gives each step of speed about the same room`() {
        // Doubling the time is the same distance along the slider wherever it starts.
        val oneToTwo = announcementSliderPosition(2_000) - announcementSliderPosition(1_000)
        val tenToTwenty = announcementSliderPosition(20_000) - announcementSliderPosition(10_000)
        assertEquals(oneToTwo, tenToTwenty, 0.001f)
        assertTrue(announcementSliderPosition(5_000) < 0.5f, "the fast end gets more room than the slow end")
    }

    @Test
    fun `the slider snaps to half seconds and finds its own value again`() {
        listOf(1_000, 1_500, 5_000, 12_000, 29_500, 30_000).forEach { ms ->
            assertEquals(ms, announcementSecondsAt(announcementSliderPosition(ms)), "$ms")
        }
        assertEquals(0, announcementSecondsAt(0.37f) % ANNOUNCEMENT_SECONDS_STEP_MS)
    }
}
