package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_editing
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.customize_style
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.show_song_number_before_title
import churchpresenter.composeapp.generated.resources.song_show_on_title_slide
import churchpresenter.composeapp.generated.resources.song_target_title_slide
import churchpresenter.composeapp.generated.resources.vertical_alignment
import org.churchpresenter.app.churchpresenter.composables.FontSettingsDropdown
import org.churchpresenter.settings.SongSettings
import org.jetbrains.compose.resources.stringResource

/**
 * The Title Slide chip of the Customize dialog's Song pane: what the global Song tab's own
 * title-slide view offers, for this output alone.
 *
 * The slide-wide settings first -- whether the number shares the title's row, and where the block
 * sits -- then one element at a time, chosen on the row below, with the full profile the global
 * tab edits: face, size, colour, the style buttons, alignment and the typography rows. The number
 * and the title are the same profiles the lyric slides draw with, as they are everywhere.
 *
 * No language switch: which of the song's titles this screen shows is its own song mode, set in
 * the screen grid this dialog opened from.
 */
/** Six segments across the control column: as wide as "Composer" needs and no wider. */
private val ELEMENT_BUTTON_WIDTH = 67.dp

/** The font picker, narrowed so it shares a row with the size and the colour beside it. */
private val FONT_FIELD_WIDTH = 136.dp

@Composable
internal fun SongTitleSlideCustomize(
    ss: SongSettings,
    lowerThird: Boolean,
    fonts: List<String>,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {
    val target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
    var element by remember { mutableStateOf(SongStyleElement.TITLE) }
    CustomizeGroup(stringResource(Res.string.song_target_title_slide)) {
        // In a captioned cell like its neighbours, so the checkbox sits level with their controls.
        CustomizeRow(stringResource(Res.string.show_song_number_before_title), labelInsideControl = true) {
            ToggleControl(
                label = stringResource(Res.string.show_song_number_before_title),
                checked = ss.titleSlideNumberBeforeTitle,
                onCheckedChange = { v -> update { it.copy(titleSlideNumberBeforeTitle = v) } },
            )
        }
        // The band keeps the block at its bottom, so the row would be a control that does nothing.
        if (!lowerThird) {
            CustomizeRow(stringResource(Res.string.vertical_alignment).removeSuffix(":")) {
                VerticalAlignControl(
                    selected = ss.titleSlideVerticalAlignment,
                    onSelect = { v -> update { it.copy(titleSlideVerticalAlignment = v) } },
                )
            }
        }
        CustomizeRow(stringResource(Res.string.bible_editing)) {
            ChoiceControl(
                options = TITLE_SLIDE_ELEMENTS.map { it.name to it.label() },
                selected = element.name,
                buttonWidth = ELEMENT_BUTTON_WIDTH,
                onSelect = { picked -> element = SongStyleElement.valueOf(picked) },
            )
        }
    }
    TitleSlideElementGroup(element, ss, target, fonts, update)
}

/** One element of the title slide, with the profile it is drawn from on [target]. */
@Composable
private fun TitleSlideElementGroup(
    element: SongStyleElement,
    ss: SongSettings,
    target: SongStyleTarget,
    fonts: List<String>,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {
    val style = ss.elementStyle(element, target)
    fun edit(transform: (SongElementStyle) -> SongElementStyle) {
        update { it.withElementStyle(element, target, transform(it.elementStyle(element, target))) }
    }
    CustomizeGroup(element.label()) {
        // Full width, so it takes a line of its own and size, colour and face share the next.
        Box(modifier = Modifier.fillMaxWidth()) {
            ToggleControl(
                label = stringResource(Res.string.song_show_on_title_slide),
                checked = ss.shownOnTitleSlide(element) == true,
                onCheckedChange = { v -> update { it.withShownOnTitleSlide(element, v) } },
            )
        }
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = style.fontSize,
                onValueChange = { v -> edit { it.copy(fontSize = v) } },
                range = FONT_SIZE_RANGE,
            )
        }
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(
                label = stringResource(Res.string.color),
                color = style.color,
                onColorChange = { v -> edit { it.copy(color = v) } },
            )
        }
        // Narrower than the shared `FontControl`, so size, colour and face make one row.
        CustomizeRow(stringResource(Res.string.font_type), labelInsideControl = true) {
            FontSettingsDropdown(
                label = stringResource(Res.string.font_type),
                value = style.fontType,
                fonts = fonts,
                onValueChange = { v -> edit { it.copy(fontType = v) } },
                modifier = Modifier.width(FONT_FIELD_WIDTH),
                fillWidth = true,
            )
        }
        CustomizeRow(stringResource(Res.string.customize_style)) {
            StyleControl(
                bold = style.bold,
                italic = style.italic,
                underline = style.underline,
                shadow = style.shadow,
                onBoldChange = { v -> edit { it.copy(bold = v) } },
                onItalicChange = { v -> edit { it.copy(italic = v) } },
                onUnderlineChange = { v -> edit { it.copy(underline = v) } },
                onShadowChange = { v -> edit { it.copy(shadow = v) } },
                strikethrough = style.strikethrough,
                onStrikethroughChange = { v -> edit { it.copy(strikethrough = v) } },
                backdrop = style.backdrop,
                onBackdropChange = { v -> edit { it.copy(backdrop = v) } },
            )
        }
        CustomizeRow(stringResource(Res.string.horizontal_alignment)) {
            HorizontalAlignControl(
                selected = style.horizontalAlignment,
                onSelect = { v -> edit { it.copy(horizontalAlignment = v) } },
            )
        }
    }
    TypographyGroup(
        letterSpacing = style.letterSpacing,
        wordSpacing = style.wordSpacing,
        transform = style.transform,
        onLetterSpacing = { v -> edit { it.copy(letterSpacing = v) } },
        onWordSpacing = { v -> edit { it.copy(wordSpacing = v) } },
        onTransform = { v -> edit { it.copy(transform = v) } },
    )
}
