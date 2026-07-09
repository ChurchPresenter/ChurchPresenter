package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.keyColorFilter
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import org.jetbrains.skia.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wraps a native Skia Bitmap so Compose's `remember` slot table releases the underlying off-heap
 * pixel buffer (~8MB @1080p) the instant this value is superseded by the next frame or the
 * composable leaves composition — instead of relying on GC/Cleaner timing, which native-backed
 * wrappers don't drive promptly since their Java-side footprint is tiny. Without this, raw-frame
 * playback (up to 30fps) leaks native/GPU memory every frame until rendering fails.
 */
private class DisposableFrameBitmap(
    val skiaBitmap: Bitmap,
    val imageBitmap: ImageBitmap
) : RememberObserver {
    override fun onRemembered() {}
    override fun onForgotten() = skiaBitmap.close()
    override fun onAbandoned() = skiaBitmap.close()
}

private fun frameToDisposableBitmap(pixels: IntArray, width: Int, height: Int): DisposableFrameBitmap {
    // ARGB int written as little-endian 32-bit value = BGRA bytes = N32 on Windows/Linux
    val bytes = ByteArray(pixels.size * 4)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels)
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(width, height)
    bitmap.installPixels(bytes)
    bitmap.setImmutable()
    return DisposableFrameBitmap(bitmap, bitmap.asComposeImageBitmap())
}

/** Decodes the current raw frame (if any), falling back to the last successfully-decoded
 *  frame on a native allocation failure rather than crashing composition for one frame. */
@Composable
private fun rememberRawFrame(
    rawFrames: List<IntArray>?,
    currentFrameIndex: Int,
    frameWidth: Int,
    frameHeight: Int
): DisposableFrameBitmap? {
    var lastGoodFrame by remember(rawFrames) { mutableStateOf<DisposableFrameBitmap?>(null) }
    return remember(rawFrames, currentFrameIndex) {
        rawFrames?.let { frames ->
            val safeIdx = currentFrameIndex.coerceIn(0, frames.size - 1)
            try {
                frameToDisposableBitmap(frames[safeIdx], frameWidth, frameHeight).also {
                    lastGoodFrame = it
                }
            } catch (e: Exception) {
                CrashReporter.reportException(e, "Lower third frame render")
                lastGoodFrame
            }
        }
    }
}

@Composable
private fun RawFrameImage(frame: DisposableFrameBitmap, isKey: Boolean, contentModifier: Modifier) {
    Image(
        bitmap = frame.imageBitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = if (isKey) keyColorFilter else null,
        modifier = contentModifier
    )
}

/**
 * Displays a Lottie animation in the presenter window.
 *
 * When [rawFrames] are available (pre-rendered ARGB pixel data), the animation is displayed
 * as a sequence of bitmaps rather than through real-time GPU vector rendering. This eliminates
 * per-frame rendering variance across GPU vendors and drivers.
 *
 * Animation progress is driven centrally by PresenterWindows so all presenter displays stay
 * in perfect sync.
 */
@Composable
fun LowerThirdPresenter(
    composition: LottieComposition?,
    progress: () -> Float,
    appSettings: AppSettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    rawFrames: List<IntArray>? = null,
    currentFrameIndex: Int = 0,
    frameWidth: Int = 1920,
    frameHeight: Int = 1080
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    if (rawFrames == null && composition == null) return

    val s = appSettings.streamingSettings
    val contentModifier = Modifier
        .padding(
            start = s.windowLeft.dp,
            end = s.windowRight.dp,
            top = s.windowTop.dp,
            bottom = s.windowBottom.dp
        )
        .fillMaxSize()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (composition != null) {
            // Keep the live Compottie painter mounted for the whole play once composition has
            // loaded (its identity is stable for an entire play — it only changes between
            // distinct plays), and pick which bitmap to actually draw via a plain value check
            // rather than a key()-forced subtree swap. main.kt's playback loop switches to raw
            // frames mid-play as soon as they're ready; tearing down and remounting the live
            // painter at that exact moment caused a visible hitch.
            val painter = rememberLottiePainter(composition = composition, progress = progress)
            val frame = rememberRawFrame(rawFrames, currentFrameIndex, frameWidth, frameHeight)
            if (frame != null) {
                RawFrameImage(frame, isKey, contentModifier)
            } else {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isKey) keyColorFilter else null,
                    modifier = contentModifier
                )
            }
        } else if (rawFrames != null) {
            // Rare: raw frames finished rendering before Compottie's own composition parse did.
            val frame = rememberRawFrame(rawFrames, currentFrameIndex, frameWidth, frameHeight)
            if (frame != null) {
                RawFrameImage(frame, isKey, contentModifier)
            }
        }
    }
}
