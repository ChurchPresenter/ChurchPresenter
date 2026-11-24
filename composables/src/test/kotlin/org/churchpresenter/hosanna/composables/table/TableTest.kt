@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.table

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.list.RowAction
import org.churchpresenter.hosanna.composables.model.SortDirection
import org.churchpresenter.hosanna.composables.model.TableBulk
import org.churchpresenter.hosanna.composables.model.TableColumn
import org.churchpresenter.hosanna.composables.model.TableRow
import org.churchpresenter.hosanna.composables.model.TableSort
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableTest {

    private val columns = listOf(
        TableColumn("number", "No.", width = 80.dp, numeric = true, secondary = true),
        TableColumn("title", "Title"),
        TableColumn("book", "Song Book", width = 140.dp, secondary = true, sortable = false),
    )
    private val rows = listOf(
        TableRow("1", listOf("1", "Amazing Grace", "Hymnal")),
        TableRow("2", listOf("2", "Be Thou My Vision", "Hymnal")),
        TableRow("3", listOf("3", "Come Thou Fount", "Hymnal")),
        TableRow("4", listOf("4", "Blessed Assurance", "Hymnal")),
    )
    private var selected by mutableStateOf(setOf<String>())
    private var checked by mutableStateOf(setOf<String>())
    private var sort by mutableStateOf<TableSort?>(null)
    private val opened = mutableListOf<String>()
    private val widths = mutableListOf<Pair<String, Dp>>()

    @Composable
    private fun Songs(bulk: Boolean = false, actions: Boolean = false) {
        HosannaSurface {
            Table(
                columns, rows, Modifier.width(560.dp).height(400.dp).testTag("songs"),
                selectedKeys = selected,
                onRowClick = { selected = setOf(it) },
                onRowOpen = { opened += it },
                sort = sort, onSortChange = { sort = it },
                onColumnWidthChange = { key, width -> widths += key to width },
                bulk = if (bulk) {
                    TableBulk(
                        checked,
                        { key, on -> checked = if (on) checked + key else checked - key },
                        { on -> checked = if (on) rows.map { it.key }.toSet() else emptySet() },
                        "Select all",
                    )
                } else {
                    null
                },
                keepActions = setOf("4"),
                rowActions = if (actions) {
                    { key -> RowAction(testIcon(), "Star $key", {}) }
                } else {
                    null
                },
            )
        }
    }

    private fun ComposeUiTest.settleClick() {
        mainClock.advanceTimeBy(DOUBLE_CLICK_WAIT)
        waitForIdle()
    }

    private companion object {
        const val DOUBLE_CLICK_WAIT = 500L
    }

    @Test
    fun `headers sort and reverse, a fixed column does not sort`() = runComposeUiTest {
        setContent { Songs() }
        onNodeWithText("Title").performClick()
        waitForIdle()
        assertEquals(TableSort("title", SortDirection.Ascending), sort)
        onNodeWithText("Title").performClick()
        waitForIdle()
        assertEquals(TableSort("title", SortDirection.Descending), sort)
        onNodeWithText("Song Book").performClick()
        waitForIdle()
        assertEquals("title", sort?.column)
    }

    @Test
    fun `a click selects, a double click opens`() = runComposeUiTest {
        setContent { Songs() }
        onNodeWithText("Be Thou My Vision").performClick()
        settleClick()
        assertEquals(setOf("2"), selected)
        onNodeWithText("Be Thou My Vision").assertIsSelected().performMouseInput { doubleClick(center) }
        settleClick()
        assertEquals(listOf("2"), opened)
    }

    @Test
    fun `the keyboard moves, opens and jumps`() = runComposeUiTest {
        setContent { Songs() }
        onNodeWithText("Amazing Grace").performClick()
        settleClick()
        val songs = onNodeWithText("Amazing Grace")
        songs.requestFocus()
        onNodeWithText("Amazing Grace").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(setOf("2"), selected)
        onNodeWithText("Be Thou My Vision").performKeyInput { pressKey(Key.MoveEnd) }
        waitForIdle()
        assertEquals(setOf("4"), selected)
        onNodeWithText("Blessed Assurance").performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("4"), opened)
        onNodeWithText("Blessed Assurance").performKeyInput { pressKey(Key.C) }
        waitForIdle()
        assertEquals(setOf("3"), selected)
    }

    @Test
    fun `bulk select reports mixed and selects all`() = runComposeUiTest {
        setContent { Songs(bulk = true) }
        onNodeWithContentDescription("Amazing Grace").performClick()
        waitForIdle()
        assertEquals(setOf("1"), checked)
        onNodeWithContentDescription("Select all")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Indeterminate))
            .performClick()
        waitForIdle()
        assertEquals(setOf("1", "2", "3", "4"), checked)
        onNodeWithContentDescription("Select all").performClick()
        waitForIdle()
        assertEquals(emptySet(), checked)
        onNodeWithText("Come Thou Fount").performClick()
        settleClick()
        onNodeWithText("Come Thou Fount").requestFocus()
        onNodeWithText("Come Thou Fount").performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertEquals(setOf("3"), checked)
        onNodeWithText("Come Thou Fount").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.A) } }
        waitForIdle()
        assertEquals(4, checked.size)
    }

    @Test
    fun `row actions wait for hover unless kept`() = runComposeUiTest {
        setContent { Songs(actions = true) }
        onNodeWithContentDescription("Star 1").assertDoesNotExist()
        onNodeWithContentDescription("Star 4").assertExists()
        onNodeWithText("Amazing Grace").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithContentDescription("Star 1").assertExists()
    }

    @Test
    fun `dragging a divider resizes, a double click fits the content`() = runComposeUiTest {
        setContent { Songs() }
        onNodeWithText("No.").performMouseInput {
            val grip = Offset(width + 4.dp.toPx(), centerY)
            moveTo(grip)
            press()
            moveBy(Offset(40.dp.toPx(), 0f))
            release()
        }
        waitForIdle()
        assertTrue(widths.last().first == "number" && widths.last().second > 80.dp)
        onNodeWithText("No.").performMouseInput { doubleClick(Offset(width + 4.dp.toPx(), centerY)) }
        waitForIdle()
        assertEquals(64.dp, widths.last().second)
    }

    @Test
    fun `an empty table shows its message, a loading one shows no rows`() = runComposeUiTest {
        var loading by mutableStateOf(false)
        setContent {
            HosannaSurface {
                Table(
                    columns, if (loading) rows else emptyList(), loading = loading,
                    empty = { TableEmpty("No songs match “zz”", actionText = "Clear search") },
                )
            }
        }
        onNodeWithText("No songs match “zz”").assertExists()
        onNodeWithText("Clear search").assertExists()
        loading = true
        waitForIdle()
        onNodeWithText("Amazing Grace").assertDoesNotExist()
        onNodeWithText("No songs match “zz”").assertDoesNotExist()
    }
}
