package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.omt.OmtReceiver
import org.churchpresenter.omt.OmtRuntimeHost
import org.churchpresenter.omt.OmtRuntimeStatus
import org.churchpresenter.settings.ScreenAssignment
import java.util.concurrent.ConcurrentHashMap

/**
 * One `libomt`, the renderers opened over it keyed by output index, and the receivers and discovery
 * the Canvas uses over the same one.
 *
 * [NdiOutputRegistry]'s twin and a class for the same reason: its bookkeeping is testable over a
 * [OmtRuntimeHost] built on a fake library, with no OMT installed and nothing global to restore.
 */
class OmtOutputRegistry(private val host: OmtRuntimeHost = OmtRuntimeHost()) {

    private val _status = MutableStateFlow<OmtRuntimeStatus>(OmtRuntimeStatus.NotInstalled)

    /** What the last [ensureStarted] found. Read by the Projection settings card. */
    val status: StateFlow<OmtRuntimeStatus> = _status

    private val renderers = ConcurrentHashMap<Int, OmtVideoRenderer>()

    /**
     * Loads the library if it is not already, and publishes what happened to [status].
     *
     * Idempotent once loaded — a second call changes nothing — so it is safe from a
     * `LaunchedEffect` keyed on the settings it takes.
     */
    @Synchronized
    fun ensureStarted(
        customPath: String = "",
        bundledDir: String = "",
        logFile: String = "",
        discoveryServer: String = "",
    ): OmtRuntimeStatus {
        val result = host.start(customPath, bundledDir, logFile, discoveryServer)
        _status.value = result
        return result
    }

    /**
     * A renderer for the output at [index], or null when the library is not ready or refused the
     * sender. Replaces any renderer already at that index, **stopping it first**, for the reason
     * [NdiOutputRegistry.createRenderer] gives.
     */
    @Synchronized
    fun createRenderer(
        index: Int,
        assignment: ScreenAssignment,
        context: OffscreenOutputContext,
        screenAssignmentState: State<ScreenAssignment>,
        name: String,
        product: String = "",
        version: String = "",
    ): OmtVideoRenderer? {
        val sender = host.createSender(
            name = name,
            mode = OmtVideoRenderer.modeOf(assignment),
            fps = assignment.omtFps,
            quality = OmtVideoRenderer.qualityOf(assignment),
            product = product,
            version = version,
        ) ?: return null
        val renderer = OmtVideoRenderer(
            sender = sender,
            context = context,
            screenAssignmentState = screenAssignmentState,
            width = assignment.omtWidth,
            height = assignment.omtHeight,
            fps = assignment.omtFps,
        )
        renderers.put(index, renderer)?.stop()
        return renderer
    }

    /** Forgets the renderer at [index] **if it is still the one registered there**; see NDI's. */
    fun release(index: Int, renderer: OmtVideoRenderer) {
        renderers.remove(index, renderer)
    }

    /** Every OMT source discovery knows about, or empty when the library is not ready. */
    fun discoverSources(): List<String> = host.discovery?.sources().orEmpty()

    /** A receiver for [address] over the same library, or null when it is not ready. */
    fun createReceiver(address: String, preview: Boolean): OmtReceiver? = host.createReceiver(address, preview)

    /** How many receivers are watching the output at [index]. 0 when there is no such output. */
    fun receiverCount(index: Int): Int = renderers[index]?.receiverCount() ?: 0

    /** The network name of the output at [index], or blank when there is none. */
    fun addressOf(index: Int): String = renderers[index]?.address().orEmpty()

    /** Whether an output is registered at [index]. */
    fun hasRenderer(index: Int): Boolean = renderers.containsKey(index)

    /** How many outputs are registered. */
    val size: Int get() = renderers.size

    /**
     * Takes every source off the network — on dispose, and from the JVM shutdown hook.
     *
     * Deliberately not followed by `omt_shutdown`. The header allows that only once every sender
     * **and receiver** is gone, and a Canvas layer's capture loop may still be inside `omt_receive`
     * when the hook runs; the process exiting takes the library's threads with it regardless.
     */
    fun stopAll() {
        for (renderer in renderers.values.toList()) renderer.stop()
        renderers.clear()
    }
}
