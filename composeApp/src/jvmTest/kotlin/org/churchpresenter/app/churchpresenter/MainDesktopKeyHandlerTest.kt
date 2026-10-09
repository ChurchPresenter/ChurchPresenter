package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.key.Key
import org.churchpresenter.app.churchpresenter.utils.keyDown
import org.churchpresenter.app.churchpresenter.utils.keyUp
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.settings.Macro
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.ShortcutMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [handleMainDesktopKey] driven with key events and [MainDesktopKeyActions] left at its defaults
 * where a case does not care: those defaults must do nothing and claim nothing live.
 */
class MainDesktopKeyHandlerTest {

    private fun newState() = MainDesktopState(mutableIntStateOf(0), showCrashFeedbackInitially = false)

    private fun context(
        actions: MainDesktopKeyActions = MainDesktopKeyActions(),
        devMode: Boolean = true,
        settings: AppSettings = AppSettings(),
        state: MainDesktopState = newState(),
    ) = MainDesktopKeyContext(
        shortcuts = ShortcutMap.from(settings.keyboardShortcutSettings),
        devMode = devMode,
        settings = settings,
        sequences = state,
        actions = actions,
    )

    private fun bound(action: ShortcutAction, key: Key) = AppSettings(
        keyboardShortcutSettings = KeyboardShortcutSettings(
            overrides = mapOf(action.name to listOf(KeyChord.of(key))),
        ),
    )

    @Test
    fun `the default actions do nothing and report nothing live`() {
        val actions = MainDesktopKeyActions()
        actions.undo()
        actions.redo()
        actions.pickQuickBackground(null)
        actions.clearOutput()
        actions.runMacro(Macro(id = "m", name = "m"))
        actions.take()
        actions.clickPresentation(true)
        actions.selectTab(Tabs.BIBLE)
        actions.unlockDeveloperMenu()
        assertFalse(actions.presentationLive())
        assertFalse(actions.anythingLive())
    }

    @Test
    fun `a key release is never used`() {
        assertFalse(handleMainDesktopKey(keyUp(Key.Z), context()))
    }

    @Test
    fun `undo and redo reach their actions through the default bindings`() {
        val seen = mutableListOf<String>()
        val ctx = context(MainDesktopKeyActions(undo = { seen += "undo" }, redo = { seen += "redo" }))
        assertTrue(handleMainDesktopKey(keyDown(Key.Z, ctrl = true), ctx))
        assertTrue(handleMainDesktopKey(keyDown(Key.Z, ctrl = true, shift = true), ctx))
        assertEquals(listOf("undo", "redo"), seen)
    }

    @Test
    fun `the clicker keys pass by unless a presentation is live`() {
        val clicks = mutableListOf<Boolean>()
        var live = false
        val ctx = context(MainDesktopKeyActions(clickPresentation = { clicks += it }, presentationLive = { live }))
        assertFalse(handleMainDesktopKey(keyDown(Key.PageDown), ctx))
        live = true
        assertTrue(handleMainDesktopKey(keyDown(Key.PageDown), ctx))
        assertTrue(handleMainDesktopKey(keyDown(Key.PageUp), ctx))
        assertEquals(listOf(true, false), clicks)
    }

    @Test
    fun `take is dev mode only`() {
        var takes = 0
        val settings = bound(ShortcutAction.TAKE, Key.F9)
        val actions = MainDesktopKeyActions(take = { takes++ })
        assertFalse(handleMainDesktopKey(keyDown(Key.F9), context(actions, devMode = false, settings = settings)))
        assertEquals(0, takes)
        assertTrue(handleMainDesktopKey(keyDown(Key.F9), context(actions, devMode = true, settings = settings)))
        assertEquals(1, takes)
    }

    @Test
    fun `a macro key falls through outside dev mode and is swallowed in it`() {
        val settings = bound(ShortcutAction.MACRO_1, Key.Insert)
        assertFalse(handleMainDesktopKey(keyDown(Key.Insert), context(devMode = false, settings = settings)))
        assertTrue(handleMainDesktopKey(keyDown(Key.Insert), context(devMode = true, settings = settings)))
    }

    @Test
    fun `the hidden sequences stop counting while anything is live`() {
        val state = newState()
        state.konamiProgress = 3
        state.crosswordProgress = 2
        val live = context(MainDesktopKeyActions(anythingLive = { true }), state = state)
        assertFalse(handleMainDesktopKey(keyDown(Key.DirectionLeft), live))
        assertEquals(0, state.konamiProgress)
        assertEquals(0, state.crosswordProgress)
    }

    @Test
    fun `seven presses of D complete the developer sequence with the default actions`() {
        val state = newState()
        val ctx = context(state = state)
        repeat(6) { handleMainDesktopKey(keyDown(Key.D), ctx) }
        assertEquals(6, state.developerUnlockProgress)
        handleMainDesktopKey(keyDown(Key.D), ctx)
        assertEquals(0, state.developerUnlockProgress, "the sequence completed and reset")
    }
}
