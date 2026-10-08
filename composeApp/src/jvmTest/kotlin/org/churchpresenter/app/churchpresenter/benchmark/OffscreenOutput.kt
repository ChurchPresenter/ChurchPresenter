package org.churchpresenter.app.churchpresenter.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.churchpresenter.liveoutput.FrameBuffer
import javax.swing.SwingUtilities
/** One frame at 60 fps, the clock every frame advances by. */
private const val FRAME_NANOS = 16_666_667L

/** Where alpha sits in a packed ARGB pixel. */
private const val ALPHA_SHIFT = 24

/** What one frame cost: composition, layout and drawing, then reading the pixels back. */
data class FrameCost(val renderNanos: Long, val readbackNanos: Long) {
    val totalNanos: Long get() = renderNanos + readbackNanos
}

/**
 * An off-screen output as the NDI, OMT and Browser Source outputs build one: an [ImageComposeScene]
 * at density 1, its virtual clock advanced a 60 fps frame at a time, every frame read back through
 * the outputs' own [FrameBuffer].
 *
 * The content receives the frame number as state, bumped before every render, so it can change
 * what it shows each frame and be timed through recomposition as well as drawing. The scene lives
 * until [close], so the composition and every `remember` in it survive across frames, as a live
 * output's do.
 *
 * **The scene is built, rendered and closed on the AWT event queue**, as `ComposeScenePump`'s is.
 * Compose's node-kind cache is global and unsynchronised: a scene composed on the caller's thread
 * while another is built on the event queue -- the lower-third pre-render a `PresenterManager`
 * starts -- corrupted it and hung a soak in its constructor, spinning in `ObjectIntMap.findKeyIndex`.
 * The cost is timed inside the hop, so the event queue's dispatch is not counted as rendering; the
 * pixel readback stays on the caller's thread, as the pump's does.
 */
@OptIn(ExperimentalComposeUiApi::class)
class OffscreenOutput(
    val width: Int,
    val height: Int,
    private val clock: () -> Long = System::nanoTime,
    content: @Composable (frame: Int) -> Unit,
) : AutoCloseable {
    private val frame = mutableIntStateOf(0)
    private val scene = onEventQueue { ImageComposeScene(width, height, Density(1f)) { content(frame.intValue) } }
    private val buffer = FrameBuffer(width, height)
    private val pixels = IntArray(width * height)
    private var time = 0L

    /** Renders the next frame, reads it back, and says what each half cost. */
    fun step(): FrameCost {
        time += FRAME_NANOS
        val (image, renderNanos) = onEventQueue {
            frame.intValue += 1
            val start = clock()
            Snapshot.sendApplyNotifications()
            val rendered = scene.render(time)
            rendered to clock() - start
        }
        val readStart = clock()
        try {
            check(buffer.readInto(image, pixels)) { "the frame could not be read back" }
        } finally {
            image.close()
        }
        return FrameCost(renderNanos, clock() - readStart)
    }

    /** Pixels the last frame drew on, so content that showed nothing is caught. */
    fun drawnPixels(): Int = pixels.count { it ushr ALPHA_SHIFT != 0 }

    override fun close() {
        buffer.close()
        onEventQueue { scene.close() }
    }
}

/** Runs [block] on the AWT event queue and hands back its result, rethrowing what it threw. */
private fun <T> onEventQueue(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var result: Result<T>? = null
    SwingUtilities.invokeAndWait { result = runCatching(block) }
    return checkNotNull(result).getOrThrow()
}
