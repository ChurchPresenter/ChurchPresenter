package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.PresenterManager.LiveStateSnapshot
import org.churchpresenter.liveoutput.findSkiaLayer
import org.churchpresenter.outputguard.GuardFiles
import org.churchpresenter.outputguard.OUTPUT_GUARD_ARGUMENT
import org.churchpresenter.outputguard.OutputGuardLink
import org.churchpresenter.outputguard.RELAUNCHED_ARGUMENT
import org.churchpresenter.sharedui.utils.addGuardedShutdownHook
import java.awt.Component
import java.awt.Container
import java.awt.Rectangle
import java.io.File

/** The running output guard, for the output windows to register with; null while it is off. */
internal val LocalOutputGuard = compositionLocalOf<OutputGuardLink?> { null }

/**
 * The output guard (docs/SHOW_CONTROL.md, Output isolation): while dev mode and its setting are on,
 * a second process keeps a still of each output under its window, shows it if the app dies, and
 * starts the app again. This runs it for [content] -- the output windows register with it -- keeps it
 * told what is live, and hands it a still of each output a moment after anything on air changes,
 * with the live state an app it relaunches puts back. In such a relaunch ([relaunchedByGuard]), that
 * state goes back on air.
 */
@Composable
internal fun AppRootState.OutputGuardWiring(
    relaunchedByGuard: Boolean,
    startGuard: (GuardFiles) -> Process? = ::startGuardProcess,
    content: @Composable () -> Unit,
) {
    val projection = appSettings.projectionSettings
    val guard = rememberOutputGuard(enabled = devMode && projection.outputGuardEnabled, startGuard)
    SideEffect { guard?.takeOverOnHang = projection.outputGuardOnHang }
    if (guard != null) OutputGuardStills(guard, presenterManager)
    if (relaunchedByGuard) RestoreAfterRelaunch(presenterManager)
    CompositionLocalProvider(LocalOutputGuard provides guard, content = content)
}

/** Registers an output [window] with the guard for as long as it is composed, where it is on screen. */
@Composable
internal fun GuardedOutputWindow(guard: OutputGuardLink, window: Component) {
    DisposableEffect(guard, window) {
        guard.register(window, window::isShowing) { Rectangle(window.bounds) }
        onDispose { guard.unregister(window) }
    }
}

/**
 * The guard while [enabled], started off the event thread -- it opens a file and starts a process
 * ([startGuard]) -- and stopped when it is switched off, when the app quits, and from a shutdown hook
 * should the app end some other orderly way. Only a crash or a kill leaves it running, which is what
 * it is for.
 */
@Composable
internal fun rememberOutputGuard(enabled: Boolean, startGuard: (GuardFiles) -> Process?): OutputGuardLink? {
    var link by remember { mutableStateOf<OutputGuardLink?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val started = withContext(Dispatchers.IO) {
            OutputGuardLink(GuardFiles.forUser(), startGuard = startGuard, relaunchCommand = relaunchCommand()).start()
        }
        val hook = addGuardedShutdownHook("output-guard") { started.stop() }
        link = started
        try {
            awaitCancellation()
        } finally {
            started.stop()
            hook.remove()
            link = null
        }
    }
    return link
}

/**
 * Keeps [guard] told whether anything is live, and a moment after anything on air changes -- once
 * the transition has run -- hands it a still of each output and the live state, off the event
 * thread. The first pass runs as soon as the guard is up.
 */
@Composable
internal fun OutputGuardStills(guard: OutputGuardLink, presenterManager: PresenterManager) {
    LaunchedEffect(guard, presenterManager) {
        snapshotFlow {
            Triple(presenterManager.program.value, presenterManager.anythingLive, presenterManager.snapshotLiveState())
        }.collectLatest { (_, live, liveState) ->
            guard.live = live
            delay(STILL_AFTER_CHANGE_MS)
            withContext(Dispatchers.IO) {
                guard.saveLiveState(guardJson.encodeToString(LiveStateSnapshot.serializer(), liveState))
                guard.outputs().forEach { (id, window) ->
                    val still = (window as? Container)?.let(::captureStill) ?: return@forEach
                    guard.snapshot(id, still.width, still.height, still.bgra)
                }
            }
        }
    }
}

