package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.LottieComposition
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.skia.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LowerThirdPresenterRenderTest {

    private val emptyComposition = LottieComposition.Companion.parse(
        """{"v":"5.5.2","fr":30,"ip":0,"op":30,"w":100,"h":100,"nm":"test","ddd":0,"assets":[],"layers":[]}"""
    )

    private fun solidFrame(argb: Int, size: Int = 4): LottieFrame {
        val bitmap = Bitmap()
        bitmap.allocN32Pixels(size, size)
        val pixels = IntArray(size * size) { argb }
        val bytes = ByteArray(pixels.size * 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels)
        bitmap.installPixels(bytes)
        bitmap.setImmutable()
        return LottieFrame(bitmap.asComposeImageBitmap(), 0, bitmap)
    }

    private fun renderAndSample(
        composition: LottieComposition?,
        frame: LottieFrame?,
        outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    ): Color {
        var pixel: Color? = null
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("lt").size(40.dp).background(Color.Black)) {
                    LowerThirdPresenter(
                        composition = composition,
                        progress = { 0f },
                        outputRole = outputRole,
                        frame = frame,
                    )
                }
            }
            pixel = onNodeWithTag("lt").captureToImage().toPixelMap()[20, 20]
        }
        return pixel!!
    }

    @Test
    fun `neither frame nor composition renders nothing`() {
        val pixel = renderAndSample(composition = null, frame = null)
        assertEquals(0f, pixel.red)
        assertEquals(0f, pixel.green)
        assertEquals(0f, pixel.blue)
    }

    @Test
    fun `a pre-decoded frame with no composition draws the frame bitmap`() {
        val pixel = renderAndSample(composition = null, frame = solidFrame(0xFF00FF00.toInt()))
        assertEquals(0f, pixel.red)
        assertEquals(1f, pixel.green)
        assertEquals(0f, pixel.blue)
    }

    @Test
    fun `a pre-decoded frame takes priority over the live painter`() {
        val pixel = renderAndSample(composition = emptyComposition, frame = solidFrame(0xFF00FF00.toInt()))
        assertEquals(0f, pixel.red)
        assertEquals(1f, pixel.green)
        assertEquals(0f, pixel.blue)
    }

    @Test
    fun `composition alone renders via the live painter without crashing`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(40.dp)) {
                LowerThirdPresenter(
                    composition = emptyComposition,
                    progress = { 0f },
                    frame = null,
                )
            }
        }
    }

    @Test
    fun `key output role forces the pre-decoded frame to white`() {
        val pixel = renderAndSample(
            composition = null,
            frame = solidFrame(0xFFFF0000.toInt()),
            outputRole = Constants.OUTPUT_ROLE_KEY,
        )
        assertEquals(1f, pixel.red)
        assertEquals(1f, pixel.green)
        assertEquals(1f, pixel.blue)
    }

    @Test
    fun `normal output role does not force white`() {
        val pixel = renderAndSample(composition = null, frame = solidFrame(0xFFFF0000.toInt()))
        assertEquals(1f, pixel.red)
        assertEquals(0f, pixel.green)
        assertEquals(0f, pixel.blue)
    }

    // ── Edge to edge ────────────────────────────────────────────────────────────────────────────

    /**
     * The animation reaches every corner of its output, with no inset of the presenter's own.
     *
     * This is the whole of "a Lottie file is self-contained". Four settings insets used to pad it,
     * and only three of the four output paths honoured them: the ATEM media pool renders through
     * [LowerThirdOffscreenRenderer], which takes no settings at all, so the same animation was
     * framed one way over NDI and another through the switcher. They were raw dp on top of that,
     * unscaled, while every other presenter scales its insets against a 1920x1080 reference — so the
     * same number meant a different inset on a 4K screen than on a 1920 feed.
     *
     * Sampled at the corners rather than the centre, because the centre was covered either way; it
     * is the corners an inset used to take away.
     */
    @Test
    fun `the animation fills its output to the corners`() {
        val corners = mutableListOf<Color>()
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("lt").size(40.dp).background(Color.Black)) {
                    LowerThirdPresenter(
                        composition = null,
                        progress = { 0f },
                        frame = solidFrame(0xFF00FF00.toInt()),
                    )
                }
            }
            val map = onNodeWithTag("lt").captureToImage().toPixelMap()
            val last = 40 - 1
            corners += listOf(map[0, 0], map[last, 0], map[0, last], map[last, last])
        }

        // A square frame in a square box: `ContentScale.Fit` covers it completely, so any black
        // corner would be padding rather than letterboxing.
        corners.forEachIndexed { index, pixel ->
            assertEquals(1f, pixel.green, "corner $index must be covered by the animation")
            assertEquals(0f, pixel.red, "corner $index must be covered by the animation")
        }
    }
}
