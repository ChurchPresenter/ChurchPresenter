package org.churchpresenter.serverui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.churchpresenter.server.DevicePermission
import org.churchpresenter.server.DevicePermissions
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.device_permission_add_to_schedule
import org.churchpresenter.strings.generated.resources.device_permission_add_to_schedule_desc
import org.churchpresenter.strings.generated.resources.device_permission_control_output
import org.churchpresenter.strings.generated.resources.device_permission_control_output_desc
import org.churchpresenter.strings.generated.resources.device_permission_go_live
import org.churchpresenter.strings.generated.resources.device_permission_go_live_desc
import org.churchpresenter.strings.generated.resources.device_permission_remove_from_schedule
import org.churchpresenter.strings.generated.resources.device_permission_remove_from_schedule_desc
import org.churchpresenter.strings.generated.resources.device_permission_show_toasts
import org.churchpresenter.strings.generated.resources.device_permission_show_toasts_desc
import org.churchpresenter.strings.generated.resources.device_permission_upload_files
import org.churchpresenter.strings.generated.resources.device_permission_upload_files_desc
import org.churchpresenter.strings.generated.resources.remote_client_no_toasts
import org.churchpresenter.strings.generated.resources.remote_client_permissions_all
import org.churchpresenter.strings.generated.resources.remote_client_permissions_count
import org.churchpresenter.strings.generated.resources.remote_client_permissions_note
import org.churchpresenter.theme.components.RaisedSwitch
import org.churchpresenter.theme.components.toggleRow
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The permissions that refuse something when switched off; the toasts are not one of them. */
private val GATES = DevicePermission.entries - DevicePermission.SHOW_TOASTS

/** How an allowed device's permissions read while its row is closed: all, or how many of them. */
@Composable
internal fun PermissionsSummary(permissions: DevicePermissions) {
    val allowed = GATES.count(permissions::allows)
    if (allowed == GATES.size) {
        Pill(stringResource(Res.string.remote_client_permissions_all), MaterialTheme.semantic.success, round = false)
    } else {
        Pill(
            stringResource(Res.string.remote_client_permissions_count, allowed, GATES.size),
            MaterialTheme.semantic.warning,
            round = false,
        )
    }
    if (!permissions.allows(DevicePermission.SHOW_TOASTS)) {
        Pill(
            stringResource(Res.string.remote_client_no_toasts),
            MaterialTheme.colorScheme.onSurfaceVariant,
            round = false,
        )
    }
}

/** One switch per permission, under an allowed device's row. */
@Composable
internal fun ClientPermissionsPanel(
    permissions: DevicePermissions,
    onPermissionChange: (DevicePermission, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 53.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DimText(stringResource(Res.string.remote_client_permissions_note), small = true)
        DevicePermission.entries.forEach { permission ->
            val (title, description) = permissionText(permission)
            PermissionSwitch(
                title = stringResource(title),
                description = stringResource(description),
                checked = permissions.allows(permission),
                onCheckedChange = { onPermissionChange(permission, it) },
            )
        }
    }
}

@Composable
private fun PermissionSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth().toggleRow(checked, onCheckedChange, interaction),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            DimText(description, small = true)
        }
        RaisedSwitch(checked = checked, onCheckedChange = null, interactionSource = interaction)
    }
}

private fun permissionText(permission: DevicePermission): Pair<StringResource, StringResource> = when (permission) {
    DevicePermission.CONTROL_OUTPUT ->
        Res.string.device_permission_control_output to Res.string.device_permission_control_output_desc
    DevicePermission.ADD_TO_SCHEDULE ->
        Res.string.device_permission_add_to_schedule to Res.string.device_permission_add_to_schedule_desc
    DevicePermission.REMOVE_FROM_SCHEDULE ->
        Res.string.device_permission_remove_from_schedule to Res.string.device_permission_remove_from_schedule_desc
    DevicePermission.GO_LIVE -> Res.string.device_permission_go_live to Res.string.device_permission_go_live_desc
    DevicePermission.UPLOAD_FILES ->
        Res.string.device_permission_upload_files to Res.string.device_permission_upload_files_desc
    DevicePermission.SHOW_TOASTS ->
        Res.string.device_permission_show_toasts to Res.string.device_permission_show_toasts_desc
}
