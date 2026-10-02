package org.churchpresenter.announcements

import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.sharedui.utils.FallbackOutputSize
import org.churchpresenter.sharedui.utils.PreviewOutput
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.anim_slide_from_bottom
import org.churchpresenter.strings.generated.resources.anim_slide_from_left
import org.churchpresenter.strings.generated.resources.anim_slide_from_right
import org.churchpresenter.strings.generated.resources.anim_slide_from_top
import org.churchpresenter.strings.generated.resources.animation_fade
import org.churchpresenter.strings.generated.resources.animation_none
import org.churchpresenter.strings.generated.resources.bottom_center
import org.churchpresenter.strings.generated.resources.bottom_left
import org.churchpresenter.strings.generated.resources.bottom_right
import org.churchpresenter.strings.generated.resources.center
import org.churchpresenter.strings.generated.resources.center_left
import org.churchpresenter.strings.generated.resources.center_right
import org.churchpresenter.strings.generated.resources.timer_expired
import org.churchpresenter.strings.generated.resources.timer_minutes
import org.churchpresenter.strings.generated.resources.timer_pause
import org.churchpresenter.strings.generated.resources.timer_reset
import org.churchpresenter.strings.generated.resources.timer_hours
import org.churchpresenter.strings.generated.resources.timer_seconds
import org.churchpresenter.strings.generated.resources.timer_start
import org.churchpresenter.strings.generated.resources.top_center
import org.churchpresenter.strings.generated.resources.top_left
import org.churchpresenter.strings.generated.resources.top_right
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Density
import org.churchpresenter.settings.WindowLayoutSettings

/**
 * Everything the Announcements tab's pieces read, for one composition: its parameters, its
 * ViewModel, the labels it resolves, the panel sizes it remembers, and what is derived from them.
 */
@Suppress("LongParameterList")
/**
 * What the app supplies about its outputs: the one the preview stands for, the screens that are
 * stage monitors, and the picker for the first.
 */
internal class AnnouncementsScreens(
    val preview: PreviewOutput,
    val stageMonitors: List<Int>,
    val picker: @Composable () -> Unit,
)

/** 1920x1080 and shown everywhere: what the preview stands for when the app has said nothing. */
internal val FallbackPreviewOutput = PreviewOutput(
    key = "",
    label = "",
    size = FallbackOutputSize,
    showsMode = true,
    assignment = ScreenAssignment(),
)

/** What the app hands the tab: the settings and where changes go, the output, and its screens. */
internal class AnnouncementsTabInputs(
    val appSettings: AppSettings,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val output: AnnouncementsOutput?,
    val onAddToSchedule: ((settings: AnnouncementsSettings) -> Unit)?,
    val onSavePreset: ((settings: AnnouncementsSettings) -> Unit)?,
    val screens: AnnouncementsScreens,
)

/** The window and the locale the tab is drawn in: its density, which layout it saves to, and the clock. */
internal class AnnouncementsDisplay(
    val density: Density,
    val isMaximized: Boolean,
    val use24HourClock: Boolean,
)

/** What the tab's composition supplies: fonts, labels, the panels and the display they are drawn on. */
internal class AnnouncementsTabEnvironment(
    val availableFonts: List<String>,
    val labels: AnnouncementsLabels,
    val onSettingsChangeState: State<((AppSettings) -> AppSettings) -> Unit>,
    val panels: AnnouncementsPanelState,
    val display: AnnouncementsDisplay,
)

/**
 * Everything the tab's pieces read and act on. Stable: what it exposes is snapshot state or an
 * input it is rebuilt for (the tab remembers it keyed on all of them), so a piece handed the same
 * scope can skip.
 */
@Stable
internal class AnnouncementsTabScope(inputs: AnnouncementsTabInputs, environment: AnnouncementsTabEnvironment) {
    val appSettings = inputs.appSettings
    val onSettingsChange = inputs.onSettingsChange
    val output = inputs.output
    val onAddToSchedule = inputs.onAddToSchedule
    val onSavePreset = inputs.onSavePreset
    val screens = inputs.screens
    val availableFonts = environment.availableFonts
    val labels = environment.labels
    val density = environment.display.density
    val onSettingsChangeState = environment.onSettingsChangeState
    val isMaximized = environment.display.isMaximized
    val use24HourClock = environment.display.use24HourClock
    private val panels = environment.panels

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

