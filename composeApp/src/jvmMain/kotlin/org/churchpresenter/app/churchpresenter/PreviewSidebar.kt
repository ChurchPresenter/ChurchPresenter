package org.churchpresenter.app.churchpresenter

import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import org.churchpresenter.liveoutput.withPreviewMode
import javax.swing.filechooser.FileNameExtensionFilter
import org.churchpresenter.sharedui.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.dialogs.PropsDialog
import org.churchpresenter.app.churchpresenter.dialogs.ClearGroupsDialog
import org.churchpresenter.app.churchpresenter.dialogs.ClearLayersMenuItems
import org.churchpresenter.liveoutput.clearGroup
import org.churchpresenter.liveoutput.clearLayer
import org.churchpresenter.strings.generated.resources.tooltip_clear_layers
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material3.DropdownMenu
import org.churchpresenter.liveoutput.setPropOn
import org.churchpresenter.liveoutput.propsOnAir
import org.churchpresenter.strings.generated.resources.props_picture
import org.churchpresenter.strings.generated.resources.tooltip_props
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.settings.ClearGroup
import org.churchpresenter.settings.MessageTemplate
import org.churchpresenter.settings.PropDefinition
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
import org.churchpresenter.liveoutput.preview.LivePreviewPanel
import org.churchpresenter.liveoutput.preview.PreviewGroupsPopover
import org.churchpresenter.app.churchpresenter.composables.QuickBackgroundTray
import org.churchpresenter.sharedui.composables.ToolbarKey
import org.churchpresenter.sharedui.composables.ToolbarKeyStyle
import org.churchpresenter.sharedui.composables.TooltipIconButton
import org.churchpresenter.profiles.previewOutputSize
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.liveoutput.clearMessage
import org.churchpresenter.liveoutput.messageOnAir
import org.churchpresenter.liveoutput.showMessage
import org.churchpresenter.app.churchpresenter.dialogs.MessageDialog
import org.churchpresenter.stt.STTManager
import org.churchpresenter.core.models.companion.CompanionSurfacePlacement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.activeLayout
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A Companion surface drawn in one of the main screen's sidebars: the connection it shows and where.
 * Built by the wiring, which holds the surface's view model; the sidebars only place it.
 */
internal typealias CompanionSurfaceSlot =
    @Composable (connection: CompanionSatelliteSettings, placement: CompanionSurfacePlacement) -> Unit

/** What the right-hand sidebar shows: the settings it reads, the live-preview copy of them and the URLs. */
internal class PreviewSidebarState(
    val appSettings: AppSettings,
    /** [appSettings] with the background possibly mirrored from an Instance Link primary — the preview only. */
    val livePreviewAppSettings: AppSettings,
    val activeQuickBackground: QuickBackground?,
    val serverUrl: String,
    val qaDisplayUrl: String,
    val showControl: SidebarShowControl = SidebarShowControl(),
)

/** What the right-hand sidebar's controls do. */
internal class PreviewSidebarActions(
    /** The clear button: every output off, the media paused, and any Instance Link follower told. */
    val onClearDisplay: () -> Unit,
    val onQuickBackgroundPicked: (QuickBackground?) -> Unit,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
)

/**
 * The right-hand sidebar: the display controls, the live preview, and any Companion surface routed
 * here, drawn at the panel's [geometry].
 *
 * [presenterManager] and [sttManager] are what the live preview renders from; neither is a view model.
 */
