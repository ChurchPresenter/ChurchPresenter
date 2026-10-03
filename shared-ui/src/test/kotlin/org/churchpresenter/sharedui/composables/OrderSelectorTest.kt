package org.churchpresenter.sharedui.composables

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OrderSelectorTest {

    private val languages = listOf(
        OrderEntry("en", "English", detail = "en.spb"),
        OrderEntry("es", "Español"),
        OrderEntry("de", "Deutsch"),
    )

    private fun ComposeUiTest.selector(entries: List<OrderEntry>, moves: MutableList<Pair<Int, Int>>) {
        var current by mutableStateOf(entries)
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                OrderSelector(
                    label = "Languages",
                    entries = current,
                    subtitle = "Which language leads",
                    moveUpLabel = "Move up",
                    moveDownLabel = "Move down",
                    onMove = { index, offset ->
                        moves += index to offset
                        current = current.toMutableList().apply { add(index + offset, removeAt(index)) }
                    },
                )
            }
        }
    }

    private fun ComposeUiTest.open() {
        onNodeWithText("English").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.has(text: String) =
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the button names the first entry and counts the rest`() = runComposeUiTest {
        selector(languages, mutableListOf())

        assertTrue(has("LANGUAGES"))
        assertTrue(has("English"))
        assertTrue(has("+2"))
    }

    @Test
    fun `a single entry shows no count`() = runComposeUiTest {
        selector(languages.take(1), mutableListOf())

        assertTrue(!has("+0"))
    }

    @Test
    fun `opening the panel lists every entry in order with its detail`() = runComposeUiTest {
        selector(languages, mutableListOf())
        open()

        assertTrue(has("Display order"))
        assertTrue(has("Which language leads"))
        assertTrue(has("en.spb"))
        listOf("1", "2", "3", "Español", "Deutsch").forEach { assertTrue(has(it), it) }
        assertTrue(has("Drag a row or use the arrows to reorder."))
    }

    @Test
    fun `the arrows move a row by one`() = runComposeUiTest {
        val moves = mutableListOf<Pair<Int, Int>>()
        selector(languages, moves)
        open()

        onAllNodesWithContentDescription("Move down")[0].performClick()
        waitForIdle()
        onAllNodesWithContentDescription("Move up")[2].performClick()
        waitForIdle()

        assertEquals(listOf(0 to 1, 2 to -1), moves)
    }

    @Test
    fun `the first row cannot go up and the last cannot go down`() = runComposeUiTest {
        val moves = mutableListOf<Pair<Int, Int>>()
        selector(languages, moves)
        open()

        onAllNodesWithContentDescription("Move up")[0].performClick()
        onAllNodesWithContentDescription("Move down")[2].performClick()
        waitForIdle()

        assertEquals(emptyList(), moves)
    }

    @Test
    fun `dragging a row down two rows moves it by two`() = runComposeUiTest {
        val moves = mutableListOf<Pair<Int, Int>>()
        selector(languages, moves)
        open()

        onAllNodes(hasContentDescription("Drag to reorder"), useUnmergedTree = true)[0].performTouchInput {
            down(center)
            moveBy(Offset(0f, 30f))
            moveBy(Offset(0f, 30f))
            moveBy(Offset(0f, 40f))
            up()
        }
        waitForIdle()

        assertEquals(1, moves.size)
        assertTrue(moves.single().first == 0 && moves.single().second > 0)
    }

    @Test
    fun `a drag that ends where it started moves nothing`() = runComposeUiTest {
        val moves = mutableListOf<Pair<Int, Int>>()
        selector(languages, moves)
        open()

        onAllNodes(hasContentDescription("Drag to reorder"), useUnmergedTree = true)[1].performTouchInput {
            down(center)
            moveBy(Offset(0f, 20f))
            moveBy(Offset(0f, -20f))
            up()
        }
        waitForIdle()

        assertEquals(emptyList(), moves)
    }
}
