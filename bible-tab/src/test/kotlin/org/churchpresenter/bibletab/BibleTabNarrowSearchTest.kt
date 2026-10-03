@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.bibletab

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The search row's narrow layout — what the tab shows when the Bible panel is dragged in.
 *
 * `BoxWithConstraints` picks between two arrangements at 440dp: above it the search field, the scope
 * and mode dropdowns and the search button sit in one row; below it the field takes a line of its own
 * and the three controls stack underneath. They are separate call sites, not one row that wraps, so
 * the narrow one is its own set of controls that no test reached while every suite composed the tab
 * at full width.
 *
 * That matters because the panel is resizable by a drag handle and an operator working on a laptop
 * runs it narrow — if the narrow branch lost its search button, search would be unreachable for them
 * and every wide-layout test would still pass.
 *
 * Each test asserts the control *works*, not merely that it rendered: a layout branch that draws the
 * right things but wires them to nothing is the failure worth catching.
 *
 * See `BibleTabTestSupport.kt` for the harness; `width` is what selects the branch.
 */
class BibleTabNarrowSearchTest {

    /** Comfortably under the 440dp threshold, and a plausible real panel width. */
    private val narrow = 360.dp

    /** Comfortably over it, for the comparisons that need both branches. */
    private val wide = 900.dp

    private fun ComposeUiTest.searchField() = onAllNodes(hasSetTextAction())[0]

    // ── The branch itself ───────────────────────────────────────────────────────

    @Test
    fun `the narrow layout still offers every search control`() =
        bibleTab(width = narrow) { _, _ ->
            // The field, both dropdowns and the button all have to survive the stack; losing any of
            // them would leave a narrow panel unable to search at all.
            assertTrue(
                onAllNodes(hasSetTextAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty(),
                "the search field must be present",
            )
            onNodeWithText("SCOPE").assertExists("the scope selector must survive the narrow layout")
            onNodeWithText("MODE").assertExists("as must the mode selector")
            onNodeWithContentDescription("Search").assertExists("and the search button")
        }

    @Test
    fun `the narrow layout stacks the controls below the field rather than beside it`() =
        bibleTab(width = narrow) { _, _ ->
            val field = onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().boundsInRoot
            val scope = onNodeWithText("SCOPE").fetchSemanticsNode().boundsInRoot

            // The distinguishing property of this branch, asserted as a relationship rather than a
            // coordinate: stacked means the scope selector starts below the field's bottom edge.
            assertTrue(
                scope.top >= field.bottom,
                "scope (top=${scope.top}) must sit below the field (bottom=${field.bottom})",
            )
        }

    @Test
    fun `the wide layout keeps them on one line`() =
        bibleTab(width = wide) { _, _ ->
            val field = onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().boundsInRoot
            val scope = onNodeWithText("SCOPE").fetchSemanticsNode().boundsInRoot

            // The mirror of the assertion above — together they prove the threshold does something,
            // rather than the narrow test passing because both branches happen to look alike.
            assertTrue(
                scope.top < field.bottom,
                "scope (top=${scope.top}) must sit beside the field (bottom=${field.bottom})",
            )
        }

    // ── The controls, driven ────────────────────────────────────────────────────

    @Test
    fun `the narrow layout's search button runs the query`() =
        bibleTab(width = narrow) { vm, _ ->
            searchField().performTextReplacement("God")
            waitForIdle()

            onNodeWithContentDescription("Search").performClick()
            waitForIdle()

            // Submitting is what turns typed text into results; the button is the only way to do it
            // in this layout other than the enter key.
            assertTrue(vm.searchQuery.value.isNotEmpty(), "the query must have reached the view model")
        }

    @Test
    fun `the narrow layout's search field accepts and reports typing`() =
        bibleTab(width = narrow) { vm, _ ->
            searchField().performTextReplacement("John 3:16")
            waitForIdle()

            assertTrue(
                vm.searchQuery.value.contains("John", ignoreCase = true),
                "the narrow field must be wired to the same smart-query handler, was '${vm.searchQuery.value}'",
            )
        }

    @Test
    fun `the narrow layout's scope selector changes the scope`() =
        bibleTab(width = narrow) { vm, _ ->
            assertEquals(0, vm.selectedScopeIndex.value, "the tab starts searching the whole Bible")

            onNodeWithText("SCOPE").performClick()
            waitForIdle()
            // The menu opens over the stacked layout rather than being clipped out of it, which is
            // the thing that could plausibly break in the narrow branch.
            onNodeWithText("Current Book").performClick()
            waitForIdle()

            // Asserted through the view model, not by reading the label back: a DropdownMenu keeps
            // showing whatever was clicked whether or not anything was stored.
            assertEquals(1, vm.selectedScopeIndex.value, "picking Current Book must narrow the search")
        }

    @Test
    fun `the narrow layout's mode selector changes the mode`() =
        bibleTab(width = narrow) { vm, _ ->
            assertEquals(0, vm.selectedModeIndex.value)

            onNodeWithText("MODE").performClick()
            waitForIdle()
            onNodeWithText("Exact Match").performClick()
            waitForIdle()

            assertEquals(1, vm.selectedModeIndex.value, "picking Exact Match must change how it matches")
        }
}
