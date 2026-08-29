package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.utils.LottieFonts

private const val FRAME_INTERVAL_MS = 16L

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
    private val height: Int,
    /**
     * Where the scene is built, rendered and closed — see [withSession] for why that is one thread.
     *
     * A parameter only so a test can see which thread the scene operations actually land on;
     * nothing in the app passes anything but the default.
     */
    private val sceneDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    private companion object {
        const val FRAME_NANOS = 16_666_667L            // scene clock step per render
        const val COMPOSITION_LOAD_TIMEOUT_MS = 10_000L
    }

    /**
     * Pre-renders [frameCount] evenly-spaced frames (progress 0f..1f) and returns them as
     * ARGB [IntArray]s (each of size width*height). Each frame is a defensive copy of the
     * shared internal buffer — all frames are safe to retain simultaneously.
     */
    suspend fun renderAllFrames(lottieJson: String, frameCount: Int): List<IntArray> {
        val frames = ArrayList<IntArray>(frameCount)
        withSession(lottieJson, initialProgress = 0f) { renderFrame ->
            for (i in 0 until frameCount) {
                val progress = i.toFloat() / (frameCount - 1).coerceAtLeast(1)
                frames.add(renderFrame(progress).copyOf())
            }
        }
        return frames
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

        // Everything that touches the scene -- constructing it, rendering it, closing it -- runs on
        // the event queue; only the pixels come back here.
        //
        // `ComposeScene.render` advances the global snapshot, and `advanceGlobalSnapshot` runs
        // *every* registered apply observer, this scene's and the on-screen AWT scene's alike. Two
        // threads doing that at once take those two observers' locks in opposite orders, which is a
        // deadlock, and on 2026-08-29 it was one: CI run 33269248282 halted a fork after 159s with
        // an explicit two-thread cycle -- `AWT-EventQueue-0` inside a desktop scrollbar's derived
        // state, this renderer's worker inside `sendApplyNotifications`, each holding the lock the
        // other wanted. The same cycle can freeze the app itself, since `LottieRenderCache`
        // pre-renders for the ATEM media pool on `Dispatchers.Default` while the UI is live.
        //
        // Confining the Compose half to one thread removes the second lock order, which is the only
        // fix available: there is no way to opt a scene out of the global observer list. The draw
        // stays cheap enough for the event queue because this renders a bounded pre-render, not a
        // live feed -- `ComposeScenePump`, which does drive a live feed, has the same hazard and is
        // deliberately left for a change that can measure the cost.
        val scene = withContext(sceneDispatcher) {
            ImageComposeScene(width, height, Density(1f)) {
                val composition by rememberLottieComposition {
                    LottieCompositionSpec.JsonString(lottieJson.ifBlank { "{}" })
                }
                val loaded = composition != null
                SideEffect { if (loaded) compositionLoaded = true }
                Image(
                    painter = rememberLottiePainter(
                        composition = composition,
                        progress = { currentProgress },
                        fontManager = LottieFonts
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        try {
            var timeNanos = 0L
            // Takes the progress write with it: `render` advances the snapshot itself, so the value
            // is applied before the scene recomposes without a separate `sendApplyNotifications`.
            suspend fun renderOnce(progress: Float? = null): org.jetbrains.skia.Image =
                withContext(sceneDispatcher) {
                    if (progress != null) currentProgress = progress
                    timeNanos += FRAME_NANOS
                    scene.render(timeNanos)
                }

            // Pump the scene until the async Lottie parse finishes
            val deadline = System.currentTimeMillis() + COMPOSITION_LOAD_TIMEOUT_MS
            while (!compositionLoaded && System.currentTimeMillis() < deadline) {
                renderOnce().close()
                delay(FRAME_INTERVAL_MS)
            }
            check(compositionLoaded) { "Lottie composition failed to load for off-screen rendering" }

            val intBuf = IntArray(width * height)
            block { progress ->
                val img = renderOnce(progress)
                try {
                    img.toComposeImageBitmap().readPixels(intBuf)
                } finally {
                    img.close()
                }
                intBuf
            }
        } finally {
            withContext(NonCancellable + sceneDispatcher) { scene.close() }
        }
    }
}
