package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.keyColorFilter
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image as SkiaImage
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun intArrayToImageBitmap(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    // ARGB int written as little-endian 32-bit value = BGRA bytes = N32 on Windows/Linux
    val bytes = ByteArray(pixels.size * 4)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels)
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(width, height)
    bitmap.installPixels(bytes)
    return SkiaImage.makeFromBitmap(bitmap).toComposeImageBitmap()
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
        // key() forces a fresh composable subtree when switching between the two paths,
        // preventing hook-ordering issues when rawFrames transitions null ↔ non-null.
        key(rawFrames != null) {
            if (rawFrames != null) {
                val safeIdx = currentFrameIndex.coerceIn(0, rawFrames.size - 1)
                val bitmap = remember(rawFrames, safeIdx) {
                    intArrayToImageBitmap(rawFrames[safeIdx], frameWidth, frameHeight)
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isKey) keyColorFilter else null,
                    modifier = contentModifier
                )
            } else if (composition != null) {
                Image(
                    painter = rememberLottiePainter(
                        composition = composition,
                        progress = progress
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (isKey) keyColorFilter else null,
                    modifier = contentModifier
                )
            }
        }
    }
}
