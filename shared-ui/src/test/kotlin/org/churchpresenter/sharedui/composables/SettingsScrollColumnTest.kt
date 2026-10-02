package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScrollColumnTest {

    @Test
    fun `the column scrolls a row below the fold into view`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsScrollColumn(modifier = Modifier.height(120.dp)) {
                    repeat(30) { Text("Row $it") }
                }
            }
        }
        onNodeWithText("Row 29").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the column takes the arrangement it is given`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SettingsScrollColumn(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("First")
                    Text("Second")
                }
            }
        }
        val first = onNodeWithText("First").fetchSemanticsNode().boundsInRoot
        val second = onNodeWithText("Second").fetchSemanticsNode().boundsInRoot
        assertEquals(20f * density.density, second.top - first.bottom, 1f)
    }

    @Test
    fun `the bar can be added to any scrolling box`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.height(100.dp)) {
                    val state = rememberScrollState()
                    Column(Modifier.verticalScroll(state)) {
                        repeat(20) { Text("Item $it") }
                    }
                    SettingsScrollbar(state)
                }
            }
        }
        onNodeWithText("Item 0").assertIsDisplayed()
    }

    @Test
    fun `the gutter leaves room for the bar`() {
        assertEquals(12.dp, SettingsScrollbarGutter)
    }
}
