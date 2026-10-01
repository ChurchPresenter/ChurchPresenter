package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.remember
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.app.churchpresenter.LocalWentLive
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.composables.CameraHost
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.AlertDialog
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import org.churchpresenter.app.churchpresenter.composables.SceneCanvas
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.cancel
import churchpresenter.composeapp.generated.resources.canvas_dual_layout_off_confirm
import churchpresenter.composeapp.generated.resources.canvas_layer_placement_in
import churchpresenter.composeapp.generated.resources.canvas_layout_landscape_short
import churchpresenter.composeapp.generated.resources.canvas_layout_portrait_short
import churchpresenter.composeapp.generated.resources.remove
import churchpresenter.composeapp.generated.resources.canvas_layer_outside
import churchpresenter.composeapp.generated.resources.canvas_layer_partly_outside

internal const val CANVAS_SCENE_LIST_WEIGHT = 0.4f
internal const val CANVAS_SOURCE_LIST_WEIGHT = 0.6f
internal const val CANVAS_HIDDEN_SOURCE_ALPHA = 0.5f
internal const val CANVAS_ASPECT_EPSILON = 0.01f

/**
 * Where a left-panel drag's final width is written back — mirrors `MainDesktop.withScheduleWidth`,
 * pulled out of the composable the same way so the windowed-vs-maximized branch is testable without
 * driving an actual drag gesture through the divider.
 */
internal fun withCanvasLeftPanelWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(canvasLeftPanelWidthDp = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(canvasLeftPanelWidthDp = widthDp))

internal fun withCanvasRightPanelWidth(settings: AppSettings, isMaximized: Boolean, widthDp: Int): AppSettings =
    if (isMaximized) settings.copy(maximizedLayout = settings.maximizedLayout.copy(canvasRightPanelWidthDp = widthDp))
    else settings.copy(windowedLayout = settings.windowedLayout.copy(canvasRightPanelWidthDp = widthDp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CanvasTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    presenterManager: PresenterManager,
    sceneViewModel: SceneViewModel,
    onAddToSchedule: (sceneId: String, sceneName: String) -> Unit,
    /** Save preset, to the left of Add to Schedule: the same scene, kept for the Calendar Manager. */
    onSavePreset: ((sceneId: String, sceneName: String) -> Unit)? = null,
    dialogDismissSignal: Int = 0,
    /** The machine the source panel's camera section describes, or null to ask this one — a test pins it. */
    cameraHost: CameraHost? = null,
) {
    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)
    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout
    val sourceNames = rememberCanvasSourceNames()
    val wentLive = LocalWentLive.current
    val state = rememberCanvasTabState(currentLayout, isMaximized, density)
    // Remembered, keyed on everything it holds: a new scope on every recomposition would hand the
    // pieces new lambdas each time, and a click handler keyed on its lambda would restart.
    val scope = remember(
        appSettings, presenterManager, onAddToSchedule, onSavePreset, cameraHost, density,
        onSettingsChangeState, isMaximized, sourceNames, wentLive, state
    ) {
        CanvasTabScope(
            appSettings = appSettings,
            presenterManager = presenterManager,
            onAddToSchedule = onAddToSchedule,
            onSavePreset = onSavePreset,
            cameraHost = cameraHost,
            density = density,
            onSettingsChangeState = onSettingsChangeState,
            isMaximized = isMaximized,
            sourceNames = sourceNames,
            wentLive = wentLive,
            state = state,
        )
    }
    with(scope) {
        confirmSingleLayoutSceneId?.let { sceneId ->
            SingleLayoutConfirmDialog(
                onConfirm = {
                    sceneViewModel.setDualLayout(sceneId, false)
                    confirmSingleLayoutSceneId = null
                },
                onDismiss = { confirmSingleLayoutSceneId = null },
            )
        }
        // focusRequester is remembered with the rest of the tab's state.
        LaunchedEffect(dialogDismissSignal) {
            focusRequester.requestFocus()
        }
        // Re-grab focus whenever a source is selected so Delete key works right after clicking canvas items
        LaunchedEffect(sceneViewModel.selectedSourceId.value) {
            if (sceneViewModel.selectedSourceId.value != null) {
                focusRequester.requestFocus()
            }
        }

        val shortcuts = LocalShortcuts.current

        Row(
            modifier = modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        shortcuts.matches(ShortcutAction.CANVAS_DELETE_SOURCE, event) &&
                        renamingSceneId == null
                    ) {
                        val sourceId = sceneViewModel.selectedSourceId.value
                        if (sourceId != null) {
                            sceneViewModel.removeSource(sourceId)
                            true
                        } else false
                    } else false
                }
        ) {
            // Left panel: Scene selector + Source list
            CanvasLeftPanel(sceneViewModel)

            // Draggable separator: left panel | center
            DragHandle(onDragEnd = { saveLeftPanel() }) { delta ->
                leftPanelPx = (leftPanelPx + delta).coerceAtLeast(with(density) { 120.dp.toPx() })
            }

            // Center panel: Toolbar + Canvas
            CanvasCenterPanel(sceneViewModel, Modifier.weight(1f))

            // Draggable separator: center | right panel
            DragHandle(onDragEnd = { saveRightPanel() }) { delta ->
                rightPanelPx = (rightPanelPx - delta).coerceAtLeast(with(density) { 120.dp.toPx() })
            }

            // Right panel: Properties
            CanvasPropertiesPanel(sceneViewModel)
        }
    }
}

