package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.percent_suffix
import churchpresenter.composeapp.generated.resources.profile_box_item_transcript
import churchpresenter.composeapp.generated.resources.profile_caption_translation
import churchpresenter.composeapp.generated.resources.profile_caption_both
import churchpresenter.composeapp.generated.resources.profile_caption_highlight
import churchpresenter.composeapp.generated.resources.profile_caption_in_progress
import churchpresenter.composeapp.generated.resources.profile_layout
import churchpresenter.composeapp.generated.resources.profile_layout_side_by_side
import churchpresenter.composeapp.generated.resources.profile_caption_layout_side_inverse
import churchpresenter.composeapp.generated.resources.profile_layout_stacked
import churchpresenter.composeapp.generated.resources.profile_caption_layout_stacked_inverse
import churchpresenter.composeapp.generated.resources.profile_caption_lines
import churchpresenter.composeapp.generated.resources.profile_caption_mode
import churchpresenter.composeapp.generated.resources.profile_caption_segments
import churchpresenter.composeapp.generated.resources.profile_caption_transcription
import churchpresenter.composeapp.generated.resources.profile_caption_translation_color
import churchpresenter.composeapp.generated.resources.profile_caption_translation_in_progress
import churchpresenter.composeapp.generated.resources.profile_caption_type_out
import churchpresenter.composeapp.generated.resources.profile_caption_type_out_sub
import churchpresenter.composeapp.generated.resources.profile_group_position
import churchpresenter.composeapp.generated.resources.profile_group_show
import churchpresenter.composeapp.generated.resources.profile_group_text
import churchpresenter.composeapp.generated.resources.profile_line_spacing
import churchpresenter.composeapp.generated.resources.profile_ms
import org.churchpresenter.app.churchpresenter.dialogs.DisplayTextStyle
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CAPTION_TRANSCRIPT_BOX
import org.churchpresenter.settings.CAPTION_TRANSLATION_BOX
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.TextBox
import org.jetbrains.compose.resources.stringResource

private const val STT = "sttSettings"
private const val MODE_TRANSCRIBE = "transcribe"
private const val MODE_TRANSLATE = "translate"
private const val MODE_BOTH = "both"
private val SEGMENTS_RANGE = 0..100
private val LINES_RANGE = 0..50
private val LINE_SPACING_RANGE = 80..300
private const val LINE_SPACING_STEP = 10
private val TYPE_SPEED_RANGE = 1..1000
private const val TYPE_SPEED_STEP = 10

/** [STTSettings]' text look, as the shared overlay rows edit it. */
internal fun STTSettings.displayStyle() = DisplayTextStyle(
    textColor = textColor, bold = bold, italic = italic, underline = underline,
    shadow = shadow, shadowColor = shadowColor, shadowSize = shadowSize, shadowOpacity = shadowOpacity,
    backdrop = backdrop, outline = outline, fontType = fontType, fontSize = fontSize,
)

internal fun STTSettings.withDisplayStyle(t: DisplayTextStyle) = copy(
    textColor = t.textColor, bold = t.bold, italic = t.italic, underline = t.underline,
    shadow = t.shadow, shadowColor = t.shadowColor, shadowSize = t.shadowSize, shadowOpacity = t.shadowOpacity,
    backdrop = t.backdrop, outline = t.outline, fontType = t.fontType, fontSize = t.fontSize,
)

/**
 * Live captions: what they show and how they arrive, how many lines are kept, the text, the box
 * behind it and where it sits. The caption server is install-wide and stays in the captions window.
 */
