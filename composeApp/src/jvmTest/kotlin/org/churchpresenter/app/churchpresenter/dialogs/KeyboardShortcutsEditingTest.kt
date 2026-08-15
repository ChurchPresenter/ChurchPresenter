@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.churchpresenter.app.churchpresenter.models.ShortcutScope
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyboardShortcutsEditingTest {
    private class Saves {
        val saved = mutableListOf<AppSettings>()
        var dismissed = 0
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
                        onDismiss = { result.dismissed++ },
                    )
                }
            }
            block(result)
        }
    }

    private fun withOverride(action: ShortcutAction, chords: List<KeyChord>) = AppSettings(
        keyboardShortcutSettings = KeyboardShortcutSettings(overrides = mapOf(action.name to chords))
    )

    private fun ComposeUiTest.show(action: ShortcutAction) {
        onNodeWithTag(shortcutCategoryTag(action.scope)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.chipFor(action: ShortcutAction) = onNodeWithTag(shortcutChipTag(action))
    private fun ComposeUiTest.revertFor(action: ShortcutAction) = onNodeWithTag(shortcutRevertTag(action))

    private fun ComposeUiTest.rebind(action: ShortcutAction, key: Key, ctrl: Boolean = false) {
        chipFor(action).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(shortcutRecordingTag(action)).performKeyInput {
            if (ctrl) withKeyDown(Key.CtrlLeft) { pressKey(key) } else pressKey(key)
        }
        waitForIdle()
    }

    private fun Saves.lastOverrides() = saved.last().keyboardShortcutSettings.overrides

    @Test
    fun `every action has a row in its own category`() = dialog {
        ShortcutScope.entries.forEach { scope ->
            onNodeWithTag(shortcutCategoryTag(scope)).performClick()
            waitForIdle()
            ShortcutAction.entries.filter { it.scope == scope }.forEach { chipFor(it).assertExists() }
        }
    }

    @Test
    fun `a row shows the shipped binding when nothing has been changed`() = dialog {
        show(ShortcutAction.MEDIA_MUTE)
        chipFor(ShortcutAction.MEDIA_MUTE).assertContentDescriptionEquals("M")
    }

    @Test
    fun `a row shows the override once one is set`() =
        dialog(withOverride(ShortcutAction.MEDIA_MUTE, listOf(KeyChord.of(Key.J)))) {
            show(ShortcutAction.MEDIA_MUTE)
            chipFor(ShortcutAction.MEDIA_MUTE).assertContentDescriptionEquals("J")
        }

    @Test
    fun `an unbound action reads Not set rather than empty`() =
        dialog(withOverride(ShortcutAction.MEDIA_MUTE, emptyList())) {
            show(ShortcutAction.MEDIA_MUTE)
            chipFor(ShortcutAction.MEDIA_MUTE).assertContentDescriptionEquals("Not set")
        }

    @Test
    fun `clicking the keys listens for a combination`() = dialog {
        show(ShortcutAction.MEDIA_MUTE)
        chipFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()

        onNodeWithTag(shortcutRecordingTag(ShortcutAction.MEDIA_MUTE)).assertExists()
        assertTrue(
            onAllNodesWithTag(shortcutChipTag(ShortcutAction.MEDIA_MUTE))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty(),
            "the caps are replaced by the listening chip, not shown beside it",
        )
    }

    @Test
    fun `the pressed combination becomes the binding`() = dialog { result ->
        show(ShortcutAction.MEDIA_MUTE)
        rebind(ShortcutAction.MEDIA_MUTE, Key.J, ctrl = true)

        chipFor(ShortcutAction.MEDIA_MUTE).assertExists()
        onNodeWithText("Apply").performClick()
        assertEquals(
            listOf(KeyChord.of(Key.J, ctrl = true)),
            result.lastOverrides()[ShortcutAction.MEDIA_MUTE.name],
        )
    }

    @Test
    fun `a bare modifier is not recorded as the binding`() = dialog {
        show(ShortcutAction.MEDIA_MUTE)
        chipFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()
        waitForIdle()

        onNodeWithTag(shortcutRecordingTag(ShortcutAction.MEDIA_MUTE)).performKeyInput {
            pressKey(Key.CtrlLeft)
        }
        waitForIdle()

        onNodeWithTag(shortcutRecordingTag(ShortcutAction.MEDIA_MUTE)).assertExists()
    }

    @Test
    fun `Escape is recorded rather than cancelling`() = dialog {
        show(ShortcutAction.MEDIA_MUTE)
        rebind(ShortcutAction.MEDIA_MUTE, Key.Escape)

        chipFor(ShortcutAction.MEDIA_MUTE).assertContentDescriptionEquals("Esc")
    }

    @Test
    fun `the stop button leaves the binding alone`() = dialog { result ->
        show(ShortcutAction.MEDIA_MUTE)
        chipFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()
        waitForIdle()

        onNodeWithContentDescription("Stop listening").performClick()
        waitForIdle()

        chipFor(ShortcutAction.MEDIA_MUTE).assertContentDescriptionEquals("M")
        onNodeWithText("Apply").performClick()
        assertEquals(emptyMap(), result.lastOverrides())
    }

    @Test
    fun `Clear unbinds the action`() = dialog { result ->
        show(ShortcutAction.MEDIA_MUTE)
        revertFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()
        chipFor(ShortcutAction.MEDIA_MUTE).assertContentDescriptionEquals("Not set")

        onNodeWithText("Apply").performClick()
        assertEquals(emptyList(), result.lastOverrides()[ShortcutAction.MEDIA_MUTE.name])
    }

    @Test
    fun `Reset removes the override entirely rather than writing the default back`() =
        dialog(withOverride(ShortcutAction.MEDIA_MUTE, listOf(KeyChord.of(Key.J)))) { result ->
            show(ShortcutAction.MEDIA_MUTE)
            revertFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()
            chipFor(ShortcutAction.MEDIA_MUTE).assertContentDescriptionEquals("M")

            onNodeWithText("Apply").performClick()
            assertFalse(result.lastOverrides().containsKey(ShortcutAction.MEDIA_MUTE.name))
        }

    @Test
    fun `Reset All drops every override at once`() {
        val settings = AppSettings(
            keyboardShortcutSettings = KeyboardShortcutSettings(
                overrides = mapOf(
                    ShortcutAction.MEDIA_MUTE.name to listOf(KeyChord.of(Key.J)),
                    ShortcutAction.UNDO.name to emptyList(),
                )
            )
        )
        dialog(settings) { result ->
            show(ShortcutAction.MEDIA_MUTE)
            onNodeWithTag(SHORTCUT_RESET_ALL_TAG).performClick()
            chipFor(ShortcutAction.MEDIA_MUTE).assertContentDescriptionEquals("M")

            onNodeWithText("Apply").performClick()
            assertEquals(emptyMap(), result.lastOverrides())
        }
    }

    @Test
    fun `editing one action leaves the others untouched`() =
        dialog(withOverride(ShortcutAction.MEDIA_MUTE, listOf(KeyChord.of(Key.J)))) { result ->
            show(ShortcutAction.MEDIA_PLAY_PAUSE)
            revertFor(ShortcutAction.MEDIA_PLAY_PAUSE).performScrollTo().performClick()

            onNodeWithText("Apply").performClick()
            assertEquals(
                mapOf(
                    ShortcutAction.MEDIA_MUTE.name to listOf(KeyChord.of(Key.J)),
                    ShortcutAction.MEDIA_PLAY_PAUSE.name to emptyList(),
                ),
                result.lastOverrides(),
            )
        }

    @Test
    fun `the dialog writes only to keyboardShortcutSettings`() {
        val initial = AppSettings()
        dialog(initial) { result ->
            show(ShortcutAction.MEDIA_MUTE)
            revertFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()
            onNodeWithText("Apply").performClick()

            val after = result.saved.last()
            assertEquals(initial.copy(keyboardShortcutSettings = after.keyboardShortcutSettings), after)
        }
    }

    @Test
    fun `the footer counts pending edits and drops the count once they are saved`() = dialog {
        onNodeWithTag(SHORTCUT_UNSAVED_TAG).assertDoesNotExist()

        show(ShortcutAction.MEDIA_MUTE)
        revertFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(SHORTCUT_UNSAVED_TAG).assertExists()

        onNodeWithTag(SHORTCUT_RESET_ALL_TAG).performClick()
        waitForIdle()
        onNodeWithTag(SHORTCUT_UNSAVED_TAG).assertDoesNotExist()
    }

    @Test
    fun `a binding saved in an earlier session is not counted as pending`() =
        dialog(withOverride(ShortcutAction.MEDIA_MUTE, listOf(KeyChord.of(Key.J)))) {
            onNodeWithTag(SHORTCUT_UNSAVED_TAG).assertDoesNotExist()
        }

    @Test
    fun `Cancel dismisses without saving`() = dialog { result ->
        show(ShortcutAction.MEDIA_MUTE)
        revertFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()
        onNodeWithText("Cancel", substring = true).performClick()

        assertEquals(1, result.dismissed)
        assertTrue(result.saved.isEmpty(), "a cancelled edit must not reach the settings")
    }

    @Test
    fun `Apply saves without dismissing`() = dialog { result ->
        show(ShortcutAction.MEDIA_MUTE)
        revertFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        assertEquals(0, result.dismissed)
        assertEquals(1, result.saved.size)
    }

    @Test
    fun `OK saves and dismisses`() = dialog { result ->
        show(ShortcutAction.MEDIA_MUTE)
        revertFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()
        onNodeWithText("OK", substring = true).performClick()

        assertEquals(1, result.dismissed)
        assertEquals(emptyList(), result.lastOverrides()[ShortcutAction.MEDIA_MUTE.name])
    }

    @Test
    fun `an edit is visible in the row before it is applied`() = dialog { result ->
        show(ShortcutAction.MEDIA_MUTE)
        revertFor(ShortcutAction.MEDIA_MUTE).performScrollTo().performClick()

        chipFor(ShortcutAction.MEDIA_MUTE).assertContentDescriptionEquals("Not set")
        assertTrue(result.saved.isEmpty(), "the row updates from pending state, not from a save")
    }

    @Test
    fun `clearing a binding frees its chord before Apply`() = dialog { result ->
        show(ShortcutAction.CLEAR_OUTPUT)
        revertFor(ShortcutAction.CLEAR_OUTPUT).performScrollTo().performClick()
        onNodeWithText("Apply").performClick()

        val pending = ShortcutMap.from(result.saved.last().keyboardShortcutSettings)
        assertNull(
            pending.conflictFor(KeyChord.of(Key.Escape), ShortcutAction.MEDIA_MUTE),
            "Escape must be free once Clear Output has been cleared",
        )
    }

    @Test
    fun `a chord assigned to another action is seen as taken`() {
        val pending = ShortcutMap.from(
            KeyboardShortcutSettings(overrides = mapOf(ShortcutAction.UNDO.name to listOf(KeyChord.of(Key.J))))
        )

        assertEquals(
            ShortcutAction.UNDO,
            pending.conflictFor(KeyChord.of(Key.J), ShortcutAction.MEDIA_MUTE),
        )
    }
}
