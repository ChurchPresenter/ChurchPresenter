package org.churchpresenter.liveoutput.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_delete
import org.churchpresenter.icons.generated.resources.ic_edit
import org.churchpresenter.strings.generated.resources.preview_layout_default_name
import org.churchpresenter.strings.generated.resources.preview_layout_edit
import org.churchpresenter.strings.generated.resources.preview_layout_fills_panel
import org.churchpresenter.strings.generated.resources.preview_layout_list_unplaced
import org.churchpresenter.strings.generated.resources.preview_layout_new
import org.churchpresenter.strings.generated.resources.preview_layout_none
import org.churchpresenter.strings.generated.resources.preview_layout_delete
import org.churchpresenter.strings.generated.resources.preview_layout_rename
import org.churchpresenter.strings.generated.resources.preview_section_display
import org.churchpresenter.strings.generated.resources.preview_section_layout
import org.churchpresenter.strings.generated.resources.preview_settings_show_labels
import org.churchpresenter.strings.generated.resources.preview_settings_show_modes
import org.churchpresenter.strings.generated.resources.profile_sets_show_button
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.activeLayout
import org.churchpresenter.settings.newPreviewLayout
import org.churchpresenter.settings.offersProfileSets
import org.churchpresenter.settings.updateLayout
import org.churchpresenter.theme.components.KeyButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.TooltipIconButton

/** Test handles for the switches, which carry no text of their own. */
internal const val TAG_SHOW_LABELS = "preview_show_labels"
internal const val TAG_SHOW_MODES = "preview_show_modes"
internal const val TAG_SHOW_PROFILE_SETS = "preview_show_profile_sets"
internal const val TAG_FILLS_PANEL = "preview_fills_panel"
internal const val TAG_LIST_UNPLACED = "preview_list_unplaced"
internal const val TAG_EDIT_LAYOUT = "preview_edit_layout"

internal fun previewLayoutTag(id: String) = "preview_layout_$id"
internal fun previewTemplateTag(index: Int) = "preview_template_$index"

private val POPOVER_WIDTH = 300.dp

/**
 * The editor for how the preview panel is arranged, opened from the gear beside the clear button:
 * whether outputs carry their names and modes, and the panel's named layouts -- which one is drawn,
 * new ones from a template, renaming and deleting them, and Edit layout, which turns the live panel
 * itself into the layout's editor through [onEditLayout]. With outputs and profiles enough to switch,
 * whether the header shows the profile sets button ([ProfileSetsPopover]).
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
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.width(POPOVER_WIDTH).padding(horizontal = 6.dp)) {
            SectionLabel(stringResource(Res.string.preview_section_display))
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
            if (proj.offersProfileSets()) {
                SwitchRow(
                    label = stringResource(Res.string.profile_sets_show_button),
                    checked = proj.showProfileSetsButton,
                    tag = TAG_SHOW_PROFILE_SETS,
                    onChange = { onChange(proj.copy(showProfileSetsButton = it)) },
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            LayoutHeader(proj, onChange)
            OtherLayouts(proj, onChange)
            NewLayout(proj, onChange)
            val active = proj.activeLayout()
            if (active != null) {
                KeyButton(
                    onClick = {
                        onEditLayout()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp)
                        .height(ROW_HEIGHT)
                        .testTag(TAG_EDIT_LAYOUT),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    LayoutPicture(
                        area = active.root,
                        cell = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        stringResource(Res.string.preview_layout_edit),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
 * The Layout heading with the drawn layout's name beside it -- typed over in place, the pencil
 * putting the cursor there -- and its delete. With none, a line saying the panel lists every output.
 */
