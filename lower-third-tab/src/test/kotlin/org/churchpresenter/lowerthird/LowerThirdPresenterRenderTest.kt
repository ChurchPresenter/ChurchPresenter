package org.churchpresenter.lowerthird

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
import org.churchpresenter.settings.AppSettings
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
                        appSettings = AppSettings(),
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
                    appSettings = AppSettings(),
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

    // ── The live painter, as opposed to a pre-decoded frame ─────────────────────

    @Test
    fun `a key output draws the live composition through the key filter too`() {
        // The frame path and the painter path each carry their own `if (isKey)`; a test that only
        // ever passes a frame leaves the painter's copy unexercised, and a key output would then
        // silently ship colour to the fill/key pair.
        renderAndSample(
            composition = emptyComposition,
            frame = null,
            outputRole = Constants.OUTPUT_ROLE_KEY,
        )
    }

    @Test
    fun `a normal output draws the live composition unfiltered`() {
        renderAndSample(composition = emptyComposition, frame = null)
    }

    @Test
    fun `the output role defaults to normal when the caller does not say`() {
        var drawn = false
        runComposeUiTest {
            setContent {
                Box(Modifier.testTag("lt").size(40.dp).background(Color.Black)) {
                    // Neither `outputRole` nor `frame` given — the shape the stage monitor uses.
                    LowerThirdPresenter(
                        composition = emptyComposition,
                        progress = { 0f },
                        appSettings = AppSettings(),
                    )
                }
            }
            onNodeWithTag("lt").captureToImage()
            drawn = true
        }
        assertEquals(true, drawn, "the presenter has to draw with only its three required inputs")
    }
}
