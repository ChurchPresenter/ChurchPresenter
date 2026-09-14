package org.churchpresenter.lottiegen.band.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.lottiegen.ui.components.ColorPickerDialog
import org.churchpresenter.lottiegen.ui.components.LottieSlider

/*
 * The band generator's own chrome: the small pieces every section is built from, drawn to the
 * v2 reference — flat, thin, captioned — over the shared palette so the light theme follows.
 */

private const val HEX_LENGTH = 6
private const val HEX_RADIX = 16
private const val OPAQUE = 0xFF000000
private const val CAPTION_TRACKING = 0.1f
internal val FIELD_SHAPE = RoundedCornerShape(7.dp)
internal val CARD_SHAPE = RoundedCornerShape(9.dp)
internal val MENU_SHAPE = RoundedCornerShape(10.dp)
internal val FIELD_HEIGHT = 29.dp

/** The tiny uppercase heading over a group of controls. */
@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (9.5f * CAPTION_TRACKING).sp,
        color = Tokens.HintText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The one-pixel rule between groups. */
@Composable
internal fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Tokens.Divider))
}

/** A slider with its name, value and unit on the line above a thin track. */
@Composable
internal fun ThinSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    unit: String = "",
    modifier: Modifier = Modifier,
    fill: Color? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                label, fontSize = 11.sp, color = Tokens.LabelText, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                format(value), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Tokens.PrimaryText,
                maxLines = 1,
            )
            if (unit.isNotEmpty()) Text(unit, fontSize = 9.sp, color = Tokens.HintText, maxLines = 1)
        }
        LottieSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            trackHeight = 4.dp,
            knobSize = 11.dp,
            fillBrush = fill?.let { SolidColor(it) },
        )
    }
}

/** A slider with its name to the left and the value to the right, all on one line. */
@Composable
internal fun InlineSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    unit: String = "",
    labelWidth: Dp = 42.dp,
    swatch: Color? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        if (swatch != null) Box(Modifier.size(13.dp).clip(RoundedCornerShape(4.dp)).background(swatch))
        Text(
            label, fontSize = 11.5.sp, color = Tokens.LabelText, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.width(labelWidth),
        )
        LottieSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            trackHeight = 4.dp,
            knobSize = 11.dp,
            fillBrush = swatch?.let { SolidColor(it) },
        )
        Text(
            format(value), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Tokens.PrimaryText,
            textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(28.dp),
        )
        if (unit.isNotEmpty()) Text(unit, fontSize = 9.sp, color = Tokens.HintText, maxLines = 1)
    }
}

/**
 * A colour as a swatch and its hex, typed in place. The swatch opens the picker; the text is
 * handed on only once it is six hex digits, so a half-typed value never reaches the file.
 */
@Composable
internal fun HexField(color: String, onColorChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(color) { mutableStateOf(color) }
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        ColorPickerDialog(
            initialHex = color,
            onDismiss = { showPicker = false },
            onColorSelected = { onColorChange(it) },
        )
    }
    Row(
        modifier = modifier
            .height(FIELD_HEIGHT)
            .clip(FIELD_SHAPE)
            .background(Tokens.FieldBg)
            .border(1.dp, Tokens.FieldBorder, FIELD_SHAPE)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(parseBandHex(color) ?: Color.Transparent)
                .border(1.dp, Tokens.BorderHover, RoundedCornerShape(3.dp))
                .clickable { showPicker = true },
        )
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                text = typed
                normalizeHex(typed)?.let(onColorChange)
            },
            singleLine = true,
            textStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Tokens.HexText),
            cursorBrush = SolidColor(Tokens.Accent),
            modifier = Modifier.weight(1f),
        )
    }
}

/** `#RRGGBB`, upper-cased, from anything six hex digits long with or without its hash; else null. */
internal fun normalizeHex(typed: String): String? {
    val digits = typed.trim().removePrefix("#")
    if (digits.length != HEX_LENGTH || digits.any { it.digitToIntOrNull(HEX_RADIX) == null }) return null
    return "#" + digits.uppercase()
}

internal fun parseBandHex(hex: String): Color? {
    val digits = normalizeHex(hex)?.removePrefix("#") ?: return null
    return Color(digits.toLong(HEX_RADIX) or OPAQUE)
}

/** The 17dp square check with its label. */
@Composable
internal fun BandCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) Tokens.Accent else Tokens.FieldBg)
                .border(1.5.dp, if (checked) Tokens.Accent else Tokens.CheckOffBorder, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check, contentDescription = null, tint = Tokens.OnAccent,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Text(label, fontSize = 12.sp, color = Tokens.OutlineText)
    }
}

/** A captioned box a line of text is typed into. */
@Composable
internal fun CaptionedInput(
    caption: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
    mono: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CARD_SHAPE)
            .background(Tokens.FieldBg)
            .border(1.dp, Tokens.FieldBorder, CARD_SHAPE)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            caption.uppercase(), fontSize = 8.5.sp, lineHeight = 10.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (8.5f * CAPTION_TRACKING).sp, color = Tokens.HintText, maxLines = 1,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = minLines == 1,
            minLines = minLines,
            textStyle = TextStyle(
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Tokens.InputText,
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            ),
            cursorBrush = SolidColor(Tokens.Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The teal filled button the pane's main action is. */
@Composable
internal fun AccentAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(MENU_SHAPE)
            .background(Tokens.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Tokens.OnAccent, maxLines = 1)
    }
}
