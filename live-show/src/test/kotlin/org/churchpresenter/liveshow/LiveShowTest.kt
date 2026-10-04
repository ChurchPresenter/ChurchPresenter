package org.churchpresenter.liveshow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LiveShowTest {

    private val announcement = Cue.Announcement("Welcome")
    private val background = Cue.Background(BackgroundSource.BIBLE)
    private val lowerThird = Cue.LowerThird("Speaker")
    private val slide = Cue.Web("https://example.org")

    @Test
    fun `a new show has nothing on air and nothing cued`() {
        val show = LiveShow()
        assertTrue(show.program.value.isEmpty())
        assertTrue(show.preview.value.isEmpty())
    }

    @Test
    fun `setting a cue puts it on its own layer`() {
        val show = LiveShow()
        show.set(lowerThird)
        assertEquals(mapOf(Layer.GRAPHICS to lowerThird), show.program.value)
    }

    @Test
    fun `setting one layer leaves the others alone`() {
        val show = LiveShow()
        show.set(slide)
        show.set(lowerThird)
        show.set(announcement)
        assertEquals(
            mapOf(Layer.SLIDE to slide, Layer.GRAPHICS to lowerThird, Layer.ANNOUNCEMENTS to announcement),
            show.program.value,
        )
    }

    @Test
    fun `a new cue on a layer replaces the old one`() {
        val show = LiveShow()
        show.set(Cue.LowerThird("Speaker"))
        show.set(Cue.LowerThird("Worship"))
        assertEquals(Cue.LowerThird("Worship"), show.program.value[Layer.GRAPHICS])
    }

    @Test
    fun `setting what is already on air changes nothing`() {
        val show = LiveShow()
        show.set(lowerThird)
        val before = show.program.value
        show.set(Cue.LowerThird("Speaker"))
        assertSame(before, show.program.value, "an equal cue must not replace the map, or every output redraws")
    }

    @Test
    fun `cueing leaves program alone`() {
        val show = LiveShow()
        show.set(slide)
        show.cue(lowerThird)
        assertEquals(mapOf(Layer.SLIDE to slide), show.program.value)
        assertEquals(mapOf(Layer.GRAPHICS to lowerThird), show.preview.value)
    }

    @Test
    fun `cueing the same thing twice changes nothing`() {
        val show = LiveShow()
        show.cue(lowerThird)
        val before = show.preview.value
        show.cue(Cue.LowerThird("Speaker"))
        assertSame(before, show.preview.value)
    }

    @Test
    fun `take moves every cued layer on air and empties preview`() {
        val show = LiveShow()
        show.set(announcement)
        show.cue(slide)
        show.cue(background)
        show.take()
        assertEquals(
            mapOf(Layer.SLIDE to slide, Layer.BACKGROUND to background, Layer.ANNOUNCEMENTS to announcement),
            show.program.value,
        )
        assertTrue(show.preview.value.isEmpty())
    }

    @Test
    fun `take on one layer moves only that layer`() {
        val show = LiveShow()
        show.cue(slide)
        show.cue(lowerThird)
        show.take(Layer.GRAPHICS)
        assertEquals(mapOf(Layer.GRAPHICS to lowerThird), show.program.value)
        assertEquals(mapOf(Layer.SLIDE to slide), show.preview.value)
    }

    @Test
    fun `take with nothing cued changes nothing`() {
        val show = LiveShow()
        show.set(slide)
        val before = show.program.value
        show.take()
        show.take(Layer.SLIDE)
        assertSame(before, show.program.value)
    }

    @Test
    fun `clearing a layer takes only that layer off air`() {
        val show = LiveShow()
        show.set(slide)
        show.set(lowerThird)
        show.clear(Layer.GRAPHICS)
        assertEquals(mapOf(Layer.SLIDE to slide), show.program.value)
    }

    @Test
    fun `clearing an empty layer changes nothing`() {
        val show = LiveShow()
        show.set(slide)
        val before = show.program.value
        show.clear(Layer.MESSAGES)
        assertSame(before, show.program.value)
    }

    @Test
    fun `clear all keeps the background by default`() {
        val show = LiveShow()
        show.set(background)
        show.set(slide)
        show.set(lowerThird)
        show.clearAll()
        assertEquals(mapOf(Layer.BACKGROUND to background), show.program.value)
    }

    @Test
    fun `clear all can take the background too`() {
        val show = LiveShow()
        show.set(background)
        show.set(slide)
        show.clearAll(keepBackground = false)
        assertTrue(show.program.value.isEmpty())
    }

    @Test
    fun `clear all with only the background up changes nothing`() {
        val show = LiveShow()
        show.set(background)
        val before = show.program.value
        show.clearAll()
        assertSame(before, show.program.value)
    }

    @Test
    fun `clear all leaves what is cued`() {
        val show = LiveShow()
        show.set(slide)
        show.cue(lowerThird)
        show.clearAll()
        assertEquals(mapOf(Layer.GRAPHICS to lowerThird), show.preview.value)
    }
}
