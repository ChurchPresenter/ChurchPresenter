package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.strings.generated.resources.tooltip_clear_display
import org.churchpresenter.strings.generated.resources.tooltip_preview_settings
import org.churchpresenter.strings.generated.resources.tooltip_toggle_displays
import org.churchpresenter.app.churchpresenter.composables.CompanionConnectionChipRow
import org.churchpresenter.app.churchpresenter.composables.CompanionSurfacePanel
import org.churchpresenter.app.churchpresenter.composables.LivePreviewPanel
import org.churchpresenter.app.churchpresenter.composables.PreviewGroupsPopover
import org.churchpresenter.app.churchpresenter.composables.QuickBackgroundTray
import org.churchpresenter.app.churchpresenter.composables.ToolbarKey
import org.churchpresenter.app.churchpresenter.composables.ToolbarKeyStyle
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.app.churchpresenter.dialogs.tabs.previewOutputSize
import org.churchpresenter.app.churchpresenter.viewmodel.CompanionSatelliteViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.STTManager
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.activeLayout
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The right-hand sidebar: the display controls, the live preview, and any Companion surface routed
 * here.
 *
 * One of MainDesktop's own layout pieces: the view models it takes are the main screen's, under the
 * standing exception AGENT.md records for the root screen's wiring and layout files.
 */
@Composable
internal fun PreviewSidebar(
    collapsed: Boolean,
    visibleFraction: Float,
    previewPanelPx: Float,
    maxPreviewPx: Float,
    presenterManager: PresenterManager,
    mediaViewModel: MediaViewModel?,
    instanceLinkSendClear: (() -> Unit)?,
    livePreviewAppSettings: AppSettings,
    appSettings: AppSettings,
    activeQuickBackground: QuickBackground?,
    onQuickBackgroundPicked: (QuickBackground?) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    serverUrl: String,
    qaDisplayUrl: String,
    sttManager: STTManager?,
    companionSatelliteViewModel: CompanionSatelliteViewModel,
) {
    // Whether the panel's layout is being edited, from the gear's Edit layout to the panel's Done.
    var editingPreviewLayout by remember { mutableStateOf(false) }
    if (isPanelRendered(collapsed, visibleFraction)) {
        Column(
            modifier = Modifier
                .sidePanelWidth { panelRenderWidthPx(previewPanelPx, maxPreviewPx, visibleFraction) }
                .fillMaxHeight()
                .padding(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TooltipIconButton(
                    painter = rememberVectorPainter(Icons.Default.Monitor),
                    text = stringResource(Res.string.tooltip_toggle_displays),
                    onClick = { presenterManager.togglePresenterWindow() },
                    buttonSize = 36.dp,
                    iconTint = if (presenterManager.showPresenterWindow.value)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                TooltipIconButton(
                    painter = painterResource(IconRes.drawable.ic_close),
                    text = stringResource(Res.string.tooltip_clear_display),
                    onClick = {
                        mediaViewModel?.pause()
                        presenterManager.requestClearDisplay()
                        instanceLinkSendClear?.invoke()
                    },
                    buttonSize = 36.dp,
                    iconTint = MaterialTheme.colorScheme.error
                )
                PreviewSettingsButton(appSettings.projectionSettings, { editingPreviewLayout = true }) { updated ->
                    onSettingsChange { s -> s.copy(projectionSettings = updated) }
                }
            }
            // A layout filling the panel takes the column's spare height; otherwise it keeps its own.
            val previewFills = appSettings.projectionSettings.run { previewLayoutFillsPanel && activeLayout() != null }
            LivePreviewPanel(
                presenterManager = presenterManager,
                appSettings = livePreviewAppSettings,
                modifier = if (previewFills) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
                serverUrl = serverUrl,
                qaDisplayUrl = qaDisplayUrl,
                sttManager = sttManager,
                onSettingsChange = onSettingsChange,
                editingLayout = editingPreviewLayout,
                onDoneEditing = { editingPreviewLayout = false },
            )
            QuickBackgroundTray(
                backgrounds = appSettings.quickBackgrounds,
                // A quick background is a full-screen background: its tile is a picture of the
                // output, so it is that output's shape.
                tileAspect = previewOutputSize(appSettings).aspectRatio,
                activeId = activeQuickBackground?.id,
                expanded = appSettings.quickBackgroundsExpanded,
                onExpandedChange = { open ->
                    onSettingsChange { s -> s.copy(quickBackgroundsExpanded = open) }
                },
                onPick = onQuickBackgroundPicked,
                modifier = Modifier.padding(top = 8.dp),
            )
            val rightSidebarConnections = appSettings.companionSatelliteConnections
                .filter { it.showInRightSidebar && it.host.isNotBlank() }
            if (rightSidebarConnections.isNotEmpty()) {
                // Pushes everything below (divider + panel) down to the bottom of this
                // fillMaxHeight column instead of sitting right under the live preview
                // with empty space left below it -- unless the preview is filling that space.
                if (!previewFills) Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                var selectedRightSidebarId by remember(rightSidebarConnections.map { it.id }) {
                    mutableStateOf(resolveSelectedConnectionId(null, rightSidebarConnections))
                }
                LaunchedEffect(rightSidebarConnections.map { it.id }) {
                    selectedRightSidebarId =
                        resolveSelectedConnectionId(selectedRightSidebarId, rightSidebarConnections)
                }
                val selectedRightSidebarConnection = rightSidebarConnections.find { it.id == selectedRightSidebarId }
                // No weight here — sizeToContent sizes this panel to exactly what its
                // configured grid needs rather than stretching to fill all remaining
                // space below the (fixed-size) live preview above it.
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    if (rightSidebarConnections.size > 1) {
                        CompanionConnectionChipRow(
                            connections = rightSidebarConnections,
                            selectedId = selectedRightSidebarId,
                            onSelect = { selectedRightSidebarId = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (selectedRightSidebarConnection != null) {
                        CompanionSurfacePanel(
                            connection = selectedRightSidebarConnection,
                            placement = CompanionSurfacePlacement.RIGHT_SIDEBAR,
                            viewModel = companionSatelliteViewModel,
                            modifier = Modifier.fillMaxWidth(),
                            sizeToContent = true
                        )
                    }
                }
            }
        }
    }
}

/** The gear beside the clear button: opens the editor for how the preview panel is arranged. */
@Composable
private fun PreviewSettingsButton(
    proj: ProjectionSettings,
    onEditLayout: () -> Unit,
    onChange: (ProjectionSettings) -> Unit,
) {
    Box {
        var open by remember { mutableStateOf(false) }
        ToolbarKey(
            painter = rememberVectorPainter(Icons.Outlined.DisplaySettings),
            text = stringResource(Res.string.tooltip_preview_settings),
            onClick = { open = true },
            style = ToolbarKeyStyle.PANEL_TOGGLE,
            open = open,
            buttonSize = 40.dp,
        )
        PreviewGroupsPopover(
            expanded = open,
            onDismiss = { open = false },
            proj = proj,
            onChange = onChange,
            onEditLayout = onEditLayout,
        )
    }
}
