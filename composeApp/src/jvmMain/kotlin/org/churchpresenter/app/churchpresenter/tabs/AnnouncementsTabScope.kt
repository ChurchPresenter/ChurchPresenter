package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.app.churchpresenter.stageMonitorScreenIndices
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.anim_slide_from_bottom
import churchpresenter.composeapp.generated.resources.anim_slide_from_left
import churchpresenter.composeapp.generated.resources.anim_slide_from_right
import churchpresenter.composeapp.generated.resources.anim_slide_from_top
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.bottom_center
import churchpresenter.composeapp.generated.resources.bottom_left
import churchpresenter.composeapp.generated.resources.bottom_right
import churchpresenter.composeapp.generated.resources.center
import churchpresenter.composeapp.generated.resources.center_left
import churchpresenter.composeapp.generated.resources.center_right
import churchpresenter.composeapp.generated.resources.timer_expired
import churchpresenter.composeapp.generated.resources.timer_minutes
import churchpresenter.composeapp.generated.resources.timer_pause
import churchpresenter.composeapp.generated.resources.timer_reset
import churchpresenter.composeapp.generated.resources.timer_hours
import churchpresenter.composeapp.generated.resources.timer_seconds
import churchpresenter.composeapp.generated.resources.timer_start
import churchpresenter.composeapp.generated.resources.top_center
import churchpresenter.composeapp.generated.resources.top_left
import churchpresenter.composeapp.generated.resources.top_right
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.AnnouncementsViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Density
import org.churchpresenter.settings.WindowLayoutSettings

/**
 * Everything the Announcements tab's pieces read, for one composition: its parameters, its
 * ViewModel, the labels it resolves, the panel sizes it remembers, and what is derived from them.
 */
