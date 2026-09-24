package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.theme.components.DropdownSelector
import org.churchpresenter.app.churchpresenter.stageMonitorScreenIndices
import org.churchpresenter.app.churchpresenter.composables.SlimSlider
import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.SavePresetButton
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerHoverIcon
import java.awt.Cursor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.WindowPlacement
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.TopStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.tooltip_add_to_schedule
import churchpresenter.composeapp.generated.resources.save_preset
import churchpresenter.composeapp.generated.resources.tooltip_go_live
import churchpresenter.composeapp.generated.resources.tooltip_send_to_stage_monitor
import churchpresenter.composeapp.generated.resources.tooltip_hide_from_stage_monitor
import churchpresenter.composeapp.generated.resources.tooltip_announcement_show
import churchpresenter.composeapp.generated.resources.tooltip_announcement_hide
import churchpresenter.composeapp.generated.resources.anim_slide_from_bottom
import churchpresenter.composeapp.generated.resources.anim_slide_from_left
import churchpresenter.composeapp.generated.resources.anim_slide_from_right
import churchpresenter.composeapp.generated.resources.anim_slide_from_top
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.announcement_animation
import churchpresenter.composeapp.generated.resources.announcement_loop_count
import churchpresenter.composeapp.generated.resources.announcement_loop_tooltip
import churchpresenter.composeapp.generated.resources.preview
import churchpresenter.composeapp.generated.resources.announcement_background_color_label
import churchpresenter.composeapp.generated.resources.announcement_text_hint
import churchpresenter.composeapp.generated.resources.transparent_default
import churchpresenter.composeapp.generated.resources.bottom_center
import churchpresenter.composeapp.generated.resources.bottom_left
import churchpresenter.composeapp.generated.resources.bottom_right
import churchpresenter.composeapp.generated.resources.center
import churchpresenter.composeapp.generated.resources.center_left
import churchpresenter.composeapp.generated.resources.center_right
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Remove
import churchpresenter.composeapp.generated.resources.ic_refresh
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.position_on_screen
import churchpresenter.composeapp.generated.resources.text_color
import churchpresenter.composeapp.generated.resources.canvas_source_clock
import churchpresenter.composeapp.generated.resources.timer_am
import churchpresenter.composeapp.generated.resources.timer_clock_format
import churchpresenter.composeapp.generated.resources.timer_clock_format_12h_sec
import churchpresenter.composeapp.generated.resources.timer_clock_format_12h
import churchpresenter.composeapp.generated.resources.timer_clock_format_24h_sec
import churchpresenter.composeapp.generated.resources.timer_clock_format_24h
import churchpresenter.composeapp.generated.resources.timer_pm
import churchpresenter.composeapp.generated.resources.timer_expired
import churchpresenter.composeapp.generated.resources.timer_expired_text_hint
import churchpresenter.composeapp.generated.resources.timer_expired_text_label
import churchpresenter.composeapp.generated.resources.timer_minutes
import churchpresenter.composeapp.generated.resources.timer_pause
import churchpresenter.composeapp.generated.resources.timer_reset
import churchpresenter.composeapp.generated.resources.timer_hours
import churchpresenter.composeapp.generated.resources.timer_seconds
import churchpresenter.composeapp.generated.resources.timer_start
import churchpresenter.composeapp.generated.resources.timer_title
import churchpresenter.composeapp.generated.resources.timer_mode_duration
import churchpresenter.composeapp.generated.resources.timer_mode_clock
import churchpresenter.composeapp.generated.resources.timer_target_time
import churchpresenter.composeapp.generated.resources.top_center
import churchpresenter.composeapp.generated.resources.top_left
import churchpresenter.composeapp.generated.resources.top_right
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.app.churchpresenter.composables.rememberTextBackdropPainter
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.settings.utils.isSystemUsing24HourFormat
import org.churchpresenter.app.churchpresenter.viewmodel.AnnouncementsViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.composables.ScreenPositionPicker
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalContentColor

