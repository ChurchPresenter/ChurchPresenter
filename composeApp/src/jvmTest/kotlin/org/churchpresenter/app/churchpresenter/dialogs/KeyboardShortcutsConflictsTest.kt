@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.KeyboardShortcutSettings
import org.churchpresenter.app.churchpresenter.models.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyboardShortcutsConflictsTest {
    private class Saves {
        val saved = mutableListOf<AppSettings>()
    }

    private fun dialog(
        initial: AppSettings = AppSettings(),
        block: ComposeUiTest.(result: Saves) -> Unit,
    ) {
        val result = Saves()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    KeyboardShortcutsDialogContent(
                        initialSettings = initial,
                        onSave = { result.saved += it },
                        onDismiss = {},
                    )
                }
            }
            block(result)
        }
    }

    private val muteOntoUndo = AppSettings(
        keyboardShortcutSettings = KeyboardShortcutSettings(
            overrides = mapOf(ShortcutAction.MEDIA_MUTE.name to listOf(KeyChord.of(Key.Z, ctrl = true)))
        )
    )

    private fun ComposeUiTest.show(action: ShortcutAction) {
        onNodeWithTag(shortcutCategoryTag(action.scope)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.rowExists(action: ShortcutAction) =
        onAllNodesWithTag(shortcutChipTag(action))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `the filter is dead while the shipped bindings are intact`() = dialog {
        onNodeWithTag(SHORTCUT_CONFLICTS_FILTER_TAG).assertIsNotEnabled()
        onNodeWithText("No conflicts").assertExists()
    }

    @Test
    fun `a clashing row names what already answers to the combination`() = dialog {
        show(ShortcutAction.MEDIA_MUTE)
        onNodeWithTag(shortcutChipTag(ShortcutAction.MEDIA_MUTE)).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(shortcutRecordingTag(ShortcutAction.MEDIA_MUTE)).performKeyInput {
            withKeyDown(Key.CtrlLeft) { pressKey(Key.Z) }
        }
        waitForIdle()

        onNodeWithText("Already used by: Undo last schedule change").assertExists()
    }

    @Test
    fun `a clashing combination is recorded and reported`() = dialog(muteOntoUndo) {
        show(ShortcutAction.MEDIA_MUTE)

        onNodeWithTag(shortcutChipTag(ShortcutAction.MEDIA_MUTE)).assertExists()
        onNodeWithText("Already used by: Undo last schedule change").assertExists()
    }

    @Test
    fun `Apply and OK are disabled while a clash is unresolved`() = dialog(muteOntoUndo) {
        onNodeWithText("Apply").assertIsNotEnabled()
        onNodeWithText("OK", substring = true).assertIsNotEnabled()
    }

    @Test
    fun `Apply and OK come back once the clash is resolved`() = dialog(muteOntoUndo) {
        show(ShortcutAction.MEDIA_MUTE)
        onNodeWithTag(shortcutRevertTag(ShortcutAction.MEDIA_MUTE)).performScrollTo().performClick()
        waitForIdle()

        onNodeWithText("Apply").assertIsEnabled()
        onNodeWithText("OK", substring = true).assertIsEnabled()
    }

    @Test
    fun `a clash cannot be saved even from an untouched dialog`() = dialog(muteOntoUndo) { result ->
        onNodeWithText("Apply").performClick()

        assertTrue(result.saved.isEmpty())
    }

    @Test
    fun `the filter collects both halves of a clash, from either category`() = dialog(muteOntoUndo) {
        onNodeWithTag(SHORTCUT_CONFLICTS_FILTER_TAG).assertIsEnabled()
        onNodeWithTag(SHORTCUT_CONFLICTS_FILTER_TAG).performClick()
        waitForIdle()

        assertTrue(rowExists(ShortcutAction.MEDIA_MUTE), "the Media half")
        assertTrue(rowExists(ShortcutAction.UNDO), "and the global half, from another category")
        assertTrue(!rowExists(ShortcutAction.MEDIA_PLAY_PAUSE), "and nothing that is not in a clash")
        onNodeWithTag(SHORTCUT_SECTION_TITLE_TAG).assertTextEquals("Conflicts")
    }

    @Test
    fun `resolving the clash empties the filter and disables it again`() = dialog(muteOntoUndo) { result ->
        onNodeWithTag(SHORTCUT_CONFLICTS_FILTER_TAG).performClick()
        waitForIdle()

        onNodeWithTag(shortcutRevertTag(ShortcutAction.MEDIA_MUTE)).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(SHORTCUT_CONFLICTS_FILTER_TAG).assertIsNotEnabled()
        onNodeWithText("Apply").performClick()
        assertEquals(emptyMap(), result.saved.last().keyboardShortcutSettings.overrides)
    }

    @Test
    fun `picking a category drops the conflicts filter`() = dialog(muteOntoUndo) {
        onNodeWithTag(SHORTCUT_CONFLICTS_FILTER_TAG).performClick()
        waitForIdle()

        show(ShortcutAction.MEDIA_PLAY_PAUSE)

        assertTrue(rowExists(ShortcutAction.MEDIA_PLAY_PAUSE), "the category is showing in full again")
        assertTrue(!rowExists(ShortcutAction.UNDO), "and the clash from another category is gone")
    }
}
