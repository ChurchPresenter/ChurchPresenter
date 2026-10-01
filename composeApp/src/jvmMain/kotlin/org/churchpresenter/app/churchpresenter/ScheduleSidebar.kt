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
import androidx.compose.runtime.rememberUpdatedState
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
import org.churchpresenter.strings.generated.resources.timer_expired
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.composables.CompanionConnectionChipRow
import org.churchpresenter.app.churchpresenter.composables.CompanionSurfacePanel
import org.churchpresenter.app.churchpresenter.composables.ConnectionStatusRow
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.app.churchpresenter.tabs.ScheduleTab
import org.churchpresenter.app.churchpresenter.tabs.ScheduleToolbarIconSize
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.theme.components.GhostButton
import org.jetbrains.compose.resources.stringResource

private const val CLOCK_TICK_MS = 1000L

/** The left-hand sidebar: the Instance Link status, the Schedule, and any Companion surface routed here. */
@Composable
internal fun MainDesktopScope.ScheduleSidebar(modifier: Modifier) {
    Column(modifier = modifier) {
        InstanceLinkStatusRows()
        Box(modifier = Modifier.weight(1f)) {
            ScheduleTabPane()
        }
        ScheduleSidebarCompanionPanel(
            connections = appSettings.companionSatelliteConnections,
            companionSatelliteViewModel = companionSatelliteViewModel,
        )
    }
}

