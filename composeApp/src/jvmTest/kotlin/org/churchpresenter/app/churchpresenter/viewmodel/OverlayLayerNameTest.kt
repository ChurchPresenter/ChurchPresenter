package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.server.clearLayerOf
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The layer names a remote client may send to take one overlay down, and how they are read. */
class OverlayLayerNameTest {

    private val json = Json { ignoreUnknownKeys = true }

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

    @Test
    fun `a socket clear carries its layer in the payload`() {
        assertEquals("captions", clearLayerOf("""{"layer":"captions"}""", json))
    }

    @Test
    fun `a socket clear without a layer clears everything`() {
        assertNull(clearLayerOf("", json))
        assertNull(clearLayerOf("{}", json))
        assertNull(clearLayerOf("""{"layer":null}""", json))
        assertNull(clearLayerOf("not json", json))
    }
}
