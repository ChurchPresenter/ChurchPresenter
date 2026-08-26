package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.ndi.NdiRuntimeHost
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.settings.ScreenAssignment
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's single NDI Runtime, and the senders opened over it.
 *
 * A process-level object for the same reason [org.churchpresenter.app.churchpresenter.composables.DeckLinkManager]
 * is one: the runtime is a global in the native library, it is brought up once, and both the render
 * wiring in `main.kt` and the settings card that reports on it need to see the same instance without
 * one being threaded through the other. The behaviour itself is `:ndi`'s [NdiRuntimeHost], which is
 * ordinary injectable code and is where the suite lives; this holds one of those and nothing else.
 *
 * Everything here tolerates the runtime being absent, because on most machines it will be: NDI is
 * installed separately and this app ships none of it.
 */
object NdiManager {
    private val host = NdiRuntimeHost()

    private val _status = MutableStateFlow<NdiRuntimeStatus>(NdiRuntimeStatus.NotInstalled)

    /** What the last [ensureStarted] found. Read by the Projection settings card. */
    val status: StateFlow<NdiRuntimeStatus> = _status

    /** Live renderers by output index, so the shutdown hook can reach every one of them. */
    private val renderers = ConcurrentHashMap<Int, NdiVideoRenderer>()

    private var shutdownHookRegistered = false

    /**
     * Brings the runtime up if it is not already, and publishes what happened to [status].
     *
     * Idempotent while the runtime is up, so it is safe to call from a `LaunchedEffect` keyed on the
     * configured path, and safe for the settings card to call again after the operator installs the
     * runtime without restarting the app.
     */
    @Synchronized
    fun ensureStarted(customPath: String = ""): NdiRuntimeStatus {
        val result = host.start(customPath)
        _status.value = result
        if (result.isReady) registerShutdownHook()
        return result
    }

    /**
     * A renderer for the output at [index], or null when the runtime is not ready or refused to
     * create the sender.
     *
     * Replaces any renderer already registered at that index, stopping it first — the caller
     * remembers this on the settings that define the output, so a resolution or mode change arrives
     * here as a new renderer for the same index.
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

    /** Forgets the renderer at [index] if it is still the one registered there. */
    fun release(index: Int, renderer: NdiVideoRenderer) {
        renderers.remove(index, renderer)
    }

    /** How many receivers are watching the output at [index]. 0 when there is no such output. */
    fun connectionCount(index: Int): Int = renderers[index]?.connectionCount() ?: 0

    /**
     * Takes every source off the network.
     *
     * Registered as a JVM shutdown hook, as `DeckLinkManager` does, because a process that exits
     * without this leaves its sources advertised: every receiver holds the last frame it got and
     * shows a frozen lower third rather than nothing.
     */
    fun stopAll() {
        for (renderer in renderers.values.toList()) renderer.stop()
        renderers.clear()
    }

    private fun registerShutdownHook() {
        if (shutdownHookRegistered) return
        shutdownHookRegistered = true
        Runtime.getRuntime().addShutdownHook(Thread { stopAll() })
    }
}
