package org.churchpresenter.lottiegen.band

import org.churchpresenter.lottiegen.lottie.LottieBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class BandTimelineTest {

    @Test
    fun `segments follow one another and add up to the total`() {
        val t = BandTimeline(bgInFrames = 10, textInFrames = 20, holdFrames = 30, textOutFrames = 40, bgOutFrames = 50)
        assertEquals(10, t.textStart)
        assertEquals(30, t.holdStart)
        assertEquals(60, t.textOutStart)
        assertEquals(100, t.bgOutStart)
        assertEquals(150, t.totalFrames)
        assertEquals(90, t.bandHoldFrames, "the band holds through the text's in, hold and out")
        assertEquals(2.5f, t.totalSeconds)
    }

    @Test
    fun `seconds become frames at sixty per second, never fewer than one`() {
        val t = BandTimeline.from(
            BibleLottieGenConfig(
                bgInSeconds = 0.5f, textInSeconds = 0.25f, holdSeconds = 0f, textOutSeconds = 0.001f, bgOutSeconds = 1f,
            ),
        )
        assertEquals(30, t.bgInFrames)
        assertEquals(15, t.textInFrames)
        assertEquals(1, t.holdFrames, "a zero-length segment is still one frame, so no two keyframes share a time")
        assertEquals(1, t.textOutFrames)
        assertEquals(60, t.bgOutFrames)
    }

    @Test
    fun `the five markers are written in order with their starts and lengths`() {
        val t = BandTimeline(bgInFrames = 10, textInFrames = 20, holdFrames = 30, textOutFrames = 40, bgOutFrames = 50)
        val builder = LottieBuilder(100, 50, t.frameRate)
        t.emitMarkers(builder)
        val doc = builder.toJson()
        assertEquals(
            mapOf(
                "bg_in" to (0 to 10), "text_in" to (10 to 20), "hold" to (30 to 30),
                "text_out" to (60 to 40), "bg_out" to (100 to 50),
            ),
            BandJson.markers(doc),
        )
    }
}
