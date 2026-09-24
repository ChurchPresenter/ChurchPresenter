package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.stt_background_color
import churchpresenter.composeapp.generated.resources.stt_display_mode
import churchpresenter.composeapp.generated.resources.stt_display_styling
import churchpresenter.composeapp.generated.resources.stt_drip_feed
import churchpresenter.composeapp.generated.resources.stt_drip_feed_speed
import churchpresenter.composeapp.generated.resources.stt_font
import churchpresenter.composeapp.generated.resources.stt_in_progress_text
import churchpresenter.composeapp.generated.resources.stt_layout
import churchpresenter.composeapp.generated.resources.stt_layout_side_by_side
import churchpresenter.composeapp.generated.resources.stt_layout_side_by_side_inverse
import churchpresenter.composeapp.generated.resources.stt_layout_stacked
import churchpresenter.composeapp.generated.resources.stt_layout_stacked_inverse
import churchpresenter.composeapp.generated.resources.stt_line_spacing
import churchpresenter.composeapp.generated.resources.stt_max_lines
import churchpresenter.composeapp.generated.resources.stt_max_segments
import churchpresenter.composeapp.generated.resources.stt_mode_both
import churchpresenter.composeapp.generated.resources.stt_mode_transcribe
import churchpresenter.composeapp.generated.resources.stt_mode_translate
import churchpresenter.composeapp.generated.resources.stt_opacity
import churchpresenter.composeapp.generated.resources.stt_position
import churchpresenter.composeapp.generated.resources.stt_size
import churchpresenter.composeapp.generated.resources.stt_text_color
import churchpresenter.composeapp.generated.resources.stt_translation_color
import churchpresenter.composeapp.generated.resources.stt_translation_in_progress
import churchpresenter.composeapp.generated.resources.stt_word_highlighting
import org.churchpresenter.app.churchpresenter.composables.ColorPickerField
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.ScreenPositionPicker
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.theme.components.DropdownSelector
import org.jetbrains.compose.resources.stringResource

private val MAX_SEGMENTS_RANGE = 0..100
private val MAX_LINES_RANGE = 0..50
private val LINE_SPACING_RANGE = 80..300
private val DRIP_SPEED_RANGE = 1..1000
private val DROPDOWN_WIDTH = 220.dp
private val DRIP_SPEED_WIDTH = 130.dp

/** A write to this profile's caption settings, as each control below makes one. */
private typealias SttUpdate = ((STTSettings) -> STTSettings) -> Unit

/**
 * How live captions look on one output: what they show, how they arrive, and their text and
 * background -- edited per profile on the Profiles tab's STT style tab.
 *
 * This was the lower half of [STTSettingsDialog], which is now only the install-wide part: the
 * Bible-engine options. Split rather than duplicated, so a caption setting has one control in one
 * place.
 */
@Composable
internal fun STTDisplaySettings(
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    availableFonts: List<String>,
    modifier: Modifier = Modifier,
) {
    val stt = appSettings.sttSettings
    val update: SttUpdate = { transform -> onSettingsChange { s -> s.copy(sttSettings = transform(s.sttSettings)) } }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CaptionContentRow(stt, update)
        CaptionArrivalRows(stt, update)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            stringResource(Res.string.stt_display_styling),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CaptionTextStyle(stt, availableFonts, update)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.stt_position),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ScreenPositionPicker(
                    positions = screenPositions(),
                    selected = stt.position,
                    onSelect = { v -> update { it.copy(position = v) } },
                )
                OpacitySliderRow(stringResource(Res.string.stt_opacity), stt.backgroundOpacity) { v ->
                    update { it.copy(backgroundOpacity = v) }
                }
            }
        }
    }
}

