package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.decrement
import org.churchpresenter.strings.generated.resources.increment
import org.churchpresenter.sharedui.composables.ColorPickerField
import org.churchpresenter.sharedui.composables.FontSettingsDropdown
import org.churchpresenter.sharedui.composables.SegmentedButton
import org.churchpresenter.sharedui.composables.SegmentedButtonItem
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyIconButton
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.stringResource

/**
 * The controls a [SettingsRow] carries, each sized for a 46dp row: the app's own segmented control,
 * switch, colour field and font picker at a compact height, and a number stepper.
 *
 * Thin by design -- every one of them is the control the settings tabs already use, so a value set
 * here and the same value set anywhere else in the app is set with the same habit.
 */


private val CONTROL_HEIGHT = 30.dp
private val FIELD_HEIGHT = 34.dp
private const val SEGMENT_CHAR_WIDTH = 7f
private const val SEGMENT_PADDING = 22f
private const val SEGMENT_MIN_WIDTH = 40f

/**
 * One choice from a short closed list, each segment as wide as its own label -- the selected one the
 * white raised key.
 *
 * A list wider than the room it is given continues on another line rather than running off the
 * card: the lines are still one choice, as the old form's long lists were.
 */
@Composable
internal fun <T> RowSegmented(
    options: List<RowOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    /** Smaller type and tighter segments, for a list that should stay on one line in a narrow strip. */
    compact: Boolean = false,
) {
    if (options.isEmpty()) return
    val allowance = if (compact) COMPACT_TRACK_ALLOWANCE else SEGMENT_TRACK_ALLOWANCE
    val type = MaterialTheme.typography
    val fontSize = if (compact) type.labelSmall.fontSize else type.labelMedium.fontSize
    // The compact row measures its labels rather than estimating them from a letter count, since
    // it is sized to fit and a wide word ("Number") would otherwise be cut short. Measured in the
    // style the segment draws them in -- labelLarge, bold -- so a label is never narrower here than
    // on screen: one that does not fit is ellipsized, and Skia on Linux can hang ellipsizing a label
    // into a width that almost holds it.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = type.labelLarge.copy(fontSize = fontSize, fontWeight = FontWeight.Bold)
    val width: (String) -> Dp = { label ->
        if (compact) {
            val measured = with(density) { measurer.measure(label, labelStyle).size.width.toDp() }
            maxOf(measured + COMPACT_PADDING, COMPACT_MIN_WIDTH)
        } else {
            segmentWidth(label)
        }
    }
    BoxWithConstraints(modifier = modifier) {
        val room = if (compact) maxWidth - COMPACT_TRACK_ENDS else maxWidth
        val lines = segmentLines(options.map { width(it.label) + allowance }, room)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
            var start = 0
            lines.forEach { count ->
                val line = options.subList(start, start + count)
                start += count
                SegmentedButton(
                    items = line.map { option ->
                        SegmentedButtonItem(
                            value = option.value,
                            label = option.label,
                            testTag = option.testTag,
                            width = width(option.label),
                        )
                    },
                    selectedValue = selected,
                    onValueChange = onSelect,
                    buttonHeight = CONTROL_HEIGHT,
                    fontSize = fontSize,
                )
            }
        }
    }
}

/** What the track adds around each segment: its inset and the gap to the next. */
private val SEGMENT_TRACK_ALLOWANCE = 12.dp

/** How many of [widths] go on each line so that no line is wider than [available]. */
internal fun segmentLines(widths: List<Dp>, available: Dp): List<Int> {
    val lines = mutableListOf<Int>()
    var count = 0
    var used = 0.dp
    widths.forEach { w ->
        if (count > 0 && used + w > available) {
            lines += count
            count = 0
            used = 0.dp
        }
        count++
        used += w
    }
    if (count > 0) lines += count
    return lines
}

/** The width a segment needs for [label] at the row's type size. */
internal fun segmentWidth(label: String): Dp =
    (label.length * SEGMENT_CHAR_WIDTH + SEGMENT_PADDING).coerceAtLeast(SEGMENT_MIN_WIDTH).dp

