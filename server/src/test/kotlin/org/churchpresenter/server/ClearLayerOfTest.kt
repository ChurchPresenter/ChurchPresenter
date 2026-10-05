package org.churchpresenter.server

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How a WebSocket `clear` names the one layer it takes down, if any. */
class ClearLayerOfTest {

    private val json = Json { ignoreUnknownKeys = true }

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
