package org.churchpresenter.outputguard

import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.Executors
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities

/** One output's backstop window, as [GuardWindows] drives it. */
interface Backstop {
    /** Puts the window over [bounds] and shows it. */
    fun place(bounds: Rectangle)

    /** Shows [still] scaled to fill the window. */
    fun show(still: BufferedImage)

    /** Lifts the window over everything on its screen. */
    fun raise()

    /** Puts the window back at normal level, behind the rest. */
    fun lower()

    fun dispose()
}

/**
 * The guard's windows, one per output of the app, each the same bounds as its output and showing
 * the output's last still -- black before the first. At normal level they sit under the app's
 * outputs, which are always on top; covering raises them to the same level and in front, which is
 * also in front of any other app's window on that screen.
 *
 * [newBackstop] makes a window, and [loadStill] reads output `id`'s still and hands it on the event
 * thread to the function it is given: a test passes its own of each.
 */
class GuardWindows(
    files: GuardFiles,
    private val newBackstop: () -> Backstop = ::AwtBackstop,
    private val loadStill: (id: Int, then: (BufferedImage) -> Unit) -> Unit = readOffThread(files),
) {
    private val windows = mutableMapOf<Int, Backstop>()
    private val bounds = mutableMapOf<Int, Rectangle>()
    private val seqs = mutableMapOf<Int, Long>()
    private var covering = false

    /** Puts a window under each of [outputs], takes away the rest, and loads any newer still. */
    fun follow(outputs: List<GuardedOutput>) {
        val ids = outputs.map { it.id }.toSet()
        windows.keys.filterNot { it in ids }.forEach { id ->
            windows.remove(id)?.dispose()
            bounds.remove(id)
            seqs.remove(id)
        }
        outputs.forEach { output ->
            val window = windows.getOrPut(output.id, newBackstop)
            if (bounds[output.id] != output.bounds) {
                bounds[output.id] = output.bounds
                window.place(output.bounds)
            }
            if (seqs[output.id] != output.snapshotSeq) {
                seqs[output.id] = output.snapshotSeq
                loadStill(output.id, window::show)
            }
        }
    }

    /** Raises every window over everything on its screen. */
    fun cover() {
        if (covering) return
        covering = true
        windows.values.forEach(Backstop::raise)
    }

    /** Puts every window back under the app's outputs. */
    fun lower() {
        if (!covering) return
        covering = false
        windows.values.forEach(Backstop::lower)
    }

    fun close() {
        windows.values.forEach(Backstop::dispose)
        windows.clear()
    }

    /** How many windows are up, and whether they cover. */
    val count: Int get() = windows.size
    val isCovering: Boolean get() = covering

    private companion object {
        /** Reads a still on a thread of its own, then hands it over on the event thread. */
        fun readOffThread(files: GuardFiles): (Int, (BufferedImage) -> Unit) -> Unit {
            val loader = Executors.newSingleThreadExecutor { task ->
                Thread(task, "output-guard-stills").apply { isDaemon = true }
            }
            return { id, then ->
                loader.execute { files.readSnapshot(id)?.let { still -> SwingUtilities.invokeLater { then(still) } } }
            }
        }
    }
}

/** A backstop as a borderless, never-focused AWT window, black until it has a still. */
private class AwtBackstop : Backstop {
    private var still: BufferedImage? = null
    private val window = JWindow().apply {
        focusableWindowState = false
        setAutoRequestFocus(false)
        background = Color.BLACK
        contentPane = object : JPanel() {
            override fun paintComponent(g: Graphics) {
                g.color = Color.BLACK
                g.fillRect(0, 0, width, height)
                (g as Graphics2D).setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                )
                g.drawImage(still, 0, 0, width, height, null)
            }
        }
    }

    override fun place(bounds: Rectangle) {
        window.bounds = bounds
        if (!window.isVisible) window.isVisible = true
    }

    override fun show(still: BufferedImage) {
        this.still = still
        window.repaint()
    }

    override fun raise() {
        window.isAlwaysOnTop = true
        window.toFront()
    }

    override fun lower() {
        window.isAlwaysOnTop = false
        window.toBack()
    }

    override fun dispose() = window.dispose()
}
