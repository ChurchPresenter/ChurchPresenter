@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The tab's own controls, either side of the canvas: putting a scene on the screen, hiding and
 * locking a source, picking a drawing tool, and the aspect-ratio warning.
 *
 * Hide and lock are the two that quietly matter. A hidden source stays in the scene and keeps its
 * place in the stack, so an operator can build a scene with an alternate lower third in it and turn
 * it on mid-service; a locked one still draws but cannot be dragged, which is what stops a
 * background being nudged off-centre while something on top of it is being positioned.
 */
class CanvasTabLiveAndListTest {

    // ── Going live ─────────────────────────────────────────────────────────────

    @Test
    fun `Go Live hands the scene being edited to the outputs`() {
        canvasTab(seed = { addScene("Welcome"); seedSources("Logo") }) { vm, output, _ ->
            onNodeWithContentDescription("Go Live").performClick()
            waitForIdle()

            assertEquals(1, output.live.size)
            assertEquals(vm.currentScene?.id, output.live.single().id)
            assertEquals("Welcome", output.live.single().name)
        }
    }

    @Test
    fun `going live twice sends the scene twice, so a re-send is possible`() {
        // The operator's way of putting the output back after something else was shown.
        canvasTab(seed = { addScene("Welcome") }) { _, output, _ ->
            onNodeWithContentDescription("Go Live").performClick()
            waitForIdle()
            onNodeWithContentDescription("Go Live").performClick()
            waitForIdle()

            assertEquals(2, output.live.size)
        }
    }

    @Test
    fun `the scene sent live carries the edits made to it`() {
        canvasTab(seed = { addScene("Welcome") }) { vm, output, _ ->
            addSourceOfType("Text")
            onNodeWithContentDescription("Go Live").performClick()
            waitForIdle()

            assertEquals(
                vm.currentScene?.sources?.size, output.live.single().sources.size,
                "the outputs must get the scene as it stands, not as it was loaded",
            )
        }
    }

    // ── Hiding and locking ─────────────────────────────────────────────────────

    @Test
    fun `hiding a source keeps it in the scene`() {
        canvasTab(seed = { addScene("Scene"); seedSources("Logo") }) { vm, _, _ ->
            onNodeWithContentDescription("Toggle visibility").performClick()
            waitForIdle()

            val source = vm.currentScene?.sources?.single()
            assertEquals(false, source?.visible)
            assertEquals("Logo", source?.name, "hidden is not deleted")
        }
    }

    @Test
    fun `hiding and showing again returns the source to view`() {
        canvasTab(seed = { addScene("Scene"); seedSources("Logo") }) { vm, _, _ ->
            onNodeWithContentDescription("Toggle visibility").performClick()
            waitForIdle()
            onNodeWithContentDescription("Toggle visibility").performClick()
            waitForIdle()

            assertEquals(true, vm.currentScene?.sources?.single()?.visible)
        }
    }

    @Test
    fun `locking a source leaves it visible`() {
        // A locked background still draws — locking is about the mouse, not the picture.
        canvasTab(seed = { addScene("Scene"); seedSources("Logo") }) { vm, _, _ ->
            onNodeWithContentDescription("Toggle lock").performClick()
            waitForIdle()

            val source = vm.currentScene?.sources?.single()
            assertEquals(true, source?.locked)
            assertEquals(true, source?.visible)
        }
    }

    @Test
    fun `unlocking gives the source back to the mouse`() {
        canvasTab(seed = { addScene("Scene"); seedSources("Logo") }) { vm, _, _ ->
            onNodeWithContentDescription("Toggle lock").performClick()
            waitForIdle()
            onNodeWithContentDescription("Toggle lock").performClick()
            waitForIdle()

            assertEquals(false, vm.currentScene?.sources?.single()?.locked)
        }
    }

    // ── The drawing tools ──────────────────────────────────────────────────────

    @Test
    fun `every drawing tool can be picked`() {
        canvasTab(seed = { addScene("Scene") }) { _, _, _ ->
            // The buttons are the glyphs the tools draw; their names are in their tooltips.
            listOf("\u25A1", "\u25CB", "\u2215", "\u2192", "\u270E", "\u25C6").forEach { glyph ->
                onAllNodesWithText(glyph).onFirst().performClick()
                waitForIdle()
            }
        }
    }

    // ── The aspect-ratio warning ───────────────────────────────────────────────

    @Test
    fun `a scene shaped unlike the output screen is flagged, and can be fixed in one click`() {
        // A 4:3 scene on a 16:9 output is pillarboxed with no warning otherwise, which is only
        // noticed once it is on the wall. Fix resizes the scene to the screen it will be shown on.
        canvasTab(seed = { addScene("Scene"); updateCanvasSize(1024, 768) }) { vm, _, _ ->
            onNodeWithText("Fix").performClick()
            waitForIdle()

            val fixed = vm.currentScene
            assertTrue(
                (fixed!!.canvasWidth.toFloat() / fixed.canvasHeight - 16f / 9f) < 0.01f,
                "the scene must be reshaped to the output, was ${fixed.canvasWidth}x${fixed.canvasHeight}",
            )
            assertTrue(
                onAllNodesWithText("Fix").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
                "once the shapes match the warning must go away",
            )
        }
    }
}