@Composable
internal fun ProfileCaptionsPage(draft: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    val stt = draft.sttSettings
    val update: ((STTSettings) -> STTSettings) -> Unit = { t ->
        onSettingsChange { s -> s.copy(sttSettings = t(s.sttSettings)) }
    }
    CaptionShowGroup(stt, update)
    SettingsGroup(
        stringResource(Res.string.profile_caption_lines),
        paths = listOf("$STT.maxSegments", "$STT.maxLines", "$STT.lineSpacing"),
    ) {
        SettingsRow(stringResource(Res.string.profile_caption_lines), paths = listOf("$STT.maxLines")) {
            RowStepper(stt.maxLines, { v -> update { it.copy(maxLines = v) } }, LINES_RANGE)
        }
        SettingsRow(
            stringResource(Res.string.profile_caption_segments),
            advanced = true,
            paths = listOf("$STT.maxSegments"),
        ) {
            RowStepper(stt.maxSegments, { v -> update { it.copy(maxSegments = v) } }, SEGMENTS_RANGE)
        }
        SettingsRow(
            stringResource(Res.string.profile_line_spacing),
            advanced = true,
            paths = listOf("$STT.lineSpacing"),
        ) {
            RowStepper(
                stt.lineSpacing,
                { v -> update { it.copy(lineSpacing = v) } },
                LINE_SPACING_RANGE,
                step = LINE_SPACING_STEP,
                unit = stringResource(Res.string.percent_suffix),
            )
        }
    }
    SettingsGroup(
        stringResource(Res.string.profile_group_text),
        paths = displayTextPaths(STT) + "$STT.translationTextColor",
    ) {
        DisplayTextRows(stt.displayStyle(), { t -> update { it.withDisplayStyle(t) } }, STT, extraBasic = {
            if (stt.displayMode != MODE_TRANSCRIBE) {
                SettingsRow(
                    stringResource(Res.string.profile_caption_translation_color),
                    paths = listOf("$STT.translationTextColor"),
                ) {
                    RowColor(stt.translationTextColor, { v -> update { it.copy(translationTextColor = v) } })
                }
            }
        })
    }
    DisplayBoxGroup(
        stt.backgroundColor,
        { v -> update { it.copy(backgroundColor = v) } },
        stt.backgroundOpacity,
        { v -> update { it.copy(backgroundOpacity = v) } },
        STT,
    )
    SettingsGroup(
        stringResource(Res.string.profile_group_position),
        paths = listOf("$STT.position", "$STT.horizontalAlignment"),
    ) {
        ScreenPlacementRow(stt.position, { v -> update { it.copy(position = v) } }, STT)
        DisplayAlignmentRow(stt.horizontalAlignment, { v -> update { it.copy(horizontalAlignment = v) } }, STT)
    }
    ItemBoxGroup(
        items = listOf(
            BoxItem(
                CAPTION_TRANSCRIPT_BOX,
                stringResource(Res.string.profile_box_item_transcript),
                TextBox(xPercent = 5f, yPercent = 55f, widthPercent = 90f, heightPercent = 20f),
            ),
            BoxItem(
                CAPTION_TRANSLATION_BOX,
                stringResource(Res.string.profile_caption_translation),
                TextBox(xPercent = 5f, yPercent = 77f, widthPercent = 90f, heightPercent = 20f),
            ),
        ),
        boxes = stt.textBoxes,
        options = stt.textBoxOptions,
        onBoxes = { boxes -> update { it.copy(textBoxes = boxes) } },
        onOptions = { options -> update { it.copy(textBoxOptions = options) } },
        paths = listOf("$STT.textBoxes", "$STT.textBoxOptions"),
    )
}

/** SHOW: transcription, translation or both, and how the words arrive. */
@Composable
private fun CaptionShowGroup(stt: STTSettings, update: ((STTSettings) -> STTSettings) -> Unit) {
    SettingsGroup(stringResource(Res.string.profile_group_show)) {
        SettingsRow(stringResource(Res.string.profile_caption_mode), paths = listOf("$STT.displayMode")) {
            RowSegmented(
                options = listOf(
                    RowOption(MODE_TRANSCRIBE, stringResource(Res.string.profile_caption_transcription)),
                    RowOption(MODE_TRANSLATE, stringResource(Res.string.profile_caption_translation)),
                    RowOption(MODE_BOTH, stringResource(Res.string.profile_caption_both)),
                ),
                selected = stt.displayMode,
                onSelect = { v -> update { it.copy(displayMode = v) } },
            )
        }
        if (stt.displayMode == MODE_BOTH) {
            SettingsRow(stringResource(Res.string.profile_layout), paths = listOf("$STT.layout")) {
                RowSegmented(
                    options = listOf(
                        RowOption("stacked", stringResource(Res.string.profile_layout_stacked)),
                        RowOption("stacked_inverse", stringResource(Res.string.profile_caption_layout_stacked_inverse)),
                        RowOption("side_by_side", stringResource(Res.string.profile_layout_side_by_side)),
                        RowOption(
                            "side_by_side_inverse",
                            stringResource(Res.string.profile_caption_layout_side_inverse),
                        ),
                    ),
                    selected = stt.layout,
                    onSelect = { v -> update { it.copy(layout = v) } },
                )
            }
        }
        SettingsSwitchRow(
            stringResource(Res.string.profile_caption_highlight),
            stt.showWordHighlighting,
            { v -> update { it.copy(showWordHighlighting = v) } },
            paths = listOf("$STT.showWordHighlighting"),
        )
        SettingsSwitchRow(
            stringResource(Res.string.profile_caption_in_progress),
            stt.showInProgress,
            { v -> update { it.copy(showInProgress = v) } },
            paths = listOf("$STT.showInProgress"),
        )
        SettingsSwitchRow(
            stringResource(Res.string.profile_caption_translation_in_progress),
            stt.showTranslationInProgress,
            { v -> update { it.copy(showTranslationInProgress = v) } },
            advanced = true,
            paths = listOf("$STT.showTranslationInProgress"),
        )
        SettingsSwitchRow(
            stringResource(Res.string.profile_caption_type_out),
            stt.dripFeedEnabled,
            { v -> update { it.copy(dripFeedEnabled = v) } },
            sub = if (stt.dripFeedEnabled) stringResource(Res.string.profile_caption_type_out_sub) else null,
            advanced = true,
            paths = listOf("$STT.dripFeedEnabled", "$STT.dripFeedSpeed"),
            extra = {
                if (stt.dripFeedEnabled) {
                    RowStepper(
                        stt.dripFeedSpeed,
                        { v -> update { it.copy(dripFeedSpeed = v) } },
                        TYPE_SPEED_RANGE,
                        step = TYPE_SPEED_STEP,
                        unit = stringResource(Res.string.profile_ms),
                        fieldWidth = 76.dp,
                    )
                }
            },
        )
    }
}