/** What the captions show -- transcription, translation or both, and how -- and how many lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptionContentRow(stt: STTSettings, update: SttUpdate) {
    // A FlowRow, so each control wraps onto a new line as a whole when the column is narrow.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DropdownSelector(
            label = stringResource(Res.string.stt_display_mode),
            value = stt.displayMode,
            options = listOf(
                "transcribe" to stringResource(Res.string.stt_mode_transcribe),
                "translate" to stringResource(Res.string.stt_mode_translate),
                "both" to stringResource(Res.string.stt_mode_both),
            ),
            onValueChange = { v -> update { it.copy(displayMode = v) } },
            modifier = Modifier.width(DROPDOWN_WIDTH),
        )
        AnimatedVisibility(visible = stt.displayMode == "both") {
            DropdownSelector(
                label = stringResource(Res.string.stt_layout),
                value = stt.layout,
                options = listOf(
                    "stacked" to stringResource(Res.string.stt_layout_stacked),
                    "stacked_inverse" to stringResource(Res.string.stt_layout_stacked_inverse),
                    "side_by_side" to stringResource(Res.string.stt_layout_side_by_side),
                    "side_by_side_inverse" to stringResource(Res.string.stt_layout_side_by_side_inverse),
                ),
                onValueChange = { v -> update { it.copy(layout = v) } },
                modifier = Modifier.width(DROPDOWN_WIDTH),
            )
        }
        NumberSettingsTextField(
            label = stringResource(Res.string.stt_max_segments),
            initialText = stt.maxSegments,
            range = MAX_SEGMENTS_RANGE,
            onValueChange = { v -> update { it.copy(maxSegments = v) } },
        )
        NumberSettingsTextField(
            label = stringResource(Res.string.stt_max_lines),
            initialText = stt.maxLines,
            range = MAX_LINES_RANGE,
            onValueChange = { v -> update { it.copy(maxLines = v) } },
        )
        NumberSettingsTextField(
            label = stringResource(Res.string.stt_line_spacing),
            initialText = stt.lineSpacing,
            range = LINE_SPACING_RANGE,
            onValueChange = { v -> update { it.copy(lineSpacing = v) } },
        )
    }
}

/** How the words arrive: highlighted, shown while still being spoken, and typed out or all at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptionArrivalRows(stt: STTSettings, update: SttUpdate) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CaptionCheckbox(stringResource(Res.string.stt_word_highlighting), stt.showWordHighlighting) { v ->
            update { it.copy(showWordHighlighting = v) }
        }
        CaptionCheckbox(stringResource(Res.string.stt_in_progress_text), stt.showInProgress) { v ->
            update { it.copy(showInProgress = v) }
        }
        CaptionCheckbox(stringResource(Res.string.stt_translation_in_progress), stt.showTranslationInProgress) { v ->
            update { it.copy(showTranslationInProgress = v) }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CaptionCheckbox(stringResource(Res.string.stt_drip_feed), stt.dripFeedEnabled) { v ->
            update { it.copy(dripFeedEnabled = v) }
        }
        NumberSettingsTextField(
            label = stringResource(Res.string.stt_drip_feed_speed),
            initialText = stt.dripFeedSpeed,
            range = DRIP_SPEED_RANGE,
            onValueChange = { v -> update { it.copy(dripFeedSpeed = v) } },
            modifier = Modifier.width(DRIP_SPEED_WIDTH),
        )
    }
}

/** The transcription's text look, then the translation's colour and the background's. */
@Composable
private fun CaptionTextStyle(stt: STTSettings, availableFonts: List<String>, update: SttUpdate) {
    DisplayTextStyleControls(
        style = DisplayTextStyle(
            textColor = stt.textColor, bold = stt.bold, italic = stt.italic, underline = stt.underline,
            shadow = stt.shadow, shadowColor = stt.shadowColor, shadowSize = stt.shadowSize,
            shadowOpacity = stt.shadowOpacity, backdrop = stt.backdrop, outline = stt.outline,
            fontType = stt.fontType, fontSize = stt.fontSize,
        ),
        labels = DisplayTextStyleLabels(
            color = stringResource(Res.string.stt_text_color),
            font = stringResource(Res.string.stt_font),
            size = stringResource(Res.string.stt_size),
        ),
        availableFonts = availableFonts,
        onChange = { t ->
            update {
                it.copy(
                    textColor = t.textColor, bold = t.bold, italic = t.italic, underline = t.underline,
                    shadow = t.shadow, shadowColor = t.shadowColor, shadowSize = t.shadowSize,
                    shadowOpacity = t.shadowOpacity, backdrop = t.backdrop, outline = t.outline,
                    fontType = t.fontType, fontSize = t.fontSize,
                )
            }
        },
    )
    ColorPickerField(
        label = stringResource(Res.string.stt_translation_color),
        color = stt.translationTextColor,
        onColorChange = { v -> update { it.copy(translationTextColor = v) } },
        modifier = Modifier.fillMaxWidth(),
    )
    ColorPickerField(
        label = stringResource(Res.string.stt_background_color),
        color = stt.backgroundColor,
        onColorChange = { v -> update { it.copy(backgroundColor = v) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CaptionCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    LabeledCheckbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        label = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
