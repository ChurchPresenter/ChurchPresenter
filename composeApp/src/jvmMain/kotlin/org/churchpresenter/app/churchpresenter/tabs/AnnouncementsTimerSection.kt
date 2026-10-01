package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.composables.ActionIconButton
import org.churchpresenter.app.churchpresenter.composables.AddToScheduleButton
import org.churchpresenter.app.churchpresenter.composables.SavePresetButton
import org.churchpresenter.app.churchpresenter.composables.GoLiveButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import org.churchpresenter.theme.AppShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res as AppRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.tooltip_add_to_schedule
import org.churchpresenter.strings.generated.resources.save_preset
import org.churchpresenter.strings.generated.resources.tooltip_go_live
import org.churchpresenter.strings.generated.resources.tooltip_send_to_stage_monitor
import org.churchpresenter.strings.generated.resources.tooltip_hide_from_stage_monitor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import churchpresenter.composeapp.generated.resources.ic_refresh
import churchpresenter.composeapp.generated.resources.ic_pause
import churchpresenter.composeapp.generated.resources.ic_play
import org.churchpresenter.strings.generated.resources.canvas_source_clock
import org.churchpresenter.strings.generated.resources.timer_am
import org.churchpresenter.strings.generated.resources.timer_clock_format
import org.churchpresenter.strings.generated.resources.timer_clock_format_12h_sec
import org.churchpresenter.strings.generated.resources.timer_clock_format_12h
import org.churchpresenter.strings.generated.resources.timer_clock_format_24h_sec
import org.churchpresenter.strings.generated.resources.timer_clock_format_24h
import org.churchpresenter.strings.generated.resources.timer_pm
import org.churchpresenter.strings.generated.resources.timer_expired_text_hint
import org.churchpresenter.strings.generated.resources.timer_expired_text_label
import org.churchpresenter.strings.generated.resources.timer_title
import org.churchpresenter.strings.generated.resources.timer_mode_duration
import org.churchpresenter.strings.generated.resources.timer_mode_clock
import org.churchpresenter.strings.generated.resources.timer_target_time
import org.churchpresenter.app.churchpresenter.composables.DropdownSettingsField
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.isSystemUsing24HourFormat
import org.churchpresenter.app.churchpresenter.viewmodel.AnnouncementsViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette

/* The Announcements tab's timer: its mode, its display, its steppers, its controls. */

@Composable
internal fun AnnouncementsTabScope.AnnouncementsTimerSection(viewModel: AnnouncementsViewModel) {
    // ── TIMER section ──────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bibleInsetFill(), AppShape(11.dp))
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
            if (use24Hour) hour24 else ((hour24 + ANNOUNCEMENT_HOUR_WRAP_OFFSET) % ANNOUNCEMENT_HOURS_PER_HALF_DAY) + 1

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
                viewModel.isTimerExpired -> viewModel.timerExpiredText.ifBlank { timerExpiredLabel }
                viewModel.timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY ->
                    viewModel.liveClockText
                else -> AnnouncementsViewModel.formatTimer(viewModel.timerDisplayValue)
            },
            style = MaterialTheme.typography.displayMedium,
            color = if (viewModel.isTimerExpired) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        val amPmSuffix =
            if (use24Hour) "" else " " + stringResource(if (targetIsPm) Res.string.timer_pm else Res.string.timer_am)
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
                modifier = Modifier
                    .padding(top = ANNOUNCEMENT_STEP_KEY_HEIGHT + ANNOUNCEMENT_STEP_GAP)
                    .height(ANNOUNCEMENT_WELL_HEIGHT),
                contentAlignment = Alignment.Center
            ) {
                Text(":", style = sepStyle, color = sepColor)
            }
        }
        if (viewModel.timerMode == Constants.TIMER_MODE_DURATION) {
            DurationSteppers(viewModel, sepBox)
        } else if (viewModel.timerMode == Constants.TIMER_MODE_CLOCK) {
            ClockSteppers(viewModel, use24Hour, targetIsPm, ::displayHour, sepBox)
        } else if (viewModel.timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY) {
            ClockFormatField(viewModel)
        }

        TimerControls(viewModel)
        ExpiredTextField(viewModel)
    }
}

