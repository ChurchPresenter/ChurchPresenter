package org.churchpresenter.canvas

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.ndi.NdiBandwidth
import org.churchpresenter.ndi.NdiFinder
import org.churchpresenter.ndi.NdiReceiver
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.ndi.NdiSourceInfo
import org.churchpresenter.omt.OmtReceiver
import org.churchpresenter.omt.OmtRuntimeStatus

/**
 * The app's NDI and OMT libraries, as the Canvas takes sources back off the network with them.
 *
 * The libraries are loaded and owned by the app, which also sends its own outputs over them; the
 * Canvas only receives. The app installs its runtimes once at startup ([install]); until then
 * every call answers as an unloaded library does.
 */
interface NetworkInputs {
    val ndiStatus: StateFlow<NdiRuntimeStatus>
    fun createNdiFinder(): NdiFinder?
    fun createNdiReceiver(source: NdiSourceInfo, bandwidth: NdiBandwidth, receiverName: String): NdiReceiver?

    val omtStatus: StateFlow<OmtRuntimeStatus>
    fun discoverOmtSources(): List<String>
    fun createOmtReceiver(address: String, preview: Boolean): OmtReceiver?

    /** No library: nothing found, nothing received. */
    object None : NetworkInputs {
        override val ndiStatus: StateFlow<NdiRuntimeStatus> = MutableStateFlow(NdiRuntimeStatus.NotInstalled)
        override fun createNdiFinder(): NdiFinder? = null
        override fun createNdiReceiver(source: NdiSourceInfo, bandwidth: NdiBandwidth, receiverName: String) = null
        override val omtStatus: StateFlow<OmtRuntimeStatus> = MutableStateFlow(OmtRuntimeStatus.NotInstalled)
        override fun discoverOmtSources(): List<String> = emptyList()
        override fun createOmtReceiver(address: String, preview: Boolean): OmtReceiver? = null
    }

    companion object {
        @Volatile
        var current: NetworkInputs = None
            private set

        /** Called once by the app at startup, before any Canvas source is drawn. */
        fun install(inputs: NetworkInputs) {
            current = inputs
        }
    }
}
