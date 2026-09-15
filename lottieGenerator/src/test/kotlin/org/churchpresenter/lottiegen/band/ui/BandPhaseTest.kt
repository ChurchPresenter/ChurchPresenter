package org.churchpresenter.lottiegen.band.ui

import org.churchpresenter.lottiegen.band.BandTimeline
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.ui.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The five phases as the Motion pane and the scrubber read them off a config and its timeline. */
class BandPhaseTest {

    private val cfg = BibleLottieGenConfig(
        bgInSeconds = 1f, textInSeconds = 0.5f, holdSeconds = 2f, textOutSeconds = 0.5f, bgOutSeconds = 1f,
    )
    private val timeline = BandTimeline.from(cfg)

    @Test
    fun `each phase reads and writes its own seconds`() {
        assertEquals(listOf(1f, 0.5f, 2f, 0.5f, 1f), BandPhase.entries.map { it.seconds(cfg) })
        for (phase in BandPhase.entries) {
            val changed = phase.with(cfg, 3.3f)
            assertEquals(3.3f, phase.seconds(changed), "$phase writes where it reads")
            BandPhase.entries.filter { it != phase }.forEach { other ->
                assertEquals(other.seconds(cfg), other.seconds(changed), "$phase leaves $other alone")
            }
        }
    }

    @Test
    fun `the phases start where the timeline says and the scrubber names the one under the head`() {
        val total = timeline.totalFrames.toFloat()
        assertEquals(0f, BandPhase.BAND_IN.startFraction(timeline))
        assertEquals(timeline.textStart / total, BandPhase.TEXT_IN.startFraction(timeline))
        assertEquals(timeline.holdStart / total, BandPhase.HOLD.startFraction(timeline))
        assertEquals(timeline.textOutStart / total, BandPhase.TEXT_OUT.startFraction(timeline))
        assertEquals(timeline.bgOutStart / total, BandPhase.BAND_OUT.startFraction(timeline))
        assertEquals(BandPhase.BAND_IN, BandPhase.at(timeline, 0f))
        assertEquals(BandPhase.BAND_IN, BandPhase.at(timeline, 0.1f), "the band takes the first fifth here")
        assertEquals(BandPhase.TEXT_IN, BandPhase.at(timeline, 0.25f))
        assertEquals(BandPhase.HOLD, BandPhase.at(timeline, 0.5f))
        assertEquals(BandPhase.TEXT_OUT, BandPhase.at(timeline, 0.75f))
        assertEquals(BandPhase.BAND_OUT, BandPhase.at(timeline, 0.99f))
        assertEquals(BandPhase.BAND_OUT, BandPhase.at(timeline, 1f))
    }

    @Test
    fun `every phase has a label from the bundle and a colour of its own`() {
        BandPhase.entries.forEach { assertTrue(it.label.isNotBlank(), "$it is labelled") }
        assertEquals(Strings.bandPhaseHold, BandPhase.HOLD.label)
        assertEquals(BandPhase.entries.size, BandPhase.entries.map { it.color }.toSet().size, "no two share a colour")
    }
}
