package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
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
import uk.co.caprica.vlcj.player.base.MediaPlayer as VlcMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.File
import java.nio.ByteBuffer

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
    if (videoPath.isBlank()) return
    val file = remember(videoPath) { File(videoPath) }
    if (!file.exists()) return
    if (!isVlcAvailable) return

    val currentFrame = remember { mutableStateOf<ImageBitmap?>(null) }

    val factory = remember {
        try { MediaPlayerFactory() } catch (_: Throwable) { null }
    } ?: return

    val mediaPlayer = remember(factory) {
        try { factory.mediaPlayers().newEmbeddedMediaPlayer() } catch (_: Throwable) { null }
    } ?: return

    // Set up callback video surface for software rendering
    DisposableEffect(Unit) {
        var bufferedImage: BufferedImage? = null

        val bufferFormatCallback = object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                bufferedImage = BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB)
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) { }
        }

        val renderCallback = object : RenderCallbackAdapter() {
            override fun onDisplay(player: VlcMediaPlayer?, rgbBuffer: IntArray) {
                val img = bufferedImage ?: return
                val pixelData = (img.raster.dataBuffer as DataBufferInt).data
                // RV32 adapter gives us ARGB int[] — copy directly
                System.arraycopy(rgbBuffer, 0, pixelData, 0, rgbBuffer.size.coerceAtMost(pixelData.size))
                try {
                    currentFrame.value = img.toComposeImageBitmap()
                } catch (_: Throwable) { }
            }
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

    // Start playback and handle seamless looping
    LaunchedEffect(videoPath) {
        delay(100) // let video surface attach
        try {
            mediaPlayer.audio().setVolume(0)
            mediaPlayer.media().play(file.absolutePath, ":file-caching=10000", ":network-caching=10000")
        } catch (_: Throwable) { }

        // Poll position and seek to 0 just before end for seamless looping
        delay(500)
        while (isActive) {
            delay(100)
            try {
                val duration = mediaPlayer.status().length()
                val position = mediaPlayer.status().time()
                if (duration > 0 && position > 0 && duration - position < 250) {
                    mediaPlayer.controls().setTime(0)
                }
            } catch (_: Throwable) { }
        }
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
