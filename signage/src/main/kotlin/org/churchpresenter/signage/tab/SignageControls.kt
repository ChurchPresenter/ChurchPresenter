package org.churchpresenter.signage.tab

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.dropdownField
import org.churchpresenter.theme.keyboardFocusRing

/**
 * A signage dialog's title: [kind]'s tile (or [icon] on [tint]), the title, and a line under it on
 * what the dialog is for.
 */
@Composable
internal fun SignageDialogTitle(
    title: String,
    hint: String? = null,
    /** A longer explanation, behind an ⓘ beside the title. */
    info: String? = null,
    kind: TileKind = TileKind.SCREEN,
    tint: KindTint = tintOf(kind),
    icon: ImageVector = iconOf(kind),
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.size(38.dp).background(tint.tile, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint.icon, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (info != null) {
                    Spacer(Modifier.size(6.dp))
                    InfoHint(info)
                }
            }
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** A field's caption, above its box, as the design draws every one; [info] behind an ⓘ beside it. */
@Composable
internal fun FieldCaption(text: String, modifier: Modifier = Modifier, info: String? = null) {
    Row(modifier = modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (info != null) {
            Spacer(Modifier.size(4.dp))
            InfoHint(info)
        }
    }
}

/**
 * A switch with its [title] and, behind an ⓘ beside the title, what it does: the app's labelled switch
 * (the whole row toggles it) with the long explanation out of the way.
 */
@Composable
internal fun InfoSwitch(
    title: String,
    info: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().toggleable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            value = checked,
            role = Role.Switch,
            onValueChange = onChange,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (info != null) {
            Spacer(Modifier.size(6.dp))
            InfoHint(info)
        }
        Spacer(Modifier.weight(1f).size(12.dp))
        RaisedSwitch(checked = checked, onCheckedChange = null)
    }
}

/**
 * A small ⓘ that shows [text] as a tooltip on hover, so a long explanation is there when wanted and
 * out of the way when not. It is a Tab stop, and a screen reader reads [text] as its name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InfoHint(text: String, modifier: Modifier = Modifier) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(8.dp),
                tonalElevation = 4.dp,
            ) {
                Text(
                    text,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.widthIn(max = INFO_MAX_WIDTH).padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        },
        delayMillis = INFO_DELAY_MS,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.BottomCenter,
            offset = DpOffset(0.dp, 4.dp),
        ),
    ) {
        val shape = CircleShape
        Icon(
            Icons.Outlined.Info,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.outline,
            modifier = modifier.size(20.dp).keyboardFocusRing(shape).clip(shape).focusable().padding(2.dp),
        )
    }
}

/**
 * One of a few choices side by side, as a tile: a radio ring (or [leading]), its [title] and a line on
 * what it means; edged and washed in the accent when [selected].
 */
@Composable
internal fun ChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val edge = if (selected) scheme.primary else scheme.outlineVariant
    Row(
        modifier = modifier
            .keyboardFocusRing(shape)
            .clip(shape)
            .background(
                if (selected) scheme.primary.copy(alpha = CHOICE_WASH) else scheme.onSurface.copy(alpha = PANEL_ALPHA),
            )
            .border(1.5.dp, edge, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (leading != null) {
            leading()
        } else {
            Box(
                modifier = Modifier.padding(top = 2.dp).size(16.dp).border(1.5.dp, edge, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Spacer(Modifier.size(8.dp).background(scheme.primary, CircleShape))
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/**
 * A compact dropdown: [leading] (a kind's tile, a colour), [value], and a chevron; its menu lists
 * [options], each with its own leading. [name] is what a screen reader says for it.
 */
@Composable
internal fun PickerField(
    value: String,
    name: String,
    options: List<PickerOption>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Box(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(height)
                .keyboardFocusRing(shape)
                .dropdownField(shape, open)
                .semantics { contentDescription = "$name $value" }
                .clickable(role = Role.DropdownList) { open = true }
                .padding(start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            leading?.invoke()
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = option.leading,
                    onClick = {
                        open = false
                        onPick(index)
                    },
                )
            }
        }
    }
}

/** One entry of a [PickerField]'s menu. */
internal class PickerOption(val label: String, val leading: (@Composable () -> Unit)? = null)

private const val CHOICE_WASH = 0.10f
private const val INFO_DELAY_MS = 300
private val INFO_MAX_WIDTH = 320.dp
private const val PANEL_ALPHA = 0.03f