@Composable
private fun AnnouncementsTabScope.DurationSteppers(viewModel: AnnouncementsViewModel, sepBox: @Composable () -> Unit) {
    var hrText by remember { mutableStateOf("%02d".format(viewModel.timerHours)) }
    var minText by remember { mutableStateOf("%02d".format(viewModel.timerMinutes)) }
    var secText by remember { mutableStateOf("%02d".format(viewModel.timerSeconds)) }
    LaunchedEffect(viewModel.timerHours) {
        hrText = "%02d".format(viewModel.timerHours)
    }
    LaunchedEffect(viewModel.timerMinutes) {
        minText = "%02d".format(viewModel.timerMinutes)
    }
    LaunchedEffect(viewModel.timerSeconds) {
        secText = "%02d".format(viewModel.timerSeconds)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        TimerColumn(hrText, hrLabel,
            onIncrement = { viewModel.stepTimerHours(1); viewModel.saveToSettings(onSettingsChange) },
            onDecrement = { viewModel.stepTimerHours(-1); viewModel.saveToSettings(onSettingsChange) },
            onValueChange = { v ->
                val d = v.filter { it.isDigit() }.take(2)
                hrText = d
                d.toIntOrNull()?.let { viewModel.setTimerHours(it); viewModel.saveToSettings(onSettingsChange) }
            }
        )
        sepBox()
        TimerColumn(minText, minLabel,
            onIncrement = { viewModel.stepTimerMinutes(1); viewModel.saveToSettings(onSettingsChange) },
            onDecrement = { viewModel.stepTimerMinutes(-1); viewModel.saveToSettings(onSettingsChange) },
            onValueChange = { v ->
                val d = v.filter { it.isDigit() }.take(2)
                minText = d
                d.toIntOrNull()?.let { viewModel.setTimerMinutes(it); viewModel.saveToSettings(onSettingsChange) }
            }
        )
        sepBox()
        TimerColumn(secText, secLabel,
            onIncrement = { viewModel.stepTimerSeconds(1); viewModel.saveToSettings(onSettingsChange) },
            onDecrement = { viewModel.stepTimerSeconds(-1); viewModel.saveToSettings(onSettingsChange) },
            onValueChange = { v ->
                val d = v.filter { it.isDigit() }.take(2)
                secText = d
                d.toIntOrNull()?.let {
                    viewModel.setTimerSeconds(it.coerceIn(0, ANNOUNCEMENT_MAX_SECOND))
                    viewModel.saveToSettings(onSettingsChange)
                }
            }
        )
    }
}

