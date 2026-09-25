@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.text.TextOutline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Text bent around a circle, on the canvas — the one thing on a scene that has no layout of its own.
 *
 * Compose has no text-on-a-path, so [CurvedText] measures every glyph, works out the arc from the
 * line's own width, and draws each one rotated to the angle its centre sits at. Nothing tested it:
 * it is reached only through a canvas text or Bible source whose `curve` is not zero, and every
 * scene test used the straight path.
 *
 * Asserted on where ink lands rather than on glyph shapes, which differ across the three platforms:
 * an arch puts its ends below its middle and a cup puts them above, and that relationship is the
 * whole of what "curve" means. The sign of [SceneSource.TextSource.curve] is what picks between them.
 */
class CurvedTextTest {

    private val stage = Modifier.testTag("shot").size(300.dp).background(Color.Black)
    private val style = TextStyle(color = Color.White, fontSize = 28.sp)

    private inline fun PixelMap.inkRows(predicate: (Color) -> Boolean): List<Int> =
        (0 until height).filter { y -> (0 until width).any { x -> predicate(this[x, y]) } }

    /** How many pixels of the drawn line's own colour landed anywhere in the shot. */
    private fun PixelMap.inkCount(): Int =
        (0 until width).sumOf { x -> (0 until height).count { y -> isInk(x, y) } }

    private fun ComposeUiTest.shot(): PixelMap = onNodeWithTag("shot").captureToImage().toPixelMap()

    private fun PixelMap.isInk(x: Int, y: Int) = this[x, y].let { it.red > 0.5f && it.green > 0.5f }

    /** The topmost row carrying ink in the left/right thirds, and in the middle third. */
    private fun PixelMap.endsAndMiddleTops(): Pair<Int, Int> {
        val third = width / 3
        fun topIn(range: IntRange) =
            (0 until height).firstOrNull { y -> range.any { x -> isInk(x, y) } } ?: height
        val ends = minOf(topIn(0 until third), topIn(third * 2 until width))
        return ends to topIn(third until third * 2)
    }

    /**
     * The tag goes on a wrapping `Box`, not on [CurvedText]'s own modifier.
     *
     * A stroked curve draws the whole bent line **twice** into the same box — that is how the two
     * passes land glyph for glyph — and it hands its modifier to both, so tagging it directly puts
     * the tag on two nodes and the capture cannot say which one it meant.
     */
    private fun ComposeUiTest.showCurved(curve: Float, outline: TextOutline = TextOutline()) {
        setContent {
            MaterialTheme {
                Box(stage) {
                    CurvedText(
                        text = "ARCHED TEXT",
                        curve = curve,
                        style = style,
                        modifier = Modifier.fillMaxSize(),
                        outline = outline,
                        outlineScale = 1f,
                    )
                }
            }
        }
    }

    @Test
    fun `a straight line puts its ends level with its middle`() {
        // The zero case still goes through the same drawing: a zero sweep is a circle of infinite
        // radius, and the guard for it is what keeps a flat line flat rather than dividing by zero.
        runComposeUiTest {
            showCurved(curve = 0f)
            val (ends, middle) = shot().endsAndMiddleTops()
            assertTrue(
                kotlin.math.abs(ends - middle) <= LEVEL_TOLERANCE,
                "a flat line's ends and middle start at the same height: $ends vs $middle",
            )
        }
    }

    @Test
    fun `a positive curve arches the line over the circle`() {
        runComposeUiTest {
            showCurved(curve = 60f)
            val (ends, middle) = shot().endsAndMiddleTops()
            assertTrue(ends > middle, "an arch drops its ends below its middle: ends=$ends middle=$middle")
        }
    }

    @Test
    fun `a negative curve cups the line under it`() {
        runComposeUiTest {
            showCurved(curve = -60f)
            val (ends, middle) = shot().endsAndMiddleTops()
            assertTrue(ends < middle, "a cup lifts its ends above its middle: ends=$ends middle=$middle")
        }
    }

    @Test
    fun `a stroked curve draws its outline under the fill`() {
        // The stroke is a whole second pass of the same bent line, drawn into the same box — a bent
        // line has no layout to disturb, so the two land glyph for glyph. With a colour of its own
        // it has to put ink where the unstroked version had none.
        var plain = 0
        var stroked = 0
        runComposeUiTest {
            showCurved(curve = 40f)
            plain = shot().inkCount()
        }
        runComposeUiTest {
            showCurved(curve = 40f, outline = TextOutline(enabled = true, width = 4, color = "#FFFFFF"))
            stroked = shot().inkCount()
        }
        assertTrue(plain > 0, "the bent line is drawn to begin with")
        assertTrue(stroked > plain, "a stroke has to add ink, got $stroked vs $plain")
    }

    @Test
    fun `an empty line draws nothing rather than dividing by a zero width`() {
        // `lineWidth` is the sum of the glyph widths and the radius is derived from it, so an empty
        // string is the one input that has to be turned away before the arithmetic starts.
        runComposeUiTest {
            setContent {
                MaterialTheme { Box(stage) { CurvedText("", 50f, style, Modifier.fillMaxSize()) } }
            }
            val map = shot()
            assertEquals(
                emptyList(),
                map.inkRows { it.red > 0.5f && it.green > 0.5f },
                "nothing is drawn for an empty line",
            )
        }
    }

    @Test
    fun `a newline becomes a space rather than wrapping`() {
        // Stated in the composable's own doc: each glyph is placed along the arc, so there is no
        // second line to wrap onto. The two spellings have to draw the same ink.
        var joined = 0
        var broken = 0
        runComposeUiTest {
            setContent { MaterialTheme { Box(stage) { CurvedText("TWO WORDS", 40f, style, Modifier.fillMaxSize()) } } }
            joined = shot().inkCount()
        }
        runComposeUiTest {
            setContent { MaterialTheme { Box(stage) { CurvedText("TWO\nWORDS", 40f, style, Modifier.fillMaxSize()) } } }
            broken = shot().inkCount()
        }
        assertEquals(joined, broken, "a newline is drawn as the space it is replaced with")
    }

    private companion object {
        /** Antialiasing puts a stray pixel a row early; a flat line is flat to within this. */
        const val LEVEL_TOLERANCE = 3
    }
}
