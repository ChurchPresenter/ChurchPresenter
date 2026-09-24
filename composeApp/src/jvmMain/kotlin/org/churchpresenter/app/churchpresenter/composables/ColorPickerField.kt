package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.sunken
import kotlin.math.roundToInt

private const val CHECKERBOARD_COLOR = 0xFFCCCCCC
private const val CHECKER_CELLS = 4
private const val HEX_ARGB_DIGITS = 8
private const val HEX_RGB_DIGITS = 6
private const val PERCENT = 100
private const val OPACITY_TEXT_ALPHA = 0.7f
private val SWATCH_SIZE = 18.dp

@Composable
fun ColorPickerField(
    color: String,
    onColorChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
) {
    val currentColor = remember(color) { parseHexColor(color) }
    val isTransparent = color.equals("transparent", ignoreCase = true) || currentColor == Color.Transparent
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        ColorPickerDialog(
            initialHex = color,
            onDismiss = { showDialog = false },
            onColorSelected = { hex ->
                onColorChange(hex)
                showDialog = false
            },
        )
    }

    Column(
        modifier = modifier
            .heightIn(min = 42.dp)
            // The same sunken well as every field it sits beside in a settings form.
            .sunken(RoundedCornerShape(8.dp), elevationPalette())
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showDialog = true }
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label.uppercase(),
                fontSize = 10.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // A checkerboard under the color, so a translucent one shows its opacity rather than
            // reading as the solid color it is not.
            Box(
                modifier = Modifier
                    .size(SWATCH_SIZE)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val sq = size.width / CHECKER_CELLS
                    drawRect(Color.White)
                    for (row in 0 until CHECKER_CELLS) for (col in 0 until CHECKER_CELLS) {
                        if ((row + col) % 2 == 1) {
                            drawRect(
                                Color(CHECKERBOARD_COLOR),
                                topLeft = Offset(col * sq, row * sq),
                                size = Size(sq, sq)
                            )
                        }
                    }
                    if (!isTransparent) drawRect(currentColor)
                }
            }
            val opacity = displayedOpacity(color, currentColor, isTransparent)
            Text(
                text = if (opacity == null) color else "#" + color.removePrefix("#").takeLast(HEX_RGB_DIGITS),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // No weight(1f) here: this Row has no fillMaxWidth of its own, so a weighted
                // child makes the whole field claim as much width as its parent allows — fine
                // when the caller bounds it with fillMaxWidth()/weight(), but inside a FlowRow
                // with only a widthIn(min=) floor this reported an unbounded "wanted" width and
                // made siblings wrap prematurely even with visible room left. maxLines+ellipsis
                // below already handles the rare long-value case without needing to expand.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (opacity != null) {
                Text(
                    text = "$opacity%",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = OPACITY_TEXT_ALPHA),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The opacity, in percent, of a stored `#AARRGGBB` color that is not fully opaque; else null. */
private fun displayedOpacity(hex: String, color: Color, isTransparent: Boolean): Int? {
    val digits = hex.removePrefix("#")
    if (isTransparent || digits.length != HEX_ARGB_DIGITS || color.alpha >= 1f) return null
    return (color.alpha * PERCENT).roundToInt()
}