@Composable
private fun AnnouncementsTabScope.ClockSteppers(
    viewModel: AnnouncementsViewModel,
    use24Hour: Boolean,
    targetIsPm: Boolean,
    displayHour: (Int) -> Int,
    sepBox: @Composable () -> Unit,
) {
    var tHrText by remember {
        mutableStateOf("%02d".format(displayHour(viewModel.targetHour)))
    }
    var tMinText by remember { mutableStateOf("%02d".format(viewModel.targetMinute)) }
    var tSecText by remember { mutableStateOf("%02d".format(viewModel.targetSecond)) }
    LaunchedEffect(viewModel.targetHour) {
        tHrText = "%02d".format(displayHour(viewModel.targetHour))
    }
    LaunchedEffect(viewModel.targetMinute) {
        tMinText = "%02d".format(viewModel.targetMinute)
    }
    LaunchedEffect(viewModel.targetSecond) {
        tSecText = "%02d".format(viewModel.targetSecond)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
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
            onValueChange = { v ->
                val d = v.filter { it.isDigit() }.take(2)
                tMinText = d
                d.toIntOrNull()?.let { viewModel.setTargetMinute(it); viewModel.saveToSettings(onSettingsChange) }
            }
        )
        sepBox()
        TimerColumn(tSecText, secLabel,
            onIncrement = { viewModel.stepTargetSecond(1); viewModel.saveToSettings(onSettingsChange) },
            onDecrement = { viewModel.stepTargetSecond(-1); viewModel.saveToSettings(onSettingsChange) },
            onValueChange = { v ->
                val d = v.filter { it.isDigit() }.take(2)
                tSecText = d
                d.toIntOrNull()?.let {
                    viewModel.setTargetSecond(it.coerceIn(0, ANNOUNCEMENT_MAX_SECOND))
                    viewModel.saveToSettings(onSettingsChange)
                }
            }
        )
        if (!use24Hour) {
            sepBox()
            AmPmToggle(
                isPm = targetIsPm,
                onToggle = {
                    viewModel.setTargetHour(
                        (viewModel.targetHour + ANNOUNCEMENT_HOURS_PER_HALF_DAY) % ANNOUNCEMENT_HOURS_PER_DAY,
                    )
                    viewModel.saveToSettings(onSettingsChange)
                }
            )
        }
    }
}

