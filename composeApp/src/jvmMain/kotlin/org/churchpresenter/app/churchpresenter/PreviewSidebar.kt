package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.dialogs.ControlPanelData
import org.churchpresenter.app.churchpresenter.dialogs.ControlPanelActions
import org.churchpresenter.app.churchpresenter.dialogs.ControlDialog
import org.churchpresenter.controlin.MidiPorts
import org.churchpresenter.controlin.ControlHub
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import javax.swing.filechooser.FileNameExtensionFilter
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.dialogs.PropsDialog
import org.churchpresenter.app.churchpresenter.dialogs.ClearGroupsDialog
import org.churchpresenter.app.churchpresenter.dialogs.ClearLayersMenuItems
import org.churchpresenter.app.churchpresenter.viewmodel.clearGroup
import org.churchpresenter.app.churchpresenter.viewmodel.clearLayer
import org.churchpresenter.strings.generated.resources.tooltip_clear_layers
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material3.DropdownMenu
import org.churchpresenter.app.churchpresenter.viewmodel.setPropOn
import org.churchpresenter.app.churchpresenter.viewmodel.propsOnAir
import org.churchpresenter.strings.generated.resources.props_picture
import org.churchpresenter.strings.generated.resources.tooltip_macros
import org.churchpresenter.strings.generated.resources.tooltip_props
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.strings.generated.resources.preview_take
import org.churchpresenter.strings.generated.resources.tooltip_clear_display
import org.churchpresenter.strings.generated.resources.tooltip_message
import org.churchpresenter.strings.generated.resources.tooltip_preview_settings
import org.churchpresenter.strings.generated.resources.tooltip_toggle_displays
import org.churchpresenter.companionsurface.CompanionConnectionChipRow
import org.churchpresenter.companionsurface.CompanionSurfacePanel
import org.churchpresenter.app.churchpresenter.composables.LivePreviewPanel
import org.churchpresenter.app.churchpresenter.composables.PreviewGroupsPopover
import org.churchpresenter.app.churchpresenter.composables.QuickBackgroundTray
import org.churchpresenter.sharedui.composables.ToolbarKey
import org.churchpresenter.sharedui.composables.ToolbarKeyStyle
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.profiles.previewOutputSize
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.clearMessage
import org.churchpresenter.app.churchpresenter.viewmodel.messageOnAir
import org.churchpresenter.app.churchpresenter.viewmodel.showMessage
import org.churchpresenter.app.churchpresenter.dialogs.MacrosDialog
import org.churchpresenter.app.churchpresenter.dialogs.MessageDialog
import org.churchpresenter.stt.STTManager
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.Macro
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
    showControl: SidebarShowControl = SidebarShowControl(),
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
            SidebarButtons(
                presenterManager = presenterManager,
                mediaViewModel = mediaViewModel,
                instanceLinkSendClear = instanceLinkSendClear,
                appSettings = appSettings,
                showControl = showControl,
                onSettingsChange = onSettingsChange,
                onEditPreviewLayout = { editingPreviewLayout = true },
            )
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

