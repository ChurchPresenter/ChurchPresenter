package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.label
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.isDarkScheme

private val ContextMenuWidth = 264.dp
private val ContextMenuShape = AppShape(14.dp)
private val ContextMenuItemShape = AppShape(9.dp)
private val ContextMenuChipShape = AppShape(7.dp)
private val ContextMenuBadgeShape = AppShape(8.dp)

private const val CHIP_ALPHA_DARK = 0.22f
private const val CHIP_ALPHA_LIGHT = 0.13f
internal const val ITEM_HOVER_ALPHA = 0.07f
private const val DANGER_HOVER_ALPHA = 0.12f
private const val SHORTCUT_ALPHA = 0.75f

@Composable
@ReadOnlyComposable
internal fun chipColor(accent: Color): Color =
    accent.copy(alpha = if (isDarkScheme(MaterialTheme.colorScheme)) CHIP_ALPHA_DARK else CHIP_ALPHA_LIGHT)

/**
 * A right-click menu: an optional header naming what was clicked, then [ContextMenuItem]s. The
 * toolbar and tab-bar dropdowns open the same container with [ToggleMenuItem]s and their own [width].
 */
@Composable
fun ContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    offset: DpOffset = DpOffset.Zero,
    header: (@Composable () -> Unit)? = null,
    width: Dp = ContextMenuWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = offset,
        shape = ContextMenuShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.width(width).padding(horizontal = 6.dp)) {
            if (header != null) {
                header()
                HorizontalDivider(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 5.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            content()
        }
    }
}

@Composable
fun ContextMenuHeader(badge: String, title: String, subtitle: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(ContextMenuBadgeShape).background(chipColor(accent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ContextMenuItem(
    label: String,
    icon: Painter,
    accent: Color,
    onClick: () -> Unit,
    shortcut: String? = null,
    emphasized: Boolean = false,
    danger: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val iconColor = if (danger) scheme.error else accent
    val hoverColor = if (danger) scheme.error.copy(alpha = DANGER_HOVER_ALPHA)
    else scheme.onSurface.copy(alpha = ITEM_HOVER_ALPHA)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(ContextMenuItemShape)
            .background(if (hovered) hoverColor else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 6.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(ContextMenuChipShape).background(chipColor(iconColor)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = iconColor)
        }
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            color = if (danger) scheme.error else scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (shortcut != null) {
            Text(
                shortcut,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = scheme.onSurfaceVariant.copy(alpha = SHORTCUT_ALPHA),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun ContextMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** The first key bound to [action], as the menu shows it beside the item, or null when unbound. */
@Composable
fun contextMenuShortcut(action: ShortcutAction): String? =
    LocalShortcuts.current.chordsFor(action).firstOrNull()?.label()