@Composable
private fun LayoutHeader(proj: ProjectionSettings, onChange: (ProjectionSettings) -> Unit) {
    val active = proj.activeLayout()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (active != null) Modifier.testTag(previewLayoutTag(active.id)) else Modifier)
            .padding(end = 4.dp, top = 4.dp, bottom = 6.dp),
    ) {
        SectionLabel(stringResource(Res.string.preview_section_layout), Modifier.padding(top = 0.dp))
        if (active == null) {
            Spacer(Modifier.weight(1f))
            return@Row
        }
        val position = proj.previewLayouts.indexOfFirst { it.id == active.id }
        val shownName = active.name.ifBlank { stringResource(Res.string.preview_layout_default_name, position + 1) }
        val focus = remember { FocusRequester() }
        val nameStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        BasicTextField(
            value = active.name,
            onValueChange = { typed -> onChange(proj.updateLayout(active.id) { it.copy(name = typed) }) },
            singleLine = true,
            textStyle = nameStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { field ->
                Box {
                    if (active.name.isBlank()) {
                        Text(shownName, style = nameStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    field()
                }
            },
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_edit),
            text = stringResource(Res.string.preview_layout_rename),
            onClick = { focus.requestFocus() },
            iconSize = 13.dp,
            buttonSize = 26.dp,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_delete),
            text = stringResource(Res.string.preview_layout_delete),
            onClick = {
                val rest = proj.previewLayouts.filterNot { it.id == active.id }
                onChange(proj.copy(previewLayouts = rest, activePreviewLayout = rest.firstOrNull()?.id.orEmpty()))
            },
            iconSize = 13.dp,
            buttonSize = 26.dp,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (active == null) {
        Text(
            stringResource(Res.string.preview_layout_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        )
    }
}

/** The layouts not drawn now, one 32dp row each: a click draws it, and each can be deleted. */
@Composable
private fun OtherLayouts(proj: ProjectionSettings, onChange: (ProjectionSettings) -> Unit) {
    val activeId = proj.activeLayout()?.id
    proj.previewLayouts.forEachIndexed { position, layout ->
        if (layout.id == activeId) return@forEachIndexed
        val shownName = layout.name.ifBlank { stringResource(Res.string.preview_layout_default_name, position + 1) }
        MenuRow(
            onClick = { onChange(proj.copy(activePreviewLayout = layout.id)) },
            modifier = Modifier.testTag(previewLayoutTag(layout.id)),
        ) {
            LayoutPicture(
                area = layout.root,
                cell = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                shownName,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TooltipIconButton(
                painter = painterResource(IconRes.drawable.ic_delete),
                text = stringResource(Res.string.preview_layout_delete),
                onClick = {
                    onChange(proj.copy(previewLayouts = proj.previewLayouts.filterNot { it.id == layout.id }))
                },
                iconSize = 13.dp,
                buttonSize = 26.dp,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * New layout: the templates as little pictures of their split, in one row. A click makes a new
 * layout from it -- drawn at once -- with the panel's outputs placed in its areas in order; the
 * line under the row names the template under the pointer.
 */
@Composable
private fun NewLayout(proj: ProjectionSettings, onChange: (ProjectionSettings) -> Unit) {
    val templates = previewLayoutTemplates()
    val outputs = previewOutputKeys(proj)
    var hovered by remember { mutableStateOf(-1) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
    ) {
        templates.forEachIndexed { index, template ->
            TemplateButton(
                template = template,
                onHover = { over -> hovered = if (over) index else hovered.takeUnless { it == index } ?: -1 },
                onClick = {
                    val layout = newPreviewLayout(proj.previewLayouts, name = "").copy(root = template.build(outputs))
                    onChange(proj.copy(previewLayouts = proj.previewLayouts + layout, activePreviewLayout = layout.id))
                },
                modifier = Modifier.weight(1f).testTag(previewTemplateTag(index)),
            )
        }
    }
    val newLabel = stringResource(Res.string.preview_layout_new)
    Text(
        templates.getOrNull(hovered)?.let { "$newLabel: ${it.label}" } ?: newLabel,
        fontSize = 11.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 5.dp, bottom = 2.dp),
    )
}
