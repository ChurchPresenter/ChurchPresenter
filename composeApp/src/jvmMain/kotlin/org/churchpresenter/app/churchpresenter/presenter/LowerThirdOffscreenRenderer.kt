package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Renders a Lottie animation to ARGB pixel frames off-screen.
 *
 * Uses [ImageComposeScene] — Compose Desktop's windowless renderer — so frames are
 * drawn deterministically into an image with no OS window involved. (The previous
 * hidden-JFrame + SkiaLayer.screenshot() approach produced blank frames on Windows:
 * DWM does not reliably composite windows positioned outside the visible desktop.)
 *
 * Returned pixels are ARGB IntArray: each int = (A shl 24) or (R shl 16) or (G shl 8) or B.
 */
@OptIn(ExperimentalComposeUiApi::class)
class LowerThirdOffscreenRenderer(
    private val width: Int,
    private val height: Int
) {
    private companion object {
        const val FRAME_NANOS = 16_666_667L            // scene clock step per render
        const val COMPOSITION_LOAD_TIMEOUT_MS = 10_000L
    }

    /**
     * Renders the Lottie animation at a single progress value (0f..1f).
     *
     * @param lottieJson  raw JSON string of the Lottie animation
     * @param progress    animation progress 0f (start) to 1f (end); default 0.5f (midpoint)
     * @return            ARGB IntArray of size width*height
     */
    suspend fun renderStill(lottieJson: String, progress: Float = 0.5f): IntArray =
        withSession(lottieJson, initialProgress = progress) { renderFrame -> renderFrame(progress).copyOf() }

    /**
     * Opens an off-screen render session and runs [block] with a frame-render function.
     *
     * [initialProgress] should be the first progress value that will be rendered, so the
     * very first composition already draws the right frame.
     *
     * The render function returns an INTERNAL BUFFER that is overwritten by the next
     * call — consume each frame (convert/upload) before requesting the next one and do
     * not retain references. This keeps memory flat regardless of clip length: a single
     * 1080p frame is ~8 MB, so buffering a whole clip of frames would exhaust the heap.
     */
    suspend fun <T> withSession(
        lottieJson: String,
        initialProgress: Float = 0f,
        block: suspend (renderFrame: suspend (Float) -> IntArray) -> T
    ): T = withContext(Dispatchers.Default) {
        var currentProgress by mutableStateOf(initialProgress)
        var compositionLoaded by mutableStateOf(false)

        val scene = ImageComposeScene(width, height, Density(1f)) {
            val composition by rememberLottieComposition {
                LottieCompositionSpec.JsonString(lottieJson.ifBlank { "{}" })
            }
            val loaded = composition != null
            SideEffect { if (loaded) compositionLoaded = true }
            Image(
                painter = rememberLottiePainter(
                    composition = composition,
                    progress = { currentProgress }
                ),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        try {
            var timeNanos = 0L
            fun renderOnce(): org.jetbrains.skia.Image {
                timeNanos += FRAME_NANOS
                return scene.render(timeNanos)
            }

            // Pump the scene until the async Lottie parse finishes
            val deadline = System.currentTimeMillis() + COMPOSITION_LOAD_TIMEOUT_MS
            while (!compositionLoaded && System.currentTimeMillis() < deadline) {
                renderOnce().close()
                delay(16)
            }
            if (!compositionLoaded) {
                throw Exception("Lottie composition failed to load for off-screen rendering")
            }

            val intBuf = IntArray(width * height)
            block { progress ->
                currentProgress = progress
                // Progress is written from a background thread — make sure the
                // snapshot change is applied before the scene recomposes and draws
                Snapshot.sendApplyNotifications()
                val img = renderOnce()
                try {
                    img.toComposeImageBitmap().readPixels(intBuf)
                } finally {
                    img.close()
                }
                intBuf
            }
        } finally {
            scene.close()
        }
    }
}
