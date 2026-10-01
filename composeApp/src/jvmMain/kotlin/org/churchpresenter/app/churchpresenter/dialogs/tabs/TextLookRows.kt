package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bible_letter_spacing
import org.churchpresenter.strings.generated.resources.bible_text_transform_capitalize
import org.churchpresenter.strings.generated.resources.bible_text_transform_lowercase
import org.churchpresenter.strings.generated.resources.bible_text_transform_uppercase
import org.churchpresenter.strings.generated.resources.bible_word_spacing
import org.churchpresenter.strings.generated.resources.center
import org.churchpresenter.strings.generated.resources.left
import org.churchpresenter.strings.generated.resources.pixels_short
import org.churchpresenter.strings.generated.resources.profile_text_alignment
import org.churchpresenter.strings.generated.resources.profile_text_as_typed
import org.churchpresenter.strings.generated.resources.profile_text_autofit
import org.churchpresenter.strings.generated.resources.profile_text_autofit_scope
import org.churchpresenter.strings.generated.resources.profile_text_autofit_sub
import org.churchpresenter.strings.generated.resources.profile_text_chord_color
import org.churchpresenter.strings.generated.resources.profile_text_color
import org.churchpresenter.strings.generated.resources.profile_text_font
import org.churchpresenter.strings.generated.resources.profile_text_highlight
import org.churchpresenter.strings.generated.resources.profile_text_letter_case
import org.churchpresenter.strings.generated.resources.profile_text_outline
import org.churchpresenter.strings.generated.resources.profile_text_shadow
import org.churchpresenter.strings.generated.resources.profile_text_size
import org.churchpresenter.strings.generated.resources.profile_text_size_unit
import org.churchpresenter.strings.generated.resources.profile_text_style
import org.churchpresenter.strings.generated.resources.right
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
    /** Where each row's value is stored, for a linked profile to mark it -- see [probeTextLookPaths]. */
    paths: TextLookPaths = TextLookPaths.NONE,
) {
    val autoFitOn = look.autoFit == true
    SettingsRow(stringResource(Res.string.profile_text_font), paths = paths[TextLookField.FONT]) {
        RowFont(look.fontType, fonts) { onChange(look.copy(fontType = it)) }
    }
    SettingsRow(stringResource(Res.string.profile_text_size), paths = paths[TextLookField.SIZE]) {
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
            paths = paths[TextLookField.AUTO_FIT],
        )
        if (autoFitOn && autoFitScope != null) {
            SettingsRow(stringResource(Res.string.profile_text_autofit_scope)) { autoFitScope() }
        }
    }
    SettingsRow(stringResource(Res.string.profile_text_color), paths = paths[TextLookField.COLOR]) {
        RowColor(look.color, { onChange(look.copy(color = it)) })
    }
    SettingsRow(stringResource(Res.string.profile_text_style), paths = paths[TextLookField.STYLE]) {
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
    SettingsRow(stringResource(Res.string.profile_text_alignment), paths = paths[TextLookField.ALIGNMENT]) {
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
    AdvancedTextRows(look, onChange, paths)
    extraAdvanced()
}

/** The Text group's Advanced rows: chord colour, letter case, spacing, outline, highlight, shadow. */
@Composable
private fun AdvancedTextRows(look: TextLook, onChange: (TextLook) -> Unit, paths: TextLookPaths) {
    look.chordColor?.let { chord ->
        SettingsRow(
            stringResource(Res.string.profile_text_chord_color),
            advanced = true,
            paths = paths[TextLookField.CHORD_COLOR],
        ) {
            RowColor(chord, { onChange(look.copy(chordColor = it)) })
        }
    }
    SettingsRow(
        stringResource(Res.string.profile_text_letter_case),
        advanced = true,
        paths = paths[TextLookField.TRANSFORM],
    ) {
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
    SettingsRow(
        stringResource(Res.string.bible_letter_spacing),
        advanced = true,
        paths = paths[TextLookField.LETTER_SPACING],
    ) {
        RowStepper(look.letterSpacing, { onChange(look.copy(letterSpacing = it)) }, LETTER_SPACING_RANGE, unit = px)
    }
    SettingsRow(
        stringResource(Res.string.bible_word_spacing),
        advanced = true,
        paths = paths[TextLookField.WORD_SPACING],
    ) {
        RowStepper(look.wordSpacing, { onChange(look.copy(wordSpacing = it)) }, WORD_SPACING_RANGE, unit = px)
    }
    SettingsRow(
        stringResource(Res.string.profile_text_outline),
        advanced = true,
        paths = paths[TextLookField.OUTLINE],
    ) {
        TextOutlineButton(look.outline, { onChange(look.copy(outline = it)) }, STYLE_BUTTON)
    }
    SettingsRow(
        stringResource(Res.string.profile_text_highlight),
        advanced = true,
        paths = paths[TextLookField.BACKDROP],
    ) {
        TextBackdropButton(look.backdrop, { onChange(look.copy(backdrop = it)) }, STYLE_BUTTON)
    }
    SettingsSwitchRow(
        stringResource(Res.string.profile_text_shadow),
        look.shadow,
        { onChange(look.copy(shadow = it)) },
        advanced = true,
        paths = paths[TextLookField.SHADOW] + paths[TextLookField.SHADOW_DETAIL],
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
