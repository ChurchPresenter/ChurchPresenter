package org.churchpresenter.liveoutput

import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Clearing one layer and leaving the rest up, and the layers [PresenterManager.liveShow] holds whole
 * beside the derived ones.
 */
class PresenterManagerClearLayerTest {

    private fun manager() = PresenterManager(showPresenterWindowInitially = false)

    @Test
    fun `clearing the slide leaves the overlays up`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.clearLayer(Layer.SLIDE)
        assertEquals(Presenting.NONE, pm.slideContent.value)
        assertTrue(pm.isLive(Presenting.LOWER_THIRD))
        assertEquals(Presenting.LOWER_THIRD, pm.lastLive.value, "the newest left up")
        assertNull(pm.program.value[Layer.BACKGROUND], "the song's background goes with it")
    }

    @Test
    fun `clearing the slide with nothing over it leaves nothing live`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.BIBLE)
        pm.clearLayer(Layer.SLIDE)
        assertFalse(pm.anythingLive)
        assertEquals(Presenting.NONE, pm.lastLive.value)
    }

    @Test
    fun `media is cleared on its own layer, not the slide's`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.PICTURES)
        pm.clearLayer(Layer.SLIDE)
        assertEquals(Presenting.PICTURES, pm.slideContent.value, "pictures are on the media layer")
        pm.clearLayer(Layer.MEDIA)
        assertEquals(Presenting.NONE, pm.slideContent.value)
    }

    @Test
    fun `each overlay layer clears its own overlay`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        listOf(Presenting.STT, Presenting.LOWER_THIRD, Presenting.ANNOUNCEMENTS).forEach(pm::setPresentingMode)
        pm.clearLayer(Layer.GRAPHICS)
        assertFalse(pm.isLive(Presenting.LOWER_THIRD))
        pm.clearLayer(Layer.CAPTIONS)
        assertFalse(pm.isLive(Presenting.STT))
        pm.clearLayer(Layer.ANNOUNCEMENTS)
        assertFalse(pm.isLive(Presenting.ANNOUNCEMENTS))
        assertEquals(Presenting.LYRICS, pm.slideContent.value)
    }

    @Test
    fun `the background is not cleared on its own`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.BIBLE)
        pm.clearLayer(Layer.BACKGROUND)
        assertEquals(Presenting.BIBLE, pm.slideContent.value)
        assertTrue(Layer.BACKGROUND in pm.program.value)
    }

    @Test
    fun `a layer held whole is on air beside the derived ones, and clears on its own`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.liveShow.set(Cue.Message("Nursery: #42"))
        assertEquals(Cue.Message("Nursery: #42"), pm.program.value[Layer.MESSAGES])
        assertTrue(Layer.SLIDE in pm.program.value)
        pm.clearLayer(Layer.MESSAGES)
        assertNull(pm.program.value[Layer.MESSAGES])
        assertEquals(Presenting.LYRICS, pm.slideContent.value)
    }

    @Test
    fun `clearing the display takes the layers held whole down too`() {
        val pm = manager()
        pm.liveShow.set(Cue.Message("Nursery: #42"))
        assertTrue(pm.anythingLive)
        pm.setPresentingMode(Presenting.NONE)
        assertTrue(pm.program.value.isEmpty())
        assertFalse(pm.anythingLive)
    }
}
