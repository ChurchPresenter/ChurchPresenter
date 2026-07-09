package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.server.LottieRenderCache
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.jetbrains.skia.Bitmap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * One decoded lower-third frame, ready to draw. [imageBitmap] is what output windows
 * render; the backing native [skiaBitmap] is owned and closed by [LottieFrameStream].
 */
class LottieFrame(
    val imageBitmap: ImageBitmap,
    val index: Int,
    internal val skiaBitmap: Bitmap
)

/**
 * Streams decoded lower-third frames out of a [LottieRenderCache] file during playback.
 *
 * One instance per play, owned by PresenterManager. A single-threaded worker observes the
 * frame index requested by main.kt's vsync playback clock, decodes that frame from the cache
 * file off the UI thread, uploads it into a native bitmap once, and publishes the resulting
 * [LottieFrame] for every output window to draw — replacing the old design where each window
 * re-did an ~8 MB pixel install on the composition thread for every frame. Peak memory is a
 * few frames regardless of clip length (the whole clip never lives in RAM).
 *
 * Bitmap lifetime: a published bitmap is closed only after [RETAIN_FRAMES] newer frames have
 * been published — windows draw the currently-published value synchronously in composition,
 * so nothing can still be drawing a bitmap that far superseded. On [close], the last frames
 * linger [CLOSE_LINGER_MS] so compositions triggered by the state clearing finish first.
 */
class LottieFrameStream(
    private val file: File,
    private val scope: CoroutineScope,
    private val onFrame: (LottieFrame?) -> Unit
) {
    private companion object {
        const val RETAIN_FRAMES = 3
        const val CLOSE_LINGER_MS = 250L
        /** Sampled frames must be >1% opaque or the whole pre-render is considered blank. */
        const val BLANK_OPAQUE_FRACTION = 0.01f
    }

    // Single thread so decode order matches request order and bitmap lifecycle is race-free.
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val requests = Channel<Int>(Channel.CONFLATED)

    private var reader: LottieRenderCache.Reader? = null
    private var worker: Job? = null
    private val liveBitmaps = ArrayDeque<Bitmap>()
    private var closed = false

    var frameCount = 0
        private set

    /**
     * Opens the cache file and verifies it isn't a blank render (a silent off-screen capture
     * failure) by sampling a few decoded frames. Returns false — without starting the worker —
     * when the content is blank; the caller should discard the cache file and stay on the
     * live renderer. Must be called exactly once, before any [requestFrame].
     */
    suspend fun open(): Boolean = withContext(decodeDispatcher) {
        val r = LottieRenderCache.Reader(file)
        reader = r
        frameCount = r.frameCount
        val sampleIndices = listOf(r.frameCount / 4, r.frameCount / 2, r.frameCount * 3 / 4, r.frameCount - 1)
            .map { it.coerceIn(0, r.frameCount - 1) }
            .distinct()
        val blankCount = sampleIndices.count { isFrameBlank(r.frameArgb(it)) }
        if (blankCount > sampleIndices.size / 2) return@withContext false
        worker = scope.launch(decodeDispatcher) {
            for (index in requests) {
                decodeAndPublish(index)
            }
        }
        true
    }

    /** Ask for a frame; conflated, so only the most recent request is decoded. */
    fun requestFrame(index: Int) {
        requests.trySend(index)
    }

    /**
     * Stops decoding and releases the reader and every bitmap this stream published.
     * The caller must clear its published frame state first so windows recompose away
     * from the last bitmap before it is closed.
     */
    fun close() {
        if (closed) return
        closed = true
        requests.close()
        worker?.cancel()
        scope.launch(decodeDispatcher) {
            delay(CLOSE_LINGER_MS)
            while (liveBitmaps.isNotEmpty()) liveBitmaps.removeFirst().close()
            reader?.close()
        }
    }

    private fun decodeAndPublish(index: Int) {
        val r = reader ?: return
        try {
            val pixels = r.frameArgb(index)
            // ARGB int written as little-endian 32-bit value = BGRA bytes = N32 pixel layout
            val bytes = ByteArray(pixels.size * 4)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels)
            val bitmap = Bitmap()
            bitmap.allocN32Pixels(r.width, r.height)
            bitmap.installPixels(bytes)
            bitmap.setImmutable()
            liveBitmaps.addLast(bitmap)
            onFrame(LottieFrame(bitmap.asComposeImageBitmap(), index, bitmap))
            while (liveBitmaps.size > RETAIN_FRAMES) liveBitmaps.removeFirst().close()
        } catch (e: Exception) {
            // Keep the previously published frame on a one-off decode/alloc failure rather
            // than crashing playback for a single frame.
            CrashReporter.reportException(e, "Lower third frame decode")
        }
    }

    /**
     * Roughly estimates whether a frame is meaningfully blank (near-fully transparent).
     * Samples pixels at a stride rather than scanning the whole frame to stay cheap.
     */
    private fun isFrameBlank(frame: IntArray): Boolean {
        if (frame.isEmpty()) return true
        val sampleStep = maxOf(1, frame.size / 500)
        var sampled = 0
        var opaqueCount = 0
        var i = 0
        while (i < frame.size) {
            sampled++
            if ((frame[i] ushr 24) and 0xFF > 8) opaqueCount++
            i += sampleStep
        }
        return opaqueCount.toFloat() / sampled < BLANK_OPAQUE_FRACTION
    }
}
