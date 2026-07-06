package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.instance_link_status_connected
import churchpresenter.composeapp.generated.resources.instance_link_status_connecting
import churchpresenter.composeapp.generated.resources.instance_link_status_disconnected
import churchpresenter.composeapp.generated.resources.instance_link_status_error
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.jetbrains.compose.resources.stringResource

/**
 * Colored dot + label showing an [InstanceLinkStatus]. Used both inside [org.churchpresenter.app.churchpresenter.dialogs.InstanceLinkDialog]
 * and as a persistent badge in the main window while a follower connection is active.
 *
 * @param connectedLabel overrides the generic "Connected" text when [status] is CONNECTED — e.g.
 *   "Following 192.168.2.254" — so the same status-dot pattern can carry richer context where there's
 *   room for it. Other statuses always use their generic label regardless of this parameter.
 */
@Composable
fun ConnectionStatusRow(
    status: InstanceLinkStatus,
    connectedLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val (color, defaultLabelRes) = when (status) {
        InstanceLinkStatus.CONNECTED -> Color(0xFF4CAF50) to Res.string.instance_link_status_connected
        InstanceLinkStatus.CONNECTING -> Color(0xFFFFC107) to Res.string.instance_link_status_connecting
        InstanceLinkStatus.ERROR -> Color(0xFFF44336) to Res.string.instance_link_status_error
        InstanceLinkStatus.DISCONNECTED -> Color(0xFF9E9E9E) to Res.string.instance_link_status_disconnected
    }
    val label = if (status == InstanceLinkStatus.CONNECTED && connectedLabel != null) {
        connectedLabel
    } else {
        stringResource(defaultLabelRes)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Spacer(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
