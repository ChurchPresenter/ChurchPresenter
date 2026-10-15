package org.churchpresenter.outputguard

import java.awt.Color
import java.awt.Graphics
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.swing.JPanel
import javax.swing.JWindow
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
    val pid = state.appPid
    val app = ProcessHandle.of(pid).orElse(null)
    if (!state.isCurrent || app == null) exitProcess(0)
    SwingUtilities.invokeLater {
        GuardProgram(files, state, pid, appAlive = { app.isAlive }, relaunch = ::startProcess).start()
    }
    // Held here until the program exits the process: with no window up yet, AWT would otherwise
    // let the JVM end as soon as this returns.
    CountDownLatch(1).await()
}

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

/**
 * The guard's windows, one per output of the app: borderless, never focused, the same bounds as the
 * output, showing the output's last still -- black before the first. At normal level they sit under
 * the app's outputs, which are always on top; covering raises them to the same level and in front,
 * which is also in front of any other app's window on that screen.
 */
class GuardWindows(private val files: GuardFiles) {

    private val windows = mutableMapOf<Int, GuardWindow>()
    private val loader = Executors.newSingleThreadExecutor { task ->
        Thread(task, "output-guard-stills").apply { isDaemon = true }
    }
    private var covering = false

    /** Puts a window under each of [outputs], takes away the rest, and loads any newer still. */
    fun follow(outputs: List<GuardedOutput>) {
        val ids = outputs.map { it.id }.toSet()
        windows.keys.filterNot { it in ids }.forEach { id -> windows.remove(id)?.dispose() }
        outputs.forEach { output ->
            val window = windows.getOrPut(output.id) { GuardWindow() }
            if (window.bounds != output.bounds) window.bounds = output.bounds
            if (!window.isVisible) window.isVisible = true
            if (output.snapshotSeq != window.seq) {
                window.seq = output.snapshotSeq
                loader.execute {
                    val still = files.readSnapshot(output.id)
                    SwingUtilities.invokeLater { window.show(still) }
                }
            }
        }
    }

    /** Raises every window over everything on its screen. */
    fun cover() {
        if (covering) return
        covering = true
        windows.values.forEach { window ->
            window.isAlwaysOnTop = true
            window.toFront()
        }
    }

    /** Puts every window back under the app's outputs. */
    fun lower() {
        if (!covering) return
        covering = false
        windows.values.forEach { window ->
            window.isAlwaysOnTop = false
            window.toBack()
        }
    }

    fun close() {
        windows.values.forEach(JWindow::dispose)
        windows.clear()
        loader.shutdownNow()
    }

    /** How many windows are up, and whether they cover. */
    val count: Int get() = windows.size
    val isCovering: Boolean get() = covering
}

/** One output's backstop: its last still scaled to fill the window, or black. */
private class GuardWindow : JWindow() {
    var seq = -1L
    private var still: BufferedImage? = null

    init {
        focusableWindowState = false
        setAutoRequestFocus(false)
        background = Color.BLACK
        contentPane = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                g.color = Color.BLACK
                g.fillRect(0, 0, width, height)
                still?.let { image ->
                    (g as? java.awt.Graphics2D)?.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    g.drawImage(image, 0, 0, width, height, null)
                }
            }
        }
    }

    fun show(image: BufferedImage?) {
        if (image == null) return
        still = image
        repaint()
    }
}

/** Starts [command] with its output thrown away: the relaunched app writes its own logs. */
private fun startProcess(command: List<String>) {
    ProcessBuilder(command)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
}
