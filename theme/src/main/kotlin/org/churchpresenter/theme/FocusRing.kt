package org.churchpresenter.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp

/**
 * The keyboard focus ring: a [RING_WIDTH] line in the theme's primary, [FOCUS_RING_GAP] outside
 * [shape], while the focusable that follows this in the modifier chain holds focus that came from
 * the keyboard. Outside the control and clear of it, so it shows on a primary key as well as a
 * neutral one; never after a mouse click, which would leave a ring on every key pressed. [raised]
 * applies it itself; a control that draws a key only under the pointer puts it before its
 * `clickable`.
 *
 * Shown only once Tab has been used in the window ([FocusVisibility]), never at launch or after a
 * pointer press until Tab is used again.
 *
 * [includeChildren] rings a container while focus is anywhere inside it -- a search well around its
 * text field. [inset] draws the ring just inside the bounds instead, for a region as large as a
 * tab's body, whose outside edge would be covered by what sits next to it.
 */
fun Modifier.keyboardFocusRing(
    shape: Shape,
    includeChildren: Boolean = false,
    inset: Boolean = false,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val inputMode = LocalInputModeManager.current.inputMode
    val keyboard = inputMode == InputMode.Keyboard
    val visibility = LocalFocusVisibility.current
    // A pointer press puts the window back to showing no rings until Tab is used again.
    if (visibility != null) LaunchedEffect(keyboard) { if (!keyboard) visibility.afterTab = false }
    val color = MaterialTheme.colorScheme.primary
    this
        .onPreviewKeyEvent { event ->
            if (visibility != null && event.key == Key.Tab && event.type == KeyEventType.KeyDown) {
                visibility.afterTab = true
            }
            false
        }
        .onFocusChanged { focused = it.isFocused || (includeChildren && it.hasFocus) }
        .drawWithContent {
            drawContent()
            if (focused && keyboard && (visibility?.afterTab ?: true)) {
                val stroke = RING_WIDTH.toPx()
                val gap = if (inset) -stroke / 2 else FOCUS_RING_GAP.toPx() + stroke / 2
                val ring = Size(size.width + gap * 2, size.height + gap * 2)
                translate(left = -gap, top = -gap) {
                    drawOutline(
                        shape.createOutline(ring, layoutDirection, this),
                        color,
                        style = Stroke(stroke),
                    )
                }
            }
        }
}

/**
 * Whether a window is showing keyboard focus rings: only once Tab or Shift+Tab has been used there,
 * and not again after a pointer press until it is used again -- so a control focused when a window
 * opens (a tab's body, its search box) shows no ring to someone working with the mouse. Provided
 * per window by [ChurchPresenterTheme]; with none provided, a ring follows keyboard input alone.
 */
@Stable
class FocusVisibility {
    var afterTab by mutableStateOf(false)
}

/** The window's [FocusVisibility]; null outside [ChurchPresenterTheme]. */
val LocalFocusVisibility = staticCompositionLocalOf<FocusVisibility?> { null }

/** Marks Tab and Shift+Tab as used, for a window root that sees every key before its controls. */
fun Modifier.noteTabNavigation(visibility: FocusVisibility?): Modifier = onPreviewKeyEvent { event ->
    if (visibility != null && event.key == Key.Tab && event.type == KeyEventType.KeyDown) visibility.afterTab = true
    false
}

private val FOCUS_RING_GAP = 2.dp
