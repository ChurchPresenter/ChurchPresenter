package org.churchpresenter.app.churchpresenter.models.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import kotlinx.serialization.Serializable

/**
 * One key press plus the modifiers held with it — the unit a shortcut is bound to.
 *
 * [keyCode] is `Key.keyCode` rather than a `Key`: `Key` is a value class over a `Long` with no
 * serializer, and storing the raw code keeps `settings.json` readable by a build whose Compose
 * version renumbered nothing but added keys. A code the running build does not recognise still
 * round-trips; it simply never matches an event.
 *
 * All four modifiers are compared **exactly**. A chord for `Ctrl+S` deliberately does not fire on
 * `Ctrl+Shift+S`, so the two can be bound to different actions.
 */
@Serializable
data class KeyChord(
    val keyCode: Long,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
) {
    val key: Key get() = Key(keyCode)

    fun matches(event: KeyEvent): Boolean =
        event.key.keyCode == keyCode &&
            event.isCtrlPressed == ctrl &&
            event.isShiftPressed == shift &&
            event.isAltPressed == alt &&
            event.isMetaPressed == meta

    /**
     * The Compose `MenuBar` accelerator form of this chord.
     *
     * Only the menu bar uses this; every other handler compares through [matches]. `KeyShortcut`
     * carries the same four modifiers, so the conversion is total.
     */
    fun toKeyShortcut(): KeyShortcut =
        KeyShortcut(key = key, ctrl = ctrl, meta = meta, alt = alt, shift = shift)

    companion object {
        fun of(key: Key, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false, meta: Boolean = false) =
            KeyChord(key.keyCode, ctrl = ctrl, shift = shift, alt = alt, meta = meta)

        /** The chord a key-down event represents, for the capture dialog. */
        fun of(event: KeyEvent) = KeyChord(
            keyCode = event.key.keyCode,
            ctrl = event.isCtrlPressed,
            shift = event.isShiftPressed,
            alt = event.isAltPressed,
            meta = event.isMetaPressed,
        )

        /**
         * Keys that only ever appear as part of a chord and can never be the chord themselves.
         *
         * The capture dialog sees a key-down for the modifier the moment the user starts holding it,
         * which would otherwise be recorded as a bare `Ctrl` binding before they press the real key.
         */
        val MODIFIER_KEYS: Set<Key> = setOf(
            Key.CtrlLeft, Key.CtrlRight,
            Key.ShiftLeft, Key.ShiftRight,
            Key.AltLeft, Key.AltRight,
            Key.MetaLeft, Key.MetaRight,
        )
    }
}
