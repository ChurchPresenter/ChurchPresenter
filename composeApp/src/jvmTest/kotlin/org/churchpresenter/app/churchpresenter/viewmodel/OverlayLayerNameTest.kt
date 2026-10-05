package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The layer names a remote client may send to take one overlay down. */
class OverlayLayerNameTest {

    @Test
    fun `each overlay answers to its spellings`() {
        listOf("lowerthird", "lower_third", "lower-third", "graphics").forEach {
            assertEquals(Presenting.LOWER_THIRD, overlayForLayerName(it), it)
        }
        listOf("captions", "stt").forEach { assertEquals(Presenting.STT, overlayForLayerName(it), it) }
        listOf("announcements", "announcement").forEach {
            assertEquals(Presenting.ANNOUNCEMENTS, overlayForLayerName(it), it)
        }
    }

    @Test
    fun `case and surrounding space do not matter`() {
        assertEquals(Presenting.LOWER_THIRD, overlayForLayerName("  LowerThird "))
    }

    @Test
    fun `a name that is no overlay clears nothing`() {
        listOf("", "slide", "bible", "background").forEach { assertNull(overlayForLayerName(it), it) }
    }
}
