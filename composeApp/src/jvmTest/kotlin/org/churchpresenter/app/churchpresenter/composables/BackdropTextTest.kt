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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.text.TextBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [BackdropText] and the painter behind it — the wrapper the ordinary call sites use, and the
 * `modifier` + `onTextLayout` pair the presenters chain onto their own measuring.
 *
 * Asserted in pixels rather than by asking the painter what it holds: whether a backdrop is
 * actually drawn is the whole point of the class, and the painter's own state says nothing about
 * whether the `drawBehind` ever ran.
 */
class BackdropTextTest {

    private val redOutline = TextBackdrop(
        border = true,
        borderColor = "#FF0000",
        borderOpacity = 100,
        borderWidth = 8,
        borderPadding = 14,
    )

    private val redBand = TextBackdrop(
        lineBackground = true,
        lineBackgroundColor = "#FF0000",
        lineBackgroundOpacity = 100,
        lineBackgroundHeight = 10,
    )

    private inline fun PixelMap.count(predicate: (Color) -> Boolean): Int {
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (predicate(this[x, y])) found++
        return found
    }

    private fun ComposeUiTest.redPixels(): Int = onNodeWithTag("shot")
        .captureToImage().toPixelMap().count { it.red > 0.4f && it.green < 0.4f && it.blue < 0.4f }

    private val stage = Modifier.testTag("shot").size(240.dp).background(Color.Black)

    // ── The String overload ───────────────────────────────────────────────────

    @Test
    fun `the text itself is drawn`() = runComposeUiTest {
        setContent { MaterialTheme { BackdropText("Hosanna", TextBackdrop(), modifier = stage) } }
        onNodeWithText("Hosanna").assertExists("the wrapper must still be a Text")
    }

    @Test
    fun `an off backdrop paints nothing behind the text`() = runComposeUiTest {
        setContent { MaterialTheme { BackdropText("Hi", TextBackdrop(), modifier = stage) } }
        assertEquals(0, redPixels(), "an empty backdrop must paint nothing at all")
    }

