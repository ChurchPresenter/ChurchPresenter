package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle

/**
 * A checkbox, radio button or switch together with its label, where **the label is part of the
 * control**.
 *
 * The pattern these replace — a bare `Text` sitting next to a `Checkbox` inside a plain `Row` — looks
 * identical on screen but only the small square is clickable. Everyone aims at the word, which is the
 * larger target and the one that reads as the thing being chosen, and nothing happens. It is also
 * inaccessible: with no association between the two, a screen reader announces an unlabelled checkbox
 * and a stray line of text.
 *
 * The fix is Material 3's own recommendation: put `toggleable`/`selectable` on the row with the right
 * [Role], and pass `null` as the control's own callback so the row owns the single click. That gives
 * one click target covering control and label, one semantics node, and a label that is announced with
 * its state.
 *
 * `enabled` propagates to both halves, so a disabled row is inert *and* reports itself disabled rather
 * than silently ignoring presses.
 *
 * The remaining parameters exist so migrated call sites keep the look they had: [color] and [spacing]
 * carry the label colour and the gap the old hand-rolled `Row` set, [supporting] is the quieter second
 * line the settings rows use, and [controlAtEnd] keeps the layout where the label sits left and the
 * control is pushed to the right edge. **[controlModifier] is the control's own modifier** — a `size`
 * or `testTag` belongs there, not on [modifier], which is the whole row.
 */

@Composable
fun LabeledCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    supporting: String? = null,
    controlModifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    controlAtEnd: Boolean = false,
) {
    Row(
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = onCheckedChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        // null: the row above already handles the click, and a control with its own handler would
        // publish a second, competing click target inside the first.
        if (controlAtEnd) {
            LabelText(label, supporting, style, color, Modifier.weight(1f))
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = controlModifier)
        } else {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = controlModifier)
            LabelText(label, supporting, style, color)
        }
    }
}

@Composable
fun LabeledRadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    supporting: String? = null,
    controlModifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    controlAtEnd: Boolean = false,
) {
    Row(
        modifier = modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (controlAtEnd) {
            LabelText(label, supporting, style, color, Modifier.weight(1f))
            RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = controlModifier)
        } else {
            RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = controlModifier)
            LabelText(label, supporting, style, color)
        }
    }
}

@Composable
fun LabeledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    supporting: String? = null,
    controlModifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    controlAtEnd: Boolean = false,
) {
    Row(
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (controlAtEnd) {
            LabelText(label, supporting, style, color, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = controlModifier)
        } else {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = controlModifier)
            LabelText(label, supporting, style, color)
        }
    }
}

/** A control's label: one line, or a title with a quieter description under it. */
@Composable
private fun LabelText(
    label: String,
    supporting: String?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (supporting == null) {
        Text(text = label, style = style, color = color, modifier = modifier)
    } else {
        Column(modifier = modifier) {
            Text(text = label, style = style, color = color)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A small caption and the control it names, held on one line.
 *
 * For a caption inside a `FlowRow`: flowed as two children the pair splits across the wrap, leaving
 * a label stranded at the end of one line and its control at the start of the next. Wrapped like
 * this the pair is one item and moves together.
 */
@Composable
fun LabeledControl(label: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LABELED_CONTROL_GAP),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** The gap between a caption and its control, matching the rows [LabeledControl] was pulled from. */
private val LABELED_CONTROL_GAP = 10.dp
