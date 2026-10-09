package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.churchpresenter.profiles.quickBackgroundSlotFor
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.Macro
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.sharedui.models.CLEAR_GROUP_ACTIONS
import org.churchpresenter.sharedui.models.MACRO_ACTIONS
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.ShortcutScope
import org.churchpresenter.sharedui.models.Tabs

/** Konami code: ↑↑↓↓←→←→BA */
private val KONAMI_SEQUENCE = listOf(
    Key.DirectionUp, Key.DirectionUp,
    Key.DirectionDown, Key.DirectionDown,
    Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionLeft, Key.DirectionRight,
    Key.B, Key.A
)

/** Opens the crossword tab: ←→←→ */
private val CROSSWORD_SEQUENCE = listOf(
    Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionLeft, Key.DirectionRight
)

private const val DEVELOPER_UNLOCK_PRESSES = 7

/** Secret Developer-menu unlock: the letter D pressed seven times in a row. */
private val DEVELOPER_UNLOCK_SEQUENCE = List(DEVELOPER_UNLOCK_PRESSES) { Key.D }

/** What the main window's shortcuts do. Every one is wired by the main screen; a test sets only those it drives. */
internal class MainDesktopKeyActions(
    val undo: () -> Unit = {},
    val redo: () -> Unit = {},
    val pickQuickBackground: (QuickBackground?) -> Unit = {},
    val clearOutput: () -> Unit = {},
    val runMacro: (Macro) -> Unit = {},
    val clearGroup: (ClearGroup) -> Unit = {},
    /** Preview mode's Take. */
    val take: () -> Unit = {},
    /** One clicker press, forward or back, on the live presentation. */
    val clickPresentation: (forward: Boolean) -> Unit = {},
    val selectTab: (Tabs) -> Unit = {},
    val unlockDeveloperMenu: () -> Unit = {},
    /** Whether a presentation is the live content, read as the key arrives. */
    val presentationLive: () -> Boolean = { false },
    /** Whether anything is on air, read as the key arrives: the hidden sequences pause while it is. */
    val anythingLive: () -> Boolean = { false },
)

/**
 * What a key is judged against: the [shortcuts], whether [devMode] is on, the [settings] the quick
 * backgrounds, macros and clear groups are read from, and the [sequences] progress the hidden key
 * sequences keep in the main screen's state.
 */
internal class MainDesktopKeyContext(
    val shortcuts: ShortcutMap,
    val devMode: Boolean,
    val settings: AppSettings,
    val sequences: MainDesktopState,
    val actions: MainDesktopKeyActions,
)

/**
 * The main window's key handler, run in the preview pass so a shortcut works whichever tab or
 * control has focus. Returns true when the key was used.
 */
internal fun handleMainDesktopKey(keyEvent: KeyEvent, context: MainDesktopKeyContext): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false
    val shortcuts = context.shortcuts
    val actions = context.actions
    val settings = context.settings
    val shortcutTab = shortcuts.actionFor(keyEvent, ShortcutScope.GLOBAL)?.targetTab
    val quickBackgroundSlot = quickBackgroundSlotFor(shortcuts, keyEvent)
    // Dev mode only: outside it these keys do nothing, and fall through.
    val macroSlot = macroSlotFor(shortcuts, keyEvent)?.takeIf { context.devMode }
    val clearGroupSlot = clearGroupSlotFor(shortcuts, keyEvent)?.takeIf { context.devMode }
    val live = actions.presentationLive()
    return when {
        shortcuts.matches(ShortcutAction.REDO, keyEvent) -> {
            actions.redo(); true
        }
        shortcuts.matches(ShortcutAction.UNDO, keyEvent) -> {
            actions.undo(); true
        }
        shortcuts.matches(ShortcutAction.QUICK_BACKGROUND_RESET, keyEvent) -> {
            actions.pickQuickBackground(null); true
        }
        quickBackgroundSlot != null -> {
            settings.quickBackgrounds.getOrNull(quickBackgroundSlot - 1)
                ?.let(actions.pickQuickBackground)
            // Swallowed whether or not that slot is filled: a tray of three must
            // not let Ctrl+4 fall through to whatever else would answer it.
            true
        }
        shortcuts.matches(ShortcutAction.CLEAR_OUTPUT, keyEvent) -> {
            actions.clearOutput(); true
        }
        macroSlot != null -> {
            // Swallowed whether or not that slot is filled, as the quick backgrounds are.
            settings.macros.getOrNull(macroSlot)?.let(actions.runMacro)
            true
        }
        clearGroupSlot != null -> {
            settings.clearGroups.getOrNull(clearGroupSlot)?.let(actions.clearGroup)
            true
        }
        shortcuts.matches(ShortcutAction.TAKE, keyEvent) && context.devMode -> {
            actions.take(); true
        }
        // Presentation clickers (Logitech/Kensington etc.) are HID keyboards
        // sending Page Down/Up. Handled here in the preview pass so a live
        // presentation responds no matter which tab or control has focus —
        // the presenter clicks from the platform while the operator works
        // elsewhere. Only claimed while a presentation is actually live.
        shortcuts.matches(ShortcutAction.CLICKER_NEXT, keyEvent) && live -> {
            actions.clickPresentation(true)
            true
        }
        shortcuts.matches(ShortcutAction.CLICKER_PREVIOUS, keyEvent) && live -> {
            actions.clickPresentation(false)
            true
        }
        shortcutTab != null -> { actions.selectTab(shortcutTab); true }
        else -> advanceKeySequences(keyEvent.key, context.sequences, actions)
    }
}

/** Which of the first nine macros [keyEvent] runs (0-based), or null when it runs none. */
private fun macroSlotFor(shortcuts: ShortcutMap, keyEvent: KeyEvent): Int? =
    MACRO_ACTIONS.indexOfFirst { shortcuts.matches(it, keyEvent) }.takeIf { it >= 0 }

/** Which of the first nine clear groups [keyEvent] fires (0-based), or null when it fires none. */
private fun clearGroupSlotFor(shortcuts: ShortcutMap, keyEvent: KeyEvent): Int? =
    CLEAR_GROUP_ACTIONS.indexOfFirst { shortcuts.matches(it, keyEvent) }.takeIf { it >= 0 }

/** Feeds a key no shortcut claimed to the hidden sequences; never claims it. */
private fun advanceKeySequences(key: Key, state: MainDesktopState, actions: MainDesktopKeyActions): Boolean {
    if (actions.anythingLive()) {
        // Suppress both easter egg sequences while live
        state.konamiProgress = 0
        state.crosswordProgress = 0
        return false
    }
    val konamiStep = advanceKeySequence(key, KONAMI_SEQUENCE, state.konamiProgress)
    state.konamiProgress = konamiStep.progress
    if (konamiStep.completed) state.showKonamiEasterEgg = true

    val crosswordStep = advanceKeySequence(key, CROSSWORD_SEQUENCE, state.crosswordProgress)
    state.crosswordProgress = crosswordStep.progress
    if (crosswordStep.completed) {
        state.showCrosswordTab = true
        actions.selectTab(Tabs.CROSSWORD)
    }

    // Upper- or lower-case; Key.D is Shift-agnostic.
    val developerStep = advanceKeySequence(key, DEVELOPER_UNLOCK_SEQUENCE, state.developerUnlockProgress)
    state.developerUnlockProgress = developerStep.progress
    if (developerStep.completed) actions.unlockDeveloperMenu()
    return false
}
