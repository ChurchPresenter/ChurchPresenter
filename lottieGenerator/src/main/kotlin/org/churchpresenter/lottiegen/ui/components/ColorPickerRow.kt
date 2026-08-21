package org.churchpresenter.lottiegen.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.lottiegen.ui.Tokens

/**
 * One colour channel: a name, a chip showing the swatch and hex (click to open the picker),
 * an inline opacity slider, and the numeric alpha.
 *
 * Hex is edited inside [ColorPickerDialog], which carries its own validated hex field — the
 * chip here is a launcher, not an input.
 */
@Composable
fun ColorPickerRow(
    label: String,
    color: String,
    alpha: Int,
    onColorChange: (String) -> Unit,
    onAlphaChange: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val chipBorder by animateColorAsState(
        if (hovered) Tokens.FieldBorderHover else Tokens.FieldBorder,
        label = "colorChipBorder"
    )

    if (showDialog) {
        ColorPickerDialog(
            initialHex = color,
            onDismiss = { showDialog = false },
            onColorSelected = { hex ->
                onColorChange(hex)
                showDialog = false
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            modifier = Modifier.width(78.dp),
            fontSize = 12.sp,
            color = Tokens.LabelText,
            maxLines = 1
        )

        // Swatch + hex chip — opens the picker.
        Row(
            modifier = Modifier
                .height(28.dp)
                .clip(Tokens.ChipShape)
                .background(Tokens.FieldBg)
                .border(1.dp, chipBorder, Tokens.ChipShape)
                .hoverable(interaction)
                .clickable { showDialog = true }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(parseHexColor(color, 100))
                    .border(1.dp, Tokens.BorderHover, RoundedCornerShape(4.dp))
            )
            Text(
                color.uppercase(),
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.23.sp,
                color = Tokens.HexText,
                maxLines = 1
            )
        }

        LottieSlider(
            value = alpha.toFloat(),
            onValueChange = { onAlphaChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f)
        )

        Text(
            "$alpha",
            modifier = Modifier.width(26.dp),
            fontSize = 11.5.sp,
            color = Tokens.ValueText,
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}

private fun parseHexColor(hex: String, alpha: Int): Color {
    return try {
        val clean = hex.removePrefix("#")
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        Color(r, g, b, (alpha * 255 / 100))
    } catch (_: Exception) {
        Color.White
    }
}
