package org.churchpresenter.app.churchpresenter.presenter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JPanel
import java.awt.Dimension

/**
 * Manages offscreen rendering to a DeckLink device.
 *
 * Creates an offscreen JFrame (positioned at -10000,-10000 so it's invisible),
 * captures its content at ~30fps, and pushes frames to the DeckLink output
 * using scheduled playback with a pre-roll of 3 frames.
 */
class DeckLinkPresenter(
    private val deviceIndex: Int,
    private val outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    private val width: Int = 1920,
    private val height: Int = 1080,
    private val fps: Double = 30.0
) {
    private var renderJob: Job? = null
    private var offscreenFrame: JFrame? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * The offscreen panel that content should be painted to.
     * Callers should add their AWT/Swing components as children of this panel.
     */
    val contentPanel: JPanel
        get() = _contentPanel

    private val _contentPanel = JPanel().apply {
        preferredSize = Dimension(width, height)
        setSize(width, height)
    }

    /**
     * Opens the DeckLink device and starts the capture-and-push loop.
     * Returns false if the device could not be opened.
     */
    fun start(): Boolean {
        if (!DeckLinkManager.isAvailable()) return false
        if (!DeckLinkManager.open(deviceIndex, width, height)) return false

        // Create offscreen frame
        offscreenFrame = JFrame().apply {
            isUndecorated = true
            setSize(width, height)
            setLocation(-10000, -10000)
            contentPane.add(_contentPanel)
            isVisible = true
        }

        // Start scheduled playback
        if (!DeckLinkManager.startScheduledPlayback(deviceIndex, fps)) {
            stop()
            return false
        }

        // Pre-roll: schedule 3 black frames
        val blackFrame = IntArray(width * height) { 0xFF000000.toInt() }
        repeat(3) {
            DeckLinkManager.scheduleFrame(blackFrame, width, height)
        }

        // Start capture loop
        val frameIntervalMs = (1000.0 / fps).toLong()
        renderJob = scope.launch {
            val buffer = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val pixels = IntArray(width * height)

            while (isActive) {
                val startTime = System.currentTimeMillis()

                // Paint the offscreen content into the buffer
                val g2d = buffer.createGraphics()
                try {
                    offscreenFrame?.contentPane?.printAll(g2d)
                } finally {
                    g2d.dispose()
                }

                // Convert ARGB to BGRA and schedule the frame
                buffer.getRGB(0, 0, width, height, pixels, 0, width)
                if (outputRole == Constants.OUTPUT_ROLE_KEY) {
                    convertToKeySignal(pixels)
                }
                argbToBgra(pixels)
                DeckLinkManager.scheduleFrame(pixels, width, height)

                // Pace the loop
                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = frameIntervalMs - elapsed
                if (sleepTime > 0) delay(sleepTime)
            }
        }

        return true
    }

    /**
     * Stops playback and releases all resources.
     */
    fun stop() {
        renderJob?.cancel()
        renderJob = null

        DeckLinkManager.stopPlayback()
        DeckLinkManager.close()

        offscreenFrame?.isVisible = false
        offscreenFrame?.dispose()
        offscreenFrame = null
    }

    /**
     * Converts pixels to a key signal: white with original alpha.
     * Non-transparent pixels become white; transparent pixels stay black.
     */
    private fun convertToKeySignal(pixels: IntArray) {
        for (i in pixels.indices) {
            val a = (pixels[i] shr 24) and 0xFF
            pixels[i] = (a shl 24) or 0x00FFFFFF
        }
    }

    /**
     * Converts an ARGB pixel array in-place to BGRA format for DeckLink.
     * ARGB: [A][R][G][B] -> BGRA: [B][G][R][A]
     */
    private fun argbToBgra(pixels: IntArray) {
        for (i in pixels.indices) {
            val argb = pixels[i]
            val a = (argb shr 24) and 0xFF
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            pixels[i] = (b shl 24) or (g shl 16) or (r shl 8) or a
        }
    }
}