@Suppress("LongParameterList")
internal class AnnouncementsTabScope(
    val appSettings: AppSettings,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val presenterManager: PresenterManager?,
    val onAddToSchedule: ((settings: AnnouncementsSettings) -> Unit)?,
    val onSavePreset: ((settings: AnnouncementsSettings) -> Unit)?,
    val viewModel: AnnouncementsViewModel,
    val availableFonts: List<String>,
    val labels: AnnouncementsLabels,
    val density: Density,
    val onSettingsChangeState: State<((AppSettings) -> AppSettings) -> Unit>,
    val isMaximized: Boolean,
    panels: AnnouncementsPanelState,
) {
    val timerExpiredLabel get() = labels.timerExpiredLabel
    val startLabel get() = labels.startLabel
    val pauseLabel get() = labels.pauseLabel
    val resetLabel get() = labels.resetLabel
    val hrLabel get() = labels.hrLabel
    val minLabel get() = labels.minLabel
    val secLabel get() = labels.secLabel
    val slideFromLeftText get() = labels.slideFromLeftText
    val slideFromRightText get() = labels.slideFromRightText
    val slideFromTopText get() = labels.slideFromTopText
    val slideFromBottomText get() = labels.slideFromBottomText
    val fadeText get() = labels.fadeText
    val noneText get() = labels.noneText
    val positions get() = labels.positions
    val animItems get() = labels.animItems

    var leftPanelPx by panels.leftPanelPx
    var textHeightPx by panels.textHeightPx
    var naturalTextHeightPx by panels.naturalTextHeightPx
    var textCardHeightPx by panels.textCardHeightPx
    var twoColHeightPx by panels.twoColHeightPx
    var twoColWidthPx by panels.twoColWidthPx

    // Screens configured as Stage Monitor — locking them to Announcements shows this content
    // there without disturbing whatever the main projection screen(s) are currently live with.
    // The lock persists (even as other tabs go live elsewhere) until toggled off again or Escape
    // is pressed (see MainDesktop.kt's global Escape handler).
    // If Stage Monitor is the ONLY configured screen there's nothing else to protect from being
    // locked out, so the button instead behaves as a plain Go Live (global presenting mode, no
    // per-screen lock) — same visible result, without blocking Bible/Songs from ever showing.
    val stageMonitorScreenIndices = stageMonitorScreenIndices(appSettings.projectionSettings)
    val hasSeparateMainScreen = stageMonitorScreenIndices.size < appSettings.projectionSettings.screenAssignments.size
    val canSendToStageMonitor = stageMonitorScreenIndices.isNotEmpty()
    val currentScreenLocks = presenterManager?.screenLocks?.value ?: emptyMap()
    val isSentToStageMonitor = if (hasSeparateMainScreen) {
        canSendToStageMonitor && stageMonitorScreenIndices.all { currentScreenLocks[it] == Presenting.ANNOUNCEMENTS }
    } else {
        presenterManager?.presentingMode?.value == Presenting.ANNOUNCEMENTS
    }
    // [stopTicker] must be true when [text] is plain announcement text (the ticker would otherwise
    // silently overwrite it within a second) and false when [text] IS the timer/clock's own current
    // value (stopping the ticker there would freeze the very content being sent).
    fun toggleStageMonitor(text: String, stopTicker: Boolean = false) {
        if (presenterManager == null || !canSendToStageMonitor) return
        if (!hasSeparateMainScreen) {
            if (isSentToStageMonitor) {
                presenterManager.requestClearDisplay()
            } else {
                if (stopTicker) viewModel.pauseTimer(presenterManager)
                presenterManager.setAnnouncementText(text)
                presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
            }
            return
        }
        if (isSentToStageMonitor) {
            stageMonitorScreenIndices.forEach { presenterManager.setScreenLock(it, null) }
        } else {
            if (stopTicker) viewModel.pauseTimer(presenterManager)
            presenterManager.setAnnouncementText(text)
            stageMonitorScreenIndices.forEach { presenterManager.setScreenLock(it, Presenting.ANNOUNCEMENTS) }
        }
    }

    // All four timer/clock modes now tick on presenterManager (see above), so "is it running" and
    // "what's the current value" must be read from there rather than from the tab's own ViewModel,
    // which may have been recreated since the countdown was actually started. announcementTickerActive
    // (not timerRunning, which is only ever true for Duration/Count-Up) reflects all four.
    val isDurationOrCountUp =
        viewModel.timerMode == Constants.TIMER_MODE_DURATION || viewModel.timerMode == Constants.TIMER_MODE_COUNT_UP
    val isTimerRunning = presenterManager?.announcementTickerActive?.value == true
    val isTimerExpired = viewModel.timerMode == Constants.TIMER_MODE_DURATION &&
        presenterManager?.announcementTimerExpired?.value == true
    val timerDisplayValue = when {
        isTimerRunning && presenterManager != null -> presenterManager.timerRemainingSeconds.value
        viewModel.timerMode == Constants.TIMER_MODE_COUNT_UP -> viewModel.countUpElapsed
        else -> viewModel.timerRemaining
    }

    fun saveLeftPanel() {
        val dp = with(density) { leftPanelPx.toDp().value.toInt() }
        onSettingsChangeState.value { s ->
            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(announcementsLeftPanelWidthDp = dp))
            else s.copy(windowedLayout = s.windowedLayout.copy(announcementsLeftPanelWidthDp = dp))
        }
    }

    // The text box's dragged height; 0 until the divider under the text card is first dragged, and
    // until then the box keeps its natural height of up to three lines.
    fun saveTextHeight() {
        val dp = with(density) { textHeightPx.toDp().value.toInt() }
        onSettingsChangeState.value { s ->
            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(announcementsTextHeightDp = dp))
            else s.copy(windowedLayout = s.windowedLayout.copy(announcementsTextHeightDp = dp))
        }
    }

    val selectedAnim = when (viewModel.animationType) {
        Constants.ANIMATION_SLIDE_FROM_LEFT        -> slideFromLeftText
        Constants.ANIMATION_SLIDE_FROM_RIGHT       -> slideFromRightText
        Constants.ANIMATION_SLIDE_FROM_TOP         -> slideFromTopText
        Constants.ANIMATION_SLIDE_FROM_BOTTOM      -> slideFromBottomText
        Constants.ANIMATION_FADE                   -> fadeText
        else                                       -> noneText
    }
    val durationMs = viewModel.animationDuration

    // The tallest the text box may be: the split panel less the text card's buttons and
    // formatting rows around the box, the divider, and the timer card's minimum. Applied to a
    // saved height too, so one dragged in a tall window cannot swallow the timer in a short one.
    val maxTextHeightPx = with(density) {
        val around = (textCardHeightPx - naturalTextHeightPx).coerceAtLeast(0)
        val reserved = ANNOUNCEMENT_MIN_SETTINGS_HEIGHT + ANNOUNCEMENT_DIVIDER_HEIGHT + ANNOUNCEMENT_SPLIT_PANEL_INSETS
        (twoColHeightPx - around - reserved.toPx()).coerceAtLeast(ANNOUNCEMENT_MIN_TEXT_HEIGHT.toPx())
    }
    val shownTextHeightPx =
        if (textHeightPx > 0f && twoColHeightPx > 0 && textCardHeightPx > 0) {
            textHeightPx.coerceAtMost(maxTextHeightPx)
        } else {
            textHeightPx
        }

    // All four timer/clock modes now share one play/pause control (isTimerRunning),
    // mutually exclusive with the announcement text — so this preview must follow
    // whichever one is actually running/live, not just which mode is selected, or it
    // shows the clock/specific-time value even while text is the one live on screen.
    val previewText = when {
        isTimerExpired -> viewModel.timerExpiredText.ifBlank { timerExpiredLabel }
        isTimerRunning && viewModel.timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY -> viewModel.liveClockText
        isTimerRunning -> AnnouncementsViewModel.formatTimer(timerDisplayValue)
        else -> viewModel.text
    }
    // A live timer/clock value changes every second and must stay legible, so skip the
    // configured entrance animation in the preview (it would otherwise cycle the value
    // fully off-screen on every animation loop, looking like it froze or went dark).
    val isShowingLiveTimerValue = isTimerExpired || isTimerRunning
    val isDirectional = !isShowingLiveTimerValue && viewModel.animationType in listOf(
        Constants.ANIMATION_SLIDE_FROM_LEFT,
        Constants.ANIMATION_SLIDE_FROM_RIGHT,
        Constants.ANIMATION_SLIDE_FROM_TOP,
        Constants.ANIMATION_SLIDE_FROM_BOTTOM
    )
    val isHorizontal = viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_LEFT ||
                       viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_RIGHT
    val movesPositive = viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_LEFT ||
                       viewModel.animationType == Constants.ANIMATION_SLIDE_FROM_TOP
    val slideAlignment: Alignment = if (isHorizontal) {
        when {
            viewModel.position.startsWith("Top")    -> Alignment.TopCenter
            viewModel.position.startsWith("Bottom") -> Alignment.BottomCenter
            else                                    -> Alignment.Center
        }
    } else {
        when {
            viewModel.position.endsWith("Left")  -> Alignment.CenterStart
            viewModel.position.endsWith("Right") -> Alignment.CenterEnd
            else                                 -> Alignment.Center
        }
    }
    val scrollDurationMs = durationMs.coerceAtLeast(500)
    val previewTextAlign = when (viewModel.horizontalAlignment) {
        Constants.LEFT -> TextAlign.Left
        Constants.RIGHT -> TextAlign.Right
        else -> TextAlign.Center
    }
}

