package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.text.TextBackdrop
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BackdropEdgeTest {

    private val band = TextBackdrop(lineBackground = true, lineBackgroundColor = "#FF0000", lineBackgroundOpacity = 100)
    private val bordered = TextBackdrop(border = true, borderColor = "#FF0000", borderWidth = 3, borderPadding = 4)

    private fun ComposeUiTest.block(backdrop: TextBackdrop, lines: List<String>, placeAll: Boolean = true) {
        setContent {
            MaterialTheme {
                val block = rememberTextBlockBackdrop(backdrop)
                Column(Modifier.testTag("shot").size(300.dp).background(Color.Black).then(block.containerModifier)) {
                    lines.forEachIndexed { index, line ->
                        Text(
                            text = line,
                            modifier = Modifier.fillMaxWidth()
                                .then(if (placeAll || index == 0) block.lineModifier(index) else Modifier),
                            style = TextStyle(fontSize = 20.sp),
                            color = Color.White,
                            onTextLayout = { block.onTextLayout(index, it) },
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.anyRed(): Boolean {
        val pixels = onNodeWithTag("shot").captureToImage().toPixelMap()
        for (y in 0 until pixels.height step 2) for (x in 0 until pixels.width step 2) {
            val c = pixels[x, y]
            if (c.red > 0.6f && c.green < 0.3f && c.blue < 0.3f) return true
        }
        return false
    }

    @Test
    fun `an off backdrop paints nothing behind a block`() = runComposeUiTest {
        block(TextBackdrop(), listOf("Grace", "Peace"))
        assertFalse(anyRed())
    }

    @Test
    fun `a bordered block of blank lines paints nothing`() = runComposeUiTest {
        block(bordered, listOf("", ""))
        assertFalse(anyRed())
    }

    @Test
    fun `a bordered block paints one frame around its lines`() = runComposeUiTest {
        block(bordered, listOf("Grace", "Peace"))
        assertTrue(anyRed())
    }

    @Test
    fun `a line that was never placed is left out of the block`() = runComposeUiTest {
        block(bordered, listOf("Placed", "Never placed"), placeAll = false)
        assertTrue(anyRed(), "the placed line still gets its frame")
    }

    @Test
    fun `a band for a line that was never placed is skipped`() = runComposeUiTest {
        block(band, listOf("Placed", "Never placed"), placeAll = false)
        assertTrue(anyRed())
    }

    @Test
    fun `uniform bands over blank lines draw nothing`() = runComposeUiTest {
        block(band.copy(lineBackgroundUniformWidth = true), listOf("", ""))
        assertFalse(anyRed())
    }

    @Test
    fun `a border with no width and no padding draws no frame`() = runComposeUiTest {
        block(bordered.copy(borderWidth = 0, borderPadding = 0), listOf("Grace"))
        assertFalse(anyRed())
    }
}
