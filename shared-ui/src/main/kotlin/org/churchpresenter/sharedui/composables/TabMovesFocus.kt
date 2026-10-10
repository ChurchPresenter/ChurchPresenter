package org.churchpresenter.sharedui.composables

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Tab and Shift+Tab move focus on from a multi-line text field, instead of typing a tab character
 * into it -- which left a keyboard operator unable to leave the field at all.
 */
fun Modifier.tabMovesFocus(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (event.key != Key.Tab) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyDown) {
            focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
        }
        true
    }
}
