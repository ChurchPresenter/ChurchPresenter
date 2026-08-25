@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The drawing toolbar's tooltips — which are the **only** name those buttons have.
 *
 * Each tool is an `IconButton` whose entire visible content is one glyph (`◆`, `□`, `○`, `∕`, `→`,
 * `✎`). Nothing else on screen says which is which, and the glyphs for line and freehand in
 * particular are not self-explanatory, so a tooltip that stopped drawing would leave an operator
 * guessing at six identical-looking buttons rather than merely losing a convenience.
 *
 * Assertions are **differential** — a count before the hover against a count after. Unlike most
 * tooltips these names appear nowhere else, so a bare `assertExists` would in fact detect them; the
 * differential form is kept anyway because it also proves the tooltip belongs to *the button that
 * was hovered*, which is the part that would break if the loop that builds them lost its binding.
 *
 * The glyph is what a test can address the button by, and that is a symptom rather than a
 * convenience: `AGENT.md` forbids text-as-icon (`Text("⏸")`) precisely here. Reported with the PR;
 * not changed by it, since real icon assets are a production change of their own.
 */
class CanvasTabToolTooltipTest {

    /** Glyph on the button, and the name only its tooltip gives. */
    private val tools = listOf(
        "◆" to "Select",
        "□" to "Rectangle",
        "○" to "Ellipse",
        "∕" to "Line",
        "→" to "Arrow",
        "✎" to "Freehand",
    )

    private fun ComposeUiTest.countOf(text: String) =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes(false).size

    private fun ComposeUiTest.hover(glyph: String) {
        onAllNodesWithText(glyph)[0].performMouseInput { moveTo(center) }
        waitForIdle()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
    }

    @Test
    fun `every drawing tool is named by its tooltip`() {
        // One test per tool would be six near-identical bodies; the loop that builds them is one
        // site, so the thing worth asserting is that each glyph carries its *own* name.
        tools.forEach { (glyph, name) ->
            canvasTab(seed = { addScene("Tools") }) { _, _, _ ->
                assertTrue(countOf(glyph) > 0, "the $name button should be on the toolbar")
                val before = countOf(name)

                hover(glyph)

                assertEquals(before + 1, countOf(name), "hovering $glyph should name it '$name'")
            }
        }
    }

    @Test
    fun `hovering one tool does not name another`() {
        // The names come from a list zipped against the buttons by position. An off-by-one there
        // would label every tool as its neighbour, and each tool would still show *a* tooltip — so
        // the test above passes only because it checks the pairing, and this one states why.
        canvasTab(seed = { addScene("Tools") }) { _, _, _ ->
            val beforeRectangle = countOf("Rectangle")

            hover("◆") // Select

            assertEquals(
                beforeRectangle, countOf("Rectangle"),
                "only the hovered tool's tooltip should be drawn",
            )
        }
    }
}
