package org.churchpresenter.sharedui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Moves the keyboard into a tab's search box from code -- opening the tab, or the search ⇄ live
 * key -- with the whole query selected, so typing replaces it and Backspace clears it.
 *
 * A click into the field is not routed through here and places the caret as usual.
 */
@Stable
class SearchFieldFocus {
    val requester = FocusRequester()
    internal var selectAllSignal by mutableIntStateOf(0)

    /**
     * Asks the field to take the keyboard. The field does it from its own effect rather than here:
     * a search box drawn inside `BoxWithConstraints` is composed during layout, after the effect
     * that opens the tab, so its requester is not attached yet when that effect runs.
     */
    fun focusAndSelectAll() {
        selectAllSignal++
    }
}

/**
 * The search field's value: [query], with the caret at its end whenever the query is changed from
 * outside the field, and all of it selected each time [focus] asks for that.
 *
 * Give the field this value and `Modifier.focusRequester(focus.requester)`, and in its
 * `onValueChange` assign the new value here before reporting the text, so the caret and selection
 * the user makes are kept. This is also where the field takes the keyboard when [focus] asks.
 */
@Composable
fun rememberSearchFieldValue(query: String, focus: SearchFieldFocus): MutableState<TextFieldValue> {
    val state = remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    if (state.value.text != query) state.value = TextFieldValue(query, TextRange(query.length))
    LaunchedEffect(focus.selectAllSignal) {
        if (focus.selectAllSignal > 0) {
            focus.requester.requestFocus()
            state.value = state.value.copy(selection = TextRange(0, state.value.text.length))
        }
    }
    return state
}
