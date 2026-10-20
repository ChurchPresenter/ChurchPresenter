package org.churchpresenter.appsettings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * The Storage card's one-line list of detected files: it folds what does not fit behind a counted
 * toggle, opens to show everything, and hides the folded chips from a screen reader.
 */
@OptIn(ExperimentalTestApi::class)
class CollapsibleChipRowTest {

    private val many = (1..30).map { "bible-%02d.spb".format(it) }

    @Test
    fun `a list that fits shows every chip and no toggle`() = runComposeUiTest {
        setContent { Chips(listOf("kjv.spb", "rst.spb"), resetKey = "a") }
        onNodeWithText("kjv.spb").assertIsDisplayed()
        onNodeWithText("rst.spb").assertIsDisplayed()
        onAllNodesWithTag(CHIPS_TOGGLE_TAG).assertCountEquals(0)
    }

    @Test
    fun `a long list folds the rest behind a toggle that counts them`() = runComposeUiTest {
        setContent { Chips(many, resetKey = "a") }
        val shown = many.count { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        assert(shown in 1 until many.size) { "expected some but not all chips, got $shown" }
        // The folded chips are not in the tree a screen reader walks, and the toggle names how many.
        onNodeWithText("${many.size - shown} more").assertIsDisplayed()
        onAllNodesWithTag(CHIPS_TOGGLE_TAG).assertCountEquals(1)
    }

    @Test
    fun `opening shows every chip and closing folds them again`() = runComposeUiTest {
        setContent { Chips(many, resetKey = "a") }
        onNodeWithTag(CHIPS_TOGGLE_TAG).performClick()
        many.forEach { onNodeWithText(it).assertExists() }
        onNodeWithText("Show less").performClick()
        onAllNodesWithText(many.last()).assertCountEquals(0)
    }

    @Test
    fun `a new folder starts folded again`() = runComposeUiTest {
        var key by mutableStateOf("a")
        setContent { Chips(many, resetKey = key) }
        onNodeWithTag(CHIPS_TOGGLE_TAG).performClick()
        onNodeWithText(many.last()).assertExists()
        key = "b"
        waitForIdle()
        onAllNodesWithText(many.last()).assertCountEquals(0)
    }

    @Test
    fun `the lead stays at the front of the line`() = runComposeUiTest {
        setContent { Chips(many, resetKey = "a") }
        onNodeWithText("Detected:").assertIsDisplayed()
    }

    @Composable
    private fun Chips(chips: List<String>, resetKey: Any) {
        MaterialTheme {
            Box(Modifier.width(500.dp)) {
                CollapsibleChipRow(chips = chips, resetKey = resetKey, lead = { Text("Detected:") })
            }
        }
    }
}
