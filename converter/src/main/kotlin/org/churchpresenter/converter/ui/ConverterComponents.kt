package org.churchpresenter.converter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val CardShape = RoundedCornerShape(10.dp)
internal val FieldShape = RoundedCornerShape(8.dp)
private val TileShape = RoundedCornerShape(7.dp)

/** A file (or folder) shown inside [SelectedFilesCard]. */
data class SelectedEntry(val name: String, val meta: String)

/** Square tile carrying a format's two-letter initials, as used by the source rail and header. */
@Composable
fun SourceInitialsTile(initials: String, selected: Boolean, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(TileShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Uppercase section caption with a hairline running to the end of the row. */
@Composable
fun RailGroupLabel(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/** One selectable format row in the "Convert from" rail. */
@Composable
fun SourceRailRow(source: SongSource, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(FieldShape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                FieldShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        SourceInitialsTile(source.initials, selected, 25.dp)
        Text(
            source.name,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            source.ext,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Compact tab strip: left-aligned text tabs on a 40dp bar, the selected one carrying a 2dp underline
 * that sits on the bar's bottom hairline. Deliberately not Material's `PrimaryTabRow`, which
 * stretches every tab to an equal share of the window and stacks an icon above the label — far too
 * heavy for a four-tab utility.
 */
@Composable
fun ConverterTabRow(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val indicator = MaterialTheme.colorScheme.primary
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 6.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clickable { onSelect(index) }
                        // Drawn, not laid out: an indicator child with fillMaxWidth() would expand
                        // to the row's constraints and stretch this tab across the whole window,
                        // pushing the other tabs off-screen.
                        .drawBehind {
                            if (!selected) return@drawBehind
                            val thickness = 2.dp.toPx()
                            drawRect(
                                color = indicator,
                                topLeft = Offset(0f, size.height - thickness),
                                size = Size(size.width, thickness)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}

/**
 * Foot of the source rail: how to get a format added that isn't listed. The rail only ever shows
 * formats that actually convert, so this is the only place the missing ones are acknowledged.
 */
@Composable
fun RequestFormatNote() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FieldShape)
            .padding(horizontal = 11.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(
                Icons.Default.Info, null, Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                Strings.requestFormatTitle,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            Strings.requestFormatBody,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Small monospace pill used for the source/target extensions in the panel header. */
@Composable
fun ExtensionBadge(text: String, emphasized: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = null
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

/** Numbered step heading; the badge turns into a check once the step is satisfied. */
@Composable
fun StepHeader(index: Int, complete: Boolean, label: String, hint: String = "") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (complete) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            if (complete) {
                Icon(
                    Icons.Default.Check, null, Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    index.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        if (hint.isNotEmpty()) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Modifier.dashedOutline(color: Color, cornerRadius: Dp) = drawBehind {
    val stroke = Stroke(
        width = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
    )
    val inset = stroke.width / 2f
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width),
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style = stroke
    )
}

/** Dashed placeholder shown until source files are chosen. */
@Composable
fun FileDropZone(
    prompt: String,
    accepts: String,
    actions: @Composable RowScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .dashedOutline(MaterialTheme.colorScheme.outlineVariant, 10.dp)
            .padding(vertical = 24.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Folder, null, Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(prompt, style = MaterialTheme.typography.bodyMedium)
            Text(
                accepts,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Summary strip plus a scrollable list of what is currently selected. */
@Composable
fun SelectedFilesCard(
    summary: String,
    entries: List<SelectedEntry>,
    onChange: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                shape = FieldShape,
                onClick = onChange,
                modifier = Modifier.height(26.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text(Strings.change, style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                shape = FieldShape,
                onClick = onClear,
                modifier = Modifier.height(26.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text(Strings.clear, style = MaterialTheme.typography.labelSmall)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        LazyColumn(modifier = Modifier.heightIn(max = 118.dp).padding(6.dp)) {
            items(entries.size) { index ->
                val entry = entries[index]
                Row(
                    modifier = Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.InsertDriveFile, null, Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        entry.meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Read-only destination path with a Browse button, and an optional "pick a folder" warning. */
@Composable
fun DestinationRow(
    path: String,
    chosen: Boolean,
    warning: String?,
    onBrowse: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(FieldShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        1.dp,
                        if (warning != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.outlineVariant,
                        FieldShape
                    )
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    Icons.Default.Folder, null, Modifier.size(14.dp),
                    tint = if (chosen) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chosen) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(shape = FieldShape, onClick = onBrowse, modifier = Modifier.height(34.dp)) {
                Text(Strings.browse, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (warning != null) {
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Warning, null, Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Titled card with an accented header strip, used for the standalone tools. */
@Composable
fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flush against the card edge, as a left border rather than an inset stripe.
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Takes the slack so the title keeps its natural width instead of stretching.
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            content = content
        )
    }
}

/** Bordered checkbox row — the mockup's tappable option tile. */
@Composable
fun OptionToggleRow(
    checked: Boolean,
    label: String,
    enabled: Boolean = true,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(if (checked) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .border(
                1.dp,
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant,
                FieldShape
            )
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .border(
                    1.dp,
                    if (checked) Color.Transparent else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Default.Check, null, Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (checked) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Progress label plus bar; a null [fraction] renders an indeterminate bar. */
@Composable
fun ConversionProgressRow(label: String, fraction: Float?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            if (fraction != null) {
                Text(
                    "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape))
        }
    }
}

/** Rounded "n converted" confirmation pill. */
@Composable
fun DoneChip(label: String) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            Icons.Default.CheckCircle, null, Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Centered icon-and-message placeholder for a results pane with nothing in it yet. */
@Composable
fun EmptyStatePanel(icon: ImageVector, message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null, Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Struck-through original next to the resulting name, as shown on the Rename tab. */
@Composable
fun RenameExampleCard(before: String, after: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            Strings.exampleLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(
                before,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                after,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
