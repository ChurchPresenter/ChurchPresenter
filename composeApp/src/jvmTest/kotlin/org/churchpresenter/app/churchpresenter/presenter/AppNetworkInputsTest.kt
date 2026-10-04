package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.ndi.NdiBandwidth
import org.churchpresenter.ndi.NdiSourceInfo
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppNetworkInputsTest {

    @Test
    fun `before either library is started the canvas finds and receives nothing`() {
        assertTrue(!AppNetworkInputs.ndiStatus.value.isReady)
        assertNull(AppNetworkInputs.createNdiFinder())
        assertNull(AppNetworkInputs.createNdiReceiver(NdiSourceInfo("HOST (Cam)"), NdiBandwidth.HIGHEST, "test"))
        assertTrue(!AppNetworkInputs.omtStatus.value.isReady)
        assertTrue(AppNetworkInputs.discoverOmtSources().isEmpty())
        assertNull(AppNetworkInputs.createOmtReceiver("omt://host:6400", preview = true))
    }
}
