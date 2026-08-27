package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val NANOS_PER_MILLI = 1_000_000L
private const val MIN_FPS = 1
private const val MAX_FPS = 60
private const val MILLIS_PER_SECOND = 1000L

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
            val scene = ImageComposeScene(width, height, Density(1f)) { content() }
            try {
                runLoop(scene, onFrame)
            } finally {
                scene.close()
            }
        }
    }

    private suspend fun runLoop(scene: ImageComposeScene, onFrame: OnFrame) {
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
            try {
                img.toComposeImageBitmap().readPixels(intBuf)
            } finally {
                img.close()
            }
            onFrame(intBuf, width, height, timeNanos / NANOS_PER_MILLI)
            delay(tickDelayMs)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
