package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.composables.CameraHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_source_browser
import churchpresenter.composeapp.generated.resources.canvas_source_color
import churchpresenter.composeapp.generated.resources.canvas_source_image
import churchpresenter.composeapp.generated.resources.canvas_source_text
import churchpresenter.composeapp.generated.resources.canvas_source_video
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.canvas_source_timer
import churchpresenter.composeapp.generated.resources.canvas_source_qrcode
import churchpresenter.composeapp.generated.resources.background_camera_option
import churchpresenter.composeapp.generated.resources.canvas_source_screen_capture
import churchpresenter.composeapp.generated.resources.canvas_source_ndi
import churchpresenter.composeapp.generated.resources.canvas_source_omt
import churchpresenter.composeapp.generated.resources.canvas_source_bible
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Density
import org.churchpresenter.settings.WindowLayoutSettings

/**
 * Everything the Canvas tab's pieces read, for one composition: its parameters, the panel sizes and
 * editing state it remembers, the scene and source selected, and the localised source names.
 */
@Suppress("LongParameterList")
internal class CanvasTabScope(
    val appSettings: AppSettings,
    val presenterManager: PresenterManager,
    val onAddToSchedule: (sceneId: String, sceneName: String) -> Unit,
    val onSavePreset: ((sceneId: String, sceneName: String) -> Unit)?,
    val cameraHost: CameraHost?,
    val density: Density,
    val onSettingsChangeState: State<((AppSettings) -> AppSettings) -> Unit>,
    val isMaximized: Boolean,
    val sourceNames: CanvasSourceNames,
    val wentLive: (ScheduleItem) -> Unit,
    private val state: CanvasTabState,
) {
    var leftPanelPx by state.leftPanelPx
    var rightPanelPx by state.rightPanelPx
    var renamingSceneId by state.renamingSceneId
    // The scene whose second layout is about to be removed, while the confirmation is open.
    var confirmSingleLayoutSceneId by state.confirmSingleLayoutSceneId
    var renameText by state.renameText
    // Drawing tool state
    var activeTool by state.activeTool
    var drawingStrokeColor by state.drawingStrokeColor
    var drawingFillColor by state.drawingFillColor
    var drawingStrokeWidth by state.drawingStrokeWidth
    val focusRequester = state.focusRequester

    val strImage get() = sourceNames.strImage
    val strText get() = sourceNames.strText
    val strColor get() = sourceNames.strColor
    val strVideo get() = sourceNames.strVideo
    val strTimer get() = sourceNames.strTimer
    val strQrCode get() = sourceNames.strQrCode
    val strCamera get() = sourceNames.strCamera
    val strScreenCapture get() = sourceNames.strScreenCapture
    val strNdi get() = sourceNames.strNdi
    val strOmt get() = sourceNames.strOmt
    val strBrowser get() = sourceNames.strBrowser
    val strBible get() = sourceNames.strBible

    fun saveLeftPanel() {
        val dp = with(density) { leftPanelPx.toDp().value.toInt() }
        onSettingsChangeState.value { s -> withCanvasLeftPanelWidth(s, isMaximized, dp) }
    }

    fun saveRightPanel() {
        val dp = with(density) { rightPanelPx.toDp().value.toInt() }
        onSettingsChangeState.value { s -> withCanvasRightPanelWidth(s, isMaximized, dp) }
    }

}

/** The source types' default names, localised in composable scope for the menu's onClick lambdas. */
@Suppress("LongParameterList")
internal class CanvasSourceNames(
    val strImage: String,
    val strText: String,
    val strColor: String,
    val strVideo: String,
    val strTimer: String,
    val strQrCode: String,
    val strCamera: String,
    val strScreenCapture: String,
    val strNdi: String,
    val strOmt: String,
    val strBrowser: String,
    val strBible: String,
)

