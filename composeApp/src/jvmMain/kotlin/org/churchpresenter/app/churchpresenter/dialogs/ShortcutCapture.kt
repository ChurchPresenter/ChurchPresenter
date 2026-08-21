package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.churchpresenter.app.churchpresenter.models.shortcuts.KeyChord

/**
 * The chord a key event should be recorded as, or null to keep waiting.
 *
 * Split out of the UI because both places that listen for a combination need exactly this decision
 * and nothing else — the row being rebound, and the dialog's "Press key" search — and because it is
 * plain Kotlin, so it is unit-tested directly rather than driven through a composable.
 *
 * Returns null for a key-up (the press is what counts) and for a bare modifier: holding Ctrl emits
 * its own key-down before the real key arrives, and recording that would bind the action to "Ctrl".
 *
 * **Escape yields a chord like any other key.** It is the default binding for Clear Output, so
 * treating it as "cancel" here would make that one action impossible to rebind.
 */
internal fun capturedChord(event: KeyEvent): KeyChord? = when {
    event.type != KeyEventType.KeyDown -> null
    event.key in KeyChord.MODIFIER_KEYS -> null
    else -> KeyChord.of(event)
}
