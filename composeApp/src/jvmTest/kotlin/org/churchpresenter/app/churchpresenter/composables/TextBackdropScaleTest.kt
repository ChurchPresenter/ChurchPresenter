@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.text.TextBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a backdrop draws, in pixels: that it scales with the type when a preview shrinks it, and that
 * changing the settings repaints without waiting for the text itself to change.
 */
class TextBackdropScaleTest {

    private val outline = TextBackdrop(
        border = true,
        borderColor = "#FF0000",
        borderOpacity = 100,
        borderWidth = 8,
        borderPadding = 16,
    )

    private fun ComposeUiTest.redPixels(tag: String = "shot"): Int =
        onNodeWithTag(tag).captureToImage().toPixelMap().count { it.red > 0.4f && it.green < 0.4f && it.blue < 0.4f }

    private inline fun PixelMap.count(predicate: (Color) -> Boolean): Int {
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (predicate(this[x, y])) found++
        return found
    }

    private fun textSource(backdrop: TextBackdrop) =
        SceneSource.TextSource(id = "t", name = "T", text = "Hi", fontSize = 60, backdrop = backdrop)

    private fun ComposeUiTest.canvasText(source: SceneSource.TextSource, fontScale: Float) = setContent {
        MaterialTheme {
            SceneSourceRenderer(
                source = source,
                modifier = Modifier.testTag("shot").size(300.dp).background(Color.Black),
                fontScale = fontScale,
            )
        }
    }

    // ── Scale ─────────────────────────────────────────────────────────────────

    @Test
    fun `a canvas source shrinks its backdrop by the same factor as its type`() {
        var full = 0
        var quarter = 0
        runComposeUiTest {
            canvasText(textSource(outline), fontScale = 1f)
            full = redPixels()
        }
        runComposeUiTest {
            canvasText(textSource(outline), fontScale = 0.25f)
            quarter = redPixels()
        }
        assertTrue(full > 0, "the outline must be drawn at full size")
        assertTrue(quarter > 0, "the outline must still be drawn in a shrunken preview")
        assertTrue(
            quarter < full / 2,
            "a quarter-scale preview must draw a far smaller outline than full size: $quarter vs $full",
        )
    }

    @Test
    fun `an empty backdrop draws nothing at any scale`() {
        for (scale in listOf(1f, 0.25f)) {
            runComposeUiTest {
                canvasText(textSource(TextBackdrop()), fontScale = scale)
                assertEquals(0, redPixels(), "nothing may be painted for a backdrop that is off")
            }
        }
    }

    // ── Repainting ────────────────────────────────────────────────────────────

    @Test
    fun `turning a backdrop on repaints text that has not changed`() = runComposeUiTest {
        var backdrop by mutableStateOf(TextBackdrop())
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    source = textSource(backdrop),
                    modifier = Modifier.testTag("shot").size(300.dp).background(Color.Black),
                    fontScale = 1f,
                )
            }
        }
        assertEquals(0, redPixels())

        backdrop = outline
        waitForIdle()
        assertTrue(redPixels() > 0, "turning the backdrop on must repaint without the text changing")
    }

    @Test
    fun `widening the border repaints text that has not changed`() = runComposeUiTest {
        var backdrop by mutableStateOf(outline)
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    source = textSource(backdrop),
                    modifier = Modifier.testTag("shot").size(300.dp).background(Color.Black),
                    fontScale = 1f,
                )
            }
        }
        val thin = redPixels()

        backdrop = outline.copy(borderWidth = 20)
        waitForIdle()
        assertTrue(redPixels() > thin, "a wider border must repaint thicker: was $thin")
    }

    @Test
    fun `a block backdrop repaints when its settings change`() = runComposeUiTest {
        var backdrop by mutableStateOf(TextBackdrop())
        setContent {
            MaterialTheme {
                val block = rememberTextBlockBackdrop(backdrop)
                Box(
                    modifier = Modifier.testTag("shot").size(300.dp).background(Color.Black)
                        .then(block.containerModifier)
                ) {
                    Text(
                        text = "Hi",
                        color = Color.White,
                        fontSize = 40.sp,
                        modifier = block.lineModifier("l0"),
                        onTextLayout = { block.onTextLayout("l0", it) },
                    )
                }
            }
        }
        assertEquals(0, redPixels())

        backdrop = outline
        waitForIdle()
        assertTrue(redPixels() > 0, "turning a block backdrop on must repaint the lines already laid out")
    }
}
