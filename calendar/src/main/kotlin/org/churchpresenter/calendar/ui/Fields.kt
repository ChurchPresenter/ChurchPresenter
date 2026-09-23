package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette

/**
 * The flat, compact text input this window is drawn with.
 *
 * **Not** Material 3's `OutlinedTextField`, and that is the point: M3's field is about 56dp tall
 * with a floating label and its own internal padding, which in a dense planner makes a one-line
 * name entry taller than the row of chips beside it — the panes stop lining up and the dialogs read
 * as a form rather than as the design. This is what the design actually specifies: roughly 30dp
 * high, one sunken well like every other field in the app, the label sitting above it as separate
 * text.
 *
 * [errorBorder] draws the invalid state without reserving the space M3 keeps for supporting text,
 * so a field that becomes invalid does not shift everything under it.
 */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    height: Dp = FIELD_HEIGHT,
    fontSize: Float = FIELD_FONT,
    textAlign: TextAlign = TextAlign.Start,
    errorBorder: Boolean = false,
    focused: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(FIELD_RADIUS)
    val rim = when {
        errorBorder -> scheme.error
        focused -> scheme.primary
        else -> Color.Unspecified
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(height)
            .sunken(shape, elevationPalette(), rim = rim)
            .padding(horizontal = 10.dp),
    ) {
        if (leading != null) {
            leading()
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize.sp),
                    color = scheme.onSurfaceVariant.copy(alpha = PLACEHOLDER_ALPHA),
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSize.sp,
                    color = scheme.onSurface,
                    textAlign = textAlign,
                ),
                cursorBrush = SolidColor(scheme.onSurface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The caption above a field.
 *
 * Uppercase, heavy and letter-spaced — the design's own spec (`9.5px/800/0.09em`), the same
 * treatment it gives `RUN OF SHOW` and `SERVICE TYPES`. Sentence-case body text here is what made
 * the sheets read as a web form rather than as this window.
 */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.85.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        modifier = modifier,
    )
}

/**
 * Commits an in-place edit when the field is left, or when Enter is pressed.
 *
 * Two things this gets right that the obvious version does not:
 *
 * - **The first unfocused callback is ignored.** `onFocusChanged` fires once with `isFocused=false`
 *   as a field attaches, before anything has been typed or focused. Acting on it commits — or, with
 *   an open editor, closes the field the instant it opens.
 * - **Nothing is written per keystroke.** Every commit here rewrites `calendar.json`; half of
 *   `10:00` is `10:`, which is not a time, so intermediate states must not be stored either.
 *
 * [hasChanges] keeps a field that was merely tabbed through from writing anything.
 */
fun Modifier.commitOnExit(hasChanges: Boolean, onCommit: () -> Unit): Modifier = composed {
    var everFocused by remember { mutableStateOf(false) }
    val changed by rememberUpdatedState(hasChanges)
    val commit by rememberUpdatedState(onCommit)

    onFocusChanged { focus ->
        if (focus.isFocused) {
            everFocused = true
        } else if (everFocused && changed) {
            commit()
        }
    }.onPreviewKeyEvent { event ->
        val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
        if (event.type == KeyEventType.KeyDown && isEnter) {
            if (changed) commit()
            true
        } else {
            false
        }
    }
}

private val FIELD_HEIGHT = 34.dp
private val FIELD_RADIUS = 8.dp
private const val FIELD_FONT = 13f
private const val PLACEHOLDER_ALPHA = 0.55f
