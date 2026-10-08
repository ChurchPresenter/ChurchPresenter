package org.churchpresenter.sharedui.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpOffset
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ContextMenuTest {

    private class Picks {
        val labels = mutableListOf<String>()
    }

    private fun ComposeUiTest.menu(
        withHeader: Boolean = true,
        shortcuts: ShortcutMap = ShortcutMap.DEFAULT,
        theme: ThemeMode = ThemeMode.LIGHT,
    ): Picks {
        val picks = Picks()
        var open by mutableStateOf(true)
        setContent {
            ChurchPresenterTheme(themeMode = theme) {
                CompositionLocalProvider(LocalShortcuts provides shortcuts) {
                    val icon = rememberVectorPainter(Icons.Default.Star)
                    ContextMenu(
                        expanded = open,
                        onDismissRequest = { open = false },
                        offset = DpOffset.Zero,
                        header = if (withHeader) {
                            { ContextMenuHeader("1", "Amazing Grace", "Hymnal · 0001", Color.Blue) }
                        } else {
                            null
                        },
                    ) {
                        ContextMenuItem(
                            label = "Go Live",
                            icon = icon,
                            accent = Color.Blue,
                            shortcut = contextMenuShortcut(ShortcutAction.GO_LIVE),
                            emphasized = true,
                            onClick = { picks.labels += "Go Live"; open = false },
                        )
                        ContextMenuItem(
                            label = "Take",
                            icon = icon,
                            accent = Color.Green,
                            shortcut = contextMenuShortcut(ShortcutAction.TAKE),
                            onClick = { picks.labels += "Take" },
                        )
                        ContextMenuDivider()
                        ContextMenuItem(
                            label = "Delete",
                            icon = icon,
                            accent = Color.Red,
                            danger = true,
                            onClick = { picks.labels += "Delete"; open = false },
                        )
                    }
                }
            }
        }
        waitForIdle()
        return picks
    }

    private fun ComposeUiTest.has(text: String) =
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the header names what was clicked`() = runComposeUiTest {
        menu()

        assertTrue(has("1"))
        assertTrue(has("Amazing Grace"))
        assertTrue(has("Hymnal · 0001"))
    }

    @Test
    fun `without a header only the items show`() = runComposeUiTest {
        menu(withHeader = false)

        assertFalse(has("Amazing Grace"))
        assertTrue(has("Go Live"))
        assertTrue(has("Delete"))
    }

    @Test
    fun `an item shows the key the user bound to its action`() = runComposeUiTest {
        menu(shortcuts = ShortcutMap(mapOf(ShortcutAction.GO_LIVE to listOf(KeyChord.of(Key.F5)))))

        assertTrue(has("F5"))
    }

    @Test
    fun `an unbound action shows no key`() = runComposeUiTest {
        menu(shortcuts = ShortcutMap(emptyMap()))

        assertFalse(has("F5"))
        assertFalse(has("Enter"))
    }

    @Test
    fun `clicking an item runs it`() = runComposeUiTest {
        val picks = menu()
        onNodeWithText("Go Live", useUnmergedTree = true).performClick()
        waitForIdle()

        assertEquals(listOf("Go Live"), picks.labels)
        assertFalse(has("Delete"), "the item closed the menu")
    }

    @Test
    fun `the danger item runs like any other`() = runComposeUiTest {
        val picks = menu(theme = ThemeMode.DARK)
        onNodeWithText("Delete", useUnmergedTree = true).performClick()
        waitForIdle()

        assertEquals(listOf("Delete"), picks.labels)
    }
}
