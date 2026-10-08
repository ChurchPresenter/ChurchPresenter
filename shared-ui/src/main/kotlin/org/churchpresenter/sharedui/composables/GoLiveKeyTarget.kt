package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap

/**
 * Makes a tab's root answer the Go Live key, for the tabs that have no key handling of their own.
 *
 * The root takes the keyboard when the tab opens and on any press inside it, so the key works after
 * a click on a button; a press on a text field ends with the field holding it, as usual. A tab
 * that puts the caret in its search box on opening passes [focusOnOpen] false. The key
 * acts only while the root itself has the keyboard -- a one-line field lets Enter through to here,
 * and Enter there means the field's own thing. [enabled] is false whenever the tab's Go Live button
 * would be disabled, or what it would send is already live.
 */
@Composable
fun Modifier.goLiveKeyTarget(enabled: Boolean, focusOnOpen: Boolean = true, onGoLive: () -> Unit): Modifier {
    val shortcuts = LocalShortcuts.current
    val requester = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    val isEnabled by rememberUpdatedState(enabled)
    val goLive by rememberUpdatedState(onGoLive)
    LaunchedEffect(requester) { if (focusOnOpen) requester.requestFocus() }
    return this
        .pointerInput(requester) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) requester.requestFocus()
                }
            }
        }
        .focusRequester(requester)
        .onFocusChanged { rootFocused = it.isFocused }
        .focusable()
        .onKeyEvent { event -> shortcuts.handleGoLiveKey(event, rootFocused, isEnabled, goLive) }
}

/**
 * The Go Live key for a tab with key handling of its own: true when [event] is that key on the tab
 * root ([rootFocused] -- the root's own focus state, not a child's), having gone live if [enabled].
 * Claimed even when disabled, so it does nothing else on the tab.
 */
fun ShortcutMap.handleGoLiveKey(
    event: KeyEvent,
    rootFocused: Boolean,
    enabled: Boolean,
    onGoLive: () -> Unit,
): Boolean {
    val claimed = event.type == KeyEventType.KeyDown && rootFocused && matches(ShortcutAction.GO_LIVE, event)
    if (claimed && enabled) onGoLive()
    return claimed
}
