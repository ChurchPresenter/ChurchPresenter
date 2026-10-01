package org.churchpresenter.lottiegen.render

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lottie text at the first frame. Compottie 2.3.2 draws no text there for a single-keyframe text
 * document (alexzhirkevich/compottie#90); [lottieDrawProgress] keeps every painter just past it.
 *
 * The fixture is a blue band with a red title, so blue pixels say the animation is drawn and red
 * ones say its text is.
 */
class LottieDrawProgressTest {

    @Test
    fun `progress is kept off the first frame and left alone after it`() {
        assertEquals(FIRST_FRAME_NUDGE, lottieDrawProgress(0f))
        assertEquals(FIRST_FRAME_NUDGE, lottieDrawProgress(-1f))
        assertEquals(0.5f, lottieDrawProgress(0.5f))
        assertEquals(1f, lottieDrawProgress(1f))
    }

    @Test
    fun `a generator painter parked on the first frame draws its title`() = render({
        val composition by rememberLottieComposition(titled("Parked")) {
            LottieCompositionSpec.JsonString(titled("Parked"))
        }
        Image(
            painter = rememberShapedLottiePainter(composition, progress = { 0f }, grouped = false),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }) {
        pumpUntil("the title drawn at the first frame") { it.red >= DRAWN }
    }

    /**
     * Compottie's own behaviour, pinned so the workaround is not kept past its need: once a release
     * draws the title here, this fails, and [lottieDrawProgress] can go.
     */
    @Test
    fun `compottie itself still drops single-keyframe text at the first frame`() = render({
        val composition by rememberLottieComposition(titled("Raw")) {
            LottieCompositionSpec.JsonString(titled("Raw"))
        }
        Image(
            painter = rememberLottiePainter(composition, progress = { 0f }),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }) {
        val drawn = pumpUntil("the band drawn") { it.blue >= DRAWN }
        assertTrue(drawn.red == 0, "compottie drew the first frame's title (${drawn.red} red pixels)")
    }

    private data class Pixels(val red: Int, val blue: Int)

    @OptIn(ExperimentalComposeUiApi::class)
    private class Probe(private val scene: ImageComposeScene) {
        private var timeNanos = 0L

        fun pixels(): Pixels {
            timeNanos += FRAME_STEP_NANOS
            val image = scene.render(timeNanos)
            val argb = IntArray(WIDTH * HEIGHT)
            try {
                image.toComposeImageBitmap().readPixels(argb)
            } finally {
                image.close()
            }
            var red = 0
            var blue = 0
            for (c in argb) {
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r > PURE && g < FAINT && b < FAINT) red++
                if (b > PURE && r < FAINT && g < FAINT) blue++
            }
            return Pixels(red, blue)
        }

        suspend fun pumpUntil(what: String, condition: (Pixels) -> Boolean): Pixels {
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val now = pixels()
                if (condition(now)) return now
                // The composition is parsed off this thread; let its result land between frames.
                yield()
            }
            throw AssertionError("timed out after ${TIMEOUT_MS}ms waiting for $what")
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(content: @Composable () -> Unit, block: suspend Probe.() -> Unit) =
        runBlocking(Dispatchers.Swing) {
            val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f), content = content)
            try {
                Probe(scene).block()
            } finally {
                scene.close()
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

        /**
         * A blue band under a red title whose document has the one keyframe static text has. Each
         * test passes its own [text] so no two share a cached composition -- a composition another
         * painter has drawn at a later frame can carry that frame's text back to the first.
         */
        fun titled(text: String) = """
            {"v":"5.7.4","fr":30,"ip":0,"op":30,"w":400,"h":200,"layers":[
             {"ty":5,"nm":"title","ind":1,"ip":0,"op":30,"st":0,
              "ks":{"o":{"a":0,"k":100},"p":{"a":0,"k":[20,120,0]},"a":{"a":0,"k":[0,0,0]},
                    "s":{"a":0,"k":[100,100,100]},"r":{"a":0,"k":0}},
              "t":{"d":{"k":[{"s":{"s":80,"f":"Sans","t":"$text","j":0,"tr":0,"lh":96,"ls":0,"fc":[1,0,0]},"t":0}]},
                   "p":{},"m":{"g":1,"a":{"a":0,"k":[0,0]}},"a":[]}},
             {"ty":4,"nm":"band","ind":2,"ip":0,"op":30,"st":0,
              "ks":{"o":{"a":0,"k":100},"p":{"a":0,"k":[200,100,0]},"a":{"a":0,"k":[0,0,0]},
                    "s":{"a":0,"k":[100,100,100]},"r":{"a":0,"k":0}},
              "shapes":[{"ty":"rc","p":{"a":0,"k":[0,0]},"s":{"a":0,"k":[400,200]},"r":{"a":0,"k":0}},
                        {"ty":"fl","c":{"a":0,"k":[0,0,1,1]},"o":{"a":0,"k":100}}]}],
             "fonts":{"list":[{"fName":"Sans","fFamily":"Sans","fStyle":"Regular","ascent":75}]}}
        """.trimIndent()
    }
}
