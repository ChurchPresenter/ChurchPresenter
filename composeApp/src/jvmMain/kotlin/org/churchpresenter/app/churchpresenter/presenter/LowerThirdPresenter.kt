package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import org.churchpresenter.app.churchpresenter.utils.LowerThirdDebugLog
import org.jetbrains.skia.Bitmap
import kotlinx.coroutines.delay
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

    // DIAGNOSTIC (temporary — see AGENT.md "lower third disappears mid-playback"): the state
    // machine driving playback (main.kt's central LaunchedEffect) has been proven correct by prior
    // logging — it runs a full, uninterrupted playthrough even on runs where the graphic visibly
    // vanished early. So the failure is somewhere in what THIS composable actually renders with.
    // These two effects log whether this specific composable instance gets torn down early (onDispose
    // firing before natural completion — see `rawFrames == null && composition == null` above,
    // which is exactly what would cause that) and whether its composition/rawFrames inputs go
    // stale mid-play even while mounted.
    val instanceId = remember { (0..9999).random() }
    val currentComposition by rememberUpdatedState(composition)
    val currentRawFrames by rememberUpdatedState(rawFrames)
    val currentFrameIdx by rememberUpdatedState(currentFrameIndex)

    DisposableEffect(Unit) {
        LowerThirdDebugLog.log("LowerThirdPresenter[$instanceId/$outputRole] ENTERED composition")
        onDispose {
            LowerThirdDebugLog.log("LowerThirdPresenter[$instanceId/$outputRole] LEFT composition (onDispose)")
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            LowerThirdDebugLog.log(
                "LowerThirdPresenter[$instanceId/$outputRole] heartbeat: composition=" +
                    (currentComposition?.let { "present(id=${System.identityHashCode(it)})" } ?: "null") +
                    " rawFrames=" + (currentRawFrames?.let { "present(${it.size})" } ?: "null") +
                    " currentFrameIndex=$currentFrameIdx"
            )
        }
    }

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
                var lastGoodFrame by remember(rawFrames) { mutableStateOf<DisposableFrameBitmap?>(null) }
                val frame = remember(rawFrames, safeIdx) {
                    try {
                        frameToDisposableBitmap(rawFrames[safeIdx], frameWidth, frameHeight).also {
                            lastGoodFrame = it
                        }
                    } catch (e: Exception) {
                        // DIAGNOSTIC (temporary — see AGENT.md "lower third disappears mid-playback"):
                        // catch instead of letting this crash composition silently, and log it so a
                        // Windows/macOS repro captures the real failure instead of a blank output.
                        LowerThirdDebugLog.logException(
                            "Lower third frame render (frame $safeIdx/${rawFrames.size}, ${frameWidth}x$frameHeight, ${LowerThirdDebugLog.heapStats()})",
                            e
                        )
                        CrashReporter.reportException(e, "Lower third frame render")
                        lastGoodFrame
                    }
                }
                if (frame != null) {
                    Image(
                        bitmap = frame.imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (isKey) keyColorFilter else null,
                        modifier = contentModifier
                    )
                }
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
