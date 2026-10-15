package org.churchpresenter.outputguard

import java.io.File
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.system.exitProcess

/** The argument that starts the app as its own output guard, followed by the guard's folder. */
const val OUTPUT_GUARD_ARGUMENT = "--output-guard"

/** The argument a guard adds when it starts the app again, so the app puts back what was on air. */
const val RELAUNCHED_ARGUMENT = "--relaunched-by-output-guard"

/**
 * The guard process: runs until the app it watches quits, or until an app it relaunched has taken
 * over. Called from `main` before anything else, so nothing of the app -- Compose, the
 * single-instance lock, settings -- is ever started in this process.
 *
 * On macOS it is a background UI element: no Dock icon, no menu bar, never the active app.
 */
fun runOutputGuard(dir: File) {
    System.setProperty("apple.awt.UIElement", "true")
    val files = GuardFiles(dir)
    val state = GuardState.open(files.state)
    val app = appToWatch(state) ?: exitProcess(0)
    SwingUtilities.invokeLater {
        GuardProgram(files, state, app.pid(), appAlive = app::isAlive, relaunch = ::startProcess).start()
    }
    // Held here until the program exits the process: with no window up yet, AWT would otherwise
    // let the JVM end as soon as this returns.
    CountDownLatch(1).await()
}

/**
 * The app [state] names, when it was written by this build and the process is still there; [find]
 * looks a pid up, so a test can be the system.
 */
fun appToWatch(
    state: GuardState,
    find: (Long) -> ProcessHandle? = { ProcessHandle.of(it).orElse(null) },
): ProcessHandle? = if (state.isCurrent) find(state.appPid) else null

/**
 * What the guard does, ten times a second on the event thread: [decide], then move its windows
 * under the app's outputs, cover them, or leave. [appAlive] and [relaunch] are the process
 * calls, so a test can be the app.
 */
class GuardProgram(
    private val files: GuardFiles,
    private val state: GuardState,
    private val appPid: Long,
    private val appAlive: () -> Boolean,
    private val relaunch: (List<String>) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val exit: () -> Unit = { exitProcess(0) },
    private val windows: GuardWindows = GuardWindows(files),
) {
    private var relaunched = false
    private var successorSeenAt: Long? = null
    private val timer = Timer(TICK_MS) { tick() }

    fun start() {
        timer.start()
    }

    /** One look at the app, and what follows from it. */
    fun tick() {
        val now = clock()
        if (relaunched) {
            if (state.appPid != appPid && successorSeenAt == null) successorSeenAt = now
            if (successorTookOver(state, appPid, successorSeenAt, now)) leave()
            return
        }
        when (decide(appAlive(), state, now)) {
            GuardAction.FOLLOW -> {
                windows.follow(state.outputs)
                windows.lower()
            }
            GuardAction.COVER_HANG -> windows.cover()
            GuardAction.COVER_DEATH -> {
                windows.cover()
                relaunched = true
                files.readRelaunchCommand().takeIf { it.isNotEmpty() }?.let { relaunch(it + RELAUNCHED_ARGUMENT) }
            }
            GuardAction.LEAVE -> leave()
        }
    }

    private fun leave() {
        timer.stop()
        windows.close()
        exit()
    }

    private companion object {
        const val TICK_MS = 100
    }
}

/** Starts [command] with its output thrown away: the relaunched app writes its own logs. */
private fun startProcess(command: List<String>) {
    ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
}
