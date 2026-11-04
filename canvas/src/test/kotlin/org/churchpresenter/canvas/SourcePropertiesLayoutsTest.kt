package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Properties panel for a layer in a scene with two layouts (#937): Landscape and Portrait choose
 * the layout its numbers are, Fill canvas and Reset act on that layout, and a picture set to Fill can
 * be cropped there.
 */
@OptIn(ExperimentalTestApi::class)
class SourcePropertiesLayoutsTest {

    private class Edits {
        var source: SceneSource = SceneSource.ImageSource(
            id = "picture",
            name = "Picture",
            filePath = "/p.png",
            contentScale = "FILL",
            transform = SourceTransform(width = 0.5f, height = 1f, opacity = 0.6f),
        )
        var editingAlternate by mutableStateOf(false)
        var alternate by mutableStateOf(SourceTransform(width = 1f, height = 0.5f))
        var moved by mutableStateOf(false)
        var resets = 0
    }

    private fun panel(
        edits: Edits = Edits(),
        withLayouts: Boolean = true,
        block: ComposeUiTest.(Edits) -> Unit,
    ) = runComposeUiTest {
        var source by mutableStateOf(edits.source)
        setContent {
            MaterialTheme {
                SourcePropertiesPanel(
                    source = source,
                    layouts = if (!withLayouts) null else SourceLayouts(
                        mainIsLandscape = true,
                        editingAlternate = edits.editingAlternate,
                        alternateTransform = edits.alternate,
                        movedInAlternate = edits.moved,
                        actions = LayoutActions(
                            onEditLayout = { edits.editingAlternate = it },
                            onAlternateTransform = { edits.alternate = it; edits.moved = true },
                            onResetAlternate = { edits.resets++ },
                        ),
                    ),
                    onSourceUpdate = { source = it; edits.source = it },
                )
            }
        }
        block(edits)
    }

    private fun ComposeUiTest.count(tag: String) =
        onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun ComposeUiTest.textCount(text: String) =
        onAllNodesWithText(text).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    @Test
    fun `one layout, no switch and no layout buttons`() = panel(withLayouts = false) {
        assertEquals(0, count(LAYOUT_PORTRAIT_TAG))
        assertEquals(0, count(FILL_CANVAS_TAG))
    }

    @Test
    fun `choosing Portrait edits the second layout`() = panel { edits ->
        onNodeWithTag(LAYOUT_PORTRAIT_TAG).performClick()
        waitForIdle()
        assertEquals(true, edits.editingAlternate)
        onNodeWithTag(LAYOUT_LANDSCAPE_TAG).performClick()
        waitForIdle()
        assertEquals(false, edits.editingAlternate)
    }

    @Test
    fun `Fill canvas fits the box to the layout being edited`() = panel { edits ->
        onNodeWithTag(FILL_CANVAS_TAG).performClick()
        waitForIdle()
        assertEquals(SourceTransform(opacity = 0.6f), edits.source.transform, "the landscape box fills its canvas")

        onNodeWithTag(LAYOUT_PORTRAIT_TAG).performClick()
        waitForIdle()
        onNodeWithTag(FILL_CANVAS_TAG).performClick()
        waitForIdle()
        assertEquals(SourceTransform(), edits.alternate, "and the portrait one its own")
    }

    @Test
    fun `Reset shows only for a layer moved in the second layout, and asks for it`() {
        val edits = Edits().apply { editingAlternate = true }
        panel(edits) {
            assertEquals(0, count(RESET_LAYOUT_TAG), "nothing to reset yet")
            edits.moved = true
            waitForIdle()
            onNodeWithTag(RESET_LAYOUT_TAG).performClick()
            assertEquals(1, edits.resets)
        }
    }

    @Test
    fun `Reset is not offered for the main layout`() {
        val edits = Edits().apply { moved = true }
        panel(edits) { assertEquals(0, count(RESET_LAYOUT_TAG)) }
    }

    @Test
    fun `a picture set to Fill offers where it is cropped, and Fit does not`() {
        panel { assertEquals(1, textCount("Crop position")) }
        val fit = Edits().apply { source = (source as SceneSource.ImageSource).copy(contentScale = "FIT") }
        panel(fit) { assertEquals(0, textCount("Crop position")) }
    }
}