    @Test
    fun `a border backdrop is painted behind the text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                BackdropText("Hi", redOutline, modifier = stage, style = TextStyle(fontSize = 40.sp))
            }
        }
        assertTrue(redPixels() > 0, "the box must be drawn")
    }

    @Test
    fun `a line background is painted behind the text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                BackdropText("Hi", redBand, modifier = stage, style = TextStyle(fontSize = 40.sp))
            }
        }
        assertTrue(redPixels() > 0, "the band must be drawn")
    }

    @Test
    fun `the caller's own layout callback still fires`() = runComposeUiTest {
        var seen: TextLayoutResult? = null
        setContent {
            MaterialTheme {
                BackdropText("Hi", redOutline, modifier = stage, onTextLayout = { seen = it })
            }
        }
        waitForIdle()
        assertNotNull(seen, "the wrapper must chain onto the caller's onTextLayout, not replace it")
        assertEquals(1, seen!!.lineCount, "one short line")
    }

    @Test
    fun `turning the backdrop on repaints text that never changed`() = runComposeUiTest {
        var backdrop by mutableStateOf(TextBackdrop())
        setContent {
            MaterialTheme {
                BackdropText("Hi", backdrop, modifier = stage, style = TextStyle(fontSize = 40.sp))
            }
        }
        assertEquals(0, redPixels())
        backdrop = redOutline
        waitForIdle()
        assertTrue(redPixels() > 0, "the painter must keep the layout it already had")
    }

    @Test
    fun `widening the border repaints thicker without the text changing`() = runComposeUiTest {
        var backdrop by mutableStateOf(redOutline)
        setContent {
            MaterialTheme {
                BackdropText("Hi", backdrop, modifier = stage, style = TextStyle(fontSize = 40.sp))
            }
        }
        val thin = redPixels()
        backdrop = redOutline.copy(borderWidth = 20)
        waitForIdle()
        assertTrue(redPixels() > thin, "a wider box must paint more: was $thin")
    }

    @Test
    fun `maxLines still clips the wrapped text`() = runComposeUiTest {
        var lines = 0
        setContent {
            MaterialTheme {
                BackdropText(
                    text = "one two three four five six seven eight nine ten",
                    backdrop = redOutline,
                    modifier = stage,
                    style = TextStyle(fontSize = 30.sp),
                    maxLines = 2,
                    onTextLayout = { lines = it.lineCount },
                )
            }
        }
        waitForIdle()
        assertEquals(2, lines, "maxLines must reach the Text underneath")
    }

    // ── The AnnotatedString overload ──────────────────────────────────────────

    @Test
    fun `the annotated overload draws its text`() = runComposeUiTest {
        setContent {
            MaterialTheme { BackdropText(AnnotatedString("Selah"), TextBackdrop(), modifier = stage) }
        }
        onNodeWithText("Selah").assertExists()
    }

    @Test
    fun `the annotated overload paints its backdrop`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                BackdropText(
                    text = AnnotatedString("Hi"),
                    backdrop = redBand,
                    modifier = stage,
                    style = TextStyle(fontSize = 40.sp),
                )
            }
        }
        assertTrue(redPixels() > 0, "the annotated overload must paint the same backdrop")
    }

    @Test
    fun `the annotated overload paints nothing when the backdrop is off`() = runComposeUiTest {
        setContent {
            MaterialTheme { BackdropText(AnnotatedString("Hi"), TextBackdrop(), modifier = stage) }
        }
        assertEquals(0, redPixels())
    }

    @Test
    fun `the annotated overload chains the caller's layout callback`() = runComposeUiTest {
        var seen: TextLayoutResult? = null
        setContent {
            MaterialTheme {
                BackdropText(AnnotatedString("Hi"), redOutline, modifier = stage, onTextLayout = { seen = it })
            }
        }
        waitForIdle()
        assertNotNull(seen, "the annotated overload must chain onTextLayout too")
    }

    // ── The painter on its own ────────────────────────────────────────────────

    @Test
    fun `the remembered painter survives a settings change`() = runComposeUiTest {
        var backdrop by mutableStateOf(TextBackdrop())
        val seen = mutableListOf<TextBackdropPainter>()
        setContent {
            MaterialTheme {
                val painter = rememberTextBackdropPainter(backdrop)
                seen += painter
                Box(stage)
            }
        }
        backdrop = redOutline
        waitForIdle()
        assertTrue(seen.size >= 2, "the composable must have recomposed")
        assertSame(seen.first(), seen.last(), "one painter across the change, not one per setting")
    }

    @Test
    fun `a painter with no layout yet paints nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val painter = rememberTextBackdropPainter(redOutline)
                Box(stage.then(painter.modifier))
            }
        }
        assertEquals(0, redPixels(), "there is nothing to frame until a Text reports its lines")
    }

    @Test
    fun `a painter driven by hand paints once the layout arrives`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val painter = rememberTextBackdropPainter(redOutline)
                Box(stage) {
                    Text(
                        text = "Hi",
                        color = Color.White,
                        fontSize = 40.sp,
                        modifier = painter.modifier,
                        onTextLayout = { painter.onTextLayout(it) },
                    )
                }
            }
        }
        waitForIdle()
        assertTrue(redPixels() > 0, "the presenters' own chaining path must paint")
    }

    @Test
    fun `scale shrinks what the painter draws`() {
        var full = 0
        var quarter = 0
        for (scale in listOf(1f, 0.25f)) {
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        val painter = rememberTextBackdropPainter(redOutline, scale)
                        Box(stage) {
                            Text(
                                text = "Hi",
                                color = Color.White,
                                fontSize = (40 * scale).sp,
                                modifier = painter.modifier,
                                onTextLayout = { painter.onTextLayout(it) },
                            )
                        }
                    }
                }
                waitForIdle()
                if (scale == 1f) full = redPixels() else quarter = redPixels()
            }
        }
        assertTrue(full > 0 && quarter > 0, "both sizes must draw something")
        assertTrue(quarter < full / 2, "a quarter-scale preview must draw far less: $quarter vs $full")
    }
}
