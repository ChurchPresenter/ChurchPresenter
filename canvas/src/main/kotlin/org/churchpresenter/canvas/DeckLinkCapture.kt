package org.churchpresenter.canvas

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.diagnostics.Log

/** Puts one polled DeckLink frame on screen; false when the poll returned no usable frame. */
internal suspend fun showDeckLinkFrame(frameData: IntArray?, entry: CacheEntry, first: Boolean): Boolean {
    if (frameData == null || frameData.size <= 2) return false
    val w = frameData[0]
    val h = frameData[1]
    if (w <= 0 || h <= 0) return false
    val img = withContext(Dispatchers.IO) {
        val bi = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        bi.setRGB(0, 0, w, h, frameData, 2, w)
        bi
    }
    entry.frame.value = img.toComposeImageBitmap()
    if (first) Log.info("DeckLink Input", "First frame: ${w}x$h")
    return true
}

/**
 * One DeckLink input's capture: opened once, then polled for frames until cancelled. The card is
 * reached through [inputs] -- [DeckLinkManagerInputs] in the app, a script in a test.
 */
internal class DeckLinkCapture(
    private val source: SceneSource.CameraSource,
    private val entry: CacheEntry,
    private val openReports: DeckLinkOpenReports,
    private val inputs: DeckLinkInputs = DeckLinkManagerInputs,
) {
    suspend fun run() {
        Log.info("DeckLink Input", "Opening device ${source.deckLinkIndex}, " +
            "format: ${source.videoFormat.ifEmpty { "auto" }}, connection: ${source.videoConnection}")

        val index = source.deckLinkIndex
        val device = withContext(Dispatchers.IO) { inputs.findDevice(index) }
        val inputModes = if (device != null) withContext(Dispatchers.IO) { inputs.inputModes(index) }
        else emptyList()
        deckLinkInputBlocker(present = device != null, hasInput = inputModes.isNotEmpty())?.let { blocker ->
            Log.warn("DeckLink Input", "Not opening device $index: $blocker")
            entry.error.value = blocker
            return
        }

        val opened = withContext(Dispatchers.IO) {
            inputs.openInput(index, source.videoFormat, source.videoConnection)
        }
        if (!opened) {
            Log.warn("DeckLink Input", "Failed to open input on device $index")
            val outputActive = inputs.isOutputActive(index)
            if (!outputActive) {
                reportDeckLinkOpenFailed(index, device?.name.orEmpty(), inputModes.size, openReports)
            }
            entry.error.value = deckLinkOpenFailure(outputActive)
            return
        }
        entry.error.value = null

        Log.info("DeckLink Input", "Input opened, polling for frames...")
        var frameCount = 0
        var nullCount = 0

        while (currentCoroutineContext().isActive) {
            val frameData = withContext(Dispatchers.IO) {
                inputs.inputFrame(source.deckLinkIndex)
            }

            if (showDeckLinkFrame(frameData, entry, first = frameCount == 0)) {
                frameCount++
                nullCount = 0
            } else {
                nullCount++
                if (nullCount > MAX_NULL_FRAMES_BEFORE_CLEAR && entry.frame.value != null) {
                    entry.frame.value = null  // no signal — clear display
                }
            }

            inputs.pause(DECKLINK_POLL_INTERVAL_MS) // ~60fps polling
        }
    }
}

/** What [DeckLinkCapture] and the camera panel ask of a DeckLink card's input. */
interface DeckLinkInputs {
    fun findDevice(index: Int): DeckLinkManager.DeckLinkDevice?
    fun inputModes(index: Int): List<DeckLinkManager.InputMode>
    fun videoConnections(index: Int): List<DeckLinkManager.VideoConnection>
    fun openInput(index: Int, mode: String, connection: Int): Boolean
    fun isOutputActive(index: Int): Boolean
    fun inputFrame(index: Int): IntArray?
    suspend fun pause(millis: Long)
}

/** The real cards, through the native DeckLink bridge. */
internal object DeckLinkManagerInputs : DeckLinkInputs {
    override fun findDevice(index: Int) = DeckLinkManager.listDevices().find { it.index == index }
    override fun inputModes(index: Int) = DeckLinkManager.listInputModes(index)
    override fun videoConnections(index: Int) = DeckLinkManager.listVideoConnections(index)
    override fun openInput(index: Int, mode: String, connection: Int) =
        DeckLinkManager.openInput(index, mode, connection)
    override fun isOutputActive(index: Int) = DeckLinkManager.isOutputActive(index)
    override fun inputFrame(index: Int) = DeckLinkManager.getInputFrame(index)
    override suspend fun pause(millis: Long) = delay(millis)
}

private const val MAX_NULL_FRAMES_BEFORE_CLEAR = 30
private const val DECKLINK_POLL_INTERVAL_MS = 16L
