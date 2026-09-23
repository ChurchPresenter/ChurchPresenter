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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.lottiegen.band.LocalBandColorField
import org.churchpresenter.lottiegen.ui.components.ColorPickerDialog
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.lottiegen.ui.components.LottieSlider
import org.churchpresenter.lottiegen.ui.components.LottieCheckbox
import org.churchpresenter.lottiegen.ui.components.raisedKey
import org.churchpresenter.theme.elevationPalette

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

/** A colour control: the host's own field when the generator is inside the app, [OwnColorField] otherwise. */
@Composable
internal fun HexField(label: String, color: String, onColorChange: (String) -> Unit, modifier: Modifier = Modifier) {
    LocalBandColorField.current(label, color, onColorChange, modifier.width(HEX_FIELD_WIDTH))
}

/**
 * The generator's own colour field, for the standalone window: the caption, the hex typed in
 * place and its swatch on the right, which opens the tool's picker. The text is handed on only
 * once it is six hex digits, so a half-typed value never reaches the file.
 */
@Composable
internal fun OwnColorField(label: String, color: String, onColorChange: (String) -> Unit, modifier: Modifier) {
    var text by remember(color) { mutableStateOf(color) }
    var showPicker by remember { mutableStateOf(false) }
    if (showPicker) {
        ColorPickerDialog(
            initialHex = color,
            onDismiss = { showPicker = false },
            onColorSelected = { onColorChange(it) },
        )
    }
    SettingsTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            normalizeHex(typed)?.let(onColorChange)
        },
        label = label,
        modifier = modifier,
        fillWidth = true,
        trailingIcon = {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(parseBandHex(color) ?: Color.Transparent)
                    .border(1.dp, Tokens.BorderHover, RoundedCornerShape(3.dp))
                    .clickable { showPicker = true },
            )
        },
    )
}

internal val HEX_FIELD_WIDTH = 128.dp

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

/** The 17dp square check with its label -- the main generator's own [LottieCheckbox], so the two windows share one. */
@Composable
internal fun BandCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    LottieCheckbox(label, checked, onCheckedChange, Modifier.fillMaxWidth())
}

/** The pane's main action: the raised accent key, as the main generator's [AccentButton] draws it. */
@Composable
internal fun AccentAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fill = elevationPalette().accent
    Box(
        modifier = modifier
            .height(42.dp)
            .raisedKey(MENU_SHAPE, fill, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fill.ink, maxLines = 1)
    }
}
