package org.churchpresenter.bibletab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * The Book and Chapter columns ([BibleBrowserColumn]) and when they scroll. A click never moves the
 * column -- the row is where the operator is already looking -- while a selection made anywhere else
 * brings its row into view only when it is not already wholly on screen.
 */
@OptIn(ExperimentalTestApi::class)
class BibleBrowserColumnScrollTest {

    private val books = List(66) { "Book $it" }

    private var selected by mutableIntStateOf(-1)

    /** A column short enough that only the first dozen or so rows fit. */
    private fun ComposeUiTest.column() {
        setContent {
            MaterialTheme {
                Box(Modifier.size(width = 200.dp, height = 400.dp)) {
                    BibleBrowserColumn(items = books, selectedIndex = selected, onItemSelected = { selected = it })
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `a click leaves the column where it is`() = runComposeUiTest {
        column()
        onNodeWithText("Book 6").performClick()
        waitForIdle()
        onNodeWithText("Book 0").assertIsDisplayed()
        onNodeWithText("Book 6").assertIsDisplayed()
    }

    @Test
    fun `a selection made elsewhere scrolls an off-screen row into view`() = runComposeUiTest {
        column()
        selected = 50
        waitForIdle()
        onNodeWithText("Book 50").assertIsDisplayed()
    }

    @Test
    fun `a selection made elsewhere that is already on screen does not scroll`() = runComposeUiTest {
        column()
        selected = 4
        waitForIdle()
        onNodeWithText("Book 0").assertIsDisplayed()
        onNodeWithText("Book 4").assertIsDisplayed()
    }
}