/**
 * Room around a compact segment's measured label -- the segment's own 4dp padding each side and a
 * few dp so rounding never leaves it a hair short -- and the least it is given.
 *
 * Four spare, not eight: the Songs strip's six elements fit one line on Linux's wider text only
 * with the difference, and a label measured Bold at its own size needs no more than rounding.
 */
private val COMPACT_PADDING = 12.dp
private val COMPACT_MIN_WIDTH = 32.dp

/**
 * What the track adds beside each compact segment: the 9dp gap to the next. Anything less and the
 * planned line is wider than the track draws it, so the row squeezes its last segment -- and on Linux
 * Skia can hang ellipsizing a label into what is left.
 */
private val COMPACT_TRACK_ALLOWANCE = 9.dp

/** The track's inset at both ends of a line, which a compact line's plan must leave room for. */
private val COMPACT_TRACK_ENDS = 6.dp

/** An on/off setting: the app's raised switch, slate when on. */
@Composable
internal fun RowSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    RaisedSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
}

/** A colour: the swatch-and-hex field, at the row's height. */
@Composable
internal fun RowColor(color: String, onColorChange: (String) -> Unit, modifier: Modifier = Modifier) {
    ColorPickerField(
        color = color,
        onColorChange = onColorChange,
        modifier = modifier.width(ROW_COLOR_WIDTH).height(FIELD_HEIGHT),
    )
}

private val ROW_COLOR_WIDTH = 132.dp
private val ROW_FONT_WIDTH = 190.dp

/** A typeface: the app's font picker, each name drawn in its own face. */
@Composable
internal fun RowFont(value: String, fonts: List<String>, onValueChange: (String) -> Unit) {
    FontSettingsDropdown(
        value = value,
        fonts = fonts,
        onValueChange = onValueChange,
        modifier = Modifier.width(ROW_FONT_WIDTH).height(FIELD_HEIGHT),
        fillWidth = true,
    )
}

/**
 * A whole number: a typed field with its unit beside it, and − / + keys stepping by [step].
 *
 * A typed value outside [range] is shown with an error edge and not stored; the keys clamp.
 */
@Composable
internal fun RowStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    unit: String? = null,
    fieldWidth: Dp = STEPPER_FIELD_WIDTH,
    testTag: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        RowNumberField(value, onValueChange, range, unit = unit, width = fieldWidth, testTag = testTag)
        KeyIconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.size(STEP_KEY_WIDTH, CONTROL_HEIGHT),
        ) {
            Icon(Icons.Filled.Remove, stringResource(Res.string.decrement), Modifier.size(STEP_ICON))
        }
        KeyIconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.size(STEP_KEY_WIDTH, CONTROL_HEIGHT),
        ) {
            Icon(Icons.Filled.Add, stringResource(Res.string.increment), Modifier.size(STEP_ICON))
        }
    }
}

private val STEPPER_FIELD_WIDTH = 66.dp
private val STEP_KEY_WIDTH = 26.dp
private val STEP_ICON = 14.dp

/**
 * A typed whole number in a small well, with its unit after it and, optionally, a caption above --
 * the four margins are four of these side by side.
 */
@Composable
internal fun RowNumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    width: Dp = STEPPER_FIELD_WIDTH,
    testTag: String? = null,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val error = text.toIntOrNull()?.let { it !in range } ?: true
    val faint = profilesPalette().faintText
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (caption != null) {
            Text(caption, fontSize = 10.sp, color = faint, maxLines = 1)
        }
        Row(
            modifier = Modifier
                .width(width)
                .height(CONTROL_HEIGHT)
                .sunken(
                    AppShape(7.dp),
                    elevationPalette(),
                    rim = if (error) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { typed ->
                    val digits = typed.filter { it.isDigit() || it == '-' }
                    text = digits
                    digits.toIntOrNull()?.takeIf { it in range }?.let(onValueChange)
                },
                singleLine = true,
                interactionSource = remember { MutableInteractionSource() },
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            )
            if (unit != null) {
                Box(Modifier.padding(start = 3.dp)) {
                    Text(unit, fontSize = 11.sp, color = faint, maxLines = 1)
                }
            }
        }
    }
}
