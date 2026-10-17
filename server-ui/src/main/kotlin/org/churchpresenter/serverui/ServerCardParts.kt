package org.churchpresenter.serverui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.components.toggleRow
import org.churchpresenter.theme.keyboardFocusRing

// The Server page's cards: one shell -- a tinted chip, the title, a line under it and the header's
// own controls -- and the rows the cards are built from.

/**
 * A Server-page card. [titleExtra] sits beside the title (a status pill, a count), [actions] at the
 * header's right; [content] is the body, under a rule.
 */
@Composable
internal fun ServerSettingsCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    titleExtra: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = AppShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconChip(icon, tint, size = 36.dp, iconSize = 18.dp)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    titleExtra()
                }
                DimText(description)
            }
            actions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

/** A part of a card's body, padded to line up with the header. */
@Composable
internal fun CardBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** A setting that is a switch: its name and what it does, the switch at the right; [extra] while it is on. */
@Composable
internal fun ColumnScope.SwitchBlock(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    CardBlock(modifier) {
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier.fillMaxWidth().toggleRow(checked, onCheckedChange, interaction),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                DimText(description)
            }
            RaisedSwitch(checked = checked, onCheckedChange = null, interactionSource = interaction)
        }
        if (checked) extra()
    }
}

/** A small label over a field, with an optional faint note after it. */
@Composable
internal fun FieldLabel(text: String, note: String = "") {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (note.isNotEmpty()) {
            Text(
                "· $note",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = FAINT_ALPHA),
            )
        }
    }
}

/**
 * Two keys joined into one: [first] and, after a hairline, [second] -- "Go Live | + Key". [secondName]
 * is what a screen reader calls the second, whose own label is only its half of the phrase.
 */
@Composable
internal fun SplitKey(
    first: String,
    onFirst: () -> Unit,
    second: String,
    secondName: String,
    onSecond: () -> Unit,
) {
    val shape = AppShape(8.dp)
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SplitHalf(first, onFirst)
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        SplitHalf(second, onSecond, Modifier.semantics { contentDescription = secondName })
    }
}

@Composable
private fun SplitHalf(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    KeyButton(
        onClick = onClick,
        shape = AppShape(0.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = modifier.fillMaxHeight(),
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

/** One tab of a card's tab row: icon, label and count, underlined while selected. */
@Composable
internal fun CardTab(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    count: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val ink = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    // As wide as its own label: the underline fills the tab, not the row.
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .keyboardFocusRing(AppShape(6.dp))
            .selectable(selected = selected, role = Role.Tab, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.height(38.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
            Box(
                modifier = Modifier
                    .background(
                        if (selected) {
                            primary.copy(alpha = COUNT_ALPHA)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        CircleShape,
                    )
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) {
                Text(
                    count.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(if (selected) primary else Color.Transparent),
        )
    }
}

private const val FAINT_ALPHA = 0.7f
private const val COUNT_ALPHA = 0.18f
