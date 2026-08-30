package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.ndi.NdiRuntimeHost
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.settings.ScreenAssignment
import java.util.concurrent.ConcurrentHashMap

/**
 * One NDI runtime and the renderers opened over it, keyed by output index.
 *
 * A class rather than the object it is reached through, so its bookkeeping is testable: which
 * renderer answers for which index, what replacing one does to the old one, and what
 * [connectionCount] returns for an index that has no renderer. That is the code a "No receivers"
 * report lands in, and while it lived inside [NdiManager] — an `object` hardcoding its own
 * [NdiRuntimeHost] — nothing could reach it without a real runtime installed.
 *
 * [host] is the seam. `NdiRuntimeHost` already takes its own `locate`/`loader` lambdas, so a test
 * builds one over a fake library and drives this class end to end with no NDI installed. That is a
 * constructor parameter, not the ad-hoc mutable `internal var` on a singleton that AGENT.md rules
 * out — nothing has to be restored afterwards because nothing is global.
 */
class NdiOutputRegistry(private val host: NdiRuntimeHost = NdiRuntimeHost()) {

    private val _status = MutableStateFlow<NdiRuntimeStatus>(NdiRuntimeStatus.NotInstalled)

    /** What the last [ensureStarted] found. Read by the Projection settings card. */
    val status: StateFlow<NdiRuntimeStatus> = _status

    /** Live renderers by output index, so [stopAll] can reach every one of them. */
    private val renderers = ConcurrentHashMap<Int, NdiVideoRenderer>()

    /**
     * Brings the runtime up if it is not already, and publishes what happened to [status].
     *
     * Idempotent while the runtime is up, so it is safe from a `LaunchedEffect` keyed on the
     * configured path, and safe for the settings card to call again after the operator installs the
     * runtime without restarting the app.
     */
    @Synchronized
    fun ensureStarted(customPath: String = ""): NdiRuntimeStatus {
        val result = host.start(customPath)
        _status.value = result
        return result
    }

    /**
     * A renderer for the output at [index], or null when the runtime is not ready or refused to
     * create the sender.
     *
     * Replaces any renderer already registered at that index, **stopping it first** — the caller
     * remembers this on the settings that define the output, so a resolution or mode change arrives
     * here as a new renderer for the same index and the old source has to leave the network.
     */
    @Synchronized
    fun createRenderer(
        index: Int,
        assignment: ScreenAssignment,
        context: OffscreenOutputContext,
        screenAssignmentState: State<ScreenAssignment>,
        name: String,
    ): NdiVideoRenderer? {
        val sender = host.createSender(name, NdiVideoRenderer.modeOf(assignment), assignment.ndiFps) ?: return null
        val renderer = NdiVideoRenderer(
            sender = sender,
            context = context,
            screenAssignmentState = screenAssignmentState,
            width = assignment.ndiWidth,
            height = assignment.ndiHeight,
            fps = assignment.ndiFps,
        )
        renderers.put(index, renderer)?.stop()
        return renderer
    }

    /**
     * Forgets the renderer at [index] **if it is still the one registered there**.
     *
     * Conditional on purpose: a settings change composes the replacement before disposing the old
     * one, so the old renderer's dispose arrives after the new one is already registered. An
     * unconditional remove would deregister the live output and leave [connectionCount] answering
     * 0 for a source that is on the network.
     */
    fun release(index: Int, renderer: NdiVideoRenderer) {
        renderers.remove(index, renderer)
    }

    /** How many receivers are watching the output at [index]. 0 when there is no such output. */
    fun connectionCount(index: Int): Int = renderers[index]?.connectionCount() ?: 0

    /** Whether an output is registered at [index] — the thing a "No receivers" report turns on. */
    fun hasRenderer(index: Int): Boolean = renderers.containsKey(index)

    /** How many outputs are registered. */
    val size: Int get() = renderers.size

    /**
     * Takes every source off the network.
     *
     * Called from a JVM shutdown hook as well as on dispose, because a process that exits without
     * it leaves its sources advertised: every receiver holds the last frame it got and shows a
     * frozen lower third rather than nothing.
     */
    fun stopAll() {
        for (renderer in renderers.values.toList()) renderer.stop()
        renderers.clear()
    }
}
