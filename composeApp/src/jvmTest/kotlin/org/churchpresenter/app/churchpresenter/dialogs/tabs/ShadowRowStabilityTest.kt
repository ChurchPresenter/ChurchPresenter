package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.BibleTranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ticking "Shadow" must not move anything, including the checkbox that was just clicked.
 *
 * The detail controls fold out *beside* that checkbox rather than under it, and they are three
 * times its height. Sized to its contents the row grew by 18dp on the click, and because these
 * controls are bottom-aligned the checkbox jumped up nine pixels out from under the cursor -- on
 * both tabs, since both panels build the same row. Worse on the song side, where
 * `ShadowDetailRow`'s `fillMaxWidth` took the whole row: Compose measures unweighted children
 * against the full width first, so the Reset button beside it was squeezed to nothing the moment
 * the box was ticked.
 *
 * Both are geometry, so both are asserted as geometry rather than by eye through a screenshot.
 */
@OptIn(ExperimentalTestApi::class)
class ShadowRowStabilityTest {

    private fun ComposeUiTest.boundsOf(text: String): Rect =
        onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot

    /**
     * The last cell whose text contains [text], in composition order.
     *
     * The shadow's own colour swatch is captioned exactly as the element's is, and the element's is
     * composed first, so the shadow's is the later of the two.
     */
    private fun ComposeUiTest.lastBoundsOf(text: String): Rect =
        onAllNodesWithText(text, substring = true)
            .fetchSemanticsNodes()
            .last()
            .boundsInRoot

    @Test
    fun `the song panel's shadow checkbox does not move when it is ticked`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1200.dp, 800.dp)) {
                var style by remember {
                    mutableStateOf(defaultSongElementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN))
                }
                SongTypographyPanel(
                    element = SongStyleElement.LYRICS,
                    style = style,
                    onStyleChange = { style = it },
                    onReset = {},
                    availableFonts = listOf("Arial"),
                )
            }
        }

        val before = boundsOf("Shadow")
        onNodeWithText("Shadow", substring = true).performClick()
        assertEquals(before, boundsOf("Shadow"), "the checkbox must stay where it was clicked")
    }

    @Test
    fun `the song panel's shadow details fold out level with the checkbox`() = runComposeUiTest {
        // The checkbox no longer shares a row with the text transform -- the transform's four 96dp
        // segments left the shadow cell nothing in a narrow column, so it was given a row of its
        // own. There is nothing beside it to sit level with any more, and the property that
        // replaced that one is this: the details fold out *beside* the box rather than under it, so
        // the two share a centre line and the row keeps the height it reserved either way.
        setContent {
            Box(Modifier.size(1200.dp, 800.dp)) {
                var style by remember {
                    mutableStateOf(defaultSongElementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN))
                }
                SongTypographyPanel(
                    element = SongStyleElement.LYRICS,
                    style = style,
                    onStyleChange = { style = it },
                    onReset = {},
                    availableFonts = listOf("Arial"),
                )
            }
        }

        val folded = boundsOf("Shadow")
        onNodeWithText("Shadow", substring = true).performClick()

        val box = boundsOf("Shadow")
        assertEquals(folded, box, "the box must not move when its own details appear")
        // The colour cell is the whole control rather than a caption over a field, so its middle is
        // the row's middle. The "SIZE (%)" and "INTENSITY (%)" captions sit above their own fields
        // inside a `ControlColumn` and ride higher by design.
        assertEquals(
            box.center.y,
            lastBoundsOf("COLOR").center.y,
            "the details must fold out beside the checkbox, not under it",
        )
    }

    @Test
    fun `the song panel keeps its Reset button when the shadow controls fold out`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1200.dp, 800.dp)) {
                var style by remember {
                    mutableStateOf(defaultSongElementStyle(SongStyleElement.LYRICS, SongStyleTarget.FULL_SCREEN))
                }
                SongTypographyPanel(
                    element = SongStyleElement.LYRICS,
                    style = style,
                    onStyleChange = { style = it },
                    onReset = {},
                    availableFonts = listOf("Arial"),
                )
            }
        }

        val before = boundsOf("Reset")
        assertTrue(before.width > 0f, "the button has to start with a width for this to mean anything")
        onNodeWithText("Shadow", substring = true).performClick()
        assertEquals(before, boundsOf("Reset"), "the shadow controls must not squeeze it out")
    }

    @Test
    fun `the bible panel's shadow checkbox does not move when it is ticked`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1200.dp, 800.dp)) {
                var translation by remember { mutableStateOf(BibleTranslationSettings(fileName = "kjv.spb")) }
                val style = translation.elementStyle(BibleStyleElement.TEXT, BibleStyleTarget.FULL_SCREEN)
                BibleTypographyPanel(
                    translation = translation,
                    moduleTitle = "King James Version",
                    element = BibleStyleElement.TEXT,
                    onElementChange = {},
                    style = style,
                    onStyleChange = {
                        translation = translation
                            .withElementStyle(BibleStyleElement.TEXT, BibleStyleTarget.FULL_SCREEN, it)
                    },
                    onTranslationChange = { transform -> translation = transform(translation) },
                    onReset = {},
                    availableFonts = listOf("Arial"),
                    autoFit = null,
                    autoFitEnabled = false,
                )
            }
        }

        val before = boundsOf("Shadow")
        onNodeWithText("Shadow", substring = true).performClick()
        assertEquals(before, boundsOf("Shadow"), "the checkbox must stay where it was clicked")
    }
}
