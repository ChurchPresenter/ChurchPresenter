package org.churchpresenter.lottiegen.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.yield

// A preview in a windowless scene on a virtual frame clock, so its animation runs as it does on
// screen. The fixtures paint the Lottie in a red no chrome uses, so red pixels are the Lottie drawn.
@OptIn(ExperimentalComposeUiApi::class)
internal class LottiePreviewProbe private constructor(private val scene: ImageComposeScene) {

    private var timeNanos = 0L

    fun redPixels(): Int {
        timeNanos += FRAME_STEP_NANOS
        val image = scene.render(timeNanos)
        val pixels = IntArray(WIDTH * HEIGHT)
        try {
            image.toComposeImageBitmap().readPixels(pixels)
        } finally {
            image.close()
        }
        return pixels.count(::isRed)
    }

    suspend fun pumpUntil(what: String, condition: (Int) -> Boolean): Int {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val red = redPixels()
            if (condition(red)) return red
            // The composition is parsed off this thread; let its result land between frames.
            yield()
        }
        throw AssertionError("timed out after ${TIMEOUT_MS}ms waiting for $what")
    }

    companion object {
        const val WIDTH = 1200
        const val HEIGHT = 800
        const val DRAWN = 200
        private const val TIMEOUT_MS = 5_000L
        private const val FRAME_STEP_NANOS = 50_000_000L

        fun run(content: @Composable () -> Unit, block: suspend LottiePreviewProbe.() -> Unit) =
            runBlocking(Dispatchers.Swing) {
                val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f), content = content)
                try {
                    LottiePreviewProbe(scene).block()
                } finally {
                    scene.close()
                }
            }

        private fun isRed(argb: Int): Boolean {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            return r > 200 && g < 60 && b < 60
        }
    }
}
