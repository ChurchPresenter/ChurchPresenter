package org.churchpresenter.liveoutput

import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.canvas.NetworkInputs
import org.churchpresenter.ndi.NdiBandwidth
import org.churchpresenter.ndi.NdiFinder
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.ndi.NdiSourceInfo
import org.churchpresenter.omt.OmtReceiver
import org.churchpresenter.omt.OmtRuntimeStatus

/** The Canvas's network sources, over the app's one NDI runtime and one OMT library. */
object AppNetworkInputs : NetworkInputs {
    override val ndiStatus: StateFlow<NdiRuntimeStatus> get() = NdiManager.status
    override fun createNdiFinder(): NdiFinder? = NdiManager.createFinder()
    override fun createNdiReceiver(source: NdiSourceInfo, bandwidth: NdiBandwidth, receiverName: String) =
        NdiManager.createReceiver(source, bandwidth, receiverName)

    override val omtStatus: StateFlow<OmtRuntimeStatus> get() = OmtManager.status
    override fun discoverOmtSources(): List<String> = OmtManager.discoverSources()
    override fun createOmtReceiver(address: String, preview: Boolean): OmtReceiver? =
        OmtManager.createReceiver(address, preview)
}