/** One output a scene can be sized to match: its name and its size. */
internal data class CanvasOutputSize(val label: String, val width: Int, val height: Int)

/** Test handle for the off-canvas banner's Bring into view button. */
internal const val CANVAS_BRING_INTO_VIEW_TAG = "canvas_bring_into_view"

/** Test handles for the two canvases of a scene with a second layout: this plus `main` or `alternate`. */
internal const val CANVAS_LAYOUT_TAG_PREFIX = "canvas_layout_"

/**
 * One of the scene's layouts, editable. A move or resize goes to the layout it was made in; a shape
 * drawn on either is added to the scene, where it starts at the drawn place in both.
 */
@Composable
internal fun EditorCanvas(
    modifier: Modifier,
    layout: EditorLayout,
    selectedSourceId: String?,
    sceneViewModel: SceneViewModel,
    activeTool: String,
    drawingStrokeColor: String,
    drawingFillColor: String,
    drawingStrokeWidth: Float,
) {
    SceneCanvas(
        modifier = modifier,
        scene = layout.scene,
        selectedSourceId = selectedSourceId,
        onSourceSelected = { sceneViewModel.selectSource(it) },
        onTransformChanged = { sourceId, transform ->
            sceneViewModel.updateTransform(sourceId, transform, alternate = layout.isAlternate)
        },
        isInteractive = true,
        activeTool = activeTool,
        drawingStrokeColor = drawingStrokeColor,
        drawingFillColor = drawingFillColor,
        drawingStrokeWidth = drawingStrokeWidth,
        onShapeDrawn = { shape -> sceneViewModel.addSource(shape) },
        autoLayout = false,
    )
}

/**
 * What a layer's warning says, keyed by (landscape layout, placement). With one layout the layout is
 * not named; with two, each line says which it is about.
 */
@Composable
internal fun placementTexts(dual: Boolean): Map<Pair<Boolean, CanvasPlacement>, String> {
    val outside = stringResource(Res.string.canvas_layer_outside)
    val partly = stringResource(Res.string.canvas_layer_partly_outside)
    val landscape = stringResource(Res.string.canvas_layout_landscape_short)
    val portrait = stringResource(Res.string.canvas_layout_portrait_short)
    val landscapeOutside = stringResource(Res.string.canvas_layer_placement_in, landscape, outside)
    val landscapePartly = stringResource(Res.string.canvas_layer_placement_in, landscape, partly)
    val portraitOutside = stringResource(Res.string.canvas_layer_placement_in, portrait, outside)
    val portraitPartly = stringResource(Res.string.canvas_layer_placement_in, portrait, partly)
    return mapOf(
        (true to CanvasPlacement.OUTSIDE) to if (dual) landscapeOutside else outside,
        (true to CanvasPlacement.PARTLY_OUTSIDE) to if (dual) landscapePartly else partly,
        (false to CanvasPlacement.OUTSIDE) to if (dual) portraitOutside else outside,
        (false to CanvasPlacement.PARTLY_OUTSIDE) to if (dual) portraitPartly else partly,
    )
}

/** Asks before a scene's second layout, and every position in it, is thrown away. */
@Composable
internal fun SingleLayoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(stringResource(Res.string.canvas_dual_layout_off_confirm), style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            GhostButton(shape = AppShape(6.dp), onClick = onConfirm) {
                Text(stringResource(Res.string.remove), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            GhostButton(shape = AppShape(6.dp), onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
