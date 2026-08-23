package org.churchpresenter.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The small widgets that carry no logic worth a file of their own: a tab row, a scanning
 * indicator, a screen-shaped frame and the settings scrollbar.
 *
 * They are here because each one had no test at all — they were only ever covered incidentally by
 * the tabs and dialogs that draw them, which is coverage that does not travel when the widget moves
 * into a library of its own.
 */
@OptIn(ExperimentalTestApi::class)
class SimpleWidgetsTest {

    @Test
    fun `a pane tab reports its label and answers a click`() = runComposeUiTest {
        var clicked = 0
        setContent {
            MaterialTheme {
                PaneTabRow { PaneTab(label = "Songs", selected = false, onClick = { clicked++ }) }
            }
        }
        onNodeWithText("Songs").assertIsDisplayed().performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun `the selected pane tab is still clickable`() = runComposeUiTest {
        var clicked = 0
        setContent {
            MaterialTheme {
                PaneTabRow { PaneTab(label = "Bible", selected = true, onClick = { clicked++ }) }
            }
        }
        onNodeWithText("Bible").performClick()
        assertEquals(1, clicked, "re-selecting the live tab must still reach the caller")
    }

    @Test
    fun `the scanning row shows its message under a findable tag`() = runComposeUiTest {
        setContent { MaterialTheme { ScanningRow(scanningText = "Scanning for devices…") } }
        onNodeWithTag(SCANNING_ROW_TAG).assertIsDisplayed()
        onNodeWithText("Scanning for devices…").assertIsDisplayed()
    }

    @Test
    fun `the tv screen box draws whatever it is given`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp)) { TvScreenBox { Text("on screen") } }
            }
        }
        onNodeWithText("on screen").assertIsDisplayed()
    }

    @Test
    fun `the settings scrollbar composes over a scroll state`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(120.dp)) { SettingsScrollbar(ScrollState(0)) }
            }
        }
        waitForIdle()
        assertTrue(SettingsScrollbarGutter.value > 0f, "the gutter has to reserve real width")
    }

    @Test
    fun `the tab strip arrows carry their own test tags`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp)) {
                    TabStripBackArrow(ScrollState(50))
                    TabStripForwardArrow(ScrollState(0))
                }
            }
        }
        onNodeWithTag(TAB_STRIP_ARROW_BACK_TAG).assertIsDisplayed()
        onNodeWithTag(TAB_STRIP_ARROW_FORWARD_TAG).assertIsDisplayed()
    }

    @Test
    fun `a window is identified by title and id`() {
        val w = WindowInfo(title = "Presenter", id = 42L)
        assertEquals("Presenter", w.title)
        assertEquals(42L, w.id)
        assertEquals(w, WindowInfo("Presenter", 42L))
    }
}
