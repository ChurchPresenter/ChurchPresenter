package org.churchpresenter.liveoutput

import org.churchpresenter.liveshow.Cue
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Firing a clear group: its layers come down, every other layer stays up. */
class PresenterManagerClearGroupTest {

    private fun manager() = PresenterManager(showPresenterWindowInitially = false)

    @Test
    fun `a group clears its layers and leaves the rest up`() {
        val pm = manager()
        pm.setPresentingMode(Presenting.LYRICS)
        pm.setPresentingMode(Presenting.LOWER_THIRD)
        pm.setPropOn("logo", true)
        pm.clearGroup(ClearGroup("g", "Clear graphics", listOf("GRAPHICS", "PROPS")))
        assertFalse(pm.isLive(Presenting.LOWER_THIRD))
        assertEquals(emptySet(), pm.propsOnAir)
        assertTrue(pm.isLive(Presenting.LYRICS))
    }

    @Test
    fun `clear text takes the slide and the message down`() {
        val pm = manager()
        pm.showMessage(Cue.Message("Nursery"))
        pm.clearGroup(ClearGroup("g", "Clear text", listOf("SLIDE", "MESSAGES")))
        assertEquals(null, pm.messageOnAir)
        pm.setPresentingMode(Presenting.BIBLE)
        pm.setPropOn("logo", true)
        pm.clearGroup(ClearGroup("g", "Clear text", listOf("SLIDE", "MESSAGES")))
        assertEquals(Presenting.NONE, pm.slideContent.value)
        assertEquals(setOf("logo"), pm.propsOnAir)
    }

    @Test
    fun `a layer this build does not know, or cannot clear, is skipped, and the rest come in stack order`() {
        val group = ClearGroup("g", "G", listOf("MESSAGES", "HOLOGRAM", "BACKGROUND", "MEDIA", "AUDIO"))
        assertEquals(listOf(Layer.MEDIA, Layer.MESSAGES), group.knownLayers())
        assertFalse(Layer.BACKGROUND in CLEARABLE_LAYERS)
        assertFalse(Layer.AUDIO in CLEARABLE_LAYERS)
    }
}
