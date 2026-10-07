package org.churchpresenter.lottiegen.render

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.yield
import kotlin.test.Test

/**
 * Lottie text at the first frame: the shape of every file this generator writes is a text document
 * with a single keyframe, and a painter parked on frame 0 -- a paused preview, the first frame of a
 * playback or a pre-rendered clip -- must draw it. Compottie 2.3.2 did not (alexzhirkevich/compottie#90,
 * fixed in 2.3.3).
 *
 * The fixture is a red title, so red pixels say its text is drawn.
 */
class FirstFrameTextTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a generator painter parked on the first frame draws its title`() = runBlocking(Dispatchers.Swing) {
        val json = TITLED
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
            val composition by rememberLottieComposition(json) { LottieCompositionSpec.JsonString(json) }
            Image(
                painter = rememberShapedLottiePainter(composition, progress = { 0f }, grouped = false),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        try {
            var timeNanos = 0L
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                timeNanos += FRAME_STEP_NANOS
                if (redPixels(scene, timeNanos) >= DRAWN) return@runBlocking
                // The composition is parsed off this thread; let its result land between frames.
                yield()
            }
            throw AssertionError("timed out after ${TIMEOUT_MS}ms waiting for the title at the first frame")
        } finally {
            scene.close()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun redPixels(scene: ImageComposeScene, timeNanos: Long): Int {
        val image = scene.render(timeNanos)
        val argb = IntArray(WIDTH * HEIGHT)
        try {
            image.toComposeImageBitmap().readPixels(argb)
        } finally {
            image.close()
        }
        return argb.count { c ->
            ((c shr 16) and 0xFF) > PURE && ((c shr 8) and 0xFF) < FAINT && (c and 0xFF) < FAINT
        }
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 200
        const val DRAWN = 200
        const val PURE = 200
        const val FAINT = 60
        const val TIMEOUT_MS = 5_000L
        const val FRAME_STEP_NANOS = 50_000_000L

        /** A red title whose document has the one keyframe static text has. */
        val TITLED = """
            {"v":"5.7.4","fr":30,"ip":0,"op":30,"w":400,"h":200,"layers":[
             {"ty":5,"nm":"title","ind":1,"ip":0,"op":30,"st":0,
              "ks":{"o":{"a":0,"k":100},"p":{"a":0,"k":[20,120,0]},"a":{"a":0,"k":[0,0,0]},
                    "s":{"a":0,"k":[100,100,100]},"r":{"a":0,"k":0}},
              "t":{"d":{"k":[{"s":{"s":80,"f":"Sans","t":"Parked","j":0,"tr":0,"lh":96,"ls":0,"fc":[1,0,0]},"t":0}]},
                   "p":{},"m":{"g":1,"a":{"a":0,"k":[0,0]}},"a":[]}}],
             "fonts":{"list":[{"fName":"Sans","fFamily":"Sans","fStyle":"Regular","ascent":75}]}}
        """.trimIndent()
    }
}
