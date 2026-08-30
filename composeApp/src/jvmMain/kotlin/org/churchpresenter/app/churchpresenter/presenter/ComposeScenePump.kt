package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.churchpresenter.diagnostics.CrashReporter
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Pixmap

private const val NANOS_PER_MILLI = 1_000_000L
private const val MIN_FPS = 1
private const val MAX_FPS = 60
private const val MILLIS_PER_SECOND = 1000L
private const val BYTES_PER_PIXEL = 4

/**
 * What [ComposeScenePump] does with a rendered frame: its pixels in row-major ARGB, its dimensions,
 * and the virtual clock's elapsed milliseconds.
 *
 * Suspending, so a callback may emit onto a flow or block on I/O without a second dispatch — it
 * already runs on the pump's own [Dispatchers.Default] coroutine, and the loop is meant to wait
 * for it rather than race ahead of it.
 */
typealias OnFrame = suspend (argb: IntArray, width: Int, height: Int, elapsedMs: Long) -> Unit

/**
 * Renders a composable off-screen at a fixed cadence and hands each frame's pixels to a callback.
 *
 * This is the half of an off-screen output that has nothing to do with what the output *is*: build
 * an [ImageComposeScene], advance a virtual animation clock in step with real sampling time, flush
 * pending snapshot writes, render, read the pixels into a buffer that is reused rather than
 * reallocated, park when there is no reason to render, and close the scene on the way out. What is
 * done with those pixels — encoded as a dirty-rect delta, converted to BGRA and sent on the wire —
 * is [onFrame]'s business and differs per output.
 *
 * It was extracted from [BrowserSourceVideoRenderer], which had it inline, because [NdiVideoRenderer]
 * needs exactly the same loop with a different [onFrame]. Copy-pasting it would have made a fourth
 * independent implementation of "render Compose off-screen and push the pixels somewhere" in this
 * package.
 *
 * [DeckLinkComposeOutput] deliberately stays outside this: it renders through an off-screen `JFrame`
 * plus a `SkiaLayer` rather than an [ImageComposeScene], which is how it dodges a
 * `GraphicsLayer.toImageBitmap()` race, and that shape should not be propagated.
 *
 * The scene is built once per [start] and closed when the job ends, so the composition — and every
 * `remember` in it — survives across frames. Restarting the pump is what discards it, which is why
 * callers `remember` an instance keyed on the dimensions and fps that would invalidate it.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ComposeScenePump(
    private val width: Int,
    private val height: Int,
    fps: Int,
    private val shouldRender: () -> Boolean = { true },
    private val onPark: () -> Unit = {},
    private val content: @Composable () -> Unit,
) {
    /**
     * Sampling cadence from the caller's fps. A ceiling rather than a constant cost: whether a
     * rendered frame is worth doing anything with is [onFrame]'s decision, and an output whose
     * content has not changed can decline every one of them.
     */
    internal val tickDelayMs = MILLIS_PER_SECOND / fps.coerceIn(MIN_FPS, MAX_FPS)

    /** Keeps the virtual animation clock in step with real sampling time. */
    private val frameNanos = tickDelayMs * NANOS_PER_MILLI

    internal companion object {
        /**
         * How often to re-check for work while parked. Only a [shouldRender] read, so this costs
         * nothing measurable; it just bounds how long a connecting client waits for its first
         * frame. Deliberately slower than any tick rate.
         */
        internal const val IDLE_POLL_MS = 250L

        /**
         * Serialises [ImageComposeScene] construction across every pump in the process.
         *
         * Building one touches Compose state that is global to the JVM rather than local to the
         * scene, and there is a pump per Browser Source output and per NDI output — all started
         * from their own `LaunchedEffect`, all on the multi-threaded [Dispatchers.Default]. Two
         * starting at once raced a shared `MutableObjectIntMap` inside the constructor and threw
         * `ArrayIndexOutOfBoundsException` out of its own `resizeStorage`, which reached an
         * operator with two outputs configured.
         *
         * Only the constructor is held: [runLoop] runs outside the lock, so N outputs still
         * render in parallel and a slow one cannot stall another's startup for more than one
         * scene creation.
         */
        private val sceneCreation = Mutex()
    }

    private var job: Job? = null

    /** Whether a render loop is currently running. */
    val isRunning: Boolean get() = job != null

    /**
     * Starts the loop on [scope], calling [onFrame] with this tick's pixels in row-major ARGB, the
     * frame's dimensions, and the virtual clock's elapsed milliseconds.
     *
     * The array passed to [onFrame] is the pump's own buffer and is overwritten by the next tick —
     * a callback that needs to keep the pixels must copy them.
     *
     * A second call while already running is ignored, matching the renderers' start/stop contract.
     */
    fun start(scope: CoroutineScope, onFrame: OnFrame) {
        if (job != null) return
        job = scope.launch(Dispatchers.Default) {
            val scene = try {
                sceneCreation.withLock { ImageComposeScene(width, height, Density(1f)) { content() } }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                // Throwable, not Exception: the reported failure was an ArrayIndexOutOfBounds from
                // inside a Compose collection, and a scene that fails to build can equally raise an
                // Error out of native graphics. Narrowing this would let exactly the crash it
                // exists for straight back through.
                //
                // An output that cannot build its scene is one dark output, not a dead app: this
                // runs on a launched coroutine, so anything escaping here is an uncaught throw.
                // Clearing the job is what lets the caller start again — a pump that failed once
                // used to be permanently wedged, because `job` stayed non-null.
                job = null
                CrashReporter.reportWarning(
                    "Off-screen output scene could not be created",
                    throwable = t,
                    tags = mapOf("subsystem" to "offscreen_output"),
                    extras = mapOf("width" to width.toString(), "height" to height.toString()),
                )
                return@launch
            }
            try {
                runLoop(scene, onFrame)
            } finally {
                scene.close()
            }
        }
    }

    private suspend fun runLoop(scene: ImageComposeScene, onFrame: OnFrame) {
        val frame = FrameBuffer(width, height)
        try {
            renderFrames(scene, frame, onFrame)
        } finally {
            frame.close()
        }
    }

    private suspend fun renderFrames(scene: ImageComposeScene, frame: FrameBuffer, onFrame: OnFrame) {
        var timeNanos = 0L
        val intBuf = IntArray(width * height)
        var parked = false
        while (true) {
            if (!shouldRender()) {
                if (!parked) {
                    parked = true
                    onPark()
                }
                // Keep the virtual clock on real time, so a client that connects after a long park
                // does not resume mid-animation at a stale timestamp.
                timeNanos += IDLE_POLL_MS * NANOS_PER_MILLI
                delay(IDLE_POLL_MS)
                continue
            }

            parked = false
            timeNanos += frameNanos
            Snapshot.sendApplyNotifications()
            val img = scene.render(timeNanos)
            val read = try {
                frame.readInto(img, intBuf)
            } finally {
                img.close()
            }
            // A frame that could not be read back is skipped rather than sent: the buffer still
            // holds the previous one, and re-sending it would read as live content that has frozen.
            if (read) {
                onFrame(intBuf, width, height, timeNanos / NANOS_PER_MILLI)
            } else {
                reportUnreadableFrame()
            }
            delay(tickDelayMs)
        }
    }

    private var reportedUnreadableFrame = false

    /**
     * Says once that the frame could not be read back, and then stops saying it.
     *
     * The conversion [FrameBuffer] asks for is one every raster image supports, so a refusal is a
     * standing condition rather than a bad frame — it would repeat at the tick rate and bury
     * everything else in the report. What the operator sees either way is an output that has gone
     * dark, and one report is what makes that traceable.
     */
    private fun reportUnreadableFrame() {
        if (reportedUnreadableFrame) return
        reportedUnreadableFrame = true
        CrashReporter.reportWarning(
            "Off-screen output frame could not be read back",
            tags = mapOf("subsystem" to "offscreen_output"),
            extras = mapOf("width" to width.toString(), "height" to height.toString()),
        )
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/**
 * The one destination a [ComposeScenePump] reads its frames back into: native pixel memory
 * allocated when the pump starts, and a [Pixmap] describing it.
 *
 * Exists because the obvious spelling of this — `img.toComposeImageBitmap().readPixels(intBuf)` —
 * moves the whole frame four times over. `toComposeImageBitmap` allocates a raster bitmap and
 * software-blits the image into it; Compose's `readPixels` then allocates a byte array the size of
 * the frame, converts into it, and copies it twice more (out of JNI, then into the caller's
 * `IntArray`). At 1080p30 that is ~25 MB of humongous-region garbage and ~33 MB of `memcpy` every
 * frame, which showed up on an operator's machine as 44% of a core and 15.9 million page faults for
 * a single NDI output.
 *
 * `SkImage::readPixels` into a pre-allocated pixmap does the same conversion once, into memory that
 * outlives the frame. What is left per frame is that conversion and one copy into [IntArray].
 *
 * **Not thread-safe**, and does not need to be: there is one per pump and only the pump's own
 * coroutine touches it.
 */
private class FrameBuffer(width: Int, height: Int) : AutoCloseable {

    /**
     * BGRA, unpremultiplied, sRGB — the same destination format Compose's own `readPixels` asks
     * for, so what a renderer receives is unchanged by this class existing.
     */
    private val info = ImageInfo(
        ColorInfo(ColorType.BGRA_8888, ColorAlphaType.UNPREMUL, ColorSpace.sRGB),
        width,
        height,
    )

    private val data = Data.makeUninitialized(width * height * BYTES_PER_PIXEL)
    private val pixmap = Pixmap.make(info, data, width * BYTES_PER_PIXEL)

    /** The pixels' address, resolved once: reading it is a native call of its own. */
    private val addr = Pointer(pixmap.addr)

    /**
     * Converts [image] into this buffer and copies it into [dst] as packed ARGB, or returns false
     * without touching [dst] if the conversion was refused.
     *
     * BGRA bytes read back as native-endian ints are `0xAARRGGBB`, which is the packed ARGB
     * [OnFrame] is defined in. That identity holds on every platform this app targets and is the
     * same one Compose's `readPixels` relies on; it would not hold on a big-endian JVM, and there
     * is no such target here.
     */
    fun readInto(image: Image, dst: IntArray): Boolean {
        if (!image.readPixels(pixmap, 0, 0, false)) return false
        addr.read(0L, dst, 0, dst.size)
        return true
    }

    /** Pixmap first: it describes memory that [data] owns. */
    override fun close() {
        pixmap.close()
        data.close()
    }
}
