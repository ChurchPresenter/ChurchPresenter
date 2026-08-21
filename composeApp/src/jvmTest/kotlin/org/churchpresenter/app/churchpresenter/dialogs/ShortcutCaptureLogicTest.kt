package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import org.churchpresenter.app.churchpresenter.models.shortcuts.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.utils.keyDown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What the capture dialog does with a key press.
 *
 * The dialog itself is a `DialogWindow`, which needs a real window and cannot be composed by
 * `runComposeUiTest`, so its decision lives in `capturedChord` and is exercised here directly
 * rather than through a mock of the dialog.
 */
class ShortcutCaptureLogicTest {

    @OptIn(InternalComposeUiApi::class)
    private fun keyUp(key: Key) = KeyEvent(
        key = key,
        type = KeyEventType.KeyUp,
        codePoint = 0,
        isCtrlPressed = false,
        isMetaPressed = false,
        isAltPressed = false,
        isShiftPressed = false,
    )

    @Test
    fun `an ordinary key press is captured`() {
        assertEquals(KeyChord.of(Key.J), capturedChord(keyDown(Key.J)))
    }

    @Test
    fun `modifiers held with the key are captured too`() {
        assertEquals(
            KeyChord.of(Key.J, ctrl = true, shift = true),
            capturedChord(keyDown(Key.J, ctrl = true, shift = true))
        )
    }

    @Test
    fun `a bare modifier press is ignored so it cannot become the binding`() {
        listOf(Key.CtrlLeft, Key.CtrlRight, Key.ShiftLeft, Key.AltRight, Key.MetaLeft).forEach {
            assertNull(capturedChord(keyDown(it)), "$it must not be recordable on its own")
        }
    }

    @Test
    fun `a key release is ignored`() {
        assertNull(capturedChord(keyUp(Key.J)))
    }

    @Test
    fun `Escape is capturable, since it is a real binding`() {
        // Escape is the shipped Clear Output binding. A dialog that treated it as "cancel" could
        // never rebind it, which is why cancelling is a button here.
        assertEquals(KeyChord.of(Key.Escape), capturedChord(keyDown(Key.Escape)))
    }

    @Test
    fun `a captured chord that clashes is reported against the action being edited`() {
        val chord = capturedChord(keyDown(Key.Z, ctrl = true))!!

        assertEquals(
            ShortcutAction.UNDO,
            ShortcutMap.DEFAULT.conflictFor(chord, ShortcutAction.CLEAR_OUTPUT)
        )
    }

    @Test
    fun `a captured chord that is free reports no clash`() {
        val chord = capturedChord(keyDown(Key.J, ctrl = true, alt = true))!!

        assertNull(ShortcutMap.DEFAULT.conflictFor(chord, ShortcutAction.CLEAR_OUTPUT))
    }
}
