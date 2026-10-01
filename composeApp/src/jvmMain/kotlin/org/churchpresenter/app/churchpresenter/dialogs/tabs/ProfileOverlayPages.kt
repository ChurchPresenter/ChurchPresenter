package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.media_subtitle_settings_hint
import churchpresenter.composeapp.generated.resources.percent_suffix
import churchpresenter.composeapp.generated.resources.profile_group_qr
import churchpresenter.composeapp.generated.resources.profile_box_item_qr_message
import churchpresenter.composeapp.generated.resources.profile_box_item_question
import churchpresenter.composeapp.generated.resources.profile_box_item_subtitle
import churchpresenter.composeapp.generated.resources.profile_box_opacity
import churchpresenter.composeapp.generated.resources.profile_caption_lines
import churchpresenter.composeapp.generated.resources.profile_group_position
import churchpresenter.composeapp.generated.resources.profile_group_text
import churchpresenter.composeapp.generated.resources.profile_line_spacing
import churchpresenter.composeapp.generated.resources.profile_qr_background
import churchpresenter.composeapp.generated.resources.profile_qr_foreground
import org.churchpresenter.app.churchpresenter.dialogs.DisplayTextStyle
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.QA_QR_CODE_BOX
import org.churchpresenter.settings.QA_QR_MESSAGE_BOX
import org.churchpresenter.settings.QA_QUESTION_BOX
import org.churchpresenter.settings.SUBTITLE_BOX
import org.churchpresenter.settings.TextBox
import org.jetbrains.compose.resources.stringResource

private const val MEDIA = "mediaSettings"
private const val QA = "qaSettings"
private val SUBTITLE_LINES_RANGE = 1..10
private val LINE_SPACING_RANGE = 80..300
private const val LINE_SPACING_STEP = 10
private val OPACITY_RANGE = 0..100
private const val OPACITY_STEP = 5

/** Every field the shared text rows write, under [prefix]. */
internal fun displayTextPaths(prefix: String): List<String> = listOf(
    "textColor", "fontType", "fontSize", "bold", "italic", "underline", "shadow", "shadowColor", "shadowSize",
    "shadowOpacity", "backdrop", "outline",
).map { "$prefix.$it" }

private fun MediaSettings.displayStyle() = DisplayTextStyle(
    textColor = textColor, bold = bold, italic = italic, underline = underline,
    shadow = shadow, shadowColor = shadowColor, shadowSize = shadowSize, shadowOpacity = shadowOpacity,
    backdrop = backdrop, outline = outline, fontType = fontType, fontSize = fontSize,
)

private fun MediaSettings.withDisplayStyle(t: DisplayTextStyle) = copy(
    textColor = t.textColor, bold = t.bold, italic = t.italic, underline = t.underline,
    shadow = t.shadow, shadowColor = t.shadowColor, shadowSize = t.shadowSize, shadowOpacity = t.shadowOpacity,
    backdrop = t.backdrop, outline = t.outline, fontType = t.fontType, fontSize = t.fontSize,
)

private fun QASettings.displayStyle() = DisplayTextStyle(
    textColor = textColor, bold = bold, italic = italic, underline = underline,
    shadow = shadow, shadowColor = shadowColor, shadowSize = shadowSize, shadowOpacity = shadowOpacity,
    backdrop = backdrop, outline = outline, fontType = fontType, fontSize = fontSize,
)

private fun QASettings.withDisplayStyle(t: DisplayTextStyle) = copy(
    textColor = t.textColor, bold = t.bold, italic = t.italic, underline = t.underline,
    shadow = t.shadow, shadowColor = t.shadowColor, shadowSize = t.shadowSize, shadowOpacity = t.shadowOpacity,
    backdrop = t.backdrop, outline = t.outline, fontType = t.fontType, fontSize = t.fontSize,
)

/**
 * Subtitles: the text, the box behind it, and where the lines sit and how many there are. Only
 * subtitle files the app draws itself take this look, which the note at the top says.
 */
