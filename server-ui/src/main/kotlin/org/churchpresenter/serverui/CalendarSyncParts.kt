package org.churchpresenter.serverui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.AppShape

// The calendar card's and its dialogs' own pieces: tinted icon chips, the inner cards the phones
// and the computers sit in, pills, banners and the dashed "nothing here yet" box.

private const val CHIP_ALPHA = 0.16f
private const val CHIP_CORNER = 0.3f
private const val BANNER_ALPHA = 0.10f
private const val BANNER_LINE_ALPHA = 0.28f
private val DASH_ON = 4.dp
private val DASH_OFF = 3.dp

/** A rounded square in a light wash of [tint], holding [icon] in the tint itself. */
@Composable
internal fun IconChip(icon: ImageVector, tint: Color, size: Dp = 30.dp, iconSize: Dp = 16.dp) {
    Box(
        modifier = Modifier.size(size).background(tint.copy(alpha = CHIP_ALPHA), AppShape(size * CHIP_CORNER)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A small rounded label in a wash of [color]; [dot] puts a status dot before the text. */
@Composable
internal fun Pill(text: String, color: Color, dot: Boolean = false, round: Boolean = true) {
    Row(
        modifier = Modifier
            .heightIn(min = 20.dp)
            .background(color.copy(alpha = CHIP_ALPHA), if (round) CircleShape else AppShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** The inner card the phones and the computers each sit in. */
@Composable
internal fun SubCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, AppShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A sub-card's header: chip, title, count and hint, and its buttons at the right. */
@Composable
internal fun SubCardHeader(
    icon: ImageVector,
    tint: Color,
    title: String,
    count: Int?,
    hint: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        IconChip(icon, tint)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (count != null) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

/** One phone or computer in a sub-card's list. */
@Composable
internal fun ListRow(
    dot: Color,
    title: String,
    subtitle: String,
    chip: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, AppShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShape(8.dp))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                chip()
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/** The dashed box a list shows while it is empty. */
@Composable
internal fun EmptyNote(text: String) {
    val dash = MaterialTheme.colorScheme.outline
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val radius = 8.dp.toPx()
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx())),
                    ),
                )
            }
            .padding(12.dp),
    )
}

/**
 * A tinted box with a warning chip: [title] in bold, then [body]; [trailing] sits at the right and
 * [below] under the text.
 */
@Composable
internal fun Banner(
    tint: Color,
    icon: ImageVector,
    title: String,
    body: @Composable () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = BANNER_ALPHA), AppShape(10.dp))
            .border(1.dp, tint.copy(alpha = BANNER_LINE_ALPHA), AppShape(10.dp))
            .padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            IconChip(icon, tint, size = 26.dp, iconSize = 15.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                body()
            }
            trailing()
        }
        below()
    }
}

/** The small caps label over a dialog's field or group. */
@Composable
internal fun FieldHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A sunken monospace box -- the connection details and the error's own words. */
@Composable
internal fun CodeBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, AppShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}
