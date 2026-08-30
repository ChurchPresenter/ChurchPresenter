package org.churchpresenter.ndi

/**
 * What the app found when it went looking for an NDI Runtime.
 *
 * Four outcomes rather than a boolean, because they are four different things to tell an operator
 * and only one of them is a fault: [NotInstalled] is the ordinary first-run state and should read
 * as "here is where to get it", while [UnsupportedCpu] and [LoadFailed] genuinely are problems.
 */
sealed interface NdiRuntimeStatus {
    /** No NDI library on this machine — the expected state until the operator installs one. */
    data object NotInstalled : NdiRuntimeStatus

    /** A library was found at [path] but would not load (wrong architecture, or too old). */
    data class LoadFailed(val path: String) : NdiRuntimeStatus

    /** The runtime loaded and says this CPU cannot run it — NDI needs SSE4.2. */
    data object UnsupportedCpu : NdiRuntimeStatus

    /** Ready to send, running [version] out of [path]. */
    data class Ready(val version: String, val path: String) : NdiRuntimeStatus

    /** Whether outputs can actually be created. Only [Ready] can. */
    val isReady: Boolean get() = this is Ready
}

/**
 * Brings the runtime up once for the whole process and hands out [NdiSender]s over it.
 *
 * One instance per app. The runtime is a global in the native library — `NDIlib_initialize` is
 * refcounted but the version string, the CPU check and the buffer inside [JnaNdiLibrary] are not —
 * so senders share this rather than each loading their own.
 *
 * [loader] is how the library is obtained, injected so the suite drives the whole lifecycle against
 * a fake and only the real `JnaNdiLibrary.load` call stays uncovered.
 */
class NdiRuntimeHost(
    private val locate: (String) -> String? = NdiRuntime::detect,
    private val loader: (String) -> NdiLibrary? = JnaNdiLibrary::load,
) {
    private var library: NdiLibrary? = null

    var status: NdiRuntimeStatus = NdiRuntimeStatus.NotInstalled
        private set

    /**
     * Finds, loads and initializes the runtime, returning the resulting [status].
     *
     * Safe to call repeatedly — a second call with the runtime already up is a no-op returning the
     * same status, which is what lets the settings card offer a "look again" after an install
     * without risking a double initialize.
     */
    fun start(customPath: String = ""): NdiRuntimeStatus {
        if (status.isReady) return status
        val path = locate(customPath) ?: return NdiRuntimeStatus.NotInstalled.also { status = it }
        val lib = loader(path) ?: return NdiRuntimeStatus.LoadFailed(path).also { status = it }
        if (!lib.isSupportedCpu()) return NdiRuntimeStatus.UnsupportedCpu.also { status = it }
        if (!lib.initialize()) return NdiRuntimeStatus.LoadFailed(path).also { status = it }
        library = lib
        status = NdiRuntimeStatus.Ready(lib.version(), path)
        return status
    }

    /** A sender over the running runtime, or null when it is not [NdiRuntimeStatus.Ready]. */
    fun createSender(name: String, mode: NdiOutputMode, fps: Int): NdiSender? {
        val lib = library ?: return null
        return NdiSender(lib, name, mode, fps)
    }

    /** Takes the runtime down. The caller is responsible for having closed its senders first. */
    fun shutdown() {
        library?.destroy()
        library = null
        status = NdiRuntimeStatus.NotInstalled
    }
}
