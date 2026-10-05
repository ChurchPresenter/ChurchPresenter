package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.liveshow.Layer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The layer names a remote client may send to take one layer down. */
class LayerNameTest {

    @Test
    fun `each layer answers to its spellings`() {
        listOf("lowerthird", "lower_third", "lower-third", "graphics").forEach {
            assertEquals(Layer.GRAPHICS, layerForName(it), it)
        }
        listOf("captions", "stt").forEach { assertEquals(Layer.CAPTIONS, layerForName(it), it) }
        listOf("announcements", "announcement").forEach { assertEquals(Layer.ANNOUNCEMENTS, layerForName(it), it) }
        listOf("messages", "message").forEach { assertEquals(Layer.MESSAGES, layerForName(it), it) }
        listOf("props", "prop").forEach { assertEquals(Layer.PROPS, layerForName(it), it) }
        assertEquals(Layer.SLIDE, layerForName("slide"))
        assertEquals(Layer.MEDIA, layerForName("media"))
    }

    @Test
    fun `case and surrounding space do not matter`() {
        assertEquals(Layer.GRAPHICS, layerForName("  LowerThird "))
    }

    @Test
    fun `a name that is no layer clears nothing`() {
        listOf("", "bible", "background", "audio").forEach { assertNull(layerForName(it), it) }
    }
}
