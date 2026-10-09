package org.churchpresenter.liveoutput.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.strings.generated.resources.preview_layout_default_name
import org.churchpresenter.strings.generated.resources.preview_layout_edit
import org.churchpresenter.strings.generated.resources.preview_layout_fills_panel
import org.churchpresenter.strings.generated.resources.preview_layout_list_unplaced
import org.churchpresenter.strings.generated.resources.preview_layout_new
import org.churchpresenter.strings.generated.resources.preview_layout_none
import org.churchpresenter.strings.generated.resources.preview_layout_delete
import org.churchpresenter.strings.generated.resources.preview_settings_show_labels
import org.churchpresenter.strings.generated.resources.preview_settings_show_modes
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.activeLayout
import org.churchpresenter.settings.newPreviewLayout
import org.churchpresenter.settings.updateLayout
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.components.SegmentTrack
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.components.toggleRow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.TooltipIconButton

/** Test handles for the switches, which carry no text of their own. */
internal const val TAG_SHOW_LABELS = "preview_show_labels"
internal const val TAG_SHOW_MODES = "preview_show_modes"
internal const val TAG_FILLS_PANEL = "preview_fills_panel"
internal const val TAG_LIST_UNPLACED = "preview_list_unplaced"
internal const val TAG_EDIT_LAYOUT = "preview_edit_layout"

internal fun previewLayoutTag(id: String) = "preview_layout_$id"
internal fun previewTemplateTag(index: Int) = "preview_template_$index"

private val POPOVER_WIDTH = 340.dp

/**
 * The editor for how the preview panel is arranged, opened from the gear beside the clear button:
 * whether outputs carry their names and modes, and the panel's named layouts -- which one is drawn,
 * new ones from a template, renaming and deleting them, and Edit layout, which turns the live panel
 * itself into the layout's editor through [onEditLayout].
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
    onEditLayout: () -> Unit = {},
) {
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
            LayoutList(proj, onChange)
            NewLayout(proj, onChange)
            val active = proj.activeLayout()
            if (active != null) {
                HorizontalDivider()
                TextButton(
                    onClick = {
                        onEditLayout()
                        onDismiss()
                    },
                    modifier = Modifier.testTag(TAG_EDIT_LAYOUT),
                ) { Text(stringResource(Res.string.preview_layout_edit)) }
                SwitchRow(
                    label = stringResource(Res.string.preview_layout_fills_panel),
                    checked = proj.previewLayoutFillsPanel,
                    tag = TAG_FILLS_PANEL,
                    onChange = { onChange(proj.copy(previewLayoutFillsPanel = it)) },
                )
                SwitchRow(
                    label = stringResource(Res.string.preview_layout_list_unplaced),
                    checked = proj.listUnplacedOutputs,
                    tag = TAG_LIST_UNPLACED,
                    onChange = { onChange(proj.copy(listUnplacedOutputs = it)) },
                )
            }
        }
    }
}

/**
 * The layouts, one to a line: the one drawn is raised, a click draws another, the drawn one's name
 * can be typed over, and each can be deleted. With none, a line saying the panel lists every output.
 */
@Composable
private fun LayoutList(proj: ProjectionSettings, onChange: (ProjectionSettings) -> Unit) {
    if (proj.previewLayouts.isEmpty()) {
        Text(
            stringResource(Res.string.preview_layout_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val activeId = proj.activeLayout()?.id
    proj.previewLayouts.forEachIndexed { position, layout ->
        val active = layout.id == activeId
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().testTag(previewLayoutTag(layout.id)),
        ) {
            val shownName = layout.name.ifBlank { stringResource(Res.string.preview_layout_default_name, position + 1) }
            if (active) {
                BasicTextField(
                    value = layout.name,
                    onValueChange = { typed -> onChange(proj.updateLayout(layout.id) { it.copy(name = typed) }) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { field ->
                        if (layout.name.isBlank()) {
                            Text(shownName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        field()
                    },
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            } else {
                Text(
                    shownName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onChange(proj.copy(activePreviewLayout = layout.id)) }
                        .padding(vertical = 6.dp),
                )
            }
            TooltipIconButton(
                painter = painterResource(IconRes.drawable.ic_delete),
                text = stringResource(Res.string.preview_layout_delete),
                onClick = {
                    val rest = proj.previewLayouts.filterNot { it.id == layout.id }
                    onChange(
                        proj.copy(
                            previewLayouts = rest,
                            activePreviewLayout = if (active) {
                                rest.firstOrNull()?.id.orEmpty()
                            } else {
                                proj.activePreviewLayout
                            },
                        ),
                    )
                },
                iconSize = 16.dp,
                buttonSize = 28.dp,
            )
        }
    }
}

/**
 * New layout: a template to start from, which becomes a new layout -- drawn at once -- with the
 * panel's outputs placed in its areas in order.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewLayout(proj: ProjectionSettings, onChange: (ProjectionSettings) -> Unit) {
    val templates = previewLayoutTemplates()
    val outputs = previewOutputKeys(proj)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.preview_layout_new), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            templates.forEachIndexed { index, template ->
                SegmentTrack {
                    SegmentTrackItem(
                        selected = false,
                        onClick = {
                            val layout = newPreviewLayout(proj.previewLayouts, name = "")
                                .copy(root = template.build(outputs))
                            val layouts = proj.previewLayouts + layout
                            onChange(proj.copy(previewLayouts = layouts, activePreviewLayout = layout.id))
                        },
                        modifier = Modifier.testTag(previewTemplateTag(index)),
                    ) {
                        Text(
                            text = template.label,
                            fontSize = 11.sp,
                            color = LocalContentColor.current,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
            }
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
