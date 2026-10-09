package org.churchpresenter.liveoutput

import org.churchpresenter.ndi.NdiSourceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the NDI and OMT managers answer before either library has been started, which is how the
 * settings cards and the Canvas meet them on a machine without a runtime: nobody watching, no
 * receiver to hand out, and stopping nothing is harmless. Nothing here loads a native library.
 */
class NetworkOutputManagersTest {

    @Test
    fun `before NDI starts, no output has receivers and no receiver can be made`() {
        assertEquals(0, NdiManager.connectionCount(0))
        assertNull(NdiManager.createReceiver(NdiSourceInfo("Camera 1")))
        NdiManager.stopAll()
        assertEquals(0, NdiManager.connectionCount(0))
    }

    @Test
    fun `before OMT starts, no output is watched, advertised or received`() {
        assertEquals(0, OmtManager.receiverCount(0))
        assertEquals("", OmtManager.addressOf(0))
        assertNull(OmtManager.createReceiver("omt://studio:6400"))
    }
}
