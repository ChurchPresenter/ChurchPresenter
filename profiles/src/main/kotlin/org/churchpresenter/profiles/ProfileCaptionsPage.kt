package org.churchpresenter.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_letter_spacing
import org.churchpresenter.strings.generated.resources.bible_word_spacing
import org.churchpresenter.strings.generated.resources.percent_suffix
import org.churchpresenter.strings.generated.resources.pixels_short
import org.churchpresenter.strings.generated.resources.profile_box_item_transcript
import org.churchpresenter.strings.generated.resources.profile_caption_all_caps
import org.churchpresenter.strings.generated.resources.profile_caption_band_edge
import org.churchpresenter.strings.generated.resources.profile_caption_band_edge_sub
import org.churchpresenter.strings.generated.resources.profile_caption_translation
import org.churchpresenter.strings.generated.resources.profile_caption_both
import org.churchpresenter.strings.generated.resources.profile_caption_first
import org.churchpresenter.strings.generated.resources.profile_caption_highlight
import org.churchpresenter.strings.generated.resources.profile_caption_in_progress
import org.churchpresenter.strings.generated.resources.profile_caption_layout_interleaved
import org.churchpresenter.strings.generated.resources.profile_caption_layout_interleaved_sub
import org.churchpresenter.strings.generated.resources.profile_caption_separate_boxes
import org.churchpresenter.strings.generated.resources.profile_caption_shape
import org.churchpresenter.strings.generated.resources.profile_caption_shape_band
import org.churchpresenter.strings.generated.resources.profile_caption_shape_card
import org.churchpresenter.strings.generated.resources.profile_caption_translation_bold
import org.churchpresenter.strings.generated.resources.profile_caption_translation_caps
import org.churchpresenter.strings.generated.resources.profile_caption_translation_italic
import org.churchpresenter.strings.generated.resources.profile_caption_translation_size
import org.churchpresenter.strings.generated.resources.profile_caption_translation_size_sub
import org.churchpresenter.strings.generated.resources.profile_layout
import org.churchpresenter.strings.generated.resources.profile_layout_side_by_side
import org.churchpresenter.strings.generated.resources.profile_layout_stacked
import org.churchpresenter.strings.generated.resources.profile_caption_lines
import org.churchpresenter.strings.generated.resources.profile_caption_mode
import org.churchpresenter.strings.generated.resources.profile_caption_segments
import org.churchpresenter.strings.generated.resources.profile_caption_transcription
import org.churchpresenter.strings.generated.resources.profile_caption_translation_color
import org.churchpresenter.strings.generated.resources.profile_caption_translation_in_progress
import org.churchpresenter.strings.generated.resources.profile_caption_type_out
import org.churchpresenter.strings.generated.resources.profile_caption_type_out_sub
import org.churchpresenter.strings.generated.resources.profile_caption_show_speed
import org.churchpresenter.strings.generated.resources.profile_caption_speed_fixed
import org.churchpresenter.strings.generated.resources.profile_caption_speed_speaker
import org.churchpresenter.strings.generated.resources.profile_caption_speed_speaker_sub
import org.churchpresenter.strings.generated.resources.profile_group_position
import org.churchpresenter.strings.generated.resources.profile_group_show
import org.churchpresenter.strings.generated.resources.profile_group_text
import org.churchpresenter.strings.generated.resources.profile_line_spacing
import org.churchpresenter.strings.generated.resources.profile_margins
import org.churchpresenter.strings.generated.resources.profile_ms
import org.churchpresenter.strings.generated.resources.profile_text_size_unit
import org.churchpresenter.stt.presenter.LAYOUT_INTERLEAVED
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CAPTION_BOX_BAND
import org.churchpresenter.settings.CAPTION_BOX_CARD
import org.churchpresenter.settings.CAPTION_TRANSCRIPT_BOX
import org.churchpresenter.settings.CAPTION_TRANSLATION_BOX
import org.churchpresenter.settings.CAPTION_STYLE_POP_ON
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
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
        key = "lines",
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
    CaptionReadingGroup(stt.reading, matchSpeaker = stt.matchSpeakerPace) { t ->
        update { it.copy(reading = t(it.reading)) }
    }
    SettingsGroup(
        stringResource(Res.string.profile_group_text),
        key = "text",
        paths = displayTextPaths(STT) + CAPTION_TEXT_PATHS,
        summary = { stt.displayStyle().let { textSummary(it.fontType, it.fontSize) } },
    ) {
        DisplayTextRows(stt.displayStyle(), { t -> update { it.withDisplayStyle(t) } }, STT, extraBasic = {
            CaptionTextRows(stt, update)
        })
    }
    DisplayBoxGroup(
        stt.backgroundColor,
        { v -> update { it.copy(backgroundColor = v) } },
        stt.backgroundOpacity,
        { v -> update { it.copy(backgroundOpacity = v) } },
        STT,
        leading = { CaptionShapeRows(stt, update) },
        leadingPaths = listOf("$STT.boxShape", "$STT.bandTouchesEdge"),
    )
    SettingsGroup(
        stringResource(Res.string.profile_group_position),
        key = "position",
        paths = listOf("$STT.position", "$STT.horizontalAlignment") + CAPTION_MARGIN_PATHS,
    ) {
        ScreenPlacementRow(stt.position, { v -> update { it.copy(position = v) } }, STT)
        DisplayAlignmentRow(stt.horizontalAlignment, { v -> update { it.copy(horizontalAlignment = v) } }, STT)
        SettingsRow(stringResource(Res.string.profile_margins), paths = CAPTION_MARGIN_PATHS) {
            MarginFields(
                Margins(stt.marginTop, stt.marginBottom, stt.marginLeft, stt.marginRight),
                { m ->
                    update {
                        it.copy(marginTop = m.top, marginBottom = m.bottom, marginLeft = m.left, marginRight = m.right)
                    }
                },
            )
        }
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
    // Matching the speaker, the typing follows them and the fixed speed only stands in until it can.
    val typedAtFixedSpeed = stt.dripFeedEnabled && !stt.matchSpeakerPace
    SettingsGroup(stringResource(Res.string.profile_group_show), key = "show") {
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
        if (stt.displayMode == MODE_BOTH) BothLanguagesRows(stt, update)
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
            sub = if (typedAtFixedSpeed) stringResource(Res.string.profile_caption_type_out_sub) else null,
            paths = listOf("$STT.dripFeedEnabled", "$STT.dripFeedSpeed"),
            extra = {
                if (typedAtFixedSpeed) {
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
        ShowSpeedRow(stt, update)
    }
}

/**
 * SHOW SPEED: new words at a fixed speed, or at the pace the speaker is talking. Pop-on brings whole
 * segments and the ticker has its own speed, so neither offers it.
 */
@Composable
private fun ShowSpeedRow(stt: STTSettings, update: ((STTSettings) -> STTSettings) -> Unit) {
    if (stt.reading.style == CAPTION_STYLE_POP_ON || stt.reading.style == CAPTION_STYLE_TICKER) return
    SettingsRow(
        stringResource(Res.string.profile_caption_show_speed),
        sub = if (stt.matchSpeakerPace) stringResource(Res.string.profile_caption_speed_speaker_sub) else null,
        paths = listOf("$STT.matchSpeakerPace"),
    ) {
        RowSegmented(
            options = listOf(
                RowOption(false, stringResource(Res.string.profile_caption_speed_fixed)),
                RowOption(true, stringResource(Res.string.profile_caption_speed_speaker)),
            ),
            selected = stt.matchSpeakerPace,
            onSelect = { v -> update { it.copy(matchSpeakerPace = v) } },
        )
    }
}

private val CAPTION_MARGIN_PATHS = listOf("marginTop", "marginBottom", "marginLeft", "marginRight").map { "$STT.$it" }

private val CAPTION_TEXT_PATHS = listOf(
    "translationTextColor", "translationFontSize", "translationBold", "translationItalic",
    "transcriptAllCaps", "translationAllCaps", "letterSpacing", "wordSpacing",
).map { "$STT.$it" }

private val TRANSLATION_SIZE_RANGE = 0..200
private val LETTER_SPACING_RANGE = -10..30
private val WORD_SPACING_RANGE = 0..40

/** The box's shape: today's rounded card, or a full-width band that may touch the screen's edge. */
@Composable
private fun CaptionShapeRows(stt: STTSettings, update: ((STTSettings) -> STTSettings) -> Unit) {
    SettingsRow(stringResource(Res.string.profile_caption_shape), paths = listOf("$STT.boxShape")) {
        RowSegmented(
            options = listOf(
                RowOption(CAPTION_BOX_CARD, stringResource(Res.string.profile_caption_shape_card)),
                RowOption(CAPTION_BOX_BAND, stringResource(Res.string.profile_caption_shape_band)),
            ),
            selected = stt.boxShape,
            onSelect = { v -> update { it.copy(boxShape = v) } },
        )
    }
    if (stt.boxShape == CAPTION_BOX_BAND) {
        SettingsSwitchRow(
            stringResource(Res.string.profile_caption_band_edge),
            stt.bandTouchesEdge,
            { v -> update { it.copy(bandTouchesEdge = v) } },
            sub = stringResource(Res.string.profile_caption_band_edge_sub),
            paths = listOf("$STT.bandTouchesEdge"),
        )
    }
}

/** The captions' own text rows: capitals, spacing, and the translation's colour, size and style. */
@Composable
private fun CaptionTextRows(stt: STTSettings, update: ((STTSettings) -> STTSettings) -> Unit) {
    val px = stringResource(Res.string.pixels_short)
    SettingsSwitchRow(
        stringResource(Res.string.profile_caption_all_caps),
        stt.transcriptAllCaps,
        { v -> update { it.copy(transcriptAllCaps = v) } },
        paths = listOf("$STT.transcriptAllCaps"),
    )
    SettingsRow(
        stringResource(Res.string.bible_letter_spacing),
        advanced = true,
        paths = listOf("$STT.letterSpacing"),
    ) {
        RowStepper(stt.letterSpacing, { v -> update { it.copy(letterSpacing = v) } }, LETTER_SPACING_RANGE, unit = px)
    }
    SettingsRow(stringResource(Res.string.bible_word_spacing), advanced = true, paths = listOf("$STT.wordSpacing")) {
        RowStepper(stt.wordSpacing, { v -> update { it.copy(wordSpacing = v) } }, WORD_SPACING_RANGE, unit = px)
    }
    if (stt.displayMode == MODE_TRANSCRIBE) return
    SettingsRow(
        stringResource(Res.string.profile_caption_translation_color),
        paths = listOf("$STT.translationTextColor"),
    ) {
        RowColor(stt.translationTextColor, { v -> update { it.copy(translationTextColor = v) } })
    }
    SettingsRow(
        stringResource(Res.string.profile_caption_translation_size),
        sub = stringResource(Res.string.profile_caption_translation_size_sub),
        paths = listOf("$STT.translationFontSize"),
    ) {
        RowStepper(
            stt.translationFontSize,
            { v -> update { it.copy(translationFontSize = v) } },
            TRANSLATION_SIZE_RANGE,
            unit = stringResource(Res.string.profile_text_size_unit),
        )
    }
    SettingsSwitchRow(
        stringResource(Res.string.profile_caption_translation_caps),
        stt.translationAllCaps,
        { v -> update { it.copy(translationAllCaps = v) } },
        paths = listOf("$STT.translationAllCaps"),
    )
    SettingsSwitchRow(
        stringResource(Res.string.profile_caption_translation_bold),
        stt.translationBold,
        { v -> update { it.copy(translationBold = v) } },
        paths = listOf("$STT.translationBold"),
    )
    SettingsSwitchRow(
        stringResource(Res.string.profile_caption_translation_italic),
        stt.translationItalic,
        { v -> update { it.copy(translationItalic = v) } },
        paths = listOf("$STT.translationItalic"),
    )
}

/**
 * With both languages shown: how they are arranged -- stacked, side by side, or each line with its
 * translation -- which comes first, and whether they share a box. Stored as one layout value, the
 * arrangement with `_inverse` for the translation first, as it always was.
 */
@Composable
private fun BothLanguagesRows(stt: STTSettings, update: ((STTSettings) -> STTSettings) -> Unit) {
    val inverse = stt.layout.endsWith(INVERSE)
    val arrangement = stt.layout.removeSuffix(INVERSE)
    val write = { arranged: String, translationFirst: Boolean ->
        update { it.copy(layout = arranged + if (translationFirst) INVERSE else "") }
    }
    SettingsRow(
        stringResource(Res.string.profile_layout),
        sub = stringResource(Res.string.profile_caption_layout_interleaved_sub)
            .takeIf { arrangement == LAYOUT_INTERLEAVED },
        paths = listOf("$STT.layout"),
    ) {
        RowSegmented(
            options = listOf(
                RowOption(LAYOUT_STACKED, stringResource(Res.string.profile_layout_stacked)),
                RowOption(LAYOUT_SIDE_BY_SIDE, stringResource(Res.string.profile_layout_side_by_side)),
                RowOption(LAYOUT_INTERLEAVED, stringResource(Res.string.profile_caption_layout_interleaved)),
            ),
            selected = arrangement,
            onSelect = { v -> write(v, inverse) },
        )
    }
    SettingsRow(stringResource(Res.string.profile_caption_first), paths = listOf("$STT.layout")) {
        RowSegmented(
            options = listOf(
                RowOption(false, stringResource(Res.string.profile_caption_transcription)),
                RowOption(true, stringResource(Res.string.profile_caption_translation)),
            ),
            selected = inverse,
            onSelect = { v -> write(arrangement, v) },
        )
    }
    if (arrangement != LAYOUT_INTERLEAVED) {
        SettingsSwitchRow(
            stringResource(Res.string.profile_caption_separate_boxes),
            stt.separateLanguageBoxes,
            { v -> update { it.copy(separateLanguageBoxes = v) } },
            paths = listOf("$STT.separateLanguageBoxes"),
        )
    }
}

private const val INVERSE = "_inverse"
private const val LAYOUT_STACKED = "stacked"
private const val LAYOUT_SIDE_BY_SIDE = "side_by_side"
