package org.churchpresenter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two pieces behind broadcast key-signal output: [Modifier.keySignal], a documented no-op for
 * layout purposes (key conversion happens elsewhere — text presenters force white directly,
 * DeckLink key converts at capture time), and [keyColorFilter], the `ColorFilter` that does the
 * real work for `Image`-backed content (Lottie, PNG) by forcing RGB to white while preserving the
 * source alpha.
 *
 * [keyColorFilter]'s actual pixel effect is verified with [captureToImage], not just inspected
 * structurally — `ColorFilter` exposes no public accessor back to its `ColorMatrix`, so the only
 * way to prove what it does is to render something through it and read the resulting pixels back,
 * the same technique `AtemSettingsTabTestSupport.fieldBorderColour` already uses in this suite.
 */
@OptIn(ExperimentalTestApi::class)
class KeySignalModifierTest {

    @Test
    fun `keySignal is a true no-op — it does not change layout`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.testTag("box").size(50.dp).keySignal())
            }
        }
        val size = onNodeWithTag("box").fetchSemanticsNode().size
        assertEquals(50, size.width, "keySignal() must not alter the modifier chain's layout")
        assertEquals(50, size.height, "keySignal() must not alter the modifier chain's layout")
    }

    @Test
    fun `keyColorFilter forces RGB to pure white while preserving the source alpha`() = runComposeUiTest {
        assertKeyedPixel(sourceColor = Color(1f, 0f, 0f, 0.5f))
        assertKeyedPixel(sourceColor = Color(0f, 0f, 1f, 1f))
    }

    private fun ComposeUiTest.assertKeyedPixel(sourceColor: Color) {
        val bitmap = ImageBitmap(4, 4)
        Canvas(bitmap).drawRect(Rect(0f, 0f, 4f, 4f), Paint().apply { color = sourceColor })

        setContent {
            MaterialTheme {
                Image(
                    painter = BitmapPainter(bitmap),
                    contentDescription = null,
                    colorFilter = keyColorFilter,
                    modifier = Modifier.testTag("img").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("img").captureToImage().toPixelMap()[20, 20]
        assertEquals(1f, pixel.red, "keyColorFilter must force red to full white regardless of the source hue")
        assertEquals(1f, pixel.green, "keyColorFilter must force green to full white regardless of the source hue")
        assertEquals(1f, pixel.blue, "keyColorFilter must force blue to full white regardless of the source hue")
        assertTrue(
            abs(sourceColor.alpha - pixel.alpha) < 0.01f,
            "keyColorFilter must preserve the source alpha unchanged " +
                "(expected ${sourceColor.alpha}, got ${pixel.alpha})",
        )
    }
}
