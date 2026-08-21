package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
import org.churchpresenter.diagnostics.CrashReporter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.nio.ByteBuffer

private const val FRAME_INTERVAL_MS = 16L
private const val SURFACE_ATTACH_MS = 100L

/**
 * A self-contained looping video background with no audio.
 * Plays the video at [videoPath] in an infinite loop, filling the available space.
 * Uses VLCJ with software rendering (CallbackVideoSurface) so video frames are
 * rendered as a Compose Image — respecting normal z-order (text draws on top).
 */
@Composable
fun LoopingVideoBackground(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    val file = remember(videoPath) { File(videoPath) }
    val playable = videoPath.isNotBlank() && file.exists() &&
        isVlcAvailable && !CrashReporter.videoBackgroundsDisabled
    if (!playable) return

    val currentFrame = remember { mutableStateOf<ImageBitmap?>(null) }
    val frameVersion = remember { mutableStateOf(0L) }
    val bufferedImageHolder = remember { mutableStateOf<BufferedImage?>(null) }

    val factory = remember {
        try { MediaPlayerFactory("--no-video-title-show") } catch (t: Throwable) {
            CrashReporter.reportException(t, "LoopingVideoBackground: VLC MediaPlayerFactory init failed"); null
        }
    } ?: return

    val mediaPlayer = remember(factory) {
        try { factory.mediaPlayers().newEmbeddedMediaPlayer() } catch (_: Throwable) { null }
    } ?: return

    // Convert frames off VLC's render thread to avoid blocking its internal pipeline
    LaunchedEffect(Unit) {
        var lastVersion = 0L
        while (isActive) {
            val v = frameVersion.value
            if (v != lastVersion) {
                lastVersion = v
                val img = bufferedImageHolder.value
                if (img != null) {
                    currentFrame.value = img.toComposeImageBitmap()
                }
            }
            delay(FRAME_INTERVAL_MS)
        }
    }

    // Set up callback video surface for software rendering
    DisposableEffect(Unit) {
        val bufferFormatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                bufferedImageHolder.value = BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB)
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
        }

        // Use RenderCallback directly (not RenderCallbackAdapter) to avoid
        // the adapter's null dst crash when VLC fires display before buffer allocation.
        val renderCallback = RenderCallback { player, nativeBuffers, bufferFormat ->
            val img = bufferedImageHolder.value ?: return@RenderCallback
            if (nativeBuffers == null || nativeBuffers.isEmpty()) return@RenderCallback
            val pixelData = (img.raster.dataBuffer as? DataBufferInt)?.data ?: return@RenderCallback
            try {
                val buf = nativeBuffers[0] ?: return@RenderCallback
                buf.rewind()
                buf.asIntBuffer().get(pixelData, 0, pixelData.size.coerceAtMost(buf.remaining() / 4))
                frameVersion.value++
            } catch (_: Throwable) { }
        }

        mediaPlayer.videoSurface().set(
            factory.videoSurfaces().newVideoSurface(bufferFormatCallback, renderCallback, true)
        )

        onDispose {
            try {
                mediaPlayer.controls().stop()
                mediaPlayer.release()
                factory.release()
            } catch (_: Throwable) { }
        }
    }

    // Start playback with VLC input-level repeat (loops at demuxer level, no gap)
    LaunchedEffect(videoPath) {
        delay(SURFACE_ATTACH_MS) // let video surface attach
        try {
            mediaPlayer.audio().setVolume(0)
            mediaPlayer.media().play(
                file.absolutePath,
                ":file-caching=10000",
                ":network-caching=10000",
                ":input-repeat=65535"
            )
        } catch (_: Throwable) { }
    }

    // Render current frame as a regular Compose Image (respects z-order)
    currentFrame.value?.let { frame ->
        Image(
            bitmap = frame,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