/** Puts back on air what the app that died had there, as its last still saved it. */
@Composable
internal fun RestoreAfterRelaunch(presenterManager: PresenterManager) {
    LaunchedEffect(presenterManager) {
        val saved = withContext(Dispatchers.IO) { readSavedLiveState(GuardFiles.forUser()) } ?: return@LaunchedEffect
        presenterManager.restoreLiveState(saved)
        Log.info("OutputGuard", "Restarted by the output guard: ${saved.mode} is back on air")
    }
}

/** The live state the guard's last still was saved with, or null when there is none or it is unreadable. */
internal fun readSavedLiveState(files: GuardFiles): LiveStateSnapshot? =
    runCatching { guardJson.decodeFromString(LiveStateSnapshot.serializer(), files.liveState.readText()) }.getOrNull()

/** An output's pixels as it last drew them: BGRA, four bytes each. */
internal class Still(val width: Int, val height: Int, val bgra: ByteArray)

/** What [window] last drew, read from its Skia surface as DeckLink reads its own; null if it cannot be. */
internal fun captureStill(window: Container): Still? = runCatching {
    val bitmap = findSkiaLayer(window)?.screenshot() ?: return@runCatching null
    try {
        val pixels = bitmap.peekPixels() ?: return@runCatching null
        Still(bitmap.width, bitmap.height, pixels.buffer.getBytes(0, bitmap.width * bitmap.height * BYTES_PER_PIXEL))
    } finally {
        bitmap.close()
    }
}.getOrNull()

/**
 * How to start the guard: the packaged app's own launcher, or -- run from the build -- this JVM with
 * its options and class path, each given the guard's folder after [OUTPUT_GUARD_ARGUMENT].
 */
internal fun guardCommand(
    dir: File,
    appPath: String? = System.getProperty(APP_PATH_PROPERTY),
    info: ProcessHandle.Info = ProcessHandle.current().info(),
): List<String> {
    val guard = listOf(OUTPUT_GUARD_ARGUMENT, dir.path)
    if (appPath != null) return listOf(appPath) + guard
    val arguments = info.arguments().orElse(emptyArray()).toList()
    val jvm = arguments.indexOf(MAIN_CLASS_NAME).takeIf { it >= 0 }?.let { arguments.subList(0, it + 1) }
        ?: listOf("-cp", System.getProperty("java.class.path").orEmpty(), MAIN_CLASS_NAME)
    return listOf(info.command().orElse(File(System.getProperty("java.home"), "bin/java").path)) + jvm + guard
}

/** How the guard starts this app again: the packaged launcher, or this JVM's own command line. */
internal fun relaunchCommand(
    appPath: String? = System.getProperty(APP_PATH_PROPERTY),
    info: ProcessHandle.Info = ProcessHandle.current().info(),
): List<String> {
    if (appPath != null) return listOf(appPath)
    val arguments = info.arguments().orElse(emptyArray()).filterNot { it == RELAUNCHED_ARGUMENT }
    return listOfNotNull(info.command().orElse(null)) + arguments
}

/** Starts the guard, its output going to a log in its folder. */
private fun startGuardProcess(files: GuardFiles): Process =
    ProcessBuilder(guardCommand(files.dir))
        .redirectErrorStream(true)
        .redirectOutput(File(files.dir, "guard.log"))
        .start()

private val guardJson = Json { ignoreUnknownKeys = true }

/** Where jpackage tells a packaged app the path of its own launcher. */
private const val APP_PATH_PROPERTY = "jpackage.app-path"

/** Long enough for a transition to finish, so the still is what stays up. */
private const val STILL_AFTER_CHANGE_MS = 1_500L

private const val BYTES_PER_PIXEL = 4