private val STEP_KEY_HEIGHT = 20.dp
private val STEP_KEY_WIDTH = 40.dp
private val STEP_GAP = 3.dp
private val WELL_WIDTH = 46.dp
private val WELL_HEIGHT = 36.dp
private const val HOURS_PER_HALF_DAY = 12
private const val HOUR_WRAP_OFFSET = 11
private const val HOURS_PER_DAY = 24
private const val MAX_SECOND = 59
private const val MILLIS_PER_SECOND_F = 1000f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnnouncementsTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    presenterManager: PresenterManager? = null,
    onAddToSchedule: ((settings: AnnouncementsSettings) -> Unit)? = null,
    /** Save preset, to the left of Add to Schedule: the same text or timer, kept for the Calendar Manager. */
    onSavePreset: ((settings: AnnouncementsSettings) -> Unit)? = null
) {
    val viewModel = remember { AnnouncementsViewModel() }

    DisposableEffect(Unit) { onDispose { viewModel.dispose() } }

    // Sync from settings on load / settings change
    LaunchedEffect(appSettings.announcementsSettings) {
        viewModel.syncFromSettings(appSettings.announcementsSettings)
    }

    val availableFonts = rememberSystemFonts()

    // Screens configured as Stage Monitor — locking them to Announcements shows this content
    // there without disturbing whatever the main projection screen(s) are currently live with.
    // The lock persists (even as other tabs go live elsewhere) until toggled off again or Escape
    // is pressed (see MainDesktop.kt's global Escape handler).
    // If Stage Monitor is the ONLY configured screen there's nothing else to protect from being
    // locked out, so the button instead behaves as a plain Go Live (global presenting mode, no
    // per-screen lock) — same visible result, without blocking Bible/Songs from ever showing.
    val stageMonitorScreenIndices = remember(appSettings.projectionSettings) {
        stageMonitorScreenIndices(appSettings.projectionSettings)
    }
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

    val timerExpiredLabel = stringResource(Res.string.timer_expired)
    val startLabel        = stringResource(Res.string.timer_start)
    val pauseLabel        = stringResource(Res.string.timer_pause)
    val resetLabel        = stringResource(Res.string.timer_reset)
    val hrLabel           = stringResource(Res.string.timer_hours)
    val minLabel          = stringResource(Res.string.timer_minutes)
    val secLabel          = stringResource(Res.string.timer_seconds)

    // All four timer/clock modes now tick on presenterManager (see above), so "is it running" and
    // "what's the current value" must be read from there rather than from the tab's own ViewModel,
    // which may have been recreated since the countdown was actually started. announcementTickerActive
    // (not timerRunning, which is only ever true for Duration/Count-Up) reflects all four.
    val isDurationOrCountUp = viewModel.timerMode == Constants.TIMER_MODE_DURATION || viewModel.timerMode == Constants.TIMER_MODE_COUNT_UP
    val isTimerRunning = presenterManager?.announcementTickerActive?.value == true
    val isTimerExpired = viewModel.timerMode == Constants.TIMER_MODE_DURATION && presenterManager?.announcementTimerExpired?.value == true
    val timerDisplayValue = when {
        isTimerRunning -> presenterManager.timerRemainingSeconds.value
        viewModel.timerMode == Constants.TIMER_MODE_COUNT_UP -> viewModel.countUpElapsed
        else -> viewModel.timerRemaining
    }

    val density = LocalDensity.current
    val onSettingsChangeState = rememberUpdatedState(onSettingsChange)
    val windowState = LocalMainWindowState.current
    val isMaximized = windowState?.placement != WindowPlacement.Floating
    val currentLayout = if (isMaximized) appSettings.maximizedLayout else appSettings.windowedLayout

    var leftPanelPx by remember(currentLayout.announcementsLeftPanelWidthDp, isMaximized) {
        mutableStateOf(with(density) { currentLayout.announcementsLeftPanelWidthDp.dp.toPx() })
    }

    fun saveLeftPanel() {
        val dp = with(density) { leftPanelPx.toDp().value.toInt() }
        onSettingsChangeState.value { s ->
            if (isMaximized) s.copy(maximizedLayout = s.maximizedLayout.copy(announcementsLeftPanelWidthDp = dp))
            else s.copy(windowedLayout = s.windowedLayout.copy(announcementsLeftPanelWidthDp = dp))
        }
    }

    // Build all the data needed up front (outside the scrollable area)
    Column(modifier = modifier.fillMaxSize()) {

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
            val selectedAnim = when (viewModel.animationType) {
                Constants.ANIMATION_SLIDE_FROM_LEFT        -> slideFromLeftText
                Constants.ANIMATION_SLIDE_FROM_RIGHT       -> slideFromRightText
                Constants.ANIMATION_SLIDE_FROM_TOP         -> slideFromTopText
                Constants.ANIMATION_SLIDE_FROM_BOTTOM      -> slideFromBottomText
                Constants.ANIMATION_FADE                   -> fadeText
                else                                       -> noneText
            }
            val durationMs = viewModel.animationDuration

        // ── Text input bar ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .sunken(RoundedCornerShape(8.dp), elevationPalette())
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = viewModel.text,
                    onValueChange = { viewModel.setText(it); viewModel.saveToSettings(onSettingsChange) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (viewModel.text.isEmpty()) {
                            Text(stringResource(Res.string.announcement_text_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                        inner()
                    }
                )
            }
            if (presenterManager != null) {
                // Mutually exclusive with the timer's own play/pause button below — only one of
                // text/timer can occupy the shared announcementText slot at a time, so whichever
                // one is started (isTimerRunning going true) automatically shows this as not-live.
                val announcementTextIsLive = presenterManager.presentingMode.value == Presenting.ANNOUNCEMENTS && !isTimerRunning
                ActionIconButton(
                    onClick = {
                        if (announcementTextIsLive) presenterManager.requestClearDisplay()
                        else { viewModel.pauseTimer(presenterManager); presenterManager.setAnnouncementText(viewModel.text); presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS) }
                    },
                    enabled = viewModel.text.isNotBlank() || announcementTextIsLive,
                    tooltipText = stringResource(if (announcementTextIsLive) Res.string.tooltip_announcement_hide else Res.string.tooltip_announcement_show),
                    painter = painterResource(if (announcementTextIsLive) Res.drawable.ic_pause else Res.drawable.ic_play),
                    containerColor = if (announcementTextIsLive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (announcementTextIsLive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                )
                if (canSendToStageMonitor) {
                    ActionIconButton(
                        onClick = { toggleStageMonitor(viewModel.text, stopTicker = true) },
                        enabled = viewModel.text.isNotBlank() || isSentToStageMonitor,
                        tooltipText = if (isSentToStageMonitor) stringResource(Res.string.tooltip_hide_from_stage_monitor) else stringResource(Res.string.tooltip_send_to_stage_monitor),
                        icon = if (isSentToStageMonitor) Icons.Default.CastConnected else Icons.Default.Cast,
                        containerColor = if (isSentToStageMonitor) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSentToStageMonitor) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onSavePreset != null) {
                SavePresetButton(
                    onClick = {
                        // A saved announcement is the text and its look; the timer fields
                        // belong to a timer preset, not to this one.
                        onSavePreset.invoke(
                            viewModel.buildSettings().copy(
                                timerHours = 0,
                                timerMinutes = 0,
                                timerSeconds = 0,
                                timerTextColor = "#FFFFFF",
                                timerExpiredText = "",
                                timerMode = Constants.TIMER_MODE_DURATION,
                            )
                        )
                    },
                    enabled = viewModel.text.isNotBlank(),
                    tooltipText = stringResource(Res.string.save_preset)
                )
            }
            if (onAddToSchedule != null) {
                AddToScheduleButton(
                    onClick = { onAddToSchedule.invoke(viewModel.buildSettings().copy(timerHours = 0, timerMinutes = 0, timerSeconds = 0, timerTextColor = "#FFFFFF", timerExpiredText = "", timerMode = Constants.TIMER_MODE_DURATION)) },
                    enabled = viewModel.text.isNotBlank(),
                    tooltipText = stringResource(Res.string.tooltip_add_to_schedule)
                )
            }
            if (presenterManager != null) {
                GoLiveButton(
                    onClick = { viewModel.goLive(presenterManager, onSettingsChange) },
                    enabled = viewModel.text.isNotBlank(),
                    tooltipText = stringResource(Res.string.tooltip_go_live)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Formatting bar ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ColorPickerField(label = stringResource(Res.string.text_color), color = viewModel.textColor, onColorChange = { viewModel.setTextColor(it); viewModel.saveToSettings(onSettingsChange) }, modifier = Modifier.width(120.dp))
            HorizontalDivider(modifier = Modifier.height(22.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TextStyleButtons(
                bold = viewModel.bold, italic = viewModel.italic, underline = viewModel.underline, shadow = viewModel.shadow,
                onBoldChange = { viewModel.setBold(it); viewModel.saveToSettings(onSettingsChange) },
                onItalicChange = { viewModel.setItalic(it); viewModel.saveToSettings(onSettingsChange) },
                onUnderlineChange = { viewModel.setUnderline(it); viewModel.saveToSettings(onSettingsChange) },
                onShadowChange = { viewModel.setShadow(it); viewModel.saveToSettings(onSettingsChange) },
                backdrop = viewModel.backdrop,
                onBackdropChange = { updated ->
                    viewModel.setBackdrop(updated)
                    viewModel.saveToSettings(onSettingsChange)
                },
                outline = viewModel.outline,
                onOutlineChange = { updated ->
                    viewModel.setOutline(updated)
                    viewModel.saveToSettings(onSettingsChange)
                },
            )
            HorizontalAlignmentButtons(
                selectedAlignment = viewModel.horizontalAlignment,
                onAlignmentChange = { viewModel.setHorizontalAlignment(it); viewModel.saveToSettings(onSettingsChange) },
                leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
            )
            HorizontalDivider(modifier = Modifier.height(22.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
            FontSettingsDropdown(label = stringResource(Res.string.font_type), value = viewModel.fontType, fonts = availableFonts, onValueChange = { viewModel.setFontType(it); viewModel.saveToSettings(onSettingsChange) })
            NumberSettingsTextField(label = stringResource(Res.string.font_size), initialText = viewModel.fontSize, range = 8..200, onValueChange = { viewModel.setFontSize(it); viewModel.saveToSettings(onSettingsChange) })
            Spacer(Modifier.weight(1f))
        }
        AnimatedVisibility(visible = viewModel.shadow) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    ShadowDetailRow(
                        shadowColor = appSettings.announcementsSettings.shadowColor,
                        shadowSize = appSettings.announcementsSettings.shadowSize,
                        shadowOpacity = appSettings.announcementsSettings.shadowOpacity,
                        onColorChange = { c -> onSettingsChange { s -> s.copy(announcementsSettings = s.announcementsSettings.copy(shadowColor = c)) } },
                        onSizeChange = { v -> onSettingsChange { s -> s.copy(announcementsSettings = s.announcementsSettings.copy(shadowSize = v)) } },
                        onOpacityChange = { v -> onSettingsChange { s -> s.copy(announcementsSettings = s.announcementsSettings.copy(shadowOpacity = v)) } }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Resizable split panel ─────────────────────────────────────
        var twoColHeightPx by remember { mutableStateOf(0) }
        var twoColWidthPx by remember { mutableStateOf(0) }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().onSizeChanged { twoColHeightPx = it.height; twoColWidthPx = it.width }) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── LEFT: background color + position + timer ──────────────
                Column(
                    modifier = Modifier
                        .width(with(density) { leftPanelPx.toDp() })
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Background color
                    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (viewModel.backgroundColor == "transparent") {
                            Row(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clickable { viewModel.setBackgroundColor("#000000"); viewModel.saveToSettings(onSettingsChange) }
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(19.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                                )
                                Text(
                                    text = stringResource(Res.string.transparent_default),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                ColorPickerField(label = stringResource(Res.string.announcement_background_color_label), color = viewModel.backgroundColor, onColorChange = { viewModel.setBackgroundColor(it); viewModel.saveToSettings(onSettingsChange) }, modifier = Modifier.weight(1f))
                                KeyButton(
                                    onClick = {
                                        viewModel.setBackgroundColor("transparent")
                                        viewModel.saveToSettings(onSettingsChange)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    // Compact, so the color field beside it keeps its width.
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    modifier = Modifier.height(42.dp)
                                ) {
                                    Text(
                                        stringResource(Res.string.transparent_default),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Position on screen
                    SectionLabel(stringResource(Res.string.position_on_screen))
                    ScreenPositionPicker(
                        positions = positions,
                        selected = viewModel.position,
                        onSelect = { posConst ->
                            viewModel.setPosition(posConst)
                            viewModel.saveToSettings(onSettingsChange)
                        },
                    )

                    // ── TIMER section ──────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val timerLabel = stringResource(Res.string.timer_title)
                        val timerModeDurationLabel = stringResource(Res.string.timer_mode_duration)
                        val timerModeClockLabel = stringResource(Res.string.timer_mode_clock)
                        val timerModeClockDisplayLabel = stringResource(Res.string.canvas_source_clock)
                        val timerTargetTimeLabel = stringResource(Res.string.timer_target_time)
                        // Not `remember`ed: re-checked every recomposition so a live OS format change
                        // (12h <-> 24h) takes effect immediately without requiring an app restart.
                        val use24Hour = isSystemUsing24HourFormat()
                        val targetIsPm = viewModel.targetHour >= 12
                        fun displayHour(hour24: Int): Int =
                            if (use24Hour) hour24 else ((hour24 + HOUR_WRAP_OFFSET) % HOURS_PER_HALF_DAY) + 1

                        TimerModeTrack(
                            modes = listOf(
                                Constants.TIMER_MODE_DURATION to timerLabel,
                                Constants.TIMER_MODE_COUNT_UP to timerModeDurationLabel,
                                Constants.TIMER_MODE_CLOCK to timerModeClockLabel,
                                Constants.TIMER_MODE_CLOCK_DISPLAY to timerModeClockDisplayLabel,
                            ),
                            selected = viewModel.timerMode,
                            onSelect = { mode ->
                                // Switching modes makes whatever was ticking on presenterManager stale
                                // (it's counting down/up for a mode that's no longer selected) — stop it,
                                // and release live status so the new mode starts as preview-only again.
                                presenterManager?.pauseAnnouncementTimer(0)
                                presenterManager?.setAnnouncementTickerLive(false)
                                viewModel.setTimerMode(mode)
                                viewModel.saveToSettings(onSettingsChange)
                            },
                        )

                        // Countdown / count-up / live clock display
                        Text(
                            text = when {
                                isTimerExpired -> viewModel.timerExpiredText.ifBlank { timerExpiredLabel }
                                viewModel.timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY -> viewModel.liveClockText
                                else -> AnnouncementsViewModel.formatTimer(timerDisplayValue)
                            },
                            style = MaterialTheme.typography.displayMedium,
                            color = if (isTimerExpired) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        val amPmSuffix = if (use24Hour) "" else " " + stringResource(if (targetIsPm) Res.string.timer_pm else Res.string.timer_am)
                        Text(
                            text = "$timerTargetTimeLabel %02d:%02d:%02d%s".format(
                                displayHour(viewModel.targetHour), viewModel.targetMinute, viewModel.targetSecond, amPmSuffix
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().graphicsLayer {
                                alpha = if (viewModel.timerMode == Constants.TIMER_MODE_CLOCK) 1f else 0f
                            },
                            textAlign = TextAlign.Center
                        )

                        // Steppers
                        val sepColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        val sepStyle = MaterialTheme.typography.titleLarge
                        // separator aligns with the center of the number well (after the + key and its gap)
                        val sepBox: @Composable () -> Unit = {
                            Box(
                                modifier = Modifier.padding(top = STEP_KEY_HEIGHT + STEP_GAP).height(WELL_HEIGHT),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(":", style = sepStyle, color = sepColor)
                            }
                        }
                        if (viewModel.timerMode == Constants.TIMER_MODE_DURATION) {
                            var hrText by remember { mutableStateOf("%02d".format(viewModel.timerHours)) }
                            var minText by remember { mutableStateOf("%02d".format(viewModel.timerMinutes)) }
                            var secText by remember { mutableStateOf("%02d".format(viewModel.timerSeconds)) }
                            LaunchedEffect(viewModel.timerHours) { hrText = "%02d".format(viewModel.timerHours) }
                            LaunchedEffect(viewModel.timerMinutes) { minText = "%02d".format(viewModel.timerMinutes) }
                            LaunchedEffect(viewModel.timerSeconds) { secText = "%02d".format(viewModel.timerSeconds) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.Top
                            ) {
                                TimerColumn(hrText, hrLabel,
                                    onIncrement = { viewModel.stepTimerHours(1); viewModel.saveToSettings(onSettingsChange) },
                                    onDecrement = { viewModel.stepTimerHours(-1); viewModel.saveToSettings(onSettingsChange) },
                                    onValueChange = { v -> val d = v.filter { it.isDigit() }.take(2); hrText = d; d.toIntOrNull()?.let { viewModel.setTimerHours(it); viewModel.saveToSettings(onSettingsChange) } }
                                )
                                sepBox()
                                TimerColumn(minText, minLabel,
                                    onIncrement = { viewModel.stepTimerMinutes(1); viewModel.saveToSettings(onSettingsChange) },
                                    onDecrement = { viewModel.stepTimerMinutes(-1); viewModel.saveToSettings(onSettingsChange) },
                                    onValueChange = { v -> val d = v.filter { it.isDigit() }.take(2); minText = d; d.toIntOrNull()?.let { viewModel.setTimerMinutes(it); viewModel.saveToSettings(onSettingsChange) } }
                                )
                                sepBox()
                                TimerColumn(secText, secLabel,
                                    onIncrement = { viewModel.stepTimerSeconds(1); viewModel.saveToSettings(onSettingsChange) },
                                    onDecrement = { viewModel.stepTimerSeconds(-1); viewModel.saveToSettings(onSettingsChange) },
                                    onValueChange = { v -> val d = v.filter { it.isDigit() }.take(2); secText = d; d.toIntOrNull()?.let { viewModel.setTimerSeconds(it.coerceIn(0, MAX_SECOND)); viewModel.saveToSettings(onSettingsChange) } }
                                )
                            }
                        } else if (viewModel.timerMode == Constants.TIMER_MODE_CLOCK) {
                            var tHrText by remember { mutableStateOf("%02d".format(displayHour(viewModel.targetHour))) }
                            var tMinText by remember { mutableStateOf("%02d".format(viewModel.targetMinute)) }
                            var tSecText by remember { mutableStateOf("%02d".format(viewModel.targetSecond)) }
                            LaunchedEffect(viewModel.targetHour) { tHrText = "%02d".format(displayHour(viewModel.targetHour)) }
                            LaunchedEffect(viewModel.targetMinute) { tMinText = "%02d".format(viewModel.targetMinute) }
                            LaunchedEffect(viewModel.targetSecond) { tSecText = "%02d".format(viewModel.targetSecond) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.Top
                            ) {
                                TimerColumn(tHrText, hrLabel,
                                    onIncrement = { viewModel.stepTargetHour(1); viewModel.saveToSettings(onSettingsChange) },
                                    onDecrement = { viewModel.stepTargetHour(-1); viewModel.saveToSettings(onSettingsChange) },
                                    onValueChange = { v ->
                                        val d = v.filter { it.isDigit() }.take(2)
                                        tHrText = d
                                        d.toIntOrNull()?.let { entered ->
                                            val hour24 = if (use24Hour) {
                                                entered
                                            } else {
                                                val clamped = entered.coerceIn(1, 12)
                                                when {
                                                    targetIsPm && clamped == 12 -> 12
                                                    targetIsPm -> clamped + 12
                                                    !targetIsPm && clamped == 12 -> 0
                                                    else -> clamped
                                                }
                                            }
                                            viewModel.setTargetHour(hour24)
                                            viewModel.saveToSettings(onSettingsChange)
                                        }
                                    }
                                )
                                sepBox()
                                TimerColumn(tMinText, minLabel,
                                    onIncrement = { viewModel.stepTargetMinute(1); viewModel.saveToSettings(onSettingsChange) },
                                    onDecrement = { viewModel.stepTargetMinute(-1); viewModel.saveToSettings(onSettingsChange) },
                                    onValueChange = { v -> val d = v.filter { it.isDigit() }.take(2); tMinText = d; d.toIntOrNull()?.let { viewModel.setTargetMinute(it); viewModel.saveToSettings(onSettingsChange) } }
                                )
                                sepBox()
                                TimerColumn(tSecText, secLabel,
                                    onIncrement = { viewModel.stepTargetSecond(1); viewModel.saveToSettings(onSettingsChange) },
                                    onDecrement = { viewModel.stepTargetSecond(-1); viewModel.saveToSettings(onSettingsChange) },
                                    onValueChange = { v -> val d = v.filter { it.isDigit() }.take(2); tSecText = d; d.toIntOrNull()?.let { viewModel.setTargetSecond(it.coerceIn(0, MAX_SECOND)); viewModel.saveToSettings(onSettingsChange) } }
                                )
                                if (!use24Hour) {
                                    sepBox()
                                    AmPmToggle(
                                        isPm = targetIsPm,
                                        onToggle = {
                                            viewModel.setTargetHour((viewModel.targetHour + HOURS_PER_HALF_DAY) % HOURS_PER_DAY)
                                            viewModel.saveToSettings(onSettingsChange)
                                        }
                                    )
                                }
                            }
                        } else if (viewModel.timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY) {
                            val clockFormatLabels = mapOf(
                                "h:mm:ss a" to stringResource(Res.string.timer_clock_format_12h_sec),
                                "h:mm a" to stringResource(Res.string.timer_clock_format_12h),
                                "HH:mm:ss" to stringResource(Res.string.timer_clock_format_24h_sec),
                                "HH:mm" to stringResource(Res.string.timer_clock_format_24h)
                            )
                            val patternForLabel = clockFormatLabels.entries.associate { (pattern, label) -> label to pattern }
                            DropdownSettingsField(
                                value = clockFormatLabels[viewModel.liveClockFormat] ?: viewModel.liveClockFormat,
                                options = clockFormatLabels.values.toList(),
                                onValueChange = { picked ->
                                    patternForLabel[picked]?.let { viewModel.setLiveClockFormat(it); viewModel.saveToSettings(onSettingsChange) }
                                },
                                label = stringResource(Res.string.timer_clock_format),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Controls row
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            val total = viewModel.timerHours * 3600 + viewModel.timerMinutes * 60 + viewModel.timerSeconds
                            // All four modes now have a real play/pause concept — pressing it on one
                            // stops any other timer/clock ticker AND the announcement text (mutually
                            // exclusive, see isTimerRunning/announcementTextIsLive above) since they
                            // all share the same announcementText slot.
                            ActionIconButton(
                                onClick = {
                                    viewModel.saveToSettings(onSettingsChange)
                                    viewModel.startPauseTimer(presenterManager)
                                },
                                enabled = viewModel.timerMode != Constants.TIMER_MODE_DURATION || total > 0 || isTimerRunning,
                                tooltipText = if (isTimerRunning) pauseLabel else startLabel,
                                painter = painterResource(if (isTimerRunning) Res.drawable.ic_pause else Res.drawable.ic_play),
                                containerColor = if (isTimerRunning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isTimerRunning) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            // Reset only makes sense for Timer/Duration, which count down/up from a
                            // starting point. Specific Time and the live Clock always track the wall
                            // clock automatically — there's nothing to reset back to.
                            if (isDurationOrCountUp) {
                                ActionIconButton(
                                    onClick = { viewModel.resetTimer(presenterManager) },
                                    tooltipText = resetLabel,
                                    painter = painterResource(Res.drawable.ic_refresh),
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Stage Monitor already shows its own always-on clock, so the plain
                            // "Clock" timer mode has nothing extra to send there.
                            if (presenterManager != null && canSendToStageMonitor && viewModel.timerMode != Constants.TIMER_MODE_CLOCK_DISPLAY) {
                                ActionIconButton(
                                    onClick = {
                                        // Sending is the only thing (besides Go Live) allowed to mark the
                                        // ticker live — the play/pause button above stays preview-only.
                                        if (!isSentToStageMonitor) {
                                            // Sending Specific Time to Stage Monitor also (re)starts its
                                            // ticker if it wasn't already running via the play/pause button.
                                            if (viewModel.timerMode == Constants.TIMER_MODE_CLOCK) {
                                                presenterManager.startAnnouncementSpecificTime(viewModel.targetHour, viewModel.targetMinute, viewModel.targetSecond)
                                            }
                                            presenterManager.setAnnouncementTickerLive(true)
                                        }
                                        val liveText = AnnouncementsViewModel.formatTimer(timerDisplayValue)
                                        toggleStageMonitor(liveText)
                                    },
                                    tooltipText = if (isSentToStageMonitor) stringResource(Res.string.tooltip_hide_from_stage_monitor) else stringResource(Res.string.tooltip_send_to_stage_monitor),
                                    icon = if (isSentToStageMonitor) Icons.Default.CastConnected else Icons.Default.Cast,
                                    containerColor = if (isSentToStageMonitor) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSentToStageMonitor) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (onSavePreset != null) {
                                SavePresetButton(
                                    onClick = { onSavePreset.invoke(viewModel.buildSettings()) },
                                    enabled = viewModel.text.isNotBlank() || viewModel.timerMode != Constants.TIMER_MODE_DURATION || viewModel.timerHours > 0 || viewModel.timerMinutes > 0 || viewModel.timerSeconds > 0,
                                    tooltipText = stringResource(Res.string.save_preset)
                                )
                            }
                            if (onAddToSchedule != null) {
                                AddToScheduleButton(
                                    onClick = { onAddToSchedule.invoke(viewModel.buildSettings()) },
                                    enabled = viewModel.text.isNotBlank() || viewModel.timerMode != Constants.TIMER_MODE_DURATION || viewModel.timerHours > 0 || viewModel.timerMinutes > 0 || viewModel.timerSeconds > 0,
                                    tooltipText = stringResource(Res.string.tooltip_add_to_schedule)
                                )
                            }
                            if (presenterManager != null) {
                                GoLiveButton(
                                    onClick = {
                                        // Also (re)starts Specific Time / Clock Display's ticker if it
                                        // wasn't already running via the play/pause button above. Go Live
                                        // is one of only two places (with Send to Stage Monitor) allowed
                                        // to mark the ticker live — the play/pause button stays preview-only.
                                        when (viewModel.timerMode) {
                                            Constants.TIMER_MODE_CLOCK -> presenterManager.startAnnouncementSpecificTime(viewModel.targetHour, viewModel.targetMinute, viewModel.targetSecond)
                                            Constants.TIMER_MODE_CLOCK_DISPLAY -> presenterManager.startAnnouncementClockDisplay(viewModel.liveClockFormat)
                                            else -> {}
                                        }
                                        presenterManager.setAnnouncementTickerLive(true)
                                        val liveText = if (viewModel.timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY) viewModel.liveClockText else AnnouncementsViewModel.formatTimer(timerDisplayValue)
                                        presenterManager.setAnnouncementText(liveText)
                                        presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
                                    },
                                    tooltipText = stringResource(Res.string.tooltip_go_live)
                                )
                            }
                        }

                        // Expired text field — only meaningful for modes that actually reach an
                        // endpoint (Timer countdown, Specific Time). Duration (count-up) and the
                        // live Clock display never "expire".
                        if (viewModel.timerMode == Constants.TIMER_MODE_DURATION || viewModel.timerMode == Constants.TIMER_MODE_CLOCK) {
                            SectionLabel(stringResource(Res.string.timer_expired_text_label))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sunken(RoundedCornerShape(8.dp), elevationPalette())
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                BasicTextField(
                                    value = viewModel.timerExpiredText,
                                    onValueChange = { viewModel.setTimerExpiredText(it); viewModel.saveToSettings(onSettingsChange) },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        if (viewModel.timerExpiredText.isEmpty()) {
                                            Text(stringResource(Res.string.timer_expired_text_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }
                                        inner()
                                    }
                                )
                            }
                        }
                    } // end timer section

                } // end left column

                // Drag handle
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                leftPanelPx = (leftPanelPx + delta).coerceIn(
                                    with(density) { 150.dp.toPx() },
                                    (twoColWidthPx - with(density) { 100.dp.toPx() }).coerceAtLeast(with(density) { 150.dp.toPx() })
                                )
                            },
                            onDragStopped = { saveLeftPanel() }
                        )
                )

                // ── RIGHT COLUMN: preview + animation/loop/speed ──────────
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Which output this preview stands for. Announcements can be routed to several
                    // differently-shaped outputs at once, so the operator says which; the picker
                    // draws nothing until there is more than one to choose between.
                    val previewOutput = rememberPreviewOutput(
                        appSettings, Constants.PREVIEW_TAB_ANNOUNCEMENTS, Presenting.ANNOUNCEMENTS
                    )
                    PreviewOutputPicker(
                        settings = appSettings,
                        tabId = Constants.PREVIEW_TAB_ANNOUNCEMENTS,
                        mode = Presenting.ANNOUNCEMENTS,
                        onSettingsChange = onSettingsChange,
                    )

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
                    var previewWidthPx by remember { mutableStateOf(0) }
                    var previewHeightPx by remember { mutableStateOf(0) }
                    // Both the frame and the type inside it are measured against the SAME output.
                    // They used to read two different things -- the box took the first non-primary
                    // monitor's ratio while the scale divided by that monitor's width -- so on any
                    // rig where announcements go somewhere else the preview was wrong twice over.
                    val scaleFactor = if (previewWidthPx > 0)
                        (previewWidthPx / density.density) / previewOutput.size.width.toFloat()
                    else 0.1f
                    val scaledFontSize = (viewModel.fontSize * scaleFactor).coerceAtLeast(4f).sp
                    val scaledPadH = (32 * scaleFactor).coerceAtLeast(1f).dp
                    val scaledPadV = (16 * scaleFactor).coerceAtLeast(1f).dp
                    val previewFontFamily = remember(viewModel.fontType) {
                        Utils.systemFontFamilyOrDefault(viewModel.fontType)
                    }
                    val previewTextStyle = TextStyle(
                        fontFamily = previewFontFamily,
                        fontWeight = if (viewModel.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (viewModel.italic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = if (viewModel.underline) TextDecoration.Underline else TextDecoration.None,
                    )
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(previewOutput.size.aspectRatio)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                RoundedCornerShape(4.dp)
                            )
                            .onSizeChanged { size ->
                                previewWidthPx = size.width
                                previewHeightPx = size.height
                            }
                    ) {
                        val previewContainerWidthPx = previewWidthPx.toFloat()
                        val previewContainerHeightPx = previewHeightPx.toFloat()
                        key(scrollDurationMs, movesPositive) {
                            val infiniteTransition = rememberInfiniteTransition(label = "previewScroll")
                            val offsetFraction by infiniteTransition.animateFloat(
                                initialValue = if (movesPositive) -1f else 1f,
                                targetValue  = if (movesPositive) 1f else -1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = scrollDurationMs, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "previewOffset"
                            )
                            if (isDirectional) {
                                val textComposable: @Composable () -> Unit = {
                                    Box(
                                        modifier = Modifier
                                            .then(if (isHorizontal) Modifier.wrapContentWidth(unbounded = true) else Modifier)
                                            .wrapContentHeight()
                                            .background(
                                                if (viewModel.backgroundColor == "transparent") Color.Transparent
                                                else Utils.parseHexColor(viewModel.backgroundColor),
                                                RoundedCornerShape(2.dp)
                                            )
                                            .padding(horizontal = scaledPadH, vertical = scaledPadV),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // The preview draws the presenter's text at `scaleFactor` of
                                        // its output size, so the backdrop is drawn at that factor
                                        // too — otherwise an outline configured against a 1080p
                                        // screen is painted full size around thumbnail type.
                                        val previewBackdrop =
                                            rememberTextBackdropPainter(viewModel.backdrop, scaleFactor)
                                        Text(
                                            text = previewText.ifBlank { stringResource(Res.string.preview) },
                                            style = previewTextStyle,
                                            fontSize = scaledFontSize,
                                            color = Utils.parseHexColor(viewModel.textColor),
                                            textAlign = previewTextAlign,
                                            softWrap = !isHorizontal,
                                            modifier = previewBackdrop.modifier,
                                            onTextLayout = previewBackdrop::onTextLayout,
                                        )
                                    }
                                }
                                Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                                    if (isHorizontal) {
                                        Box(
                                            modifier = Modifier
                                                .align(slideAlignment)
                                                .graphicsLayer { translationX = previewContainerWidthPx * offsetFraction },
                                            contentAlignment = Alignment.Center
                                        ) { textComposable() }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .align(slideAlignment)
                                                .graphicsLayer { translationY = previewContainerHeightPx * offsetFraction },
                                            contentAlignment = Alignment.Center
                                        ) { textComposable() }
                                    }
                                }
                            } else {
                                val previewAlignment = when (viewModel.position) {
                                    Constants.TOP_LEFT      -> Alignment.TopStart
                                    Constants.TOP_CENTER    -> Alignment.TopCenter
                                    Constants.TOP_RIGHT     -> Alignment.TopEnd
                                    Constants.CENTER_LEFT   -> Alignment.CenterStart
                                    Constants.CENTER        -> Alignment.Center
                                    Constants.CENTER_RIGHT  -> Alignment.CenterEnd
                                    Constants.BOTTOM_LEFT   -> Alignment.BottomStart
                                    Constants.BOTTOM_CENTER -> Alignment.BottomCenter
                                    Constants.BOTTOM_RIGHT  -> Alignment.BottomEnd
                                    else                    -> Alignment.Center
                                }
                                val previewDuration = durationMs.coerceAtLeast(50)
                                val previewKey = Triple(previewText, viewModel.animationType, viewModel.position)
                                AnimatedContent(
                                    targetState = previewKey,
                                    transitionSpec = {
                                        if (!isShowingLiveTimerValue && viewModel.animationType == Constants.ANIMATION_FADE)
                                            fadeIn(tween(previewDuration)) togetherWith fadeOut(tween(previewDuration))
                                        else
                                            EnterTransition.None togetherWith ExitTransition.None
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    label = "AnnouncementPreview"
                                ) { (text, _, _) ->
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = previewAlignment
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .wrapContentHeight()
                                                .background(
                                                    if (viewModel.backgroundColor == "transparent") Color.Transparent
                                                    else Utils.parseHexColor(viewModel.backgroundColor),
                                                    RoundedCornerShape(2.dp)
                                                )
                                                .padding(horizontal = scaledPadH, vertical = scaledPadV),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val previewBackdrop =
                                                rememberTextBackdropPainter(viewModel.backdrop, scaleFactor)
                                            Text(
                                                text = text.ifBlank { stringResource(Res.string.preview) },
                                                style = previewTextStyle,
                                                fontSize = scaledFontSize,
                                                color = Utils.parseHexColor(viewModel.textColor),
                                                textAlign = previewTextAlign,
                                                modifier = previewBackdrop.modifier,
                                                onTextLayout = previewBackdrop::onTextLayout,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } // end preview Box

                    // Animation + speed slider + loop count on same line
                    val sliderMin = 500f
                    val sliderMax = 30000f
                    val sliderSum = sliderMin + sliderMax
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        DropdownSelector(
                            label = stringResource(Res.string.announcement_animation),
                            items = animItems,
                            selected = selectedAnim,
                            onSelectedChange = { sel ->
                                val key = when (sel) {
                                    slideFromLeftText       -> Constants.ANIMATION_SLIDE_FROM_LEFT
                                    slideFromRightText      -> Constants.ANIMATION_SLIDE_FROM_RIGHT
                                    slideFromTopText        -> Constants.ANIMATION_SLIDE_FROM_TOP
                                    slideFromBottomText     -> Constants.ANIMATION_SLIDE_FROM_BOTTOM
                                    fadeText                -> Constants.ANIMATION_FADE
                                    else                    -> Constants.ANIMATION_NONE
                                }
                                viewModel.setAnimationType(key)
                                viewModel.saveToSettings(onSettingsChange)
                            }
                        )
                        TooltipArea(
                            tooltip = {
                                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) {
                                    Text(
                                        stringResource(Res.string.announcement_loop_tooltip),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                        ) {
                            NumberSettingsTextField(
                                label = stringResource(Res.string.announcement_loop_count),
                                initialText = viewModel.loopCount,
                                range = 0..99,
                                onValueChange = { v ->
                                    viewModel.setLoopCount(v)
                                    viewModel.saveToSettings(onSettingsChange)
                                }
                            )
                        }
                        } // end inner Row (animation + loop count)
                        SlimSlider(
                            value = (sliderSum - durationMs.toFloat()),
                            onValueChange = { v ->
                                val dur = (sliderSum - v)
                                val snapped = (dur / sliderMin).toInt() * sliderMin.toInt()
                                viewModel.setAnimationDuration(snapped.coerceIn(sliderMin.toInt(), sliderMax.toInt()))
                                viewModel.saveToSettings(onSettingsChange)
                            },
                            valueRange = sliderMin..sliderMax,
                            trailingLabel = "${"%.1f".format((sliderSum - durationMs) / MILLIS_PER_SECOND_F)}s",
                            modifier = Modifier.weight(1f)
                        )
                    }
                } // end right column
            } // end two-column Row
        } // end split panel Box
    } // end outer Column
}

@Composable
private fun SectionLabel(text: String) {
    if (text.isNotEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The four timer types in one sunken track, two to a row. */
@Composable
private fun TimerModeTrack(
    modes: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sunken(RoundedCornerShape(10.dp), elevationPalette())
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        modes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { (mode, label) ->
                    SegmentTrackItem(
                        selected = mode == selected,
                        onClick = { onSelect(mode) },
                        modifier = Modifier.weight(1f).height(30.dp),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (mode == selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = LocalContentColor.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** One unit of the time picker: a raised + key, the digits in a sunken well, a raised - key, the unit. */
@Composable
private fun TimerColumn(
    value: String,
    label: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val keyShape = RoundedCornerShape(9.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(STEP_GAP)
    ) {
        KeyButton(
            onClick = onIncrement,
            modifier = Modifier.height(STEP_KEY_HEIGHT).width(STEP_KEY_WIDTH),
            shape = keyShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier
                .size(WELL_WIDTH, WELL_HEIGHT)
                .sunken(RoundedCornerShape(10.dp), elevationPalette())
                .padding(horizontal = 2.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { inner() }
            }
        )
        KeyButton(
            onClick = onDecrement,
            modifier = Modifier.height(STEP_KEY_HEIGHT).width(STEP_KEY_WIDTH),
            shape = keyShape,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(12.dp))
        }
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AmPmToggle(isPm: Boolean, onToggle: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(STEP_GAP)
    ) {
        Spacer(Modifier.height(STEP_KEY_HEIGHT))
        KeyButton(
            onClick = onToggle,
            modifier = Modifier.size(40.dp, WELL_HEIGHT),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = stringResource(if (isPm) Res.string.timer_pm else Res.string.timer_am),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(STEP_KEY_HEIGHT))
    }
}