@Composable
internal fun PreviewSidebar(
    geometry: PreviewPanelGeometry,
    state: PreviewSidebarState,
    actions: PreviewSidebarActions,
    presenterManager: PresenterManager,
    sttManager: STTManager?,
    companionSurface: CompanionSurfaceSlot,
) {
    val appSettings = state.appSettings
    val onSettingsChange = actions.onSettingsChange
    val collapsed = geometry.collapsed
    val visibleFraction = geometry.visibleFraction
    val previewPanelPx = geometry.previewPanelPx
    val maxPreviewPx = geometry.maxPreviewPx
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
                onClearDisplay = actions.onClearDisplay,
                appSettings = appSettings,
                showControl = state.showControl,
                onSettingsChange = onSettingsChange,
                onEditPreviewLayout = { editingPreviewLayout = true },
            )
            // A layout filling the panel takes the column's spare height; otherwise it keeps its own.
            val previewFills = appSettings.projectionSettings.run { previewLayoutFillsPanel && activeLayout() != null }
            LivePreviewPanel(
                presenterManager = presenterManager,
                appSettings = state.livePreviewAppSettings,
                modifier = (if (previewFills) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth())
                    .guideTarget(GuideTargets.LIVE_PREVIEW),
                serverUrl = state.serverUrl,
                qaDisplayUrl = state.qaDisplayUrl,
                sttManager = sttManager,
                onSettingsChange = onSettingsChange,
                editingLayout = editingPreviewLayout,
                onDoneEditing = { editingPreviewLayout = false },
                isRelease = BuildConfig.IS_RELEASE,
            )
            QuickBackgroundTray(
                backgrounds = appSettings.quickBackgrounds,
                // A quick background is a full-screen background: its tile is a picture of the
                // output, so it is that output's shape.
                tileAspect = previewOutputSize(appSettings).aspectRatio,
                activeId = state.activeQuickBackground?.id,
                expanded = appSettings.quickBackgroundsExpanded,
                onExpandedChange = { open ->
                    onSettingsChange { s -> s.copy(quickBackgroundsExpanded = open) }
                },
                onPick = actions.onQuickBackgroundPicked,
                modifier = Modifier.padding(top = 8.dp),
            )
            RightSidebarCompanion(appSettings, previewFills, companionSurface)
        }
    }
}

/**
 * The Companion surfaces routed to the right sidebar, under a divider at the bottom of the column:
 * a chip row to pick one when there are several, and that surface's buttons.
 */
@Composable
private fun ColumnScope.RightSidebarCompanion(
    appSettings: AppSettings,
    previewFills: Boolean,
    companionSurface: CompanionSurfaceSlot,
) {
    val rightSidebarConnections = appSettings.companionSatelliteConnections
        .filter { it.showInRightSidebar && it.host.isNotBlank() }
    if (rightSidebarConnections.isNotEmpty()) {
        // Pushes everything below (divider + panel) down to the bottom of this
        // fillMaxHeight column instead of sitting right under the live preview
        // with empty space left below it -- unless the preview is filling that space.
        if (!previewFills) Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(8.dp))
        // Tagged from the divider down, which is also where the helper lamp keeps above.
        Column(modifier = Modifier.fillMaxWidth().guideTarget(GuideTargets.COMPANION_SIDEBAR)) {
            HorizontalDivider()
            var selectedRightSidebarId by remember(rightSidebarConnections.map { it.id }) {
                mutableStateOf(resolveSelectedConnectionId(null, rightSidebarConnections))
            }
            LaunchedEffect(rightSidebarConnections.map { it.id }) {
                selectedRightSidebarId =
                    resolveSelectedConnectionId(selectedRightSidebarId, rightSidebarConnections)
            }
            val selectedRightSidebarConnection =
                rightSidebarConnections.find { it.id == selectedRightSidebarId }
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
                    companionSurface(selectedRightSidebarConnection, CompanionSurfacePlacement.RIGHT_SIDEBAR)
                }
            }
        }
    }
}

/**
 * The Message, Props and clear-group editors the sidebar opens. In the app each is its own window; a test, which
 * cannot open one, provides stand-ins through [LocalSidebarDialogs] and drives their callbacks.
 */
internal interface SidebarDialogs {
    @Composable
    fun Message(
        isVisible: Boolean,
        templates: List<MessageTemplate>,
        onTemplatesChange: (List<MessageTemplate>) -> Unit,
        onAir: Cue.Message?,
        onGoLive: (Cue.Message) -> Unit,
        onClear: () -> Unit,
        onDismiss: () -> Unit,
    )

    @Composable
    fun Props(
        isVisible: Boolean,
        props: List<PropDefinition>,
        onPropsChange: (List<PropDefinition>) -> Unit,
        onAir: Set<String>,
        onSwitch: (id: String, on: Boolean) -> Unit,
        onChoosePicture: suspend () -> String?,
        onDismiss: () -> Unit,
    )

    @Composable
    fun ClearGroups(
        isVisible: Boolean,
        groups: List<ClearGroup>,
        onGroupsChange: (List<ClearGroup>) -> Unit,
        onClearGroup: (ClearGroup) -> Unit,
        onDismiss: () -> Unit,
    )
}

