package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_letter_spacing
import churchpresenter.composeapp.generated.resources.bible_text_transform_capitalize
import churchpresenter.composeapp.generated.resources.bible_text_transform_lowercase
import churchpresenter.composeapp.generated.resources.bible_text_transform_uppercase
import churchpresenter.composeapp.generated.resources.bible_word_spacing
import churchpresenter.composeapp.generated.resources.center
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.pixels_short
import churchpresenter.composeapp.generated.resources.profile_text_alignment
import churchpresenter.composeapp.generated.resources.profile_text_as_typed
import churchpresenter.composeapp.generated.resources.profile_text_autofit
import churchpresenter.composeapp.generated.resources.profile_text_autofit_scope
import churchpresenter.composeapp.generated.resources.profile_text_autofit_sub
import churchpresenter.composeapp.generated.resources.profile_text_chord_color
import churchpresenter.composeapp.generated.resources.profile_text_color
import churchpresenter.composeapp.generated.resources.profile_text_font
import churchpresenter.composeapp.generated.resources.profile_text_highlight
import churchpresenter.composeapp.generated.resources.profile_text_letter_case
import churchpresenter.composeapp.generated.resources.profile_text_outline
import churchpresenter.composeapp.generated.resources.profile_text_shadow
import churchpresenter.composeapp.generated.resources.profile_text_size
import churchpresenter.composeapp.generated.resources.profile_text_size_unit
import churchpresenter.composeapp.generated.resources.profile_text_style
import churchpresenter.composeapp.generated.resources.right
import org.churchpresenter.app.churchpresenter.composables.ShadowDetailRow
import org.churchpresenter.app.churchpresenter.composables.TextBackdropButton
import org.churchpresenter.app.churchpresenter.composables.TextOutlineButton
import org.churchpresenter.app.churchpresenter.composables.TextStyleButtons
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

private val FONT_SIZE_RANGE = 8..200
private val LETTER_SPACING_RANGE = -10..30
private val WORD_SPACING_RANGE = 0..40
private const val SIZE_STEP = 2
private val STYLE_BUTTON = 26.dp

/**
 * The Text group's rows: font, size, auto-fit, colour, style and alignment in Basic, and the letter
 * case, spacing, outline, highlight and shadow in Advanced.
 *
 * [autoFitScope] is drawn beside Auto-fit while it is on -- the song's Whole song / Each slide.
 * [extraBasic] and [extraAdvanced] are rows the page adds for its element: the reference's
 * position and abbreviation, the number's corner.
 */
@Composable
internal fun TextLookRows(
    look: TextLook,
    onChange: (TextLook) -> Unit,
    fonts: List<String>,
    autoFitScope: (@Composable () -> Unit)? = null,
    extraBasic: @Composable () -> Unit = {},
    extraAdvanced: @Composable () -> Unit = {},
) {
    val autoFitOn = look.autoFit == true
    SettingsRow(stringResource(Res.string.profile_text_font)) {
        RowFont(look.fontType, fonts) { onChange(look.copy(fontType = it)) }
    }
    SettingsRow(stringResource(Res.string.profile_text_size)) {
        RowStepper(
            value = look.fontSize,
            onValueChange = { onChange(look.copy(fontSize = it)) },
            range = FONT_SIZE_RANGE,
            step = SIZE_STEP,
            unit = stringResource(Res.string.profile_text_size_unit),
            testTag = TEXT_SIZE_FIELD_TAG,
        )
    }
    if (look.autoFit != null) {
        SettingsSwitchRow(
            stringResource(Res.string.profile_text_autofit),
            autoFitOn,
            { onChange(look.copy(autoFit = it)) },
            sub = stringResource(Res.string.profile_text_autofit_sub),
        )
        if (autoFitOn && autoFitScope != null) {
            SettingsRow(stringResource(Res.string.profile_text_autofit_scope)) { autoFitScope() }
        }
    }
    SettingsRow(stringResource(Res.string.profile_text_color)) {
        RowColor(look.color, { onChange(look.copy(color = it)) })
    }
    SettingsRow(stringResource(Res.string.profile_text_style)) {
        TextStyleButtons(
            bold = look.bold,
            italic = look.italic,
            underline = look.underline,
            shadow = look.shadow,
            onBoldChange = { onChange(look.copy(bold = it)) },
            onItalicChange = { onChange(look.copy(italic = it)) },
            onUnderlineChange = { onChange(look.copy(underline = it)) },
            onShadowChange = { onChange(look.copy(shadow = it)) },
            strikethrough = look.strikethrough,
            onStrikethroughChange = { onChange(look.copy(strikethrough = it)) },
            showShadow = false,
            buttonSize = STYLE_BUTTON,
        )
    }
    SettingsRow(stringResource(Res.string.profile_text_alignment)) {
        RowSegmented(
            options = listOf(
                RowOption(Constants.LEFT, stringResource(Res.string.left)),
                RowOption(Constants.CENTER, stringResource(Res.string.center)),
                RowOption(Constants.RIGHT, stringResource(Res.string.right)),
            ),
            selected = look.alignment,
            onSelect = { onChange(look.copy(alignment = it)) },
        )
    }
    extraBasic()
    AdvancedTextRows(look, onChange)
    extraAdvanced()
}

