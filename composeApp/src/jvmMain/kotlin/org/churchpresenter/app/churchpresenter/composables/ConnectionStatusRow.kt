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
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.instance_link_status_connected
import churchpresenter.composeapp.generated.resources.instance_link_status_connecting
import churchpresenter.composeapp.generated.resources.instance_link_status_disconnected
import churchpresenter.composeapp.generated.resources.instance_link_status_error
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.ui.theme.semantic
import org.jetbrains.compose.resources.stringResource

/**
 * Colored dot + label showing an [InstanceLinkStatus]. Used both inside [org.churchpresenter.app.churchpresenter.dialogs.InstanceLinkDialog]
 * and as a persistent badge in the main window while a follower connection is active.
 *
 * @param connectedLabel overrides the generic "Connected" text when [status] is CONNECTED — e.g.
 *   "Following 192.168.2.254" — so the same status-dot pattern can carry richer context where there's
 *   room for it. Other statuses always use their generic label regardless of this parameter.
 * @param errorLabel same idea for ERROR — e.g. "Link lost — reconnecting in 8 s".
 */
@Composable
fun ConnectionStatusRow(
    status: InstanceLinkStatus,
    connectedLabel: String? = null,
    errorLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val (color, defaultLabelRes) = when (status) {
        InstanceLinkStatus.CONNECTED -> MaterialTheme.semantic.success to Res.string.instance_link_status_connected
        InstanceLinkStatus.CONNECTING -> MaterialTheme.semantic.warning to Res.string.instance_link_status_connecting
        InstanceLinkStatus.ERROR -> MaterialTheme.colorScheme.error to Res.string.instance_link_status_error
        InstanceLinkStatus.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant to Res.string.instance_link_status_disconnected
    }
    val label = when {
        status == InstanceLinkStatus.CONNECTED && connectedLabel != null -> connectedLabel
        status == InstanceLinkStatus.ERROR && errorLabel != null -> errorLabel
        else -> stringResource(defaultLabelRes)
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
