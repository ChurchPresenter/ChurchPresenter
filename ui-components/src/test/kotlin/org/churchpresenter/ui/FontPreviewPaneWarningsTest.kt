package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The preview pane's warning about a family that cannot draw the operator's own scripture.
 *
 * A verse in a family with no Cyrillic or no Hebrew is not an error anywhere in the app — it simply
 * comes out of whatever fallback font the renderer finds, in front of the congregation, looking
 * nothing like the family that was picked. The warning is the only place that says so, and it says
 * something different per script.
 */
@OptIn(ExperimentalTestApi::class)
class FontPreviewPaneWarningsTest {

    private fun face(cyrillic: Boolean, hebrew: Boolean) = FontFace(
        name = "Testface",
        category = FontCategory.SANS,
        cyrillic = cyrillic,
        hebrew = hebrew,
        recommended = false,
    )

    private val hebrewVerse = "בְּרֵאשִׁית בָּרָא אֱלֹהִים"
    private val cyrillicVerse = "В начале сотворил Бог небо и землю."

    @Test
    fun `a family with no Hebrew glyphs is called out by name`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FontPreviewPane(face(cyrillic = true, hebrew = false), measured = true, lines = listOf(hebrewVerse))
            }
        }

        assertTrue(
            showsContainingText("Testface has no Hebrew glyphs"),
            "the Hebrew warning must name the family, got ${renderedText()}",
        )
        assertFalse(showsContainingText("no Cyrillic glyphs"), "and must not warn about a script it can draw")
    }

    @Test
    fun `a family with no Cyrillic glyphs is called out by name`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FontPreviewPane(face(cyrillic = false, hebrew = true), measured = true, lines = listOf(cyrillicVerse))
            }
        }

        assertTrue(showsContainingText("Testface has no Cyrillic glyphs"), "got ${renderedText()}")
        assertFalse(showsContainingText("no Hebrew glyphs"))
    }

    @Test
    fun `both warnings appear when neither script can be drawn`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FontPreviewPane(
                    face(cyrillic = false, hebrew = false),
                    measured = true,
                    lines = listOf(cyrillicVerse, hebrewVerse),
                )
            }
        }

        assertTrue(showsContainingText("no Cyrillic glyphs"))
        assertTrue(showsContainingText("no Hebrew glyphs"))
    }

    @Test
    fun `nothing is warned about before the glyph scan has run`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                FontPreviewPane(
                    face(cyrillic = false, hebrew = false),
                    measured = false,
                    lines = listOf(cyrillicVerse, hebrewVerse),
                )
            }
        }

        // Unmeasured, every face reads as covering nothing — warning on that would warn about
        // every family on the machine for the frames before the scan lands.
        assertFalse(showsContainingText("no Cyrillic glyphs"))
        assertFalse(showsContainingText("no Hebrew glyphs"))
    }
}
