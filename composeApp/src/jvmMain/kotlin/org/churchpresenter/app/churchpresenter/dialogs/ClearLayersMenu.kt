package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.churchpresenter.app.churchpresenter.viewmodel.CLEARABLE_LAYERS
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.clear_groups_edit
import org.jetbrains.compose.resources.stringResource

/**
 * What the sidebar's Clear layers button drops down: the saved clear groups, then each layer on
 * its own (dimmed when nothing is on it), then the way into [ClearGroupsDialog].
 */
@Composable
internal fun ClearLayersMenuItems(
    groups: List<ClearGroup>,
    onAir: Set<Layer>,
    onClearGroup: (ClearGroup) -> Unit,
    onClearLayer: (Layer) -> Unit,
    onEdit: () -> Unit,
) {
    groups.forEach { group ->
        DropdownMenuItem(
            text = { Text(group.name, fontWeight = FontWeight.Bold) },
            onClick = { onClearGroup(group) },
            modifier = Modifier.testTag(clearGroupItemTag(group.id)),
        )
    }
    if (groups.isNotEmpty()) HorizontalDivider()
    CLEARABLE_LAYERS.forEach { layer ->
        DropdownMenuItem(
            text = { Text(stringResource(layer.clearLabel)) },
            onClick = { onClearLayer(layer) },
            enabled = layer in onAir,
            modifier = Modifier.testTag(clearLayerItemTag(layer)),
        )
    }
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.clear_groups_edit)) },
        onClick = onEdit,
        modifier = Modifier.testTag(CLEAR_GROUPS_EDIT_TAG),
    )
}

internal const val CLEAR_GROUPS_EDIT_TAG = "clear_groups_edit"

internal fun clearGroupItemTag(id: String) = "clear_group_item_$id"

internal fun clearLayerItemTag(layer: Layer) = "clear_layer_item_${layer.name}"
