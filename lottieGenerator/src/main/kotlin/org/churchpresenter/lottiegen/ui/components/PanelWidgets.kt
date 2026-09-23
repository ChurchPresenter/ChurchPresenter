package org.churchpresenter.lottiegen.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import org.churchpresenter.lottiegen.ui.Tokens
import org.churchpresenter.theme.RaisedFill
import org.churchpresenter.theme.components.SegmentTrack
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.sunken

/** Hover tooltip wrapper for control-panel widgets. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HoverTooltip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        },
        content = content
    )
}

/** The amber used to warn that a field won't render — not a Material or [Tokens] role. */
private val WarningAmber = Color(0xFFFFC107)

/** Marks a field whose own "Hide X" checkbox is checked elsewhere in the panel. */
@Composable
fun HiddenFieldWarning(tooltip: String) {
    HoverTooltip(tooltip) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * A square check: sunken while clear, a raised accent key with a check mark when ticked -- the
 * app's own checkbox look.
 */
@Composable
fun LottieCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = elevationPalette()
    val boxShape = RoundedCornerShape(5.dp)
    Row(
        modifier = modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(
            modifier = Modifier
                .size(17.dp)
                .then(
                    if (checked) {
                        Modifier.raised(boxShape, palette.accent, palette, lift = 2.dp)
                    } else {
                        Modifier.sunken(boxShape, palette)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = palette.accent.ink,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Text(
            label,
            fontSize = 12.5.sp,
            color = if (checked) Tokens.PrimaryText else Tokens.LabelText,
            maxLines = 1
        )
    }
}

/**
 * A segmented control: the app's sunken track with the chosen option raised. [selectedIndex] may
 * be -1 when the current config matches no preset, in which case every segment renders flat.
 */
@Composable
fun SegmentedButtons(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentTrack(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(9.dp)) {
        labels.forEachIndexed { i, label ->
            val active = i == selectedIndex
            SegmentTrackItem(
                selected = active,
                onClick = { onSelect(i) },
                modifier = Modifier.weight(1f).height(28.dp),
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = LocalContentColor.current,
                    maxLines = 1
                )
            }
        }
    }
}

/** The raised primary action (Download JSON / Save Lower Third), in the accent. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fill = elevationPalette().accent
    Box(
        modifier = modifier
            .height(38.dp)
            .raisedKey(Tokens.ButtonShape, fill, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fill.ink, maxLines = 1)
    }
}

/** The raised neutral secondary action. [compact] is the 25–30dp variant used inside section headers. */
@Composable
fun SubtleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val fill = elevationPalette().key
    val shape = if (compact) RoundedCornerShape(7.dp) else Tokens.ButtonShape

    Box(
        modifier = modifier
            .height(if (compact) 25.dp else 38.dp)
            .raisedKey(shape, fill, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = fill.ink,
            maxLines = 1
        )
    }
}

/** The small ✕ used to remove a saved preset or color theme -- a destructive key. */
@Composable
fun DeleteIconButton(onClick: () -> Unit, contentDescription: String = "Delete") {
    SmallIconButton(Icons.Default.Close, onClick, contentDescription, elevationPalette().danger)
}

/** The small pencil that opens a row's fuller controls. */
@Composable
fun EditIconButton(onClick: () -> Unit, contentDescription: String = "Edit") {
    SmallIconButton(Icons.Default.Edit, onClick, contentDescription, elevationPalette().key)
}

/** A 24dp raised key around one icon, the shape the ✕ and the pencil share. */
@Composable
private fun SmallIconButton(icon: ImageVector, onClick: () -> Unit, contentDescription: String, fill: RaisedFill) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .raisedKey(RoundedCornerShape(6.dp), fill, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = fill.ink,
            modifier = Modifier.size(13.dp)
        )
    }
}
