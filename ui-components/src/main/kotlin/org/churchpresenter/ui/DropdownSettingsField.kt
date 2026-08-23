package org.churchpresenter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.ic_arrow_down
import org.jetbrains.compose.resources.painterResource

/** The style the field draws its current value in. Shared with [rememberDropdownWidthFor] so a width
 *  measured for a set of options matches the text that will actually be drawn in it. */
private val dropdownValueStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    )

/** Everything in the field that is not the value text: start and end padding, the gap before the
 *  chevron, and the chevron itself. */
private val DROPDOWN_CHROME_WIDTH = 11.dp + 11.dp + 4.dp + 14.dp

/**
 * A width that fits the widest of [options], clamped between [min] and [max].
 *
 * For a column of fields that should line up as a block rather than each sizing to whatever value it
 * happens to hold — without the longest option being free to push whatever sits beside it off the
 * panel. Anything wider than [max] ellipsizes inside the field.
 */
@Composable
fun rememberDropdownWidthFor(options: List<String>, min: Dp = 160.dp, max: Dp = 280.dp): Dp {
    val measurer = rememberTextMeasurer()
    val style = dropdownValueStyle
    val density = LocalDensity.current
    return remember(options, style, density, min, max) {
        val widest = options.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        (with(density) { widest.toDp() } + DROPDOWN_CHROME_WIDTH).coerceIn(min, max)
    }
}

@Composable
fun DropdownSettingsField(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
    /**
     * Fixes the field's width instead of letting it hug its value.
     *
     * Set it when a column of fields should line up as a block — see [rememberDropdownWidthFor]. The
     * chevron then sits against the right edge rather than trailing the text halfway across the
     * field, and an over-long value ellipsizes instead of being cut off by the edge.
     */
    width: Dp? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .heightIn(min = 42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { expanded = true }
            .padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Only stretch when a width was imposed: with none, filling would resolve against the
        // parent's constraints and blow the field out to the full width of whatever contains it.
        Row(
            modifier = if (width != null) Modifier.fillMaxWidth() else Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = if (width != null) Modifier.weight(1f) else Modifier,
                verticalArrangement = Arrangement.Center,
            ) {
                if (label.isNotEmpty()) {
                    Text(
                        text = label.uppercase(),
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(1.dp))
                }
                Text(
                    text = value,
                    style = dropdownValueStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Clip rather than grow: an over-long value used to widen the whole field and
                    // shove anything sharing its row toward the edge of the panel.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
