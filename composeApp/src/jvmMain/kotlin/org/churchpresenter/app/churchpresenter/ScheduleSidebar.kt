package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.connect
import org.churchpresenter.strings.generated.resources.instance_link_controlling_host
import org.churchpresenter.strings.generated.resources.instance_link_following_host
import org.churchpresenter.strings.generated.resources.instance_link_primary_badge
import org.churchpresenter.strings.generated.resources.instance_link_status_reconnecting_in
import org.churchpresenter.strings.generated.resources.menu_disconnect
import kotlinx.coroutines.delay
import org.churchpresenter.companionsurface.CompanionConnectionChipRow
import org.churchpresenter.serverui.ConnectionStatusRow
import org.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.theme.components.GhostButton
import org.jetbrains.compose.resources.stringResource

private const val CLOCK_TICK_MS = 1000L

/**
 * The left-hand sidebar: the Instance Link status from [link], the [schedule] pane, and any of
 * [connections] routed here, drawn by [companionSurface].
 */
@Composable
internal fun ScheduleSidebar(
    modifier: Modifier,
    link: InstanceLinkBridge,
    connections: List<CompanionSatelliteSettings>,
    schedule: @Composable () -> Unit,
    companionSurface: CompanionSurfaceSlot,
) {
    Column(modifier = modifier) {
        InstanceLinkStatusRows(link)
        Box(modifier = Modifier.weight(1f)) {
            schedule()
        }
        ScheduleSidebarCompanionPanel(connections = connections, companionSurface = companionSurface)
    }
}

@Composable
private fun InstanceLinkStatusRows(link: InstanceLinkBridge) {
    // Shown once a host has ever been configured — not just while actively connected —
    // so the operator can always see the last-known status and reconnect/disconnect
    // without reopening the Connect dialog.
    if (link.followingHost.isNotBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 1s ticker so the "reconnecting in Xs" countdown stays current while
            // the link is down; idle (single recomposition) otherwise.
            var reconnectNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(link.connectionStatus == InstanceLinkStatus.ERROR) {
                while (link.connectionStatus == InstanceLinkStatus.ERROR) {
                    reconnectNowMs = System.currentTimeMillis()
                    delay(CLOCK_TICK_MS)
                }
            }
            val retrySecondsLeft = retrySecondsLeft(link.nextRetryAtMs, reconnectNowMs)
            ConnectionStatusRow(
                status = link.connectionStatus,
                connectedLabel = if (link.role == InstanceLinkRole.CONTROLLER)
                    stringResource(Res.string.instance_link_controlling_host, link.followingHost)
                else
                    stringResource(Res.string.instance_link_following_host, link.followingHost),
                errorLabel = retrySecondsLeft?.let {
                    stringResource(Res.string.instance_link_status_reconnecting_in, it.toInt())
                }
            )
            // != DISCONNECTED (not just CONNECTED/CONNECTING) so the operator can
            // stop an ERROR-state retry loop without reopening the dialog.
            if (canDisconnectInstanceLink(link.connectionStatus)) {
                GhostButton(onClick = link.onDisconnect) {
                    Text(stringResource(Res.string.menu_disconnect), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                GhostButton(onClick = link.onConnect) {
                    Text(stringResource(Res.string.connect), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    if (link.followerCount > 0) {
        ConnectionStatusRow(
            status = InstanceLinkStatus.CONNECTED,
            connectedLabel = stringResource(Res.string.instance_link_primary_badge, link.followerCount),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Any Companion surface routed to the left sidebar, under the schedule.
 */
@Composable
private fun ScheduleSidebarCompanionPanel(
    connections: List<CompanionSatelliteSettings>,
    companionSurface: CompanionSurfaceSlot,
) {
    val leftSidebarConnections = connections.filter { it.showInLeftSidebar && it.host.isNotBlank() }
    if (leftSidebarConnections.isNotEmpty()) {
        HorizontalDivider()
        var selectedLeftSidebarId by remember(leftSidebarConnections.map { it.id }) {
            mutableStateOf(resolveSelectedConnectionId(null, leftSidebarConnections))
        }
        LaunchedEffect(leftSidebarConnections.map { it.id }) {
            selectedLeftSidebarId = resolveSelectedConnectionId(selectedLeftSidebarId, leftSidebarConnections)
        }
        val selectedLeftSidebarConnection = leftSidebarConnections.find { it.id == selectedLeftSidebarId }
        // No weight here — this panel sizes itself to exactly what its configured
        // grid needs (sizeToContent), so the ScheduleTab above (weight(1f)) gets all
        // the remaining space instead of being forced into a fixed 50/50 split.
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            if (leftSidebarConnections.size > 1) {
                CompanionConnectionChipRow(
                    connections = leftSidebarConnections,
                    selectedId = selectedLeftSidebarId,
                    onSelect = { selectedLeftSidebarId = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (selectedLeftSidebarConnection != null) {
                companionSurface(selectedLeftSidebarConnection, CompanionSurfacePlacement.LEFT_SIDEBAR)
            }
        }
    }
}
