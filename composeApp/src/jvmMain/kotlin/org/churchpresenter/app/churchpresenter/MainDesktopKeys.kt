package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import org.churchpresenter.profiles.quickBackgroundSlotFor
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.ShortcutScope
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.schedule.undo
import org.churchpresenter.schedule.redo

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

/**
 * The main window's key handler, run in the preview pass so a shortcut works whichever tab or
 * control has focus. Returns true when the key was used.
 */
internal fun MainDesktopScope.handleMainDesktopKey(keyEvent: KeyEvent): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false
    val shortcutTab = shortcuts.actionFor(keyEvent, ShortcutScope.GLOBAL)?.targetTab
    val quickBackgroundSlot = quickBackgroundSlotFor(shortcuts, keyEvent)
    val live = slideContent == Presenting.PRESENTATION
    return when {
        shortcuts.matches(ShortcutAction.REDO, keyEvent) -> {
            scheduleViewModel.redo(); true
        }
        shortcuts.matches(ShortcutAction.UNDO, keyEvent) -> {
            scheduleViewModel.undo(); true
        }
        shortcuts.matches(ShortcutAction.QUICK_BACKGROUND_RESET, keyEvent) -> {
            onQuickBackgroundPicked(null); true
        }
        quickBackgroundSlot != null -> {
            appSettings.quickBackgrounds.getOrNull(quickBackgroundSlot - 1)
                ?.let(onQuickBackgroundPicked)
            // Swallowed whether or not that slot is filled: a tray of three must
            // not let Ctrl+4 fall through to whatever else would answer it.
            true
        }
        shortcuts.matches(ShortcutAction.CLEAR_OUTPUT, keyEvent) -> {
            clearOutput(); true
        }
        shortcuts.matches(ShortcutAction.TAKE, keyEvent) -> {
            presenterManager.previewBus.take(); true
        }
        // Presentation clickers (Logitech/Kensington etc.) are HID keyboards
        // sending Page Down/Up. Handled here in the preview pass so a live
        // presentation responds no matter which tab or control has focus —
        // the presenter clicks from the platform while the operator works
        // elsewhere. Only claimed while a presentation is actually live.
        shortcuts.matches(ShortcutAction.CLICKER_NEXT, keyEvent) && live -> {
            clickerScope.launch { clickPresentation(forward = true) }
            true
        }
        shortcuts.matches(ShortcutAction.CLICKER_PREVIOUS, keyEvent) && live -> {
            clickerScope.launch { clickPresentation(forward = false) }
            true
        }
        shortcutTab != null -> { selectTab(shortcutTab); true }
        else -> advanceKeySequences(keyEvent.key)
    }
}

/** One clicker press: the deck's next or previous animation step, else the next or previous slide. */
private suspend fun MainDesktopScope.clickPresentation(forward: Boolean) =
    clickPresentationSlide(forward, presentationViewModel, presenterManager, link)

/** Feeds a key no shortcut claimed to the hidden sequences; never claims it. */
private fun MainDesktopScope.advanceKeySequences(key: Key): Boolean {
    if (presenterManager.anythingLive) {
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
        selectTab(Tabs.CROSSWORD)
    }

    // Upper- or lower-case; Key.D is Shift-agnostic.
    val developerStep = advanceKeySequence(key, DEVELOPER_UNLOCK_SEQUENCE, state.developerUnlockProgress)
    state.developerUnlockProgress = developerStep.progress
    if (developerStep.completed) onRequestDeveloperMenuUnlock()
    return false
}
