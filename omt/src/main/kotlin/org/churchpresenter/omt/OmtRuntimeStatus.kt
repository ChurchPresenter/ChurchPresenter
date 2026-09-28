package org.churchpresenter.omt

import java.io.File

/**
 * What the app found when it went looking for `libomt`.
 *
 * Three outcomes where NDI has four: `libomt` has no CPU check to fail. And [NotInstalled] is rarer
 * here than NDI's — the app bundles the library, so it means a platform or a checkout without it.
 */
sealed interface OmtRuntimeStatus {
    /** No `libomt` anywhere this run looked. */
    data object NotInstalled : OmtRuntimeStatus

    /** A library was found at [path] but would not load — wrong architecture, or `libvmx` missing. */
    data class LoadFailed(val path: String) : OmtRuntimeStatus

    /** Ready, running out of [path]; [bundled] when that is the app's own copy. */
    data class Ready(val path: String, val bundled: Boolean) : OmtRuntimeStatus

    /** Whether outputs and receivers can actually be created. Only [Ready] can. */
    val isReady: Boolean get() = this is Ready
}

/**
 * Loads `libomt` once for the whole process and hands out [OmtSender]s, [OmtReceiver]s and the one
 * [OmtDiscovery] over it.
 *
 * One instance per app, as `NdiRuntimeHost` is: the library's discovery and logging are process
 * globals, so the outputs on the Projection tab and the sources on the Canvas share this rather than
 * each loading their own.
 *
 * [locate] and [loader] are injected so the suite drives the whole lifecycle against a fake and only
 * the real `JnaOmtLibrary.load` call stays uncovered.
 */
class OmtRuntimeHost(
    private val locate: (customPath: String, bundledDir: String) -> String? = OmtRuntime::detect,
    private val loader: (String) -> OmtLibrary? = JnaOmtLibrary::load,
) {
    private var library: OmtLibrary? = null

    var status: OmtRuntimeStatus = OmtRuntimeStatus.NotInstalled
        private set

    /** The one discovery over the running library, or null before it is ready. */
    var discovery: OmtDiscovery? = null
        private set

    /**
     * Finds and loads the library, returning the resulting [status].
     *
     * [logFile] is where the library's own log goes — see [OmtLibrary.setLoggingFilename]; blank
     * writes none. [discoveryServer] is set before anything can discover, which matters: the library
     * reads it **once**, when its process-wide discovery is created, so a change takes effect at the
     * next launch. Blank leaves the setting alone rather than clearing it, so a server configured in
     * OMT's own `settings.xml` — shared with every OMT app on the machine — still applies.
     *
     * Safe to call repeatedly: once loaded, a second call changes nothing and returns the status.
     */
    fun start(
        customPath: String = "",
        bundledDir: String = "",
        logFile: String = "",
        discoveryServer: String = "",
    ): OmtRuntimeStatus {
        if (library != null) return status
        val path = locate(customPath, bundledDir)
        val lib = path?.let(loader)
        status = when {
            path == null -> OmtRuntimeStatus.NotInstalled
            lib == null -> OmtRuntimeStatus.LoadFailed(path)
            else -> {
                lib.setLoggingFilename(logFile.ifBlank { null })
                if (discoveryServer.isNotBlank()) lib.setDiscoveryServer(discoveryServer)
                library = lib
                discovery = OmtDiscovery(lib)
                OmtRuntimeStatus.Ready(path, bundled = isInside(path, bundledDir))
            }
        }
        return status
    }

    /**
     * A sender over the running library, or null when it is not [OmtRuntimeStatus.Ready].
     *
     * [product] and [version] are what receivers are told made the source.
     */
    fun createSender(
        name: String,
        mode: OmtOutputMode,
        fps: Int,
        quality: OmtQuality = OmtQuality.DEFAULT,
        product: String = "",
        version: String = "",
    ): OmtSender? {
        val lib = library ?: return null
        return OmtSender(lib, name, mode, fps, quality, product, version)
    }

    /** A receiver over the running library, or null when it is not [OmtRuntimeStatus.Ready]. */
    fun createReceiver(address: String, preview: Boolean = false): OmtReceiver? {
        val lib = library ?: return null
        return OmtReceiver(lib, address, preview)
    }

    /**
     * Stops the library's background threads. Once, at process exit, after every sender and receiver
     * is closed — `omt_shutdown` is not refcounted and nothing may be called after it.
     */
    fun shutdown() {
        library?.shutdown()
        library = null
        discovery = null
        status = OmtRuntimeStatus.NotInstalled
    }

    private fun isInside(path: String, dir: String): Boolean =
        dir.isNotBlank() && File(path).parentFile?.absoluteFile == File(dir).absoluteFile
}
