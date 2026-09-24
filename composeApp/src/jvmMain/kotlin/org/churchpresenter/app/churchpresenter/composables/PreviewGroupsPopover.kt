package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.minimumInteractiveComponentSize
import org.churchpresenter.theme.components.toggleRow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.browser_source_output_label
import churchpresenter.composeapp.generated.resources.ic_arrow_down
import churchpresenter.composeapp.generated.resources.ic_arrow_up
import churchpresenter.composeapp.generated.resources.ic_close
import churchpresenter.composeapp.generated.resources.ic_delete
import churchpresenter.composeapp.generated.resources.ndi_output_numbered
import churchpresenter.composeapp.generated.resources.preview_settings_add_output
import churchpresenter.composeapp.generated.resources.preview_settings_delete_group
import churchpresenter.composeapp.generated.resources.preview_settings_empty
import churchpresenter.composeapp.generated.resources.preview_settings_group_title
import churchpresenter.composeapp.generated.resources.preview_settings_hide_group
import churchpresenter.composeapp.generated.resources.preview_settings_move_down
import churchpresenter.composeapp.generated.resources.preview_settings_move_up
import churchpresenter.composeapp.generated.resources.preview_settings_new_group
import churchpresenter.composeapp.generated.resources.preview_settings_remove_output
import churchpresenter.composeapp.generated.resources.preview_settings_show_labels
import churchpresenter.composeapp.generated.resources.preview_settings_show_modes
import churchpresenter.composeapp.generated.resources.screen_number
import org.churchpresenter.settings.PreviewGroup
import org.churchpresenter.settings.PreviewGroupShape
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.addPreviewGroup
import org.churchpresenter.settings.addPreviewMember
import org.churchpresenter.settings.movePreviewMember
import org.churchpresenter.settings.newPreviewGroup
import org.churchpresenter.settings.removePreviewGroup
import org.churchpresenter.settings.removePreviewMember
import org.churchpresenter.settings.updatePreviewGroup
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.components.SegmentTrack
import org.churchpresenter.theme.components.SegmentTrackItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.font.FontWeight

/** Test handles for the switches, which carry no text of their own. */
internal const val TAG_SHOW_LABELS = "preview_show_labels"
internal const val TAG_SHOW_MODES = "preview_show_modes"

internal fun hideGroupTag(groupId: String) = "preview_hide_$groupId"

private val POPOVER_WIDTH = 340.dp

/** The per-group Hide switch is drawn smaller than the full-size ones so it fits the title line. */
private const val HIDE_SWITCH_SCALE = 0.75f

/** One output the popover can place in a group: its stored key and the name to show for it. */
private class OutputChoice(val key: String, val label: String)

/** The grid a shape stands for, in the digits and sign every locale reads the same. */
private fun PreviewGroupShape.title(): String = "$columns×$rows"

/**
 * The editor for how the preview panel is arranged, opened from the gear beside the clear button:
 * whether outputs carry their names, and the groups the previews sit in.
 *
 * Works on a [ProjectionSettings] value and hands the changed one back through [onChange] -- it
 * keeps no state of its own beyond what the caller keeps.
 */
