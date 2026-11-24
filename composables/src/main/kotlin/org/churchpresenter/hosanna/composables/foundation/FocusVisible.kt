package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import org.churchpresenter.theme.LocalFocusVisibility

/**
 * Whether a control's focus should show: it has focus, the keyboard is what is being used, and Tab
 * has been used in the window since the last pointer press — the app's own rule for focus rings
 * ([org.churchpresenter.theme.FocusVisibility]). A control focused by a mouse click shows no focus
 * look. Controls typed into keep [collectIsFocusedAsState]: their focus is where the typing goes.
 */
@Composable
internal fun InteractionSource.collectIsFocusVisibleAsState(): State<Boolean> {
    val focused = collectIsFocusedAsState()
    val inputMode = LocalInputModeManager.current
    val visibility = LocalFocusVisibility.current
    return remember(focused, inputMode, visibility) {
        derivedStateOf {
            focused.value && inputMode.inputMode == InputMode.Keyboard && (visibility?.afterTab ?: true)
        }
    }
}