@Composable
internal fun ProfileSubtitlesPage(draft: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    val media = draft.mediaSettings
    val update: ((MediaSettings) -> MediaSettings) -> Unit = { t ->
        onSettingsChange { s -> s.copy(mediaSettings = t(s.mediaSettings)) }
    }
    PageNote(stringResource(Res.string.media_subtitle_settings_hint))
    SettingsGroup(stringResource(Res.string.profile_group_text), paths = displayTextPaths(MEDIA)) {
        DisplayTextRows(media.displayStyle(), { t -> update { it.withDisplayStyle(t) } }, MEDIA)
    }
    DisplayBoxGroup(
        media.backgroundColor,
        { v -> update { it.copy(backgroundColor = v) } },
        media.backgroundOpacity,
        { v -> update { it.copy(backgroundOpacity = v) } },
        MEDIA,
    )
    SettingsGroup(
        stringResource(Res.string.profile_group_position),
        paths = listOf("$MEDIA.position", "$MEDIA.maxLines", "$MEDIA.lineSpacing"),
    ) {
        ScreenPlacementRow(media.position, { v -> update { it.copy(position = v) } }, MEDIA)
        SettingsRow(stringResource(Res.string.profile_caption_lines), paths = listOf("$MEDIA.maxLines")) {
            RowStepper(media.maxLines, { v -> update { it.copy(maxLines = v) } }, SUBTITLE_LINES_RANGE)
        }
        SettingsRow(
            stringResource(Res.string.profile_line_spacing),
            advanced = true,
            paths = listOf("$MEDIA.lineSpacing"),
        ) {
            RowStepper(
                media.lineSpacing,
                { v -> update { it.copy(lineSpacing = v) } },
                LINE_SPACING_RANGE,
                step = LINE_SPACING_STEP,
                unit = stringResource(Res.string.percent_suffix),
            )
        }
    }
    ItemBoxGroup(
        items = listOf(
            BoxItem(
                SUBTITLE_BOX,
                stringResource(Res.string.profile_box_item_subtitle),
                TextBox(xPercent = 10f, yPercent = 78f, widthPercent = 80f, heightPercent = 18f),
            ),
        ),
        boxes = media.textBoxes,
        options = media.textBoxOptions,
        onBoxes = { boxes -> update { it.copy(textBoxes = boxes) } },
        onOptions = { options -> update { it.copy(textBoxOptions = options) } },
        paths = listOf("$MEDIA.textBoxes", "$MEDIA.textBoxOptions"),
    )
}

/**
 * Q&A: how a question and its QR code look on this output -- the text, the box behind it, where it
 * sits, and the code's colours. Links, public access and the rate limit are install-wide, and stay
 * in the Q&A window.
 */
@Composable
internal fun ProfileQaPage(draft: AppSettings, onSettingsChange: ((AppSettings) -> AppSettings) -> Unit) {
    val qa = draft.qaSettings
    val update: ((QASettings) -> QASettings) -> Unit = { t ->
        onSettingsChange { s -> s.copy(qaSettings = t(s.qaSettings)) }
    }
    SettingsGroup(stringResource(Res.string.profile_group_text), paths = displayTextPaths(QA)) {
        DisplayTextRows(qa.displayStyle(), { t -> update { it.withDisplayStyle(t) } }, QA)
    }
    DisplayBoxGroup(
        qa.backgroundColor,
        { v -> update { it.copy(backgroundColor = v) } },
        qa.backgroundOpacity,
        { v -> update { it.copy(backgroundOpacity = v) } },
        QA,
    )
    SettingsGroup(
        stringResource(Res.string.profile_group_position),
        paths = listOf("$QA.position", "$QA.horizontalAlignment"),
    ) {
        ScreenPlacementRow(qa.position, { v -> update { it.copy(position = v) } }, QA)
        DisplayAlignmentRow(qa.horizontalAlignment, { v -> update { it.copy(horizontalAlignment = v) } }, QA)
    }
    SettingsGroup(
        stringResource(Res.string.profile_group_qr),
        paths = listOf("$QA.qrForegroundColor", "$QA.qrBackgroundColor", "$QA.qrBackgroundOpacity"),
    ) {
        SettingsRow(stringResource(Res.string.profile_qr_foreground), paths = listOf("$QA.qrForegroundColor")) {
            RowColor(qa.qrForegroundColor, { v -> update { it.copy(qrForegroundColor = v) } })
        }
        SettingsRow(stringResource(Res.string.profile_qr_background), paths = listOf("$QA.qrBackgroundColor")) {
            RowColor(qa.qrBackgroundColor, { v -> update { it.copy(qrBackgroundColor = v) } })
        }
        SettingsRow(
            stringResource(Res.string.profile_box_opacity),
            advanced = true,
            paths = listOf("$QA.qrBackgroundOpacity"),
        ) {
            RowStepper(
                qa.qrBackgroundOpacity,
                { v -> update { it.copy(qrBackgroundOpacity = v) } },
                OPACITY_RANGE,
                step = OPACITY_STEP,
                unit = stringResource(Res.string.percent_suffix),
            )
        }
    }
    ItemBoxGroup(
        items = listOf(
            BoxItem(
                QA_QUESTION_BOX,
                stringResource(Res.string.profile_box_item_question),
                TextBox(xPercent = 10f, yPercent = 20f, widthPercent = 60f, heightPercent = 60f),
            ),
            BoxItem(
                QA_QR_CODE_BOX,
                stringResource(Res.string.profile_group_qr),
                TextBox(xPercent = 74f, yPercent = 20f, widthPercent = 22f, heightPercent = 40f),
            ),
            BoxItem(
                QA_QR_MESSAGE_BOX,
                stringResource(Res.string.profile_box_item_qr_message),
                TextBox(xPercent = 72f, yPercent = 62f, widthPercent = 26f, heightPercent = 10f),
            ),
        ),
        boxes = qa.textBoxes,
        options = qa.textBoxOptions,
        onBoxes = { boxes -> update { it.copy(textBoxes = boxes) } },
        onOptions = { options -> update { it.copy(textBoxOptions = options) } },
        paths = listOf("$QA.textBoxes", "$QA.textBoxOptions"),
    )
}
