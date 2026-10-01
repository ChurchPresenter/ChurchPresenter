package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import org.churchpresenter.app.churchpresenter.BuildConfig
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.app.churchpresenter.composables.appResourcesDir
import org.churchpresenter.sharedui.utils.addGuardedShutdownHook
import org.churchpresenter.omt.OmtReceiver
import org.churchpresenter.omt.OmtRuntimeStatus
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import java.io.File

/** Where the bundled `libomt` sits inside the per-OS app resources directory. */
private const val BUNDLED_OMT_DIR = "omt"

/**
 * Moves last run's [log] aside to `<name>.1`, replacing any older one, so this run starts empty.
 *
 * `libomt` never trims its log, and it is not quiet: a Canvas layer whose source has gone away is
 * retried by the library every couple of seconds, two lines each time — about 3 KB a minute for the
 * rest of the service (measured on Windows). Two runs is what a support conversation needs, and it
 * bounds the file by what one run can write rather than letting it grow for as long as the app is
 * installed. A failed move is not an error worth stopping for: the library then appends, as before.
 */
internal fun rotateOmtLog(log: File) {
    log.parentFile?.mkdirs()
    if (!log.isFile) return
    val previous = File(log.parentFile, log.name + ".1")
    runCatching {
        previous.delete()
        log.renameTo(previous)
    }
}

/**
 * The app's single `libomt`: the senders opened over it and the receivers the Canvas takes sources
 * back off the network with.
 *
 * A process-level object for the reason [NdiManager] is one, and like that one it holds no logic —
 * everything is [OmtOutputRegistry]'s, plus the shutdown hook and where the bundled copy lives.
 */
object OmtManager {
    private val registry = OmtOutputRegistry()

    private var shutdownHookRegistered = false

    val status: StateFlow<OmtRuntimeStatus> get() = registry.status

    /**
     * Loads the library — [customPath] if the operator set one, otherwise the copy bundled with the
     * app — pointing its log at [logFile] and its discovery at [discoveryServer].
     *
     * Blocking — a `Native.load` of an 18 MB library — so callers are off the UI thread.
     */
    fun ensureStarted(customPath: String = "", discoveryServer: String = ""): OmtRuntimeStatus {
        val result = registry.ensureStarted(
            customPath = customPath,
            bundledDir = bundledDir()?.absolutePath.orEmpty(),
            logFile = logFile.absolutePath,
            discoveryServer = discoveryServer,
        )
        if (result.isReady) registerShutdownHook()
        return result
    }

    /** A renderer for output [index], telling receivers it is this app, at this version, that sends it. */
    fun createRenderer(
        index: Int,
        assignment: ScreenAssignment,
        context: OffscreenOutputContext,
        screenAssignmentState: State<ScreenAssignment>,
        name: String,
    ): OmtVideoRenderer? = registry.createRenderer(
        index, assignment, context, screenAssignmentState, name,
        product = Constants.SERVER_APP_NAME,
        version = BuildConfig.APP_VERSION,
    )

    fun release(index: Int, renderer: OmtVideoRenderer) = registry.release(index, renderer)

    /** Every OMT source on the network so far — the Canvas source picker's list. */
    fun discoverSources(): List<String> = registry.discoverSources()

    /** A receiver over the app's library — one OMT layer on the Canvas. */
    fun createReceiver(address: String, preview: Boolean = false): OmtReceiver? =
        registry.createReceiver(address, preview)

    fun receiverCount(index: Int): Int = registry.receiverCount(index)

    fun addressOf(index: Int): String = registry.addressOf(index)

    /** `<app resources>/omt`, where `fetchBundledOmt` put the library, or null when this run has none. */
    private fun bundledDir(): File? = appResourcesDir()?.let { File(it, BUNDLED_OMT_DIR) }?.takeIf { it.isDirectory }

    /**
     * The library's own log, in the app's own folder. Left alone, `libomt` writes a new file per
     * process into a `~/.OMT/logs` the operator never asked for; this keeps it where a support
     * conversation would look for it.
     *
     * Started fresh each run, the last run kept beside it — see [rotateOmtLog]. Resolved once, so
     * the rotation happens once per process however often [ensureStarted] is called.
     */
    private val logFile: File by lazy {
        File(System.getProperty("user.home"), ".churchpresenter/omt.log").also(::rotateOmtLog)
    }

    private fun registerShutdownHook() {
        if (shutdownHookRegistered) return
        shutdownHookRegistered = true
        addGuardedShutdownHook("omt") { registry.stopAll() }
    }
}