/** The editors as the app opens them: [MessageDialog], [PropsDialog] and [ClearGroupsDialog], each a window. */
internal object WindowedSidebarDialogs : SidebarDialogs {
    @Composable
    override fun Message(
        isVisible: Boolean,
        templates: List<MessageTemplate>,
        onTemplatesChange: (List<MessageTemplate>) -> Unit,
        onAir: Cue.Message?,
        onGoLive: (Cue.Message) -> Unit,
        onClear: () -> Unit,
        onDismiss: () -> Unit,
    ) = MessageDialog(isVisible, templates, onTemplatesChange, onAir, onGoLive, onClear, onDismiss)

    @Composable
    override fun Props(
        isVisible: Boolean,
        props: List<PropDefinition>,
        onPropsChange: (List<PropDefinition>) -> Unit,
        onAir: Set<String>,
        onSwitch: (id: String, on: Boolean) -> Unit,
        onChoosePicture: suspend () -> String?,
        onDismiss: () -> Unit,
    ) = PropsDialog(isVisible, props, onPropsChange, onAir, onSwitch, onChoosePicture, onDismiss)

    @Composable
    override fun ClearGroups(
        isVisible: Boolean,
        groups: List<ClearGroup>,
        onGroupsChange: (List<ClearGroup>) -> Unit,
        onClearGroup: (ClearGroup) -> Unit,
        onDismiss: () -> Unit,
    ) = ClearGroupsDialog(isVisible, groups, onGroupsChange, onClearGroup, onDismiss)
}

/** Where the sidebar's editors open: in their own windows, unless a test says otherwise. */
internal val LocalSidebarDialogs = staticCompositionLocalOf<SidebarDialogs> { WindowedSidebarDialogs }

/** The row of buttons over the preview: displays, clear, settings, message, props, macros, clear layers, take. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SidebarButtons(
    presenterManager: PresenterManager,
    onClearDisplay: () -> Unit,
    appSettings: AppSettings,
    showControl: SidebarShowControl,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onEditPreviewLayout: () -> Unit,
) {
    // Wraps rather than clips: the panel is resizable.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TooltipIconButton(
            painter = rememberVectorPainter(Icons.Default.Monitor),
            text = stringResource(Res.string.tooltip_toggle_displays),
            onClick = { presenterManager.togglePresenterWindow() },
            buttonSize = 36.dp,
            modifier = Modifier.guideTarget(GuideTargets.TOGGLE_OUTPUTS),
            iconTint = if (presenterManager.showPresenterWindow.value)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        TooltipIconButton(
            painter = painterResource(IconRes.drawable.ic_close),
            text = stringResource(Res.string.tooltip_clear_display),
            onClick = onClearDisplay,
            buttonSize = 36.dp,
            modifier = Modifier.guideTarget(GuideTargets.CLEAR_OUTPUT),
            iconTint = MaterialTheme.colorScheme.error
        )
        PreviewSettingsButton(appSettings.projectionSettings, onEditPreviewLayout) { updated ->
            onSettingsChange { s -> s.copy(projectionSettings = updated) }
        }
    }
    if (showControl.devMode) {
        DevModeBox {
            MessageButton(presenterManager, appSettings, onSettingsChange)
            PropsButton(presenterManager, appSettings, onSettingsChange)
            MacrosButton(appSettings, showControl, onSettingsChange)
            ClearLayersButton(presenterManager, appSettings, onSettingsChange)
            PreviewModeToggle(appSettings.projectionSettings.previewModeEnabled) { on ->
                onSettingsChange { it.withPreviewMode(on) }
            }
            if (appSettings.projectionSettings.previewModeEnabled) PreviewTakeButton(presenterManager)
        }
    }
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
    LocalSidebarDialogs.current.Message(
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
    LocalSidebarDialogs.current.Props(
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
    LocalSidebarDialogs.current.ClearGroups(
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
        modifier = Modifier.height(36.dp).testTag(PREVIEW_TAKE_TAG).guideTarget(GuideTargets.TAKE),
    ) {
        Text(stringResource(Res.string.preview_take))
    }
}

/** Test handle for preview mode's Take button. */
internal const val PREVIEW_TAKE_TAG = "preview_take"
