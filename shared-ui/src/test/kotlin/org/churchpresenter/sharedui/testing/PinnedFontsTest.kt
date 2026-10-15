package org.churchpresenter.sharedui.testing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.screenshot.PinnedFonts
import org.churchpresenter.sharedui.screenshot.captureTo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [PinnedFonts] really draws the system font's text in the bundled fonts, and [captureTo] refuses a
 * capture made outside it.
 *
 * Measured rather than looked at: a line in the default family inside [PinnedFonts] is exactly as
 * wide as the same line drawn outside it in the bundled font, named outright. The sample is chosen
 * so Inter and the system font differ by several pixels -- their ordinary text is within a pixel of
 * each other -- and the test checks that it still does. This is what fails if a Compose release
 * changes the internals `ComposeFontCollection` reaches into and the pin quietly stops applying.
 *
 * Lives outside the `screenshot` package: it takes no screenshot, and `ScreenshotInvariantsTest`
 * holds every test class there to the `…ScreenshotTest` name the record job renders.
 */
@OptIn(ExperimentalTestApi::class)
class PinnedFontsTest {

    private companion object {
        const val SAMPLE = "WWWWWWWWWW iiiiiiiiii"
    }

    /** The bundled [file] at [weight], named outright rather than reached through the system's name. */
    private fun bundled(file: String, weight: FontWeight = FontWeight.Normal): FontFamily {
        val bytes = checkNotNull(javaClass.getResourceAsStream("/fonts/$file")).use { it.readBytes() }
        return FontFamily(
            Font(
                "$file-${weight.weight}", bytes, weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            ),
        )
    }

    private fun style(family: FontFamily? = null, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontSize = 20.sp, fontFamily = family, fontWeight = weight)

    private fun ComposeUiTest.widthOf(tag: String): Int = onNodeWithTag(tag).fetchSemanticsNode().size.width

    @Test
    fun `the system font's text is drawn in the bundled fonts, every other family as it was`() = runComposeUiTest {
        val inter = bundled("InterVariable.ttf")
        val interBold = bundled("InterVariable.ttf", FontWeight.Bold)
        val mono = bundled("JetBrainsMonoVariable.ttf")
        setContent {
            Column {
                BasicText(SAMPLE, Modifier.testTag("system"), style = style())
                BasicText(SAMPLE, Modifier.testTag("inter"), style = style(inter))
                BasicText(SAMPLE, Modifier.testTag("interBold"), style = style(interBold, FontWeight.Bold))
                BasicText(SAMPLE, Modifier.testTag("mono"), style = style(mono))
                BasicText(SAMPLE, Modifier.testTag("serif"), style = style(FontFamily.Serif))
                PinnedFonts {
                    Column {
                        BasicText(SAMPLE, Modifier.testTag("pinned"), style = style())
                        BasicText(SAMPLE, Modifier.testTag("pinnedBold"), style = style(weight = FontWeight.Bold))
                        BasicText(SAMPLE, Modifier.testTag("pinnedMono"), style = style(FontFamily.Monospace))
                        BasicText(SAMPLE, Modifier.testTag("pinnedSerif"), style = style(FontFamily.Serif))
                    }
                }
            }
        }

        assertNotEquals(widthOf("system"), widthOf("inter"), "the sample must tell the two fonts apart")
        assertEquals(widthOf("inter"), widthOf("pinned"), "the default family is Inter")
        assertEquals(widthOf("interBold"), widthOf("pinnedBold"), "bold is Inter's bold")
        assertEquals(widthOf("mono"), widthOf("pinnedMono"), "monospace is JetBrains Mono")
        assertEquals(widthOf("serif"), widthOf("pinnedSerif"), "a family other than the system's is left alone")
    }

    @Test
    fun `a capture made outside PinnedFonts is refused before anything is written`() = runComposeUiTest {
        setContent { BasicText(SAMPLE) }
        val file = File("unpinned.png")

        val refused = assertFailsWith<IllegalStateException> { captureTo(file) }

        assertTrue("PinnedFonts" in refused.message.orEmpty(), refused.message)
        assertTrue(!file.exists(), "nothing was written")
    }
}
