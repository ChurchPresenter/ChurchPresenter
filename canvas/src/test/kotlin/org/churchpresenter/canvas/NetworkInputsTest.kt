package org.churchpresenter.canvas

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkInputsTest {

    @Test
    fun `with no library installed nothing is found and nothing can be received`() {
        val none = NetworkInputs.None

        assertNull(none.createNdiFinder())
        assertTrue(none.discoverOmtSources().isEmpty())
        assertNull(none.createOmtReceiver("omt://booth", preview = false))
        assertTrue(!none.ndiStatus.value.isReady && !none.omtStatus.value.isReady)
    }
}
