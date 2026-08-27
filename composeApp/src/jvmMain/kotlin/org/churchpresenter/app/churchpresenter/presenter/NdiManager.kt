package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.ndi.NdiRuntimeStatus
import org.churchpresenter.settings.ScreenAssignment

/**
 * The app's single NDI runtime, and the senders opened over it.
 *
 * A process-level object for the same reason
 * [org.churchpresenter.app.churchpresenter.composables.DeckLinkManager] is one: the runtime is a
 * global in the native library, it is brought up once, and both the render wiring in `main.kt` and
 * the settings card that reports on it need to see the same instance without one being threaded
 * through the other.
 *
 * **It holds no logic.** Everything it appears to do is [NdiOutputRegistry]'s, which is an ordinary
 * class a test can build over a fake runtime — this is one instance of it, plus the shutdown hook,
 * which is the only part that genuinely has to be global.
 */
object NdiManager {
    private val registry = NdiOutputRegistry()

    private var shutdownHookRegistered = false

    val status: StateFlow<NdiRuntimeStatus> get() = registry.status

    fun ensureStarted(customPath: String = ""): NdiRuntimeStatus {
        val result = registry.ensureStarted(customPath)
        if (result.isReady) registerShutdownHook()
        return result
    }

    fun createRenderer(
        index: Int,
        assignment: ScreenAssignment,
        context: OffscreenOutputContext,
        screenAssignmentState: State<ScreenAssignment>,
        name: String,
    ): NdiVideoRenderer? = registry.createRenderer(index, assignment, context, screenAssignmentState, name)

    fun release(index: Int, renderer: NdiVideoRenderer) = registry.release(index, renderer)

    fun connectionCount(index: Int): Int = registry.connectionCount(index)

    fun stopAll() = registry.stopAll()

    private fun registerShutdownHook() {
        if (shutdownHookRegistered) return
        shutdownHookRegistered = true
        Runtime.getRuntime().addShutdownHook(Thread { registry.stopAll() })
    }
}
