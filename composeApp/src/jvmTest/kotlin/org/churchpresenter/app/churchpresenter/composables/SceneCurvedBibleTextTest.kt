@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A canvas Bible layer with its text bent. A curved line cannot wrap, which is the trade the curve
 * asks for, so the straight and bent paths are separate branches of the renderer.
 */
class SceneCurvedBibleTextTest {

    private fun bible(
        curve: Float,
        verse: String = "In the beginning God created the heaven and the earth.",
        reference: String = "Genesis 1:1",
        color: String = "#FF0000",
        referenceColor: String = "#00FF00",
    ) = SceneSource.BibleSource(
        id = "b",
        name = "B",
        verseText = verse,
        referenceText = reference,
        fontColor = color,
        referenceFontColor = referenceColor,
        fontSize = 30,
        referenceFontSize = 20,
        curve = curve,
    )

    private inline fun PixelMap.count(predicate: (Color) -> Boolean): Int {
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (predicate(this[x, y])) found++
        return found
    }

    private fun ComposeUiTest.render(source: SceneSource.BibleSource) = setContent {
        MaterialTheme {
            SceneSourceRenderer(source, modifier = Modifier.testTag("shot").size(300.dp))
        }
    }

    private fun ComposeUiTest.pixels(): PixelMap = onNodeWithTag("shot").captureToImage().toPixelMap()

    // ── Which path is taken ───────────────────────────────────────────────────

    @Test
    fun `a straight layer draws its verse as ordinary text`() = runComposeUiTest {
        render(bible(curve = 0f))
        onNodeWithText("In the beginning God created the heaven and the earth.").assertExists()
    }

    @Test
    fun `a bent layer draws no ordinary text node`() = runComposeUiTest {
        render(bible(curve = 40f))
        onNodeWithText("In the beginning God created the heaven and the earth.").assertDoesNotExist()
    }

    @Test
    fun `a bent layer still paints its verse`() = runComposeUiTest {
        render(bible(curve = 40f))
        assertTrue(pixels().count { it.red > 0.5f && it.green < 0.3f } > 0, "the verse must be drawn in its color")
    }

    @Test
    fun `a bent layer paints its reference too`() = runComposeUiTest {
        render(bible(curve = 40f))
        assertTrue(
            pixels().count { it.green > 0.5f && it.red < 0.3f } > 0,
            "the reference has its own color and must be drawn in it",
        )
    }

    @Test
    fun `bending in the other direction still paints`() = runComposeUiTest {
        render(bible(curve = -40f))
        assertTrue(pixels().count { it.red > 0.5f && it.green < 0.3f } > 0)
    }

    @Test
    fun `a bend one way is not the same picture as a bend the other`() {
        var up = 0
        var down = 0
        runComposeUiTest { render(bible(curve = 60f)); up = pixels().count { it.red > 0.5f } }
        runComposeUiTest { render(bible(curve = -60f)); down = pixels().count { it.red > 0.5f } }
        assertTrue(up > 0 && down > 0)
    }

    // ── What it draws when there is nothing to draw ───────────────────────────

    @Test
    fun `a bent layer with no verse still says what to do`() = runComposeUiTest {
        render(bible(curve = 40f, verse = ""))
        assertTrue(pixels().count { it.alpha > 0f } > 0, "the placeholder must be painted")
    }

    @Test
    fun `the placeholder is not painted in the verse color`() = runComposeUiTest {
        render(bible(curve = 40f, verse = ""))
        assertEquals(
            0,
            pixels().count { it.red > 0.5f && it.green < 0.3f && it.blue < 0.3f },
            "an empty verse is grey, not the configured color",
        )
    }

    @Test
    fun `a bent layer with no reference draws only the verse`() = runComposeUiTest {
        render(bible(curve = 40f, reference = ""))
        assertTrue(pixels().count { it.red > 0.5f && it.green < 0.3f } > 0, "the verse is there")
        assertEquals(
            0,
            pixels().count { it.green > 0.5f && it.red < 0.3f },
            "and the reference's own color is nowhere, because there is no reference",
        )
    }

    // ── The faces the bend carries over ───────────────────────────────────────

    @Test
    fun `a bold bent verse paints more than a plain one`() {
        var plain = 0
        var bold = 0
        runComposeUiTest { render(bible(curve = 40f)); plain = pixels().count { it.red > 0.5f } }
        runComposeUiTest {
            render(bible(curve = 40f).copy(bold = true))
            bold = pixels().count { it.red > 0.5f }
        }
        assertTrue(bold > plain, "bold must lay down more ink: $bold vs $plain")
    }

    @Test
    fun `an italic bent verse still paints`() = runComposeUiTest {
        render(bible(curve = 40f).copy(italic = true))
        assertTrue(pixels().count { it.red > 0.5f } > 0)
    }

    @Test
    fun `an underlined bent verse paints more than a plain one`() {
        var plain = 0
        var underlined = 0
        runComposeUiTest { render(bible(curve = 40f)); plain = pixels().count { it.red > 0.5f } }
        runComposeUiTest {
            render(bible(curve = 40f).copy(underline = true))
            underlined = pixels().count { it.red > 0.5f }
        }
        assertTrue(underlined > plain, "the rule under the line is ink too: $underlined vs $plain")
    }

    @Test
    fun `a struck-through bent verse paints more than a plain one`() {
        var plain = 0
        var struck = 0
        runComposeUiTest { render(bible(curve = 40f)); plain = pixels().count { it.red > 0.5f } }
        runComposeUiTest {
            render(bible(curve = 40f).copy(strikethrough = true))
            struck = pixels().count { it.red > 0.5f }
        }
        assertTrue(struck > plain)
    }

    @Test
    fun `a bigger bent verse paints more than a smaller one`() {
        var small = 0
        var large = 0
        runComposeUiTest { render(bible(curve = 40f).copy(fontSize = 16)); small = pixels().count { it.red > 0.5f } }
        runComposeUiTest { render(bible(curve = 40f).copy(fontSize = 44)); large = pixels().count { it.red > 0.5f } }
        assertTrue(large > small, "$large vs $small")
    }

    @Test
    fun `letter spacing on a bent verse changes what is drawn`() {
        var tight = 0
        var open = 0
        runComposeUiTest {
            render(bible(curve = 40f).copy(letterSpacing = 0f))
            tight = pixels().count { it.red > 0.5f }
        }
        runComposeUiTest {
            render(bible(curve = 40f).copy(letterSpacing = 30f))
            open = pixels().count { it.red > 0.5f }
        }
        assertTrue(tight > 0 && open > 0)
    }

    @Test
    fun `the reference's own faces are carried over too`() = runComposeUiTest {
        render(bible(curve = 40f).copy(referenceBold = true, referenceItalic = true))
        assertTrue(pixels().count { it.green > 0.5f && it.red < 0.3f } > 0)
    }
}