@Composable
private fun AnnouncementsTabScope.ClockFormatField(viewModel: AnnouncementsViewModel) {
    val clockFormatLabels = mapOf(
        "h:mm:ss a" to stringResource(Res.string.timer_clock_format_12h_sec),
        "h:mm a" to stringResource(Res.string.timer_clock_format_12h),
        "HH:mm:ss" to stringResource(Res.string.timer_clock_format_24h_sec),
        "HH:mm" to stringResource(Res.string.timer_clock_format_24h)
    )
    val patternForLabel = clockFormatLabels.entries.associate { (pattern, label) -> label to pattern }
    DropdownSettingsField(
        value = clockFormatLabels[viewModel.liveClockFormat]
            ?: viewModel.liveClockFormat,
        options = clockFormatLabels.values.toList(),
        onValueChange = { picked ->
            patternForLabel[picked]?.let {
                viewModel.setLiveClockFormat(it)
                viewModel.saveToSettings(onSettingsChange)
            }
        },
        label = stringResource(Res.string.timer_clock_format),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Play/pause, reset, stage monitor, Save preset, Add to schedule and Go live, for the timer. */
@Composable
private fun AnnouncementsTabScope.TimerControls(viewModel: AnnouncementsViewModel) {
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
            painter = painterResource(if (isTimerRunning) AppRes.drawable.ic_pause else AppRes.drawable.ic_play),
            containerColor = if (isTimerRunning) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (isTimerRunning) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
        // Reset only makes sense for Timer/Duration, which count down/up from a
        // starting point. Specific Time and the live Clock always track the wall
        // clock automatically — there's nothing to reset back to.
        if (viewModel.isDurationOrCountUp) {
            ActionIconButton(
                onClick = { viewModel.resetTimer(presenterManager) },
                tooltipText = resetLabel,
                painter = painterResource(AppRes.drawable.ic_refresh),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Stage Monitor already shows its own always-on clock, so the plain
        // "Clock" timer mode has nothing extra to send there.
        TimerStageMonitorButton(viewModel)
        TimerScheduleButtons(viewModel)
    }
}

/** Sends the timer to the stage monitor, or takes it off; the only other way (with Go live) to mark it live. */
@Composable
private fun AnnouncementsTabScope.TimerStageMonitorButton(viewModel: AnnouncementsViewModel) {
    if (presenterManager != null &&
        canSendToStageMonitor &&
        viewModel.timerMode != Constants.TIMER_MODE_CLOCK_DISPLAY) {
        ActionIconButton(
            onClick = {
                // Sending is the only thing (besides Go Live) allowed to mark the
                // ticker live — the play/pause button above stays preview-only.
                if (!isSentToStageMonitor) {
                    // Sending Specific Time to Stage Monitor also (re)starts its
                    // ticker if it wasn't already running via the play/pause button.
                    if (viewModel.timerMode == Constants.TIMER_MODE_CLOCK) {
                        presenterManager.startAnnouncementSpecificTime(
                            viewModel.targetHour,
                            viewModel.targetMinute,
                            viewModel.targetSecond,
                        )
                    }
                    presenterManager.setAnnouncementTickerLive(true)
                }
                val liveText = AnnouncementsViewModel.formatTimer(viewModel.timerDisplayValue)
                toggleStageMonitor(viewModel, liveText)
            },
            tooltipText = if (isSentToStageMonitor) stringResource(
                Res.string.tooltip_hide_from_stage_monitor,
            ) else stringResource(Res.string.tooltip_send_to_stage_monitor),
            icon = if (isSentToStageMonitor) Icons.Default.CastConnected else Icons.Default.Cast,
            containerColor = if (isSentToStageMonitor) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSentToStageMonitor) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** Save preset, Add to schedule and Go live, for the timer. */
@Composable
private fun AnnouncementsTabScope.TimerScheduleButtons(viewModel: AnnouncementsViewModel) {
    if (onSavePreset != null) {
        SavePresetButton(
            onClick = { onSavePreset.invoke(viewModel.buildSettings()) },
            enabled = viewModel.text.isNotBlank() ||
                viewModel.timerMode != Constants.TIMER_MODE_DURATION ||
                viewModel.timerHours > 0 ||
                viewModel.timerMinutes > 0 ||
                viewModel.timerSeconds > 0,
            tooltipText = stringResource(Res.string.save_preset)
        )
    }
    if (onAddToSchedule != null) {
        AddToScheduleButton(
            onClick = { onAddToSchedule.invoke(viewModel.buildSettings()) },
            enabled = viewModel.text.isNotBlank() ||
                viewModel.timerMode != Constants.TIMER_MODE_DURATION ||
                viewModel.timerHours > 0 ||
                viewModel.timerMinutes > 0 ||
                viewModel.timerSeconds > 0,
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
                    Constants.TIMER_MODE_CLOCK -> presenterManager.startAnnouncementSpecificTime(
                        viewModel.targetHour,
                        viewModel.targetMinute,
                        viewModel.targetSecond,
                    )
                    Constants.TIMER_MODE_CLOCK_DISPLAY -> presenterManager.startAnnouncementClockDisplay(
                        viewModel.liveClockFormat,
                    )
                    else -> {}
                }
                presenterManager.setAnnouncementTickerLive(true)
                val liveText = if (viewModel.timerMode == Constants.TIMER_MODE_CLOCK_DISPLAY) {
                    viewModel.liveClockText
                } else {
                    AnnouncementsViewModel.formatTimer(viewModel.timerDisplayValue)
                }
                presenterManager.setAnnouncementText(liveText)
                presenterManager.setPresentingMode(Presenting.ANNOUNCEMENTS)
            },
            tooltipText = stringResource(Res.string.tooltip_go_live)
        )
    }
}

@Composable
private fun AnnouncementsTabScope.ExpiredTextField(viewModel: AnnouncementsViewModel) {
    // Expired text field — only meaningful for modes that actually reach an
    // endpoint (Timer countdown, Specific Time). Duration (count-up) and the
    // live Clock display never "expire".
    if (viewModel.timerMode == Constants.TIMER_MODE_DURATION || viewModel.timerMode == Constants.TIMER_MODE_CLOCK) {
        SectionLabel(stringResource(Res.string.timer_expired_text_label))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .sunken(AppShape(8.dp), elevationPalette())
                .hoverTint(AppShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = viewModel.timerExpiredText,
                onValueChange = {
                    viewModel.setTimerExpiredText(it)
                    viewModel.saveToSettings(onSettingsChange)
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (viewModel.timerExpiredText.isEmpty()) {
                        Text(
                            stringResource(Res.string.timer_expired_text_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            )
        }
    }
}