/** The Text group's Advanced rows: chord colour, letter case, spacing, outline, highlight, shadow. */
@Composable
private fun AdvancedTextRows(look: TextLook, onChange: (TextLook) -> Unit) {
    look.chordColor?.let { chord ->
        SettingsRow(stringResource(Res.string.profile_text_chord_color), advanced = true) {
            RowColor(chord, { onChange(look.copy(chordColor = it)) })
        }
    }
    SettingsRow(stringResource(Res.string.profile_text_letter_case), advanced = true) {
        RowSegmented(
            options = listOf(
                RowOption(Constants.TEXT_TRANSFORM_NONE, stringResource(Res.string.profile_text_as_typed)),
                RowOption(
                    Constants.TEXT_TRANSFORM_UPPERCASE,
                    stringResource(Res.string.bible_text_transform_uppercase),
                ),
                RowOption(
                    Constants.TEXT_TRANSFORM_LOWERCASE,
                    stringResource(Res.string.bible_text_transform_lowercase),
                ),
                RowOption(
                    Constants.TEXT_TRANSFORM_CAPITALIZE,
                    stringResource(Res.string.bible_text_transform_capitalize),
                ),
            ),
            selected = look.transform,
            onSelect = { onChange(look.copy(transform = it)) },
        )
    }
    val px = stringResource(Res.string.pixels_short)
    SettingsRow(stringResource(Res.string.bible_letter_spacing), advanced = true) {
        RowStepper(look.letterSpacing, { onChange(look.copy(letterSpacing = it)) }, LETTER_SPACING_RANGE, unit = px)
    }
    SettingsRow(stringResource(Res.string.bible_word_spacing), advanced = true) {
        RowStepper(look.wordSpacing, { onChange(look.copy(wordSpacing = it)) }, WORD_SPACING_RANGE, unit = px)
    }
    SettingsRow(stringResource(Res.string.profile_text_outline), advanced = true) {
        TextOutlineButton(look.outline, { onChange(look.copy(outline = it)) }, STYLE_BUTTON)
    }
    SettingsRow(stringResource(Res.string.profile_text_highlight), advanced = true) {
        TextBackdropButton(look.backdrop, { onChange(look.copy(backdrop = it)) }, STYLE_BUTTON)
    }
    SettingsSwitchRow(
        stringResource(Res.string.profile_text_shadow),
        look.shadow,
        { onChange(look.copy(shadow = it)) },
        advanced = true,
    )
    if (look.shadow) {
        SettingsWideRow(advanced = true, searchTerms = stringResource(Res.string.profile_text_shadow)) {
            ShadowDetailRow(
                shadowColor = look.shadowColor,
                shadowSize = look.shadowSize,
                shadowOpacity = look.shadowOpacity,
                onColorChange = { onChange(look.copy(shadowColor = it)) },
                onSizeChange = { onChange(look.copy(shadowSize = it)) },
                onOpacityChange = { onChange(look.copy(shadowOpacity = it)) },
            )
        }
    }
}

/** Test handle for the Text group's size field. */
internal const val TEXT_SIZE_FIELD_TAG = "profile_text_size"
