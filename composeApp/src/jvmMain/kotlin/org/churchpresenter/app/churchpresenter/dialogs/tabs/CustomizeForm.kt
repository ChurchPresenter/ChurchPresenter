package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.PositionButtons
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.CompositionLocalProvider
import org.churchpresenter.app.churchpresenter.composables.LocalSegmentedButtonTone
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonTone
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import kotlin.math.roundToInt

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

private val CHOICE_HEIGHT = 28.dp
/** The height of a text field, so a shorter control centres against one beside it. */
private val CONTROL_ROW_HEIGHT = 42.dp
private const val CHOICE_CHAR_WIDTH = 7f
private const val CHOICE_PADDING = 16f
private val CHOICE_MIN_WIDTH = 44.dp
private val FONT_FIELD_WIDTH = 190.dp
private val COLOR_FIELD_WIDTH = 132.dp
private val NUMBER_FIELD_WIDTH = 104.dp
private const val NUMBER_CHAR_WIDTH = 7f
private const val NUMBER_PADDING = 26f
private val SLIDER_WIDTH = 190.dp

/** A titled group of rows: an uppercase accent heading with a rule down its left edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomizeGroup(label: String, content: @Composable FlowRowScope.() -> Unit) {
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
        // Flowed, not stacked: these are small fields, and one per row left most of the dialog's
        // width empty and pushed the group below it off the bottom. The tabs pack their controls
        // into rows of cells for the same reason; a flow does it without pairing each one by hand.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * One setting: its caption, then its control — the cell shape `ControlColumn` gives the settings
 * tabs, so a control reads the same in this dialog as it does there.
 *
 * The caption line is kept even when [labelInsideControl] blanks it, so cells sitting beside each
 * other in a flow line their controls up rather than one riding a caption's height above the next.
 */
@Composable
internal fun CustomizeRow(
    label: String,
    /** The control draws [label] itself, as a dropdown and a numeric field do. */
    labelInsideControl: Boolean = false,
    control: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Not clipped to the control's width: a cell is as wide as the wider of its caption and its
        // control, so a long name reads in full instead of being cut to the field beneath it.
        Text(
            text = if (labelInsideControl) "" else label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.heightIn(min = CONTROL_ROW_HEIGHT),
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
 * the caption inside the field is where the setting's name goes, exactly as it does there.
 */
@Composable
internal fun NumberControl(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    autoLabel: String? = null,
    auto: Boolean = false,
    onAutoChange: (Boolean) -> Unit = {},
) {
    // Widened to fit its own caption when the caption is long — the label is drawn inside the box,
    // so a fixed width clipped "Intensity (%)" to "Intensit…".
    val width = (label.length * NUMBER_CHAR_WIDTH + NUMBER_PADDING)
        .coerceAtLeast(NUMBER_FIELD_WIDTH.value).dp
    NumberSettingsTextField(
        label = label,
        initialText = value,
        onValueChange = onValueChange,
        range = range,
        modifier = Modifier.width(width),
    )
    if (autoLabel != null) {
        // The Song tab's Auto-fit box, not a drawn square of this dialog's own.
        LabeledCheckbox(
            checked = auto,
            onCheckedChange = onAutoChange,
            label = autoLabel,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** A colour swatch and its hex. */
@Composable
internal fun ColorControl(label: String, color: String, onColorChange: (String) -> Unit) {
    ColorPickerField(
        label = label,
        color = color,
        onColorChange = onColorChange,
        modifier = Modifier.width(COLOR_FIELD_WIDTH),
    )
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
    )
}

/**
 * A whole-pixel slider reading out its own value, the way the settings tabs draw spacing.
 *
 * A slider rather than a typed field because these are nudged until the line looks right rather
 * than set to a number anyone knows in advance — which is the reason the Bible and Song tabs draw
 * them this way too.
 */
@Composable
internal fun SliderControl(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    suffix: String,
) {
    SlimSlider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        modifier = Modifier.width(SLIDER_WIDTH),
        trailingLabel = "$value$suffix",
    )
}

/**
 * A row of mutually exclusive choices — the same [SegmentedButton] the settings tabs pick a text
 * transform or a display mode with, rather than a pill of this dialog's own.
 */
@Composable
internal fun ChoiceControl(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    // Sized to the longest label rather than a fixed width: `SegmentedButton` gives every segment
    // the same width and clips at one line, so a fixed width cut "Transparent" and "Video Loop"
    // off mid-word in the background list.
    val width = (options.maxOf { it.second.length } * CHOICE_CHAR_WIDTH + CHOICE_PADDING)
        .coerceAtLeast(CHOICE_MIN_WIDTH.value).dp
    CompositionLocalProvider(LocalSegmentedButtonTone provides SegmentedButtonTone.ACCENT) {
        SegmentedButton(
            items = options.map { (value, label) -> SegmentedButtonItem(value, label) },
            selectedValue = selected,
            onValueChange = onSelect,
            buttonWidth = width,
            buttonHeight = CHOICE_HEIGHT,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
        )
    }
}

/**
 * The bold/italic/underline/shadow quartet — the shared [TextStyleButtons] the Song tab and the
 * canvas editors draw, so the buttons match wherever text is styled.
 */
@Suppress("LongParameterList")
@Composable
internal fun StyleControl(
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    shadow: Boolean,
    onBoldChange: (Boolean) -> Unit,
    onItalicChange: (Boolean) -> Unit,
    onUnderlineChange: (Boolean) -> Unit,
    onShadowChange: (Boolean) -> Unit,
) {
    TextStyleButtons(
        bold = bold,
        italic = italic,
        underline = underline,
        shadow = shadow,
        onBoldChange = onBoldChange,
        onItalicChange = onItalicChange,
        onUnderlineChange = onUnderlineChange,
        onShadowChange = onShadowChange,
        buttonSize = CHOICE_HEIGHT,
    )
}

/** An on/off setting, drawn as the [LabeledCheckbox] every settings tab uses for a boolean. */
@Composable
internal fun ToggleControl(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    LabeledCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = label,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** The font picker, carrying its own label the way it does on every settings tab. */
@Composable
internal fun FontControl(
    label: String,
    value: String,
    fonts: List<String>,
    onValueChange: (String) -> Unit,
) {
    FontSettingsDropdown(
        label = label,
        value = value,
        fonts = fonts,
        onValueChange = onValueChange,
        modifier = Modifier.width(FONT_FIELD_WIDTH),
        fillWidth = true,
    )
}