/** The tab's resolved labels -- the timer's and the animation names -- and the screen positions. */
@Suppress("LongParameterList")
internal class AnnouncementsLabels(
    val timerExpiredLabel: String,
    val startLabel: String,
    val pauseLabel: String,
    val resetLabel: String,
    val hrLabel: String,
    val minLabel: String,
    val secLabel: String,
    val slideFromLeftText: String,
    val slideFromRightText: String,
    val slideFromTopText: String,
    val slideFromBottomText: String,
    val fadeText: String,
    val noneText: String,
    val positions: List<Pair<String, String>>,
    val animItems: List<String>,
)

@Composable
internal fun rememberAnnouncementsLabels(): AnnouncementsLabels {
    val timerExpiredLabel = stringResource(Res.string.timer_expired)
    val startLabel        = stringResource(Res.string.timer_start)
    val pauseLabel        = stringResource(Res.string.timer_pause)
    val resetLabel        = stringResource(Res.string.timer_reset)
    val hrLabel           = stringResource(Res.string.timer_hours)
    val minLabel          = stringResource(Res.string.timer_minutes)
    val secLabel          = stringResource(Res.string.timer_seconds)
    val positions = listOf(
        Constants.TOP_LEFT      to stringResource(Res.string.top_left),
        Constants.TOP_CENTER    to stringResource(Res.string.top_center),
        Constants.TOP_RIGHT     to stringResource(Res.string.top_right),
        Constants.CENTER_LEFT   to stringResource(Res.string.center_left),
        Constants.CENTER        to stringResource(Res.string.center),
        Constants.CENTER_RIGHT  to stringResource(Res.string.center_right),
        Constants.BOTTOM_LEFT   to stringResource(Res.string.bottom_left),
        Constants.BOTTOM_CENTER to stringResource(Res.string.bottom_center),
        Constants.BOTTOM_RIGHT  to stringResource(Res.string.bottom_right)
    )
    val slideFromLeftText       = stringResource(Res.string.anim_slide_from_left)
    val slideFromRightText      = stringResource(Res.string.anim_slide_from_right)
    val slideFromTopText        = stringResource(Res.string.anim_slide_from_top)
    val slideFromBottomText     = stringResource(Res.string.anim_slide_from_bottom)
    val fadeText                = stringResource(Res.string.animation_fade)
    val noneText                = stringResource(Res.string.animation_none)
    val animItems = listOf(
        slideFromBottomText, slideFromTopText,
        slideFromLeftText, slideFromRightText,
        fadeText, noneText
    )
    return AnnouncementsLabels(
        timerExpiredLabel = timerExpiredLabel,
        startLabel = startLabel,
        pauseLabel = pauseLabel,
        resetLabel = resetLabel,
        hrLabel = hrLabel,
        minLabel = minLabel,
        secLabel = secLabel,
        slideFromLeftText = slideFromLeftText,
        slideFromRightText = slideFromRightText,
        slideFromTopText = slideFromTopText,
        slideFromBottomText = slideFromBottomText,
        fadeText = fadeText,
        noneText = noneText,
        positions = positions,
        animItems = animItems,
    )
}

