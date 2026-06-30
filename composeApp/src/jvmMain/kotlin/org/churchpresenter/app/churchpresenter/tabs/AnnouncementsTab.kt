package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.composables.DropdownSelector
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Slider
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
import churchpresenter.composeapp.generated.resources.add_to_schedule
import churchpresenter.composeapp.generated.resources.tooltip_add_to_schedule
import churchpresenter.composeapp.generated.resources.tooltip_go_live
import churchpresenter.composeapp.generated.resources.tooltip_announcement_show
import churchpresenter.composeapp.generated.resources.tooltip_announcement_hide
import churchpresenter.composeapp.generated.resources.anim_slide_from_bottom
import churchpresenter.composeapp.generated.resources.anim_slide_from_left
import churchpresenter.composeapp.generated.resources.anim_slide_from_right
import churchpresenter.composeapp.generated.resources.anim_slide_from_top
import churchpresenter.composeapp.generated.resources.animation_fade
import churchpresenter.composeapp.generated.resources.animation_none
import churchpresenter.composeapp.generated.resources.announcement_animation
import churchpresenter.composeapp.generated.resources.announcement_animation_speed
import churchpresenter.composeapp.generated.resources.announcement_loop_count
import churchpresenter.composeapp.generated.resources.announcement_loop_tooltip
import churchpresenter.composeapp.generated.resources.preview
import churchpresenter.composeapp.generated.resources.time_separator
import churchpresenter.composeapp.generated.resources.announcement_background_color_label
import churchpresenter.composeapp.generated.resources.announcement_text
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
import churchpresenter.composeapp.generated.resources.go_live
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import churchpresenter.composeapp.generated.resources.ic_playlist_add
import churchpresenter.composeapp.generated.resources.ic_refresh
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import churchpresenter.composeapp.generated.resources.position_on_screen
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
import org.churchpresenter.app.churchpresenter.composables.ConditionalTooltipArea
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.HorizontalAlignmentButtons
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.app.churchpresenter.data.settings.AnnouncementsSettings
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.presenterAspectRatio
import org.churchpresenter.app.churchpresenter.utils.presenterScreenBounds
import org.churchpresenter.app.churchpresenter.utils.Utils
import org.churchpresenter.app.churchpresenter.viewmodel.AnnouncementsViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.GraphicsEnvironment

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnnouncementsTab(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    presenterManager: PresenterManager? = null,
    onAddToSchedule: ((settings: AnnouncementsSettings) -> Unit)? = null,
    scheduleTimerVersion: Int = 0
) {
    val viewModel = remember { AnnouncementsViewModel() }

    DisposableEffect(Unit) { onDispose { viewModel.dispose() } }

    // Sync from settings on load / settings change
    LaunchedEffect(appSettings.announcementsSettings) {
        viewModel.syncFromSettings(appSettings.announcementsSettings)
    }

    val availableFonts = remember {
        GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
    }

    val timerExpiredLabel = stringResource(Res.string.timer_expired)
    val startLabel        = stringResource(Res.string.timer_start)
    val pauseLabel        = stringResource(Res.string.timer_pause)
    val resetLabel        = stringResource(Res.string.timer_reset)
    val hrLabel           = stringResource(Res.string.timer_hours)
    val minLabel          = stringResource(Res.string.timer_minutes)
    val secLabel          = stringResource(Res.string.timer_seconds)

    // Auto-start timer when triggered from the schedule panel
    LaunchedEffect(scheduleTimerVersion) {
        if (scheduleTimerVersion == 0) return@LaunchedEffect
        viewModel.syncFromSettings(appSettings.announcementsSettings)
        viewModel.resetTimer()
        val anyScreenOnAnnouncements = presenterManager?.presentingMode?.value == Presenting.ANNOUNCEMENTS
            || presenterManager?.screenLocks?.value?.values?.any { it == Presenting.ANNOUNCEMENTS } == true
        if (presenterManager != null && anyScreenOnAnnouncements) {
            presenterManager.setAnnouncementText(AnnouncementsViewModel.formatTimer(viewModel.timerRemaining))
        }
        presenterManager?.setTimerState(viewModel.timerRemaining, true)
        viewModel.startPauseTimer(
            onTick = { remaining ->
                val anyScreen = presenterManager?.presentingMode?.value == Presenting.ANNOUNCEMENTS
                    || presenterManager?.screenLocks?.value?.values?.any { it == Presenting.ANNOUNCEMENTS } == true
                if (presenterManager != null && anyScreen)
                    presenterManager.setAnnouncementText(AnnouncementsViewModel.formatTimer(remaining))
                presenterManager?.setTimerState(remaining, true)
            },
            onExpired = { expiredMsg ->
                if (presenterManager != null) {
                    presenterManager.setAnnouncementText(expiredMsg.ifBlank { timerExpiredLabel })
                    presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
                    presenterManager.setTimerState(0, false)
                }
            }
        )
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
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
            if (onAddToSchedule != null) {
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.tooltip_add_to_schedule), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(
                        onClick = { onAddToSchedule.invoke(viewModel.buildSettings().copy(timerHours = 0, timerMinutes = 0, timerSeconds = 0, timerTextColor = "#FFFFFF", timerExpiredText = "", timerMode = Constants.TIMER_MODE_DURATION)) },
                        enabled = viewModel.text.isNotBlank(),
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary, disabledContainerColor = MaterialTheme.colorScheme.outlineVariant, disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    ) {
                        Icon(painterResource(Res.drawable.ic_playlist_add), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (presenterManager != null) {
                val announcementTextIsLive = presenterManager.presentingMode.value == Presenting.ANNOUNCEMENTS && !viewModel.timerRunning
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(if (announcementTextIsLive) Res.string.tooltip_announcement_hide else Res.string.tooltip_announcement_show), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(
                        onClick = {
                            if (announcementTextIsLive) presenterManager.requestClearDisplay()
                            else { if (viewModel.timerRunning) viewModel.pauseTimer(); presenterManager.setAnnouncementText(viewModel.text); presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS) }
                        },
                        enabled = viewModel.text.isNotBlank() || announcementTextIsLive,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (announcementTextIsLive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (announcementTextIsLive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.outlineVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    ) {
                        Icon(painterResource(if (announcementTextIsLive) Res.drawable.ic_pause else Res.drawable.ic_play), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
                TooltipArea(
                    tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.tooltip_go_live), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } },
                    tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.BottomCenter, offset = DpOffset(0.dp, 4.dp))
                ) {
                    IconButton(
                        onClick = { viewModel.goLive(presenterManager, onSettingsChange) },
                        enabled = viewModel.text.isNotBlank(),
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, disabledContainerColor = MaterialTheme.colorScheme.outlineVariant, disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ── Formatting bar ────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ColorPickerField(color = viewModel.textColor, onColorChange = { viewModel.setTextColor(it); viewModel.saveToSettings(onSettingsChange) }, modifier = Modifier.width(120.dp))
            HorizontalDivider(modifier = Modifier.height(22.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TextStyleButtons(
                bold = viewModel.bold, italic = viewModel.italic, underline = viewModel.underline, shadow = viewModel.shadow,
                onBoldChange = { viewModel.setBold(it); viewModel.saveToSettings(onSettingsChange) },
                onItalicChange = { viewModel.setItalic(it); viewModel.saveToSettings(onSettingsChange) },
                onUnderlineChange = { viewModel.setUnderline(it); viewModel.saveToSettings(onSettingsChange) },
                onShadowChange = { viewModel.setShadow(it); viewModel.saveToSettings(onSettingsChange) }
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
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp)) {
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
                                OutlinedButton(onClick = { viewModel.setBackgroundColor("transparent"); viewModel.saveToSettings(onSettingsChange) }, shape = RoundedCornerShape(8.dp)) {
                                    Text(stringResource(Res.string.transparent_default), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    // Position on screen
                    SectionLabel(stringResource(Res.string.position_on_screen))
                    BoxWithConstraints(modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth()) {
                        val useAbbrev = maxWidth / 3 < 80.dp
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            positions.chunked(3).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    rowItems.forEach { (posConst, posLabel) ->
                                        val isSelected = viewModel.position == posConst
                                        val displayLabel = if (useAbbrev)
                                            posLabel.split(" ").joinToString("") { it.first().toString() }
                                        else posLabel
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(if (useAbbrev) 1.5f else 3f)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .border(
                                                    BorderStroke(
                                                        1.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                                    ),
                                                    RoundedCornerShape(3.dp)
                                                )
                                                .clickable {
                                                    viewModel.setPosition(posConst)
                                                    viewModel.saveToSettings(onSettingsChange)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = displayLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── TIMER section ──────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val timerModeDurationLabel = stringResource(Res.string.timer_mode_duration)
                        val timerModeClockLabel = stringResource(Res.string.timer_mode_clock)
                        val timerTargetTimeLabel = stringResource(Res.string.timer_target_time)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(Res.string.timer_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            SegmentedButton(
                                items = listOf(
                                    SegmentedButtonItem(Constants.TIMER_MODE_DURATION, timerModeDurationLabel),
                                    SegmentedButtonItem(Constants.TIMER_MODE_CLOCK, timerModeClockLabel)
                                ),
                                selectedValue = viewModel.timerMode,
                                onValueChange = { mode ->
                                    viewModel.setTimerMode(mode)
                                    viewModel.saveToSettings(onSettingsChange)
                                },
                                buttonWidth = 120.dp,
                                buttonHeight = 28.dp,
                                fontSize = 11.sp
                            )
                        }

                        // Countdown display
                        Text(
                            text = AnnouncementsViewModel.formatTimer(viewModel.timerRemaining),
                            style = MaterialTheme.typography.displayMedium,
                            color = if (viewModel.timerExpired) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "$timerTargetTimeLabel %02d:%02d:%02d".format(
                                viewModel.targetHour, viewModel.targetMinute, viewModel.targetSecond
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
                        val sepStyle = MaterialTheme.typography.displaySmall
                        // separator aligns with the center of the number field (after 28dp button + 4dp gap)
                        val sepBox: @Composable () -> Unit = {
                            Box(
                                modifier = Modifier.padding(top = 28.dp + 4.dp).height(64.dp),
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
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
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
                                    onValueChange = { v -> val d = v.filter { it.isDigit() }.take(2); secText = d; d.toIntOrNull()?.let { viewModel.setTimerSeconds(it.coerceIn(0, 59)); viewModel.saveToSettings(onSettingsChange) } }
                                )
                            }
                        } else {
                            var tHrText by remember { mutableStateOf("%02d".format(viewModel.targetHour)) }
                            var tMinText by remember { mutableStateOf("%02d".format(viewModel.targetMinute)) }
                            var tSecText by remember { mutableStateOf("%02d".format(viewModel.targetSecond)) }
                            LaunchedEffect(viewModel.targetHour) { tHrText = "%02d".format(viewModel.targetHour) }
                            LaunchedEffect(viewModel.targetMinute) { tMinText = "%02d".format(viewModel.targetMinute) }
                            LaunchedEffect(viewModel.targetSecond) { tSecText = "%02d".format(viewModel.targetSecond) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.Top
                            ) {
                                TimerColumn(tHrText, hrLabel,
                                    onIncrement = { viewModel.stepTargetHour(1); viewModel.saveToSettings(onSettingsChange) },
                                    onDecrement = { viewModel.stepTargetHour(-1); viewModel.saveToSettings(onSettingsChange) },
                                    onValueChange = { v -> val d = v.filter { it.isDigit() }.take(2); tHrText = d; d.toIntOrNull()?.let { viewModel.setTargetHour(it); viewModel.saveToSettings(onSettingsChange) } }
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
                                    onValueChange = { v -> val d = v.filter { it.isDigit() }.take(2); tSecText = d; d.toIntOrNull()?.let { viewModel.setTargetSecond(it.coerceIn(0, 59)); viewModel.saveToSettings(onSettingsChange) } }
                                )
                            }
                        }

                        // Controls row
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            val total = viewModel.timerHours * 3600 + viewModel.timerMinutes * 60 + viewModel.timerSeconds
                            ConditionalTooltipArea(tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(if (viewModel.timerRunning) pauseLabel else startLabel, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } }) {
                                IconButton(
                                    onClick = {
                                        viewModel.saveToSettings(onSettingsChange)
                                        val anyScreenOnAnnouncements = presenterManager?.presentingMode?.value == Presenting.ANNOUNCEMENTS || presenterManager?.screenLocks?.value?.values?.any { it == Presenting.ANNOUNCEMENTS } == true
                                        if (!viewModel.timerRunning && presenterManager != null && anyScreenOnAnnouncements) {
                                            presenterManager.setAnnouncementText(AnnouncementsViewModel.formatTimer(viewModel.timerRemaining))
                                        }
                                        presenterManager?.setTimerState(viewModel.timerRemaining, !viewModel.timerRunning)
                                        viewModel.startPauseTimer(
                                            onTick = { remaining ->
                                                val anyScreenOnAnnouncements = presenterManager?.presentingMode?.value == Presenting.ANNOUNCEMENTS || presenterManager?.screenLocks?.value?.values?.any { it == Presenting.ANNOUNCEMENTS } == true
                                                if (presenterManager != null && anyScreenOnAnnouncements) presenterManager.setAnnouncementText(AnnouncementsViewModel.formatTimer(remaining))
                                                presenterManager?.setTimerState(remaining, true)
                                            },
                                            onExpired = { expiredMsg ->
                                                if (presenterManager != null) {
                                                    presenterManager.setAnnouncementText(expiredMsg.ifBlank { timerExpiredLabel })
                                                    presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
                                                    presenterManager.setTimerState(0, false)
                                                }
                                            }
                                        )
                                    },
                                    enabled = viewModel.timerMode == Constants.TIMER_MODE_CLOCK || total > 0 || viewModel.timerRunning,
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (viewModel.timerRunning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                ) {
                                    Icon(painter = painterResource(if (viewModel.timerRunning) Res.drawable.ic_pause else Res.drawable.ic_play), contentDescription = if (viewModel.timerRunning) pauseLabel else startLabel, tint = if (viewModel.timerRunning) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                            ConditionalTooltipArea(tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(resetLabel, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } }) {
                                IconButton(onClick = { viewModel.resetTimer(); val total = viewModel.timerHours * 3600 + viewModel.timerMinutes * 60 + viewModel.timerSeconds; presenterManager?.setTimerState(total, false) }, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    Icon(painter = painterResource(Res.drawable.ic_refresh), contentDescription = resetLabel, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (onAddToSchedule != null) {
                                ConditionalTooltipArea(tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.tooltip_add_to_schedule), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } }) {
                                    IconButton(onClick = { onAddToSchedule.invoke(viewModel.buildSettings()) }, enabled = viewModel.text.isNotBlank() || viewModel.timerMode == Constants.TIMER_MODE_CLOCK || viewModel.timerHours > 0 || viewModel.timerMinutes > 0 || viewModel.timerSeconds > 0, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary, disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))) {
                                        Icon(painter = painterResource(Res.drawable.ic_playlist_add), contentDescription = stringResource(Res.string.add_to_schedule), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            if (presenterManager != null) {
                                ConditionalTooltipArea(tooltip = { Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = MaterialTheme.shapes.extraSmall, tonalElevation = 4.dp) { Text(stringResource(Res.string.tooltip_go_live), color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall) } }) {
                                    IconButton(onClick = { presenterManager.setAnnouncementText(AnnouncementsViewModel.formatTimer(viewModel.timerRemaining)); presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS) }, colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))) {
                                        Icon(Icons.Default.Tv, contentDescription = stringResource(Res.string.go_live), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        // Expired text field
                        SectionLabel(stringResource(Res.string.timer_expired_text_label))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
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
                    } // end timer section

                } // end left column

                // Drag handle
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(onDragEnd = { saveLeftPanel() }) { _, amount ->
                                leftPanelPx = (leftPanelPx + amount).coerceIn(
                                    with(density) { 150.dp.toPx() },
                                    (twoColWidthPx - with(density) { 100.dp.toPx() }).coerceAtLeast(with(density) { 150.dp.toPx() })
                                )
                            }
                        }
                )

                // ── RIGHT COLUMN: preview + animation/loop/speed ──────────
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Show timer countdown in preview while running, expired message when done,
                    // and fall back to announcement text otherwise.
                    val previewText = when {
                        viewModel.timerRunning -> AnnouncementsViewModel.formatTimer(viewModel.timerRemaining)
                        viewModel.timerExpired -> viewModel.timerExpiredText.ifBlank { timerExpiredLabel }
                        else -> viewModel.text
                    }
                    var previewWidthPx by remember { mutableStateOf(0) }
                    var previewHeightPx by remember { mutableStateOf(0) }
                    val scaleFactor = if (previewWidthPx > 0)
                        (previewWidthPx / density.density) / presenterScreenBounds().width.toFloat()
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
                    val isDirectional = viewModel.animationType in listOf(
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
                            .aspectRatio(presenterAspectRatio())
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
                                        Text(
                                            text = previewText.ifBlank { stringResource(Res.string.preview) },
                                            style = previewTextStyle,
                                            fontSize = scaledFontSize,
                                            color = Utils.parseHexColor(viewModel.textColor),
                                            textAlign = previewTextAlign,
                                            softWrap = !isHorizontal,
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
                                        if (viewModel.animationType == Constants.ANIMATION_FADE)
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
                                            Text(
                                                text = text.ifBlank { stringResource(Res.string.preview) },
                                                style = previewTextStyle,
                                                fontSize = scaledFontSize,
                                                color = Utils.parseHexColor(viewModel.textColor),
                                                textAlign = previewTextAlign,
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${"%.1f".format((sliderSum - durationMs) / 1000f)}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            Slider(
                                value = (sliderSum - durationMs.toFloat()),
                                onValueChange = { v ->
                                    val dur = (sliderSum - v)
                                    val snapped = (dur / sliderMin).toInt() * sliderMin.toInt()
                                    viewModel.setAnimationDuration(snapped.coerceIn(sliderMin.toInt(), sliderMax.toInt()))
                                    viewModel.saveToSettings(onSettingsChange)
                                },
                                valueRange = sliderMin..sliderMax,
                                steps = 58,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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

@Composable
private fun TimerColumn(
    value: String,
    label: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val buttonColor = MaterialTheme.colorScheme.surfaceVariant
    val buttonShape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(
            onClick = onIncrement,
            modifier = Modifier.height(28.dp).widthIn(min = 48.dp),
            shape = buttonShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.displaySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier
                .size(68.dp, 64.dp)
                .background(buttonColor, buttonShape)
                .padding(horizontal = 4.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { inner() }
            }
        )
        Button(
            onClick = onDecrement,
            modifier = Modifier.height(28.dp).widthIn(min = 48.dp),
            shape = buttonShape,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}