@Composable
private fun MainDesktopScope.InstanceLinkStatusRows() {
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

@Composable
private fun MainDesktopScope.ScheduleTabPane() {
    // Keep a stable reference to onScheduleActionsReady so the onActionsReady lambda always
    // hands the menu the latest callback.
    val currentOnScheduleActionsReady by rememberUpdatedState(publish.onScheduleActionsReady)
    val timerExpiredDefaultLabel = stringResource(Res.string.timer_expired)
    ScheduleTab(
        scheduleViewModel = scheduleViewModel,
        onPresenting = live.presenting,
        onAddLabel = { state.showAddLabelDialog = true },
        upcomingServiceLoad = service.upcomingServiceLoad,
        onLoadServiceNow = service.onLoadServiceNow,
        scheduleService = service.scheduleService,
        onSaveScheduleToCalendar = service.onSaveScheduleToCalendar,
        onAddScheduleToCalendar = service.onAddScheduleToCalendar,
        onPresentBible = this::presentBibleFromSchedule,
        onPresentSong = this::presentSongFromSchedule,
        onPresentPresentation = this::presentPresentationFromSchedule,
        onPresentPictures = this::presentPicturesFromSchedule,
        onPresentMedia = this::presentMediaFromSchedule,
        onPresentAnnouncement = { item -> presentAnnouncementFromSchedule(item, timerExpiredDefaultLabel) },
        onPresentLowerThird = this::presentLowerThirdFromSchedule,
        onPresentWebsite = this::presentWebsiteFromSchedule,
        onPresentDictionary = this::presentDictionaryFromSchedule,
        onPresentCue = service.onPresentCue,
        onPresentScene = { item -> presentScene(item.sceneId) },
        onItemClick = this::openScheduleItem,
        onEditLabel = { labelItem ->
            state.editingLabelItem = labelItem
            state.showAddLabelDialog = true
        },
        onActionsReady = { actions ->
            state.scheduleActions = actions
            currentOnScheduleActionsReady(
                scheduleActionsFrom(actions, presentScene = this::presentScene, playSlideshow = this::playSlideshow)
            )
        },
        onSelectedItemChanged = { id ->
            publish.onScheduleItemSelected(id)
        },
        onScheduleChanged = publish.onScheduleChanged,
        theme = theme,
        itemZoomPercent = appSettings.scheduleItemZoomPercent,
        onItemZoomChange = { percent ->
            onSettingsChange { settings -> settings.copy(scheduleItemZoomPercent = percent) }
        },
        legacyRowActions = appSettings.scheduleLegacyRowActions,
        onLegacyRowActionsChange = { legacy ->
            onSettingsChange { settings -> settings.copy(scheduleLegacyRowActions = legacy) }
        },
        toolbarIconSize = ScheduleToolbarIconSize.fromName(appSettings.scheduleToolbarIconSize),
        onToolbarIconSizeChange = { size ->
            onSettingsChange { settings -> settings.copy(scheduleToolbarIconSize = size.name) }
        },
        hiddenToolbarButtons = appSettings.hiddenScheduleButtons,
        onToggleToolbarButton = { button ->
            onSettingsChange { settings ->
                settings.copy(
                    hiddenScheduleButtons =
                        toggleHiddenScheduleButton(settings.hiddenScheduleButtons, button)
                )
            }
        },
        planningCenterSettings = appSettings.planningCenterSettings,
        onPlanningCenterTokensRefreshed = { accessToken, refreshToken, expiresAtEpochMs ->
            onSettingsChange { settings ->
                withPlanningCenterTokens(settings, accessToken, refreshToken, expiresAtEpochMs, personName = null)
            }
        },
        onPlanningCenterConnected = { accessToken, refreshToken, expiresAtEpochMs, personName ->
            onSettingsChange { settings ->
                withPlanningCenterTokens(settings, accessToken, refreshToken, expiresAtEpochMs, personName)
            }
        },
        onPlanningCenterDisconnect = {
            onSettingsChange { settings -> withPlanningCenterTokens(settings, "", "", 0L, personName = "") }
        }
    )
}

/** Puts a Canvas scene live and shows it in its tab — from a Schedule row or the menu. */
internal fun MainDesktopScope.presentScene(sceneId: String) {
    sceneViewModel.selectScene(sceneId)
    presenterManager.setActiveScene(sceneViewModel.scenes.find { it.id == sceneId })
    selectTab(Tabs.CANVAS)
    live.presenting(Presenting.CANVAS)
}

/** Starts a Schedule row's slideshow in the player that shows it, for [plays] passes. */
private fun MainDesktopScope.playSlideshow(item: ScheduleItem, plays: Int) {
    when (item) {
        is ScheduleItem.MediaItem ->
            mediaViewModel?.requestPlayback(plays, item.mediaUrl)
        is ScheduleItem.PictureItem ->
            picturesViewModel.requestPlayback(plays, item.folderPath)
        is ScheduleItem.PresentationItem ->
            presentationViewModel.requestPlayback(plays, item.filePath)
        else -> Unit
    }
}

/** A row clicked in the Schedule: opens it in the tab it belongs to, without going live. */
internal fun MainDesktopScope.openScheduleItem(item: ScheduleItem) {
    tabForScheduleItem(item)?.let { selectTab(it) }
    if (state.select(item)) return
    when (item) {
        is ScheduleItem.LabelItem -> {
            state.editingLabelItem = item
            state.showAddLabelDialog = true
        }
        is ScheduleItem.AnnouncementItem -> {
            onSettingsChange { settings -> withAnnouncementFrom(settings, item) }
        }
        is ScheduleItem.SceneItem -> {
            sceneViewModel.selectScene(item.sceneId)
        }
        is ScheduleItem.DictionaryItem -> {
            dictionaryViewModel.selectByNumber(item.number)
        }
        // Planned time that never goes on screen; nothing to open.
        else -> Unit
    }
}

/**
 * [settings] with Planning Center's tokens replaced — by a refresh, a new connection or a
 * disconnect (all blank). [personName] is left as it was when null, as a refresh does.
 */
private fun withPlanningCenterTokens(
    settings: AppSettings,
    accessToken: String,
    refreshToken: String,
    expiresAtEpochMs: Long,
    personName: String?,
): AppSettings = settings.copy(
    planningCenterSettings = settings.planningCenterSettings.copy(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenExpiresAtEpochMs = expiresAtEpochMs,
        connectedPersonName = personName ?: settings.planningCenterSettings.connectedPersonName,
    )
)

/**
 * Any Companion surface routed to the left sidebar, under the schedule.
 */
@Composable
private fun ScheduleSidebarCompanionPanel(
    connections: List<CompanionSatelliteSettings>,
    companionSatelliteViewModel: CompanionSatelliteViewModel,
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
                CompanionSurfacePanel(
                    connection = selectedLeftSidebarConnection,
                    placement = CompanionSurfacePlacement.LEFT_SIDEBAR,
                    viewModel = companionSatelliteViewModel,
                    modifier = Modifier.fillMaxWidth(),
                    sizeToContent = true
                )
            }
        }
    }
}
