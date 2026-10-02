package org.churchpresenter.media.composables

import androidx.compose.runtime.Composable
import org.churchpresenter.diagnostics.CrashReporter
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import androidx.compose.runtime.MutableState
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer

/**
 * An opened VLC player for [SoftwareVideoPlayer]: the player itself, how to point its decoded
 * frames at a callback surface, and how to let go of everything it holds.
 */
internal class SoftwareVlc(
    val mp: MediaPlayer,
    val attachSurface: (BufferFormatCallback, RenderCallback) -> Unit,
    val release: () -> Unit,
)

/**
 * Opens VLC for software rendering, or null when it cannot be: VLC is missing, its component will
 * not start, or the factory behind the callback surface will not load.
 *
 * The surface factory is used only to create the CallbackVideoSurface; the media player component
 * manages its own internal factory for the playback itself. On macOS,
 * factory.mediaPlayers().newEmbeddedMediaPlayer() does NOT deliver video frames to a callback
 * surface — that requires CallbackMediaPlayerComponent, which createMediaPlayerComponent() already
 * picks per platform.
 */
internal fun openSoftwareVlc(): SoftwareVlc? {
    if (!isVlcAvailable) return null
    val component = createMediaPlayerComponent() ?: return null
    // Loads libvlc, whose failures are Errors rather than Exceptions.
    val factory = try {
        MediaPlayerFactory()
    } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
        CrashReporter.reportException(t, "VideoPlayer: VLC MediaPlayerFactory init failed")
        component.releasePlayer()
        return null
    }
    val mp = component.mediaPlayer()
    return SoftwareVlc(
        mp = mp,
        // Setting a new video surface replaces the component's internal surface, directing all
        // decoded frames to the render callback instead.
        attachSurface = { format, render ->
            mp.videoSurface().set(factory.videoSurfaces().newVideoSurface(format, render, true))
        },
        release = {
            component.releasePlayer()
            factory.release()
        },
    )
}

/**
 * Turns each captured frame into an [ImageBitmap] rather than on VLC's render thread, which must
 * not be blocked or the audio pipeline stutters. Capped at ~60fps.
 *
 * The actual conversion runs on [Dispatchers.Default], not on the polling loop's own coroutine:
 * `LaunchedEffect` otherwise runs on the composition's dispatcher, the same one that drives
 * Compose's own recomposition and layout -- and `toComposeImageBitmap()` is real work, a full-frame
 * pixel copy done up to 60 times a second. Left there, it competed directly with the rest of the
 * app's UI work on the one thread both needed, which is what read as stutter across the whole
 * window, video included, not only the video.
 */
@Composable
internal fun ConvertFramesOffRenderThread(
    frameVersion: MutableState<Long>,
    holder: MutableState<BufferedImage?>,
    out: MutableState<ImageBitmap?>,
) {
    LaunchedEffect(Unit) {
        var lastVersion = 0L
        while (isActive) {
            val v = frameVersion.value
            if (v != lastVersion) {
                lastVersion = v
                val img = holder.value
                if (img != null) {
                    val bitmap = withContext(Dispatchers.Default) { img.toComposeImageBitmap() }
                    out.value = bitmap
                    SharedVideoOutput.frame.value = bitmap
                }
            }
            delay(FRAME_INTERVAL_MS)
        }
    }
}

/**
 * Two same-sized [BufferedImage]s so VLC's render thread and the composable's own conversion
 * coroutine ([ConvertFramesOffRenderThread]) are never touching the same one at once.
 *
 * Before this, both sides shared one [BufferedImage] and mutated/read its backing `int[]` in
 * place: VLC could start copying frame N+1's pixels into that array while the coroutine was still
 * mid-read of frame N for `toComposeImageBitmap()`, tearing the frame -- a torn frame reads on
 * screen as a glitch, and since neither side is throttled to the other's pace, it recurred rather
 * than being a one-off. [writeTarget] is always the buffer nobody is reading: the previous
 * [completeWrite] handed the just-finished one off for display and only then flipped which one
 * VLC writes into next, so a buffer is never both the current write target and the current display
 * source at the same time.
 */
internal class FramePingPong(width: Int, height: Int) {
    private val bufferA = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    private val bufferB = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    private var nextIsA = true

    /** Where the render callback copies the next frame's pixels. */
    val writeTarget: BufferedImage get() = if (nextIsA) bufferA else bufferB

    /** Called once that copy finishes: hands back the now-complete buffer and flips the target. */
    fun completeWrite(): BufferedImage {
        val completed = writeTarget
        nextIsA = !nextIsA
        return completed
    }
}

/**
 * The RV32 buffer format VLC renders into, re-allocating the [FramePingPong] pair whenever the
 * source size changes.
 */
internal fun rv32BufferFormatCallback(pingPong: MutableState<FramePingPong?>) =
    object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val w = sourceWidth.coerceAtLeast(1)
            val h = sourceHeight.coerceAtLeast(1)
            pingPong.value = FramePingPong(w, h)
            return RV32BufferFormat(w, h)
        }

        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
    }

/**
 * Copies each decoded frame into [pingPong]'s current write target, publishes the completed buffer
 * to [displayHolder], and bumps [frameVersion]. Runs on VLC's own render thread, so it does no
 * conversion work: that happens in the composable's frame loop.
 */
internal fun frameRenderCallback(
    pingPong: MutableState<FramePingPong?>,
    displayHolder: MutableState<BufferedImage?>,
    firstFrameCaptured: MutableState<Boolean>,
    frameVersion: MutableState<Long>,
) = RenderCallback { _, nativeBuffers, _ ->
    val pp = pingPong.value ?: return@RenderCallback
    if (nativeBuffers == null || nativeBuffers.isEmpty()) return@RenderCallback
    val target = pp.writeTarget
    val pixelData = (target.raster.dataBuffer as? DataBufferInt)?.data ?: return@RenderCallback
    try {
        val buf = nativeBuffers[0] ?: return@RenderCallback
        buf.rewind()
        buf.asIntBuffer().get(pixelData, 0, pixelData.size.coerceAtMost(buf.remaining() / RV32_BYTES_PER_PIXEL))
        displayHolder.value = pp.completeWrite()
        firstFrameCaptured.value = true
        frameVersion.value++  // signal new frame available, conversion happens off-thread
    } catch (_: Throwable) { }
}