    var leftPanelPx
        get() = panels.leftPanelPx.value
        set(value) {
            panels.leftPanelPx.value = value
        }
    var textHeightPx
        get() = panels.textHeightPx.value
        set(value) {
            panels.textHeightPx.value = value
        }
    var naturalTextHeightPx
        get() = panels.naturalTextHeightPx.value
        set(value) {
            panels.naturalTextHeightPx.value = value
        }
    var textCardHeightPx
        get() = panels.textCardHeightPx.value
        set(value) {
            panels.textCardHeightPx.value = value
        }
    var twoColHeightPx
        get() = panels.twoColHeightPx.value
        set(value) {
            panels.twoColHeightPx.value = value
        }
    var twoColWidthPx
        get() = panels.twoColWidthPx.value
        set(value) {
            panels.twoColWidthPx.value = value
        }

    // Screens configured as Stage Monitor — locking them to Announcements shows this content
    // there without disturbing whatever the main projection screen(s) are currently live with.
    // The lock persists (even as other tabs go live elsewhere) until toggled off again or Escape
    // is pressed (see MainDesktop.kt's global Escape handler).
    // If Stage Monitor is the ONLY configured screen there's nothing else to protect from being
    // locked out, so the button instead behaves as a plain Go Live (global presenting mode, no
    // per-screen lock) — same visible result, without blocking Bible/Songs from ever showing.
    val stageMonitorScreenIndices get() = screens.stageMonitors
    val hasSeparateMainScreen get() =
        stageMonitorScreenIndices.size < appSettings.projectionSettings.screenAssignments.size
    val canSendToStageMonitor get() = stageMonitorScreenIndices.isNotEmpty()
    val currentScreenLocks get() = output?.screenLocks?.value ?: emptyMap()
    val isSentToStageMonitor get() = if (hasSeparateMainScreen) {
        canSendToStageMonitor && stageMonitorScreenIndices.all { currentScreenLocks[it] == Presenting.ANNOUNCEMENTS }
    } else {
        output?.presentingMode?.value == Presenting.ANNOUNCEMENTS
    }
    // [stopTicker] must be true when [text] is plain announcement text (the ticker would otherwise
    // silently overwrite it within a second) and false when [text] IS the timer/clock's own current
    // value (stopping the ticker there would freeze the very content being sent).
    fun toggleStageMonitor(viewModel: AnnouncementsViewModel, text: String, stopTicker: Boolean = false) {
        if (output == null || !canSendToStageMonitor) return
        if (!hasSeparateMainScreen) {
            if (isSentToStageMonitor) {
                output.requestClearDisplay()
            } else {
                if (stopTicker) viewModel.pauseTimer(output)
                output.setAnnouncementText(text)
                output.setPresentingMode(Presenting.ANNOUNCEMENTS)
            }
            return
        }
        if (isSentToStageMonitor) {
            stageMonitorScreenIndices.forEach { output.setScreenLock(it, null) }
        } else {
            if (stopTicker) viewModel.pauseTimer(output)
            output.setAnnouncementText(text)
            stageMonitorScreenIndices.forEach { output.setScreenLock(it, Presenting.ANNOUNCEMENTS) }
        }
    }

