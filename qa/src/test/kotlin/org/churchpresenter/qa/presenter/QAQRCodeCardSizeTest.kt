package org.churchpresenter.qa.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The unboxed QR card is sized to the output it is on, so a dev window, a portrait output and a
 * preview tile draw the same card a full-HD screen does, at their own scale -- and a small window
 * never pushes the message off its bottom. Invariants, not pixels: font metrics differ by platform.
 */
@OptIn(ExperimentalTestApi::class)
class QAQRCodeCardSizeTest {

    private val message = "Scan to ask a question"

    private fun ComposeUiTest.card(width: Dp, height: Dp) = setContent {
        Box(Modifier.size(width, height)) { QAQRCodePresenter(url = "https://example.church/qa") }
    }

    private fun ComposeUiTest.code(): DpRect = onNodeWithContentDescription("QR Code").getBoundsInRoot()
    private fun ComposeUiTest.label(): DpRect = onNodeWithText(message).getBoundsInRoot()

    private fun DpRect.inside(width: Dp, height: Dp) =
        left >= 0.dp && top >= 0.dp && right <= width && bottom <= height

    @Test
    fun `on a small landscape window the message stays on screen`() = runComposeUiTest {
        card(320.dp, 180.dp)

        assertTrue(label().inside(320.dp, 180.dp), "the message ran off the window: ${label()}")
        assertTrue(code().bottom < label().top, "the message sits under the code")
    }

    @Test
    fun `on a narrow portrait output the message stays on screen and the code fits the width`() =
        runComposeUiTest {
            card(200.dp, 400.dp)

            assertTrue(label().inside(200.dp, 400.dp), "the message ran off the output: ${label()}")
            assertTrue(code().width <= 200.dp * 0.7f + 1.dp, "the code is wider than its share: ${code().width}")
        }

    @Test
    fun `the card scales with the output, keeping its proportions`() {
        var small = DpRect(0.dp, 0.dp, 0.dp, 0.dp)
        var large = small
        runComposeUiTest { card(480.dp, 270.dp); small = code() }
        runComposeUiTest { card(960.dp, 540.dp); large = code() }

        assertTrue(
            abs(large.width.value - 2 * small.width.value) <= 2f,
            "half the output, half the code: $small $large",
        )
        // 1080 lines give a 512 code, so 540 give 256.
        assertTrue(abs(large.width.value - 256f) <= 1f, "a 540-line output draws a 256 code: ${large.width}")
    }
}