@Composable
fun PreviewGroupsPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    proj: ProjectionSettings,
    onChange: (ProjectionSettings) -> Unit,
) {
    val choices = previewOutputChoices(proj)
    val labelOf = choices.associate { it.key to it.label }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.width(POPOVER_WIDTH).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SwitchRow(
                label = stringResource(Res.string.preview_settings_show_labels),
                checked = proj.showOutputLabels,
                tag = TAG_SHOW_LABELS,
                onChange = { onChange(proj.copy(showOutputLabels = it)) },
            )
            SwitchRow(
                label = stringResource(Res.string.preview_settings_show_modes),
                checked = proj.showOutputModes,
                tag = TAG_SHOW_MODES,
                onChange = { onChange(proj.copy(showOutputModes = it)) },
            )
            HorizontalDivider()
            if (proj.previewGroups.isEmpty()) {
                Text(
                    stringResource(Res.string.preview_settings_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            proj.previewGroups.forEachIndexed { position, group ->
                GroupEditor(
                    title = stringResource(Res.string.preview_settings_group_title, position + 1),
                    group = group,
                    proj = proj,
                    choices = choices,
                    labelOf = labelOf,
                    onChange = onChange,
                )
            }
            Text(
                text = stringResource(Res.string.preview_settings_new_group),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable { onChange(proj.addPreviewGroup(newPreviewGroup(proj.previewGroups))) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.toggleRow(checked, onChange, interaction).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        RaisedSwitch(
            checked = checked,
            onCheckedChange = null,
            interactionSource = interaction,
            modifier = Modifier.minimumInteractiveComponentSize(),
        )
    }
}

@Composable
private fun GroupEditor(
    title: String,
    group: PreviewGroup,
    proj: ProjectionSettings,
    choices: List<OutputChoice>,
    labelOf: Map<String, String>,
    onChange: (ProjectionSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                stringResource(Res.string.preview_settings_hide_group),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 6.dp),
            )
            RaisedSwitch(
                checked = group.hidden,
                onCheckedChange = { hide -> onChange(proj.updatePreviewGroup(group.id) { it.copy(hidden = hide) }) },
                modifier = Modifier.scale(HIDE_SWITCH_SCALE).testTag(hideGroupTag(group.id)),
            )
            TooltipIconButton(
                painter = painterResource(Res.drawable.ic_delete),
                text = stringResource(Res.string.preview_settings_delete_group),
                onClick = { onChange(proj.removePreviewGroup(group.id)) },
                iconSize = 16.dp,
                buttonSize = 28.dp,
            )
        }
        // One segmented control: the shapes share a sunken track and the chosen one is raised.
        SegmentTrack {
            PreviewGroupShape.entries.forEach { shape ->
                val selected = shape == group.shape
                SegmentTrackItem(
                    selected = selected,
                    onClick = { onChange(proj.updatePreviewGroup(group.id) { it.copy(shape = shape) }) },
                ) {
                    Text(
                        text = shape.title(),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = LocalContentColor.current,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
        }
        group.members.forEachIndexed { index, key ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MemberRow(
                    label = labelOf[key] ?: key,
                    // Past the grid's capacity a member is stored but drawn with the ungrouped outputs.
                    fits = index < group.shape.capacity,
                    canMoveUp = index > 0,
                    canMoveDown = index < group.members.lastIndex,
                    onMoveUp = { onChange(proj.movePreviewMember(group.id, index, -1)) },
                    onMoveDown = { onChange(proj.movePreviewMember(group.id, index, 1)) },
                    onRemove = { onChange(proj.removePreviewMember(group.id, key)) },
                )
            }
        }
        // Outputs that are in no group yet, offered as one-tap additions to this one.
        val placed = proj.previewGroups.flatMap { it.members }.toSet()
        choices.filter { it.key !in placed }.forEach { choice ->
            Text(
                text = stringResource(Res.string.preview_settings_add_output, choice.label),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChange(proj.addPreviewMember(group.id, choice.key)) }
                    .padding(vertical = 2.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun RowScope.MemberRow(
    label: String,
    fits: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Text(
        text = label,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (fits) 1f else 0.4f),
        modifier = Modifier.weight(1f),
    )
    ReorderArrowButton(
        icon = painterResource(Res.drawable.ic_arrow_up),
        contentDescription = stringResource(Res.string.preview_settings_move_up),
        enabled = canMoveUp,
        onClick = onMoveUp,
    )
    ReorderArrowButton(
        icon = painterResource(Res.drawable.ic_arrow_down),
        contentDescription = stringResource(Res.string.preview_settings_move_down),
        enabled = canMoveDown,
        onClick = onMoveDown,
    )
    TooltipIconButton(
        painter = painterResource(Res.drawable.ic_close),
        text = stringResource(Res.string.preview_settings_remove_output),
        onClick = onRemove,
        iconSize = 14.dp,
        buttonSize = 24.dp,
    )
}

/** Every output the panel can show, named the way the panel names it. */
@Composable
private fun previewOutputChoices(proj: ProjectionSettings): List<OutputChoice> {
    return buildList {
        proj.screenAssignments.forEachIndexed { i, assignment ->
            add(
                OutputChoice(
                    Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, i),
                    proj.screenLabelOr(assignment, stringResource(Res.string.screen_number, i + 1)),
                )
            )
        }
        proj.browserSourceOutputs.forEachIndexed { i, output ->
            add(
                OutputChoice(
                    Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, i),
                    output.browserSourceLabelOr(stringResource(Res.string.browser_source_output_label, i + 1)),
                )
            )
        }
        proj.ndiOutputs.forEachIndexed { i, output ->
            add(
                OutputChoice(
                    Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, i),
                    output.ndiLabelOr(stringResource(Res.string.ndi_output_numbered, i + 1)),
                )
            )
        }
    }
}
