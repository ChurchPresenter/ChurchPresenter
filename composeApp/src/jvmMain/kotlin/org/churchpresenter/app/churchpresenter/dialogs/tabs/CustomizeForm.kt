package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.PositionButtons
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField

/**
 * The compact settings form the per-output Customize dialog is built from.
 *
 * Deliberately not the layout the global settings tabs use. Those spread every field across two
 * wide columns because they configure the whole install; this one sits in a dialog pane beside a
 * category rail and shows the handful of things worth varying from one screen to the next, as a
 * label column and a control column under small group headings.
 *
 * Colours come from the theme rather than from the mockup this follows: group headings take the
 * tertiary accent, selection takes primary, and controls take the outline and surface roles. The
 * app ships nine themes and a light mode, and a hard-coded palette would be right in exactly one.
 */

private val LABEL_COLUMN = 118.dp
private val CHOICE_HEIGHT = 28.dp
private val NUMBER_FIELD_WIDTH = 104.dp

/** A titled group of rows: an uppercase accent heading with a rule down its left edge. */
@Composable
internal fun CustomizeGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.9.sp,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        content()
    }
}

/** One labelled row: a fixed label column, then whatever controls the setting needs. */
@Composable
internal fun CustomizeRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(LABEL_COLUMN),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            content = { control() },
        )
    }
}

/**
 * A number in the boxed, unit-captioned field the settings tabs already use, and an optional Auto
 * checkbox beside it.
 *
 * The same `NumberSettingsTextField` the Bible tab's sizes and margins are typed into, rather than a
 * form control of this dialog's own — one numeric field across the app means one set of habits, and
 * the caption is where the unit goes so the row's label can stay the name of the setting.
 */
@Composable
internal fun NumberControl(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    unit: String = "",
    autoLabel: String? = null,
    auto: Boolean = false,
    onAutoChange: (Boolean) -> Unit = {},
) {
    NumberSettingsTextField(
        label = unit,
        initialText = value,
        onValueChange = onValueChange,
        range = range,
        modifier = Modifier.width(NUMBER_FIELD_WIDTH),
    )
    if (autoLabel != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.clickable { onAutoChange(!auto) },
        ) {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (auto) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (auto) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(4.dp),
                    ),
            )
            Text(
                text = autoLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A colour swatch and its hex. */
@Composable
internal fun ColorControl(color: String, onColorChange: (String) -> Unit) {
    ColorPickerField(color = color, onColorChange = onColorChange, modifier = Modifier.width(132.dp))
}

/** Left / center / right, drawn with the icon buttons every other settings tab uses. */
@Composable
internal fun HorizontalAlignControl(selected: String, onSelect: (String) -> Unit) {
    HorizontalAlignmentButtons(
        selectedAlignment = selected,
        onAlignmentChange = onSelect,
        leftValue = Constants.LEFT,
        centerValue = Constants.CENTER,
        rightValue = Constants.RIGHT,
        buttonSize = CHOICE_HEIGHT,
    )
}

/** Top / middle / bottom, the vertical twin of [HorizontalAlignControl]. */
@Composable
internal fun VerticalAlignControl(selected: String, onSelect: (String) -> Unit) {
    VerticalAlignmentButtons(
        selectedAlignment = selected,
        onAlignmentChange = onSelect,
        topValue = Constants.TOP,
        middleValue = Constants.MIDDLE,
        bottomValue = Constants.BOTTOM,
        buttonSize = CHOICE_HEIGHT,
    )
}

/** Above / below, for the things that sit either side of the text they belong to. */
@Composable
internal fun PositionControl(
    selected: String,
    aboveValue: String,
    belowValue: String,
    onSelect: (String) -> Unit,
) {
    PositionButtons(
        selectedPosition = selected,
        onPositionChange = onSelect,
        aboveValue = aboveValue,
        belowValue = belowValue,
        buttonSize = CHOICE_HEIGHT,
    )
}

/** A row of mutually exclusive choices — text transforms, show-on-page modes. */
@Composable
internal fun ChoiceControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 30.dp)
                    .height(CHOICE_HEIGHT)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The bold/italic/underline/shadow quartet, each drawn in the face it applies. */
@Suppress("LongParameterList")
@Composable
internal fun StyleControl(
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    shadow: Boolean,
    shadowLabel: String,
    onBoldChange: (Boolean) -> Unit,
    onItalicChange: (Boolean) -> Unit,
    onUnderlineChange: (Boolean) -> Unit,
    onShadowChange: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        StyleToggle("B", bold, FontWeight.Bold, null, null, onBoldChange)
        StyleToggle("I", italic, null, FontStyle.Italic, null, onItalicChange)
        StyleToggle("U", underline, null, null, TextDecoration.Underline, onUnderlineChange)
        StyleToggle(shadowLabel, shadow, null, null, null, onShadowChange)
    }
}

@Suppress("LongParameterList")
@Composable
private fun StyleToggle(
    glyph: String,
    on: Boolean,
    weight: FontWeight?,
    style: FontStyle?,
    decoration: TextDecoration?,
    onChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 27.dp, height = CHOICE_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(6.dp),
            )
            .clickable { onChange(!on) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = weight,
            fontStyle = style,
            textDecoration = decoration,
            color = if (on) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** An on/off switch for a form row. */
@Composable
internal fun ToggleControl(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}

/** A font picker, kept narrow enough to leave the label column intact. */
@Composable
internal fun FontControl(value: String, fonts: List<String>, onValueChange: (String) -> Unit) {
    FontSettingsDropdown(
        value = value,
        fonts = fonts,
        onValueChange = onValueChange,
        modifier = Modifier.width(190.dp),
        fillWidth = true,
    )
}
