package org.churchpresenter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The generic row/grid of mutually-exclusive buttons used for compact multiple-choice pickers
 * (alignment, scale mode, etc). Its distinguishing behaviour is the optional [compactColumns]:
 * with a bounded parent width too narrow for one row of every item, it wraps into a grid instead
 * of overflowing — verified here by comparing items' row position (their y-coordinate), since the
 * wrap itself has no other observable trace in the semantics tree.
 */
@OptIn(ExperimentalTestApi::class)
class SegmentedButtonTest {

    @Test
    fun `SegmentedButton requires at least one item`() = runComposeUiTest {
        assertFailsWith<IllegalArgumentException> {
            setContent {
                MaterialTheme {
                    SegmentedButton(
                        items = emptyList<SegmentedButtonItem<String>>(),
                        selectedValue = "x",
                        onValueChange = { },
                    )
                }
            }
            waitForIdle()
        }
    }

    @Test
    fun `an item without an icon shows its label`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SegmentedButton(
                    items = listOf(SegmentedButtonItem("a", "Apple")),
                    selectedValue = "a",
                    onValueChange = { },
                )
            }
        }
        onNodeWithText("Apple").assertExists("the label must be shown when no icon is supplied")
    }

    @Test
    fun `an item with an icon shows it, using the label as its content description`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SegmentedButton(
                    items = listOf(SegmentedButtonItem("a", "Go live", icon = Icons.Default.Tv)),
                    selectedValue = "a",
                    onValueChange = { },
                )
            }
        }
        onNodeWithContentDescription("Go live").assertExists("the icon must expose the label for accessibility")
        onNodeWithText("Go live").assertDoesNotExist()
    }

    @Test
    fun `clicking an item invokes onValueChange with that item's value`() = runComposeUiTest {
        var selected = "a"
        setContent {
            MaterialTheme {
                SegmentedButton(
                    items = listOf(
                        SegmentedButtonItem("a", "Apple"),
                        SegmentedButtonItem("b", "Banana"),
                    ),
                    selectedValue = selected,
                    onValueChange = { selected = it },
                )
            }
        }
        onNodeWithText("Banana").performClick()
        assertEquals("b", selected, "clicking an item must report its own value, not the previously selected one")
    }

    @Test
    fun `buttonWidth and buttonHeight control each item's rendered size`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SegmentedButton(
                    items = listOf(SegmentedButtonItem("a", "A")),
                    selectedValue = "a",
                    onValueChange = { },
                    buttonWidth = 77.dp,
                    buttonHeight = 55.dp,
                )
            }
        }
        val size = onNodeWithText("A").fetchSemanticsNode().size
        assertEquals(77, size.width, "the button must be laid out at the requested buttonWidth")
        assertEquals(55, size.height, "the button must be laid out at the requested buttonHeight")
    }

    @Test
    fun `hovering an item with a tooltip shows its tooltip text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SegmentedButton(
                    items = listOf(SegmentedButtonItem("a", "Apple", tooltip = "The first fruit")),
                    selectedValue = "a",
                    onValueChange = { },
                )
            }
        }
        onNodeWithText("The first fruit", substring = true).assertDoesNotExist()

        onNode(hasClickAction()).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(600)
        waitForIdle()

        onNodeWithText("The first fruit", useUnmergedTree = true).assertExists("the tooltip must appear once hovered")
    }

    @Test
    fun `without compactColumns, items always render in a single row regardless of available width`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.width(50.dp)) {
                        SegmentedButton(
                            items = listOf(
                                SegmentedButtonItem("a", "A"),
                                SegmentedButtonItem("b", "B"),
                                SegmentedButtonItem("c", "C"),
                            ),
                            selectedValue = "a",
                            onValueChange = { },
                            buttonWidth = 40.dp,
                            buttonHeight = 40.dp,
                        )
                    }
                }
            }
            val aY = onNodeWithText("A").fetchSemanticsNode().positionInRoot.y
            val cY = onNodeWithText("C").fetchSemanticsNode().positionInRoot.y
            assertEquals(aY, cY, "with no compactColumns, every item must stay on the same row even when it overflows")
        }

    @Test
    fun `compactColumns wraps items into multiple rows when the available width is too narrow`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Box(Modifier.width(50.dp)) {
                        SegmentedButton(
                            items = listOf(
                                SegmentedButtonItem("a", "A"),
                                SegmentedButtonItem("b", "B"),
                                SegmentedButtonItem("c", "C"),
                            ),
                            selectedValue = "a",
                            onValueChange = { },
                            buttonWidth = 40.dp,
                            buttonHeight = 40.dp,
                            compactColumns = 2,
                        )
                    }
                }
            }
            val aY = onNodeWithText("A").fetchSemanticsNode().positionInRoot.y
            val bY = onNodeWithText("B").fetchSemanticsNode().positionInRoot.y
            val cY = onNodeWithText("C").fetchSemanticsNode().positionInRoot.y
            assertEquals(aY, bY, "the first two items must share the first row of 2 columns")
            assertTrue(cY > aY, "the third item must wrap onto a new row below the first")
        }

    @Test
    fun `when the available width fits a full row, compactColumns is ignored`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.width(300.dp)) {
                    SegmentedButton(
                        items = listOf(
                            SegmentedButtonItem("a", "A"),
                            SegmentedButtonItem("b", "B"),
                            SegmentedButtonItem("c", "C"),
                        ),
                        selectedValue = "a",
                        onValueChange = { },
                        buttonWidth = 40.dp,
                        buttonHeight = 40.dp,
                        compactColumns = 2,
                    )
                }
            }
        }
        val aY = onNodeWithText("A").fetchSemanticsNode().positionInRoot.y
        val cY = onNodeWithText("C").fetchSemanticsNode().positionInRoot.y
        assertEquals(aY, cY, "when the full row fits, compactColumns must not force a wrap")
    }
}