/** The row of buttons over the preview: displays, clear, settings, message, props, macros, clear layers, take. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongParameterList")
private fun SidebarButtons(
    presenterManager: PresenterManager,
    mediaViewModel: MediaViewModel?,
    instanceLinkSendClear: (() -> Unit)?,
    appSettings: AppSettings,
    showControl: SidebarShowControl,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onEditPreviewLayout: () -> Unit,
) {
    // Wraps rather than clips: the panel is resizable and these buttons need ~320dp together.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
        PreviewSettingsButton(appSettings.projectionSettings, onEditPreviewLayout) { updated ->
            onSettingsChange { s -> s.copy(projectionSettings = updated) }
        }
        MessageButton(presenterManager, appSettings, onSettingsChange)
        PropsButton(presenterManager, appSettings, onSettingsChange)
        MacrosButton(appSettings, showControl, onSettingsChange)
        ClearLayersButton(presenterManager, appSettings, onSettingsChange)
        if (appSettings.projectionSettings.previewModeEnabled) PreviewTakeButton(presenterManager)
    }
}

/** Opens the Message dialog; lit while a message is on air. */
@Composable
private fun MessageButton(
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val onAir = presenterManager.messageOnAir
    TooltipIconButton(
        painter = rememberVectorPainter(Icons.AutoMirrored.Filled.Message),
        text = stringResource(Res.string.tooltip_message),
        onClick = { open = true },
        buttonSize = 36.dp,
        iconTint = if (onAir != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(MESSAGE_BUTTON_TAG),
    )
    MessageDialog(
        isVisible = open,
        templates = appSettings.messageTemplates,
        onTemplatesChange = { templates -> onSettingsChange { it.copy(messageTemplates = templates) } },
        onAir = onAir,
        onGoLive = { message ->
            presenterManager.showMessage(message)
            presenterManager.setShowPresenterWindow(true)
        },
        onClear = { presenterManager.clearMessage() },
        onDismiss = { open = false },
    )
}

internal const val MESSAGE_BUTTON_TAG = "preview_message"

/** Opens the Props dialog; lit while a prop is up. */
@Composable
private fun PropsButton(
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val onAir = presenterManager.propsOnAir
    val chooseTitle = stringResource(Res.string.props_picture)
    TooltipIconButton(
        painter = rememberVectorPainter(Icons.Outlined.Layers),
        text = stringResource(Res.string.tooltip_props),
        onClick = { open = true },
        buttonSize = 36.dp,
        iconTint = if (onAir.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(PROPS_BUTTON_TAG),
    )
    PropsDialog(
        isVisible = open,
        props = appSettings.props,
        onPropsChange = { props -> onSettingsChange { it.copy(props = props) } },
        onAir = onAir,
        onSwitch = { id, on ->
            presenterManager.setPropOn(id, on)
            if (on) presenterManager.setShowPresenterWindow(true)
        },
        onChoosePicture = {
            FileChooser.platformInstance.chooseSingle(
                path = null,
                filters = listOf(FileNameExtensionFilter(chooseTitle, "png", "jpg", "jpeg", "webp", "gif", "bmp")),
                title = chooseTitle,
                selectDirectory = false,
            )?.toString()
        },
        onDismiss = { open = false },
    )
}

internal const val PROPS_BUTTON_TAG = "preview_props"

/** What the sidebar's show-control buttons need: the schedule's rows, running a macro, and the MIDI/OSC hub. */
internal class SidebarShowControl(
    val rows: List<ScheduleItem> = emptyList(),
    val onRunMacro: (Macro) -> Unit = {},
    val controlHub: ControlHub? = null,
)

/** Opens the Macros dialog: the named action lists, to run or edit. */
@Composable
private fun MacrosButton(
    appSettings: AppSettings,
    showControl: SidebarShowControl,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var controlOpen by remember { mutableStateOf(false) }
    TooltipIconButton(
        painter = rememberVectorPainter(Icons.Outlined.PlayCircle),
        text = stringResource(Res.string.tooltip_macros),
        onClick = { open = true },
        buttonSize = 36.dp,
        iconTint = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(MACROS_BUTTON_TAG),
    )
    MacrosDialog(
        isVisible = open,
        macros = appSettings.macros,
        rows = showControl.rows,
        onMacrosChange = { macros -> onSettingsChange { it.copy(macros = macros) } },
        onRun = showControl.onRunMacro,
        onDismiss = { open = false },
        onOpenControl = showControl.controlHub?.let { { controlOpen = true } },
    )
    showControl.controlHub?.let { hub ->
        ControlSetup(controlOpen, hub, appSettings, showControl.rows, onSettingsChange) { controlOpen = false }
    }
}

/** The MIDI & OSC dialog over [hub]: the devices there are, how the ports stand, Learn, and saving. */
@Composable
private fun ControlSetup(
    visible: Boolean,
    hub: ControlHub,
    appSettings: AppSettings,
    scheduleRows: List<ScheduleItem>,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val status by hub.status.collectAsState()
    val learning by hub.isLearning.collectAsState()
    // Asking the system for its devices can block, so it is done off the UI thread, once per opening.
    val devices by produceState(emptyList<String>() to emptyList(), visible) {
        if (visible) value = withContext(Dispatchers.IO) { MidiPorts.inputNames() to MidiPorts.outputNames() }
    }
    ControlDialog(
        isVisible = visible,
        settings = appSettings.control,
        data = ControlPanelData(status, devices.first, devices.second, scheduleRows),
        actions = ControlPanelActions(
            isLearning = learning,
            onLearn = hub::learn,
            onCancelLearn = hub::cancelLearn,
            onSave = { saved -> onSettingsChange { it.copy(control = saved) } },
        ),
        onDismiss = onDismiss,
    )
}

internal const val MACROS_BUTTON_TAG = "preview_macros"

/** Drops down the clear groups and the layers to clear one by one, and opens the group editor. */
@Composable
private fun ClearLayersButton(
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Outlined.LayersClear),
            text = stringResource(Res.string.tooltip_clear_layers),
            onClick = { menuOpen = true },
            buttonSize = 36.dp,
            modifier = Modifier.testTag(CLEAR_LAYERS_BUTTON_TAG),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ClearLayersMenuItems(
                groups = appSettings.clearGroups,
                onAir = presenterManager.program.value.keys,
                onClearGroup = { menuOpen = false; presenterManager.clearGroup(it) },
                onClearLayer = { menuOpen = false; presenterManager.clearLayer(it) },
                onEdit = { menuOpen = false; editing = true },
            )
        }
    }
    ClearGroupsDialog(
        isVisible = editing,
        groups = appSettings.clearGroups,
        onGroupsChange = { groups -> onSettingsChange { it.copy(clearGroups = groups) } },
        onClearGroup = { presenterManager.clearGroup(it) },
        onDismiss = { editing = false },
    )
}

internal const val CLEAR_LAYERS_BUTTON_TAG = "preview_clear_layers"

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

/**
 * Preview mode's Take, while preview mode is on: what is cued on Preview goes on air. It can be
 * pressed only while something is cued. Preview mode itself is switched in System settings.
 */
@Composable
internal fun RowScope.PreviewTakeButton(presenterManager: PresenterManager) {
    val bus = presenterManager.previewBus
    Spacer(Modifier.weight(1f))
    Button(
        onClick = bus::take,
        enabled = bus.anythingCued,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.height(36.dp).testTag(PREVIEW_TAKE_TAG),
    ) {
        Text(stringResource(Res.string.preview_take))
    }
}

/** Test handle for preview mode's Take button. */
internal const val PREVIEW_TAKE_TAG = "preview_take"