@Composable
internal fun rememberCanvasSourceNames(): CanvasSourceNames {
    // Localised default source names (resolved in composable scope so they
    // can be captured by non-composable onClick lambdas below)
    val strImage         = stringResource(Res.string.canvas_source_image)
    val strText          = stringResource(Res.string.canvas_source_text)
    val strColor         = stringResource(Res.string.canvas_source_color)
    val strVideo         = stringResource(Res.string.canvas_source_video)
    val strTimer         = stringResource(Res.string.canvas_source_timer)
    val strQrCode        = stringResource(Res.string.canvas_source_qrcode)
    val strCamera        = stringResource(Res.string.background_camera_option)
    val strScreenCapture = stringResource(Res.string.canvas_source_screen_capture)
    val strNdi           = stringResource(Res.string.canvas_source_ndi)
    val strOmt           = stringResource(Res.string.canvas_source_omt)
    val strBrowser       = stringResource(Res.string.canvas_source_browser)
    val strBible         = stringResource(Res.string.canvas_source_bible)
    return remember(
        strImage, strText, strColor, strVideo, strTimer, strQrCode, strCamera, strScreenCapture, strNdi, strOmt,
        strBrowser, strBible
    ) {
        CanvasSourceNames(
            strImage = strImage,
            strText = strText,
            strColor = strColor,
            strVideo = strVideo,
            strTimer = strTimer,
            strQrCode = strQrCode,
            strCamera = strCamera,
            strScreenCapture = strScreenCapture,
            strNdi = strNdi,
            strOmt = strOmt,
            strBrowser = strBrowser,
            strBible = strBible,
        )
    }
}

/** The Canvas tab's remembered editing state: panel widths, renaming, and the drawing tool. */
@Suppress("LongParameterList")
internal class CanvasTabState(
    val leftPanelPx: MutableState<Float>,
    val rightPanelPx: MutableState<Float>,
    val renamingSceneId: MutableState<String?>,
    val confirmSingleLayoutSceneId: MutableState<String?>,
    val renameText: MutableState<String>,
    val activeTool: MutableState<String>,
    val drawingStrokeColor: MutableState<String>,
    val drawingFillColor: MutableState<String>,
    val drawingStrokeWidth: MutableState<Float>,
    val focusRequester: FocusRequester,
)

@Composable
internal fun rememberCanvasTabState(
    currentLayout: WindowLayoutSettings,
    isMaximized: Boolean,
    density: Density,
): CanvasTabState {
    val leftPanelPx = remember(currentLayout.canvasLeftPanelWidthDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.canvasLeftPanelWidthDp.dp.toPx() })
    }
    val rightPanelPx = remember(currentLayout.canvasRightPanelWidthDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.canvasRightPanelWidthDp.dp.toPx() })
    }
    val renamingSceneId = remember { mutableStateOf<String?>(null) }
    val confirmSingleLayoutSceneId = remember { mutableStateOf<String?>(null) }
    val renameText = remember { mutableStateOf("") }
    val activeTool = remember { mutableStateOf("select") }
    val drawingStrokeColor = remember { mutableStateOf("#FFFFFF") }
    val drawingFillColor = remember { mutableStateOf("#00000000") }
    val drawingStrokeWidth = remember { mutableStateOf(DEFAULT_DRAWING_STROKE_WIDTH) }
    val focusRequester = remember { FocusRequester() }
    return remember(
        leftPanelPx, rightPanelPx, renamingSceneId, confirmSingleLayoutSceneId, renameText, activeTool,
        drawingStrokeColor, drawingFillColor, drawingStrokeWidth, focusRequester
    ) {
        CanvasTabState(
            leftPanelPx = leftPanelPx,
            rightPanelPx = rightPanelPx,
            renamingSceneId = renamingSceneId,
            confirmSingleLayoutSceneId = confirmSingleLayoutSceneId,
            renameText = renameText,
            activeTool = activeTool,
            drawingStrokeColor = drawingStrokeColor,
            drawingFillColor = drawingFillColor,
            drawingStrokeWidth = drawingStrokeWidth,
            focusRequester = focusRequester,
        )
    }
}

private const val DEFAULT_DRAWING_STROKE_WIDTH = 3f
