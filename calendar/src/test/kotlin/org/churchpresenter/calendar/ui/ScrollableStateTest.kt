@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scrolling surfaces with a state of their own: a list or column the caller scrolls -- to a
 * picked row, back to the top -- has to move with that state, not with one it made for itself.
 */
class ScrollableStateTest {

    @Test
    fun `a list scrolls with the state it is given`() = runComposeUiTest {
        lateinit var scrollTo: suspend (Int) -> Unit
        setContent {
            val state = rememberLazyListState()
            scrollTo = { state.scrollToItem(it) }
            ScrollableList(
                modifier = Modifier.height(120.dp),
                state = state,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(100) { Text("Row $it", Modifier.height(30.dp)) }
            }
        }
        onNodeWithText("Row 60").assertDoesNotExist()

        runOnIdle { runBlocking { scrollTo(60) } }

        onNodeWithText("Row 60").assertExists()
    }

    @Test
    fun `a column scrolls with the state it is given`() = runComposeUiTest {
        var offset = { 0 }
        lateinit var scrollTo: suspend (Int) -> Unit
        setContent {
            val state = rememberScrollState()
            offset = { state.value }
            scrollTo = { state.scrollTo(it) }
            ScrollableColumn(
                modifier = Modifier.height(120.dp),
                state = state,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                repeat(40) { Text("Line $it", Modifier.height(30.dp)) }
            }
        }

        runOnIdle { runBlocking { scrollTo(300) } }
        waitForIdle()

        assertEquals(300, offset())
        assertTrue(offset() > 0)
    }
}