    // All four timer/clock modes now tick on output (see above), so "is it running" and
    // "what's the current value" must be read from there rather than from the tab's own ViewModel,
    // which may have been recreated since the countdown was actually started. announcementTickerActive
    // (not timerRunning, which is only ever true for Duration/Count-Up) reflects all four.
    val AnnouncementsViewModel.isDurationOrCountUp get() =
        timerMode == Constants.TIMER_MODE_DURATION || timerMode == Constants.TIMER_MODE_COUNT_UP
    val isTimerRunning get() = output?.announcementTickerActive?.value == true
    val AnnouncementsViewModel.isTimerExpired get() = timerMode == Constants.TIMER_MODE_DURATION &&
        output?.announcementTimerExpired?.value == true
    val AnnouncementsViewModel.timerDisplayValue get() = when {
        isTimerRunning && output != null -> output.timerRemainingSeconds.value
        timerMode == Constants.TIMER_MODE_COUNT_UP -> countUpElapsed
        else -> timerRemaining
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

    val AnnouncementsViewModel.selectedAnim get() = when (animationType) {
        Constants.ANIMATION_SLIDE_FROM_LEFT        -> slideFromLeftText
        Constants.ANIMATION_SLIDE_FROM_RIGHT       -> slideFromRightText
        Constants.ANIMATION_SLIDE_FROM_TOP         -> slideFromTopText
        Constants.ANIMATION_SLIDE_FROM_BOTTOM      -> slideFromBottomText
        Constants.ANIMATION_FADE                   -> fadeText
        else                                       -> noneText
    }
    val AnnouncementsViewModel.durationMs get() = animationDuration

    // The tallest the text box may be: the split panel less the text card's buttons and
    // formatting rows around the box, the divider, and the timer card's minimum. Applied to a
    // saved height too, so one dragged in a tall window cannot swallow the timer in a short one.
    val maxTextHeightPx get() = with(density) {
        val around = (textCardHeightPx - naturalTextHeightPx).coerceAtLeast(0)
        val reserved = ANNOUNCEMENT_MIN_SETTINGS_HEIGHT + ANNOUNCEMENT_DIVIDER_HEIGHT + ANNOUNCEMENT_SPLIT_PANEL_INSETS
        (twoColHeightPx - around - reserved.toPx()).coerceAtLeast(ANNOUNCEMENT_MIN_TEXT_HEIGHT.toPx())
    }
    val shownTextHeightPx get() =
        if (textHeightPx > 0f && twoColHeightPx > 0 && textCardHeightPx > 0) {
            textHeightPx.coerceAtMost(maxTextHeightPx)
        } else {
            textHeightPx
        }

    // All four timer/clock modes now share one play/pause control (isTimerRunning),
    // mutually exclusive with the announcement text — so this preview must follow
    // whichever one is actually running/live, not just which mode is selected, or it
    // shows the clock/specific-time value even while text is the one live on screen.
    val AnnouncementsViewModel.previewText get() = when {
        isTimerExpired -> timerExpiredText.ifBlank { timerExpiredLabel }
        isTimerRunning && timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY -> liveClockText
        isTimerRunning -> AnnouncementsViewModel.formatTimer(timerDisplayValue)
        else -> text
    }
    // A live timer/clock value changes every second and must stay legible, so skip the
    // configured entrance animation in the preview (it would otherwise cycle the value
    // fully off-screen on every animation loop, looking like it froze or went dark).
    val AnnouncementsViewModel.isShowingLiveTimerValue get() = isTimerExpired || isTimerRunning
    val AnnouncementsViewModel.isDirectional get() = !isShowingLiveTimerValue && animationType in listOf(
        Constants.ANIMATION_SLIDE_FROM_LEFT,
        Constants.ANIMATION_SLIDE_FROM_RIGHT,
        Constants.ANIMATION_SLIDE_FROM_TOP,
        Constants.ANIMATION_SLIDE_FROM_BOTTOM
    )
    val AnnouncementsViewModel.isHorizontal get() = animationType == Constants.ANIMATION_SLIDE_FROM_LEFT ||
                       animationType == Constants.ANIMATION_SLIDE_FROM_RIGHT
    val AnnouncementsViewModel.movesPositive get() = animationType == Constants.ANIMATION_SLIDE_FROM_LEFT ||
                       animationType == Constants.ANIMATION_SLIDE_FROM_TOP
    val AnnouncementsViewModel.slideAlignment: Alignment get() = if (isHorizontal) {
        when {
            position.startsWith("Top")    -> Alignment.TopCenter
            position.startsWith("Bottom") -> Alignment.BottomCenter
            else                                    -> Alignment.Center
        }
    } else {
        when {
            position.endsWith("Left")  -> Alignment.CenterStart
            position.endsWith("Right") -> Alignment.CenterEnd
            else                                 -> Alignment.Center
        }
    }
    val AnnouncementsViewModel.scrollDurationMs get() = durationMs.coerceAtLeast(500)
    val AnnouncementsViewModel.previewTextAlign get() = when (horizontalAlignment) {
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
    return remember(
        timerExpiredLabel, startLabel, pauseLabel, resetLabel, hrLabel, minLabel, secLabel, slideFromLeftText,
        slideFromRightText, slideFromTopText, slideFromBottomText, fadeText, noneText, positions, animItems
    ) {
        AnnouncementsLabels(
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
): AnnouncementsPanelState {
    val leftPanelPx = remember(currentLayout.announcementsLeftPanelWidthDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.announcementsLeftPanelWidthDp.dp.toPx() })
    }
    // The text box's dragged height; 0 until the divider under the text card is first dragged, and
    // until then the box keeps its natural height of up to three lines.
    val textHeightPx = remember(currentLayout.announcementsTextHeightDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.announcementsTextHeightDp.dp.toPx() })
    }
    val naturalTextHeightPx = remember { mutableStateOf(0) }
    val textCardHeightPx = remember { mutableStateOf(0) }
    val twoColHeightPx = remember { mutableStateOf(0) }
    val twoColWidthPx = remember { mutableStateOf(0) }
    return remember(leftPanelPx, textHeightPx, naturalTextHeightPx, textCardHeightPx, twoColHeightPx, twoColWidthPx) {
        AnnouncementsPanelState(
            leftPanelPx = leftPanelPx,
            textHeightPx = textHeightPx,
            naturalTextHeightPx = naturalTextHeightPx,
            textCardHeightPx = textCardHeightPx,
            twoColHeightPx = twoColHeightPx,
            twoColWidthPx = twoColWidthPx,
        )
    }
}
