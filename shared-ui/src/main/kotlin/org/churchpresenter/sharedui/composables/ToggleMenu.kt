package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.toggle_menu_count
import org.churchpresenter.strings.generated.resources.toggle_menu_show_all
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.stringResource

private val ToggleItemShape = AppShape(8.dp)
private val ToggleChipShape = AppShape(6.dp)
private val ToggleBoxShape = AppShape(5.dp)
private val ShowAllShape = AppShape(6.dp)

/** An item that is off keeps its place but steps back: dimmed icon, quieter label. */
private const val OFF_ICON_ALPHA = 0.45f
private const val DISABLED_ALPHA = 0.38f

/** The small uppercase caption over a group in a toggle menu ("Icon size", "Buttons", "Tabs"). */
@Composable
fun ToggleMenuSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.1.em,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

/**
 * The line over a list of [ToggleMenuItem]s: what the list is, how many of it are shown, and a
 * Show all link while anything is hidden.
 */
@Composable
fun ToggleMenuHeader(title: String, shown: Int, total: Int, onShowAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp).padding(start = 8.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleMenuSectionLabel(title, modifier = Modifier.weight(1f))
        Text(
            stringResource(Res.string.toggle_menu_count, shown, total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (shown < total) {
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Text(
                stringResource(Res.string.toggle_menu_show_all),
                modifier = Modifier
                    .clip(ShowAllShape)
                    .background(
                        if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = ITEM_HOVER_ALPHA)
                        else Color.Transparent
                    )
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, onClick = onShowAll)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

/**
 * One thing a toggle menu shows or hides: its own icon on the left, so the list reads as the
 * buttons themselves, and a check on the right. [icon] is optional — the tab list has none.
 */
@Composable
fun ToggleMenuItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(ToggleItemShape)
            .background(
                if (hovered && enabled) scheme.onSurface.copy(alpha = ITEM_HOVER_ALPHA) else Color.Transparent
            )
            .hoverable(interaction, enabled = enabled)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .alpha(if (checked) 1f else OFF_ICON_ALPHA)
                    .clip(ToggleChipShape)
                    .background(chipColor(accent)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = accent)
            }
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (checked) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ToggleMenuCheck(checked)
    }
}

@Composable
private fun ToggleMenuCheck(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(ToggleBoxShape)
            .background(if (checked) scheme.primary else Color.Transparent)
            .border(
                BorderStroke(1.5.dp, if (checked) scheme.primary else scheme.outline),
                ToggleBoxShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = scheme.onPrimary,
            )
        }
    }
}
