package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** [PaneTabRow] and its [PaneTab]s: each tab answers its own click, whichever is selected. */
@OptIn(ExperimentalTestApi::class)
class PaneTabRowTest {

    @Test
    fun `clicking a tab selects it, and the selected one still answers`() = runComposeUiTest {
        var selected by mutableStateOf(0)
        val clicks = mutableListOf<Int>()
        setContent {
            MaterialTheme {
                PaneTabRow {
                    listOf("Lyrics", "Secondary").forEachIndexed { index, label ->
                        PaneTab(label, selected = selected == index) {
                            clicks += index
                            selected = index
                        }
                    }
                }
            }
        }

        onNodeWithText("Secondary").performClick()
        waitForIdle()
        onNodeWithText("Secondary").performClick()
        onNodeWithText("Lyrics").performClick()
        waitForIdle()

        assertEquals(listOf(1, 1, 0), clicks)
        assertEquals(0, selected)
    }
}
