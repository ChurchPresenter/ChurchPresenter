@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.KeyboardShortcutSettings
import org.churchpresenter.app.churchpresenter.dialogs.KeyboardShortcutsDialogContent
import org.churchpresenter.app.churchpresenter.dialogs.SHORTCUT_CONFLICTS_FILTER_TAG
import org.churchpresenter.app.churchpresenter.dialogs.SHORTCUT_PRESS_MODE_TAG
import org.churchpresenter.app.churchpresenter.dialogs.SHORTCUT_PRESS_PANEL_TAG
import org.churchpresenter.app.churchpresenter.dialogs.shortcutCategoryTag
import org.churchpresenter.app.churchpresenter.dialogs.shortcutChipTag
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.models.ShortcutScope
import org.churchpresenter.app.churchpresenter.ui.theme.ChurchPresenterTheme
import kotlin.test.Test

/**
 * The Keyboard Shortcuts dialog (Help → Keyboard Shortcuts, F1), in both themes.
 *
 * A category rail on the left, one category's rows beside it, and Cancel/Apply/OK under both. This
 * is the only place shortcuts are both listed and changed — the editing UI was briefly a Settings
 * tab and was merged in here, and the separate capture window was folded into the row.
 *
 * What changes the shape of a row rather than a value in it:
 *
 *  - **Whether the action is customized.** The caps are tinted, and the row's one button turns from
 *    *Clear* into *Reset*. The footer also starts counting, but only against what was passed in.
 *  - **Whether the action is bound at all.** An unbound row reads "Not set" rather than empty caps.
 *  - **Whether it clashes with another binding.** The row, its caps and the rail entry all take the
 *    error colour and the row names what else answers to the combination.
 *  - **Whether it is listening.** The caps are replaced by the recording chip for as long as the
 *    row holds the keyboard.
 *
 * Light and dark are **separate images** here rather than stacked: the dialog is 720 tall, and a
 * stacked pair is a strip no reviewer can take in at once.
 *
 * **These are macOS renders**, so modifiers appear as `⌃⌥⇧⌘` rather than `Ctrl+Alt+…`. That is what
 * the same code produces on this platform, not a defect — see the platform table in AGENT.md before
 * re-recording anywhere else.
 */
class KeyboardShortcutsDialogScreenshotTest {

    @Test
    fun `as it opens`() = shoot("defaults")

    /**
     * The Global category, with bindings moved, cleared and left alone side by side.
     *
     * Shot on Global rather than the category the dialog opens on because that is where the seeded
     * overrides land — a customized row is only visible in its own category now.
     */
    @Test
    fun `with customized bindings`() = shoot("customized", settings = CUSTOMIZED) {
        onNodeWithTag(shortcutCategoryTag(ShortcutScope.GLOBAL)).performClick()
        waitForIdle()
    }

    /**
     * Filtered down by a search.
     *
     * A search overrides the rail and spans every category, so each row carries the category it
     * came from — three of them read "Play / Pause" and only the tag tells them apart.
     */
    @Test
    fun `filtered by a search`() = shoot("filtered") {
        onNode(hasSetTextAction()).performTextInput("verse")
        waitForIdle()
    }

    /**
     * "Press key" mode, after pressing the left arrow.
     *
     * A distinct layout, not a variant of the text search: the box stops accepting text and shows
     * the chord instead, because the arrow keys have to reach the filter rather than move a cursor.
     */
    @Test
    fun `filtered by a pressed key`() = shoot("press_key") {
        onNodeWithTag(SHORTCUT_PRESS_MODE_TAG).performClick()
        waitForIdle()
        onNodeWithTag(SHORTCUT_PRESS_PANEL_TAG).performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
    }

    /**
     * Two actions on one combination, collected by the toolbar's filter.
     *
     * The only state where the error colour is on screen at all — the toolbar count, the rail dot,
     * the row and its caps.
     */
    @Test
    fun `showing the conflicts`() = shoot("conflicts", settings = CLASHING) {
        onNodeWithTag(SHORTCUT_CONFLICTS_FILTER_TAG).performClick()
        waitForIdle()
    }

    /** A row listening for a new combination, which is what replaced the capture window. */
    @Test
    fun `recording a new binding`() = shoot("recording") {
        onNodeWithTag(shortcutCategoryTag(ShortcutScope.MEDIA)).performClick()
        waitForIdle()
        onNodeWithTag(shortcutChipTag(ShortcutAction.MEDIA_MUTE)).performClick()
        waitForIdle()
    }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Shot at the dialog's real size rather than the runner's default window.
     *
     * `KeyboardShortcutsDialog` opens at 900×720 — wide enough for the rail and a full row beside
     * it — and at the runner's default the rows stretch far past anything anyone will see. The
     * point of a committed image is that a reviewer can approve what ships.
     */
    private fun shoot(
        name: String,
        settings: AppSettings = AppSettings(),
        drive: SkikoComposeUiTest.() -> Unit = {},
    ) = separateThemes(SECTION, name) { mode, file ->
        runSkikoComposeUiTest(size = Size(DIALOG_WIDTH, DIALOG_HEIGHT), density = Density(1f)) {
            setContent {
                ChurchPresenterTheme(themeMode = mode) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.fillMaxSize()) {
                            KeyboardShortcutsDialogContent(
                                initialSettings = settings,
                                onSave = {},
                                onDismiss = {},
                            )
                        }
                    }
                }
            }
            waitForIdle()
            drive()
            waitForIdle()
            captureTo(file)
        }
    }

    private companion object {
        const val SECTION = "keyboardShortcutsDialog"

        /** Matches the DialogWindow size in `KeyboardShortcutsDialog`. */
        const val DIALOG_WIDTH = 900f
        const val DIALOG_HEIGHT = 720f

        /** Moved, moved again and unbound — every state a Global row can be in at once. */
        val CUSTOMIZED = AppSettings(
            keyboardShortcutSettings = KeyboardShortcutSettings(
                overrides = mapOf(
                    ShortcutAction.UNDO.name to listOf(KeyChord.of(Key.U, ctrl = true)),
                    ShortcutAction.CLEAR_OUTPUT.name to emptyList(),
                    ShortcutAction.SWITCH_TO_BIBLE.name to listOf(KeyChord.of(Key.B, ctrl = true, alt = true)),
                )
            )
        )

        /** Mute moved onto Undo's chord, which is global and so competes with it. */
        val CLASHING = AppSettings(
            keyboardShortcutSettings = KeyboardShortcutSettings(
                overrides = mapOf(
                    ShortcutAction.MEDIA_MUTE.name to listOf(KeyChord.of(Key.Z, ctrl = true))
                )
            )
        )
    }
}
