package org.churchpresenter.sharedui.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ToggleMenuTest {

    /** Three items, one starting hidden, in a menu as the toolbar dropdowns open it. */
    private class Menu {
        var shown by mutableStateOf(setOf("Bible", "Songs"))
        var showAllClicks = 0
    }

    private val items = listOf("Bible", "Songs", "Media")

    private fun ComposeUiTest.menu(theme: ThemeMode = ThemeMode.LIGHT, withIcons: Boolean = true): Menu {
        val menu = Menu()
        setContent {
            ChurchPresenterTheme(themeMode = theme) {
                val icon = rememberVectorPainter(Icons.Default.Star)
                ContextMenu(expanded = true, onDismissRequest = {}, width = 220.dp) {
                    ToggleMenuHeader(
                        title = "Tabs",
                        shown = menu.shown.size,
                        total = items.size,
                        onShowAll = { menu.showAllClicks++; menu.shown = items.toSet() },
                    )
                    items.forEach { item ->
                        ToggleMenuItem(
                            label = item,
                            checked = item in menu.shown,
                            onCheckedChange = { on -> menu.shown = if (on) menu.shown + item else menu.shown - item },
                            modifier = Modifier.testTag(item),
                            icon = if (withIcons) icon else null,
                            accent = Color.Blue,
                        )
                    }
                }
            }
        }
        return menu
    }

    @Test
    fun `the header counts what is shown and offers Show all while something is hidden`() = runComposeUiTest {
        menu()
        onNodeWithText("TABS").assertExists()
        onNodeWithText("2 of 3").assertExists()
        onNodeWithText("Show all").assertExists()
    }

    @Test
    fun `Show all is called once and then goes away when everything is shown`() = runComposeUiTest {
        val menu = menu()
        onNodeWithText("Show all").performClick()
        waitForIdle()
        assertEquals(1, menu.showAllClicks)
        onNodeWithText("3 of 3").assertExists()
        assertTrue(onAllNodesWithText("Show all").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `an item is a checkbox that reports its own state`() = runComposeUiTest {
        menu()
        onNodeWithTag("Bible").assertIsOn()
        onNodeWithTag("Media").assertIsOff()
    }

    @Test
    fun `clicking an item asks for the opposite of what it shows`() = runComposeUiTest {
        val menu = menu()
        onNodeWithTag("Songs").performClick()
        waitForIdle()
        assertEquals(setOf("Bible"), menu.shown)
        onNodeWithTag("Songs").assertIsOff()
        onNodeWithTag("Media").performClick()
        waitForIdle()
        assertEquals(setOf("Bible", "Media"), menu.shown)
        onNodeWithTag("Media").assertIsOn()
    }

    @Test
    fun `the items fill the width the menu was opened with`() = runComposeUiTest {
        menu(withIcons = false)
        // The menu's own 6dp side padding comes out of the 220dp it was given.
        onNodeWithTag("Bible").assertWidthIsEqualTo(208.dp)
    }

    @Test
    fun `a disabled item ignores the click`() = runComposeUiTest {
        var calls = 0
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                ContextMenu(expanded = true, onDismissRequest = {}) {
                    ToggleMenuItem(
                        label = "Bible",
                        checked = true,
                        onCheckedChange = { calls++ },
                        modifier = Modifier.testTag("only"),
                        enabled = false,
                    )
                }
            }
        }
        onNodeWithTag("only").assertIsNotEnabled().performClick()
        waitForIdle()
        assertEquals(0, calls)
    }

    @Test
    fun `the dark theme draws the same menu`() = runComposeUiTest {
        menu(theme = ThemeMode.DARK)
        onNodeWithText("2 of 3").assertExists()
        onNodeWithTag("Media").assertIsOff()
    }
}
