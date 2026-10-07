package org.churchpresenter.announcements

import org.churchpresenter.sharedui.composables.ActionIconButton
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.sharedui.composables.AddToScheduleButton
import org.churchpresenter.sharedui.composables.SavePresetButton
import org.churchpresenter.sharedui.composables.GoLiveButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import org.churchpresenter.theme.AppShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.tooltip_add_to_schedule
import org.churchpresenter.strings.generated.resources.save_preset
import org.churchpresenter.strings.generated.resources.tooltip_go_live
import org.churchpresenter.strings.generated.resources.tooltip_send_to_stage_monitor
import org.churchpresenter.strings.generated.resources.tooltip_hide_from_stage_monitor
import org.churchpresenter.strings.generated.resources.announcement_text_hint
import org.churchpresenter.strings.generated.resources.font_size
import org.churchpresenter.strings.generated.resources.font_type
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import org.churchpresenter.strings.generated.resources.text_color
import org.churchpresenter.sharedui.composables.ColorPickerField
import org.churchpresenter.sharedui.composables.FontSettingsDropdown
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.sharedui.composables.ShadowDetailRow
import org.churchpresenter.sharedui.composables.HorizontalAlignmentButtons
import org.churchpresenter.sharedui.composables.TextStyleButtons
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.SettingsScrollbarGutter
import org.churchpresenter.theme.hoverTint
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.sharedui.composables.DragHandle
import org.churchpresenter.sharedui.composables.bibleListCard

/*
 * The Announcements tab's left column: the text card, with its actions, input and formatting, and
 * the timer card under it.
 */

@Composable
internal fun AnnouncementsTabScope.AnnouncementsLeftColumn(viewModel: AnnouncementsViewModel) {
    Column(modifier = Modifier.width(with(density) { leftPanelPx.toDp() }).fillMaxHeight()) {
        AnnouncementsTextCard(viewModel)
        // Drag down to give the text box more lines; the settings card below keeps at least
        // ANNOUNCEMENT_MIN_SETTINGS_HEIGHT so the timer stays reachable.
        DragHandle(
            onDragEnd = { saveTextHeight() },
            orientation = Orientation.Vertical,
            modifier = Modifier.testTag(ANNOUNCEMENTS_TEXT_DIVIDER_TAG),
        ) { delta ->
            val start = if (shownTextHeightPx > 0f) shownTextHeightPx else naturalTextHeightPx.toFloat()
            val min = with(density) { ANNOUNCEMENT_MIN_TEXT_HEIGHT.toPx() }
            textHeightPx = (start + delta).coerceIn(min, maxTextHeightPx)
        }
        AnnouncementsSettingsCard(viewModel, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun AnnouncementsTabScope.AnnouncementsTextCard(viewModel: AnnouncementsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .bibleListCard()
            .onSizeChanged { textCardHeightPx = it.height }
    ) {
        AnnouncementsTextActions(viewModel)
        AnnouncementsTextInput(viewModel)
        AnnouncementsFormattingBar(viewModel)
        AnimatedVisibility(visible = viewModel.shadow) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(
                    horizontal = 16.dp,
                    vertical = 6.dp,
                )) {
                    ShadowDetailRow(
                        shadowColor = appSettings.announcementsSettings.shadowColor,
                        shadowSize = appSettings.announcementsSettings.shadowSize,
                        shadowOpacity = appSettings.announcementsSettings.shadowOpacity,
                        onColorChange = { c -> onSettingsChange { s -> s.copy(
                            announcementsSettings = s.announcementsSettings.copy(shadowColor = c),
                        ) } },
                        onSizeChange = { v -> onSettingsChange { s -> s.copy(
                            announcementsSettings = s.announcementsSettings.copy(shadowSize = v),
                        ) } },
                        onOpacityChange = { v -> onSettingsChange { s -> s.copy(
                            announcementsSettings = s.announcementsSettings.copy(shadowOpacity = v),
                        ) } }
                    )
                }
            }
        }
    }
}

