package org.churchpresenter.app.churchpresenter

import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.telemetry.GpuInfo
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/*
 * Windows machines whose GPU can run neither ANGLE nor Direct3D 12 end up on skiko's OpenGL rung,
 * and the OpenGL drivers of that generation (Intel HD 3000 and the like) fault inside swapBuffers —
 * a native crash skiko cannot fall back from. When the main window's first frame shows skiko took
 * that rung on its own, the app remembers software drawing for this GPU and starts again with it;
 * asking for SOFTWARE skips every GPU rung, OpenGL included.
 */

internal const val SOFTWARE_RENDER_API = "SOFTWARE"

/** Passed to the relaunched app: the pid it waits for, so two copies never run side by side. */
internal const val REPLACING_PID_ARGUMENT = "--replacing-pid="

private const val FALLBACK_FILE = "render-fallback.properties"
private const val KEY_API = "api"
private const val KEY_GPU = "gpu"
private const val REPLACED_EXIT_WAIT_SECONDS = 20L

/**
 * The render API to switch to after the first frame, or null to keep this one: SOFTWARE when on
 * Windows the platform default was asked for ([requested] null) and skiko settled on OpenGL.
 */
internal fun renderFallbackAfterFirstFrame(osName: String, requested: String?, actual: String): String? =
    if (osName.lowercase().contains("win") && requested == null && actual == "OPENGL") SOFTWARE_RENDER_API else null

/** The render API saved for [gpu], or null when none was saved or it was saved for another GPU. */
internal fun savedRenderFallback(file: File, gpu: String?): String? {
    if (!file.isFile) return null
    val props = Properties()
    runCatching { file.inputStream().use { props.load(it) } }.onFailure { return null }
    return props.getProperty(KEY_API)?.takeIf { props.getProperty(KEY_GPU).orEmpty() == gpu.orEmpty() }
}

/** Saves [api] for [gpu], so a new GPU or driver setup gets the platform default again. */
internal fun saveRenderFallback(file: File, api: String, gpu: String?) {
    val props = Properties()
    props.setProperty(KEY_API, api)
    props.setProperty(KEY_GPU, gpu.orEmpty())
    file.outputStream().use { props.store(it, null) }
}

/** The pid the relaunched app waits for, from its arguments. */
internal fun replacedPid(args: Array<String>): Long? =
    args.firstOrNull { it.startsWith(REPLACING_PID_ARGUMENT) }?.removePrefix(REPLACING_PID_ARGUMENT)?.toLongOrNull()

internal fun renderFallbackFile(dataDir: File): File = File(dataDir, FALLBACK_FILE)

/** The adapter the fallback is saved against; null where it cannot be read. */
internal fun renderFallbackGpu(): String? = runCatching { GpuInfo.crashTags()["gpu.adapter"] }.getOrNull()

/** Blocks until the app this one replaces has exited, so it has let go of its lock and ports. */
internal fun awaitReplacedProcess(args: Array<String>, timeoutSeconds: Long = REPLACED_EXIT_WAIT_SECONDS) {
    val pid = replacedPid(args) ?: return
    ProcessHandle.of(pid).ifPresent { handle ->
        runCatching { handle.onExit().get(timeoutSeconds, TimeUnit.SECONDS) }
    }
}

/** [base] with this process's [pid] as the one to replace, in place of any it already carried. */
internal fun replacingCommand(base: List<String>, pid: Long): List<String> =
    base.filterNot { it.startsWith(REPLACING_PID_ARGUMENT) } + "$REPLACING_PID_ARGUMENT$pid"

/**
 * Saves [api] for this GPU, starts the app again and exits. Called once the main window has drawn,
 * before anything is live. A failed save or start keeps this session running on what it has.
 */
internal fun switchRenderApiAndRelaunch(
    api: String,
    actual: String,
    dataDir: File,
    gpu: String? = renderFallbackGpu(),
    relaunch: (List<String>) -> Unit = ::replaceThisProcess,
) {
    try {
        saveRenderFallback(renderFallbackFile(dataDir), api, gpu)
    } catch (e: IOException) {
        Log.warn("Startup", "Could not save the render fallback: ${e.message}")
        return
    }
    CrashReporter.reportWarning(
        "Render API fell back to OpenGL; switching to software",
        tags = mapOf("render.api.actual" to actual, "render.fallback" to api),
    )
    try {
        relaunch(replacingCommand(relaunchCommand(), ProcessHandle.current().pid()))
    } catch (e: IOException) {
        Log.warn("Startup", "Could not restart with $api drawing: ${e.message}")
    }
}

/** Starts [command] and exits this process, which the new one waits for. */
private fun replaceThisProcess(command: List<String>) {
    ProcessBuilder(command).inheritIO().start()
    Log.warn("Startup", "Restarting with a different render API")
    exitProcess(0)
}