/** The split panel's sizes: remembered per window mode, measured, or dragged. */
internal class AnnouncementsPanelState(
    val leftPanelPx: MutableState<Float>,
    val textHeightPx: MutableState<Float>,
    val naturalTextHeightPx: MutableState<Int>,
    val textCardHeightPx: MutableState<Int>,
    val twoColHeightPx: MutableState<Int>,
    val twoColWidthPx: MutableState<Int>,
)

@Composable
internal fun rememberAnnouncementsPanelState(
    currentLayout: WindowLayoutSettings,
    isMaximized: Boolean,
    density: Density,
): AnnouncementsPanelState = AnnouncementsPanelState(
    leftPanelPx = remember(currentLayout.announcementsLeftPanelWidthDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.announcementsLeftPanelWidthDp.dp.toPx() })
    },
    // The text box's dragged height; 0 until the divider under the text card is first dragged, and
    // until then the box keeps its natural height of up to three lines.
    textHeightPx = remember(currentLayout.announcementsTextHeightDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.announcementsTextHeightDp.dp.toPx() })
    },
    naturalTextHeightPx = remember { mutableStateOf(0) },
    textCardHeightPx = remember { mutableStateOf(0) },
    twoColHeightPx = remember { mutableStateOf(0) },
    twoColWidthPx = remember { mutableStateOf(0) },
)