/** Stage monitor, Save preset, Add to schedule and Go live, for the text. */
@Composable
private fun AnnouncementsTabScope.AnnouncementsTextActions(viewModel: AnnouncementsViewModel) {
    // ── Text actions ──────────────────────────────────────────────
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (output != null) {
            if (canSendToStageMonitor) {
                ActionIconButton(
                    onClick = { toggleStageMonitor(viewModel, viewModel.text, stopTicker = true) },
                    enabled = viewModel.text.isNotBlank() || isSentToStageMonitor,
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
                onClick = { onAddToSchedule.invoke(viewModel.buildSettings().copy(
                    timerHours = 0,
                    timerMinutes = 0,
                    timerSeconds = 0,
                    timerTextColor = "#FFFFFF",
                    timerExpiredText = "",
                    timerMode = Constants.TIMER_MODE_DURATION,
                )) },
                enabled = viewModel.text.isNotBlank(),
                tooltipText = stringResource(Res.string.tooltip_add_to_schedule)
            )
        }
        if (output != null) {
            GoLiveButton(
                modifier = Modifier.guideTarget(GuideTargets.ANNOUNCEMENT_GO_LIVE),
                onClick = { viewModel.goLive(output, onSettingsChange) },
                enabled = viewModel.text.isNotBlank(),
                tooltipText = stringResource(Res.string.tooltip_go_live)
            )
        }
    }
}

@Composable
private fun AnnouncementsTabScope.AnnouncementsTextInput(viewModel: AnnouncementsViewModel) {
    // ── Text input ────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .onSizeChanged { naturalTextHeightPx = it.height }
            .testTag(ANNOUNCEMENTS_TEXT_BOX_TAG)
            .guideTarget(GuideTargets.ANNOUNCEMENT_TEXT)
            .then(
                if (shownTextHeightPx > 0f) Modifier.height(with(density) { shownTextHeightPx.toDp() })
                else Modifier
            )
            .sunken(AppShape(8.dp), elevationPalette())
            .hoverTint(AppShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        BasicTextField(
            value = viewModel.text,
            onValueChange = { viewModel.text = it; viewModel.saveToSettings(onSettingsChange) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            maxLines = if (textHeightPx > 0f) Int.MAX_VALUE else 3,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (textHeightPx > 0f) Modifier.fillMaxHeight() else Modifier),
            decorationBox = { inner ->
                if (viewModel.text.isEmpty()) {
                    Text(
                        stringResource(Res.string.announcement_text_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                inner()
            }
        )
    }
}

/** Colour, style, alignment, font and size — wraps to the column's width. */
@Composable
private fun AnnouncementsTabScope.AnnouncementsFormattingBar(viewModel: AnnouncementsViewModel) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        ColorPickerField(
            label = stringResource(Res.string.text_color),
            color = viewModel.textColor,
            onColorChange = { viewModel.textColor = it; viewModel.saveToSettings(onSettingsChange) },
            modifier = Modifier.width(120.dp),
        )
        TextStyleButtons(
            bold = viewModel.bold,
            italic = viewModel.italic,
            underline = viewModel.underline,
            shadow = viewModel.shadow,
            onBoldChange = { viewModel.bold = it; viewModel.saveToSettings(onSettingsChange) },
            onItalicChange = {
                viewModel.italic = it
                viewModel.saveToSettings(onSettingsChange)
            },
            onUnderlineChange = {
                viewModel.underline = it
                viewModel.saveToSettings(onSettingsChange)
            },
            onShadowChange = {
                viewModel.shadow = it
                viewModel.saveToSettings(onSettingsChange)
            },
            backdrop = viewModel.backdrop,
            onBackdropChange = { updated ->
                viewModel.backdrop = updated
                viewModel.saveToSettings(onSettingsChange)
            },
            outline = viewModel.outline,
            onOutlineChange = { updated ->
                viewModel.outline = updated
                viewModel.saveToSettings(onSettingsChange)
            },
        )
        HorizontalAlignmentButtons(
            selectedAlignment = viewModel.horizontalAlignment,
            onAlignmentChange = {
                viewModel.horizontalAlignment = it
                viewModel.saveToSettings(onSettingsChange)
            },
            leftValue = Constants.LEFT, centerValue = Constants.CENTER, rightValue = Constants.RIGHT
        )
        FontSettingsDropdown(
            label = stringResource(Res.string.font_type),
            value = viewModel.fontType,
            fonts = availableFonts,
            onValueChange = { viewModel.fontType = it; viewModel.saveToSettings(onSettingsChange) },
        )
        NumberSettingsTextField(
            label = stringResource(Res.string.font_size),
            initialText = viewModel.fontSize,
            range = 8..200,
            onValueChange = { viewModel.fontSize = it; viewModel.saveToSettings(onSettingsChange) },
        )
    }
}

@Composable
private fun AnnouncementsTabScope.AnnouncementsSettingsCard(viewModel: AnnouncementsViewModel, modifier: Modifier) {
    val settingsScroll = rememberScrollState()
    Box(modifier = modifier.bibleListCard().testTag(ANNOUNCEMENTS_TIMER_CARD_TAG)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(settingsScroll)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 16.dp + SettingsScrollbarGutter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnnouncementsTimerSection(viewModel)

        } // end settings column
        SettingsScrollbar(settingsScroll)
    }
}
