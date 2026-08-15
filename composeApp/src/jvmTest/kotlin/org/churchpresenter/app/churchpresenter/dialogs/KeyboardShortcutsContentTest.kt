@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.KeyboardShortcutSettings
import org.churchpresenter.app.churchpresenter.models.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.models.ShortcutScope
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardShortcutsContentTest {
    private fun dialog(
        settings: AppSettings = AppSettings(),
        block: ComposeUiTest.(dismissed: () -> Int) -> Unit,
    ) {
        var dismissed = 0
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    KeyboardShortcutsDialogContent(
                        initialSettings = settings,
                        onSave = {},
                        onDismiss = { dismissed++ },
                    )
                }
            }
            block { dismissed }
        }
    }

    private fun settingsWith(action: ShortcutAction, chords: List<KeyChord>) = AppSettings(
        keyboardShortcutSettings = KeyboardShortcutSettings(overrides = mapOf(action.name to chords))
    )

    private fun ComposeUiTest.show(action: ShortcutAction) {
        onNodeWithTag(shortcutCategoryTag(action.scope)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.search(text: String) {
        onNode(hasSetTextAction()).performTextInput(text)
        waitForIdle()
    }

    @Test
    fun `clicking OK dismisses the dialog`() = dialog { dismissed ->
        onNodeWithText("OK", substring = true).performClick()
        assertEquals(1, dismissed())
    }

    @Test
    fun `every category is offered in the rail`() {
        val categories = ShortcutScope.entries.map { it.name to it.titleRes } 
        dialog {
            ShortcutScope.entries.forEach { scope ->
                onNodeWithTag(shortcutCategoryTag(scope)).assertExists()
            }
            onNodeWithTag(shortcutCategoryTag(null)).assertTextContains("Mouse")
        }
        assertEquals(8, categories.size, "a new scope needs a rail entry and a heading of its own")
    }

    @Test
    fun `the global shortcut for opening this dialog is listed`() = dialog {
        show(ShortcutAction.KEYBOARD_SHORTCUTS)
        onNodeWithText("F1").assertExists()
        onNodeWithText("Open Keyboard Shortcuts").assertExists()
    }

    @Test
    fun `the new schedule shortcut is listed`() = dialog {
        show(ShortcutAction.NEW_SCHEDULE)
        onNodeWithText("New Schedule").assertExists()
    }

    @Test
    fun `the heading names the category that is open`() = dialog {
        show(ShortcutAction.MEDIA_MUTE)
        onNodeWithTag(SHORTCUT_SECTION_TITLE_TAG).assertTextEquals("Media Tab")

        search("verse")
        onNodeWithTag(SHORTCUT_SECTION_TITLE_TAG).assertTextEquals("Search results")
    }

    @Test
    fun `the media mute shortcut is listed`() = dialog {
        show(ShortcutAction.MEDIA_MUTE)
        onNodeWithText("M").assertExists()
        onNodeWithText("Mute / Unmute").assertExists()
    }

    @Test
    fun `bindings the dialog never used to mention are now listed`() = dialog {
        show(ShortcutAction.CLICKER_NEXT)
        onNodeWithText("Next (presentation clicker)").assertExists()
        onNodeWithText("Previous (presentation clicker)").assertExists()
        onNodeWithText("PgDn").assertExists()

        show(ShortcutAction.PRESENTATION_BLANK)
        onNodeWithText("Blank Screen").assertExists()
    }

    @Test
    fun `a rebound action shows its new key, not the shipped one`() {
        dialog(settingsWith(ShortcutAction.MEDIA_MUTE, listOf(KeyChord.of(Key.J)))) {
            show(ShortcutAction.MEDIA_MUTE)
            onNodeWithText("J").assertExists()
            onNodeWithText("Mute / Unmute").assertExists()
        }
    }

    @Test
    fun `an unbound action is shown as not set rather than blank`() {
        fun ComposeUiTest.unboundRows() =
            onAllNodesWithText("Not set").fetchSemanticsNodes(atLeastOneRootRequired = false).size

        dialog {
            show(ShortcutAction.SAVE_SCHEDULE_AS)
            assertEquals(1, unboundRows())
        }

        dialog(settingsWith(ShortcutAction.MEDIA_MUTE, emptyList())) {
            show(ShortcutAction.MEDIA_MUTE)
            assertEquals(1, unboundRows(), "the cleared row reads Not set rather than showing an empty box")
        }
    }

    @Test
    fun `mouse gestures are still listed, since they are not rebindable`() = dialog {
        onNodeWithTag(shortcutCategoryTag(null)).performClick()
        waitForIdle()
        onNodeWithText("Double-click").assertExists()
        onNodeWithText("Right-click").assertExists()
        onNodeWithText("Shift+Drag").assertExists()
    }
}
