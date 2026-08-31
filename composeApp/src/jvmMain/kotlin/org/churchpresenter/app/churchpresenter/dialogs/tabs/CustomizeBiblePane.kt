package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.bible_translation_spacing
import churchpresenter.composeapp.generated.resources.bible_translation_divider
import churchpresenter.composeapp.generated.resources.bible_block_and_transition
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.customize_group_reference
import churchpresenter.composeapp.generated.resources.customize_group_verse_text
import churchpresenter.composeapp.generated.resources.customize_show_abbreviation
import churchpresenter.composeapp.generated.resources.customize_style
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.position
import churchpresenter.composeapp.generated.resources.vertical_alignment
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputStyleScope
import org.jetbrains.compose.resources.stringResource

/**
 * The Bible pane.
 *
 * Edits every translation in the stack at once. The global tab is where one translation is styled
 * apart from another; here the question is "how does the Bible look on this screen", and a stack
 * whose languages disagree about that on one output is not what the operator came to say.
 */
@Composable
internal fun BibleCustomizePane(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val scope = LocalOutputStyleScope.current
    val lowerThird = scope == OutputStyleScope.LOWER_THIRD
    val fonts = rememberSystemFonts()
    val bs = settings.bibleSettings
    val t = bs.translationList().firstOrNull() ?: BibleTranslationSettings()

    fun updateAll(transform: (BibleTranslationSettings) -> BibleTranslationSettings) {
        onSettingsChange { s ->
            val current = s.bibleSettings
            s.copy(bibleSettings = current.withTranslations(current.translationList().map(transform)))
        }
    }

    fun updateBible(transform: (BibleSettings) -> BibleSettings) {
        onSettingsChange { s -> s.copy(bibleSettings = transform(s.bibleSettings)) }
    }

    PaneScaffold {
        BibleVerseTextGroup(bs, t, lowerThird, fonts, ::updateAll, ::updateBible)
        BibleTypographyGroup(t, lowerThird, ::updateAll)
        BibleReferenceGroup(t, lowerThird, ::updateAll)
        BibleBlockGroup(bs, lowerThird, ::updateBible)
        BibleTransitionsGroup(bs, ::updateBible)
        BibleMarginsGroup(bs, ::updateBible)
    }
}


@Composable
private fun BibleVerseTextGroup(
    bs: BibleSettings,
    t: BibleTranslationSettings,
    lowerThird: Boolean,
    fonts: List<String>,
    updateAll: ((BibleTranslationSettings) -> BibleTranslationSettings) -> Unit,
    updateBible: ((BibleSettings) -> BibleSettings) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.customize_group_verse_text)) {
        CustomizeRow(stringResource(Res.string.font_type), labelInsideControl = true) {
            FontControl(
                label = stringResource(Res.string.font_type),
                value = if (lowerThird) t.lowerThirdTextFontType else t.textFontType,
                fonts = fonts,
                onValueChange = { v ->
                    updateAll { if (lowerThird) it.copy(lowerThirdTextFontType = v) else it.copy(textFontType = v) }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = if (lowerThird) t.lowerThirdTextFontSize else t.textFontSize,
                onValueChange = { v ->
                    updateAll { if (lowerThird) it.copy(lowerThirdTextFontSize = v) else it.copy(textFontSize = v) }
                },
                range = FONT_SIZE_RANGE,
            )
        }
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(
                label = stringResource(Res.string.color),
                color = if (lowerThird) t.lowerThirdTextColor else t.textColor,
                onColorChange = { v ->
                    updateAll { if (lowerThird) it.copy(lowerThirdTextColor = v) else it.copy(textColor = v) }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.customize_style)) {
            StyleControl(
                bold = if (lowerThird) t.lowerThirdTextBold else t.textBold,
                italic = if (lowerThird) t.lowerThirdTextItalic else t.textItalic,
                underline = if (lowerThird) t.lowerThirdTextUnderline else t.textUnderline,
                shadow = if (lowerThird) t.lowerThirdTextShadow else t.textShadow,
                onBoldChange = { v ->
                    updateAll { if (lowerThird) it.copy(lowerThirdTextBold = v) else it.copy(textBold = v) }
                },
                onItalicChange = { v ->
                    updateAll { if (lowerThird) it.copy(lowerThirdTextItalic = v) else it.copy(textItalic = v) }
                },
                onUnderlineChange = { v ->
                    updateAll {
                        if (lowerThird) it.copy(lowerThirdTextUnderline = v) else it.copy(textUnderline = v)
                    }
                },
                onShadowChange = { v ->
                    updateAll { if (lowerThird) it.copy(lowerThirdTextShadow = v) else it.copy(textShadow = v) }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.horizontal_alignment)) {
            HorizontalAlignControl(
                selected = if (lowerThird) t.lowerThirdTextHorizontalAlignment else t.textHorizontalAlignment,
                onSelect = { v ->
                    updateAll {
                        if (lowerThird) it.copy(lowerThirdTextHorizontalAlignment = v)
                        else it.copy(textHorizontalAlignment = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.vertical_alignment)) {
            VerticalAlignControl(bs.verticalAlignment) { v ->
                updateBible { it.copy(verticalAlignment = v) }
            }
        }
    }
}

@Composable
private fun BibleTypographyGroup(
    t: BibleTranslationSettings,
    lowerThird: Boolean,
    updateAll: ((BibleTranslationSettings) -> BibleTranslationSettings) -> Unit,
) {

    TypographyGroup(
        letterSpacing = if (lowerThird) t.lowerThirdTextLetterSpacing else t.textLetterSpacing,
        wordSpacing = if (lowerThird) t.lowerThirdTextWordSpacing else t.textWordSpacing,
        transform = if (lowerThird) t.lowerThirdTextTransform else t.textTransform,
        onLetterSpacing = { v ->
            updateAll {
                if (lowerThird) it.copy(lowerThirdTextLetterSpacing = v) else it.copy(textLetterSpacing = v)
            }
        },
        onWordSpacing = { v ->
            updateAll { if (lowerThird) it.copy(lowerThirdTextWordSpacing = v) else it.copy(textWordSpacing = v) }
        },
        onTransform = { v ->
            updateAll { if (lowerThird) it.copy(lowerThirdTextTransform = v) else it.copy(textTransform = v) }
        },
    )
}

@Composable
private fun BibleReferenceGroup(
    t: BibleTranslationSettings,
    lowerThird: Boolean,
    updateAll: ((BibleTranslationSettings) -> BibleTranslationSettings) -> Unit,
) {

    CustomizeGroup(stringResource(Res.string.customize_group_reference)) {
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = if (lowerThird) t.lowerThirdReferenceFontSize else t.referenceFontSize,
                onValueChange = { v ->
                    updateAll {
                        if (lowerThird) it.copy(lowerThirdReferenceFontSize = v)
                        else it.copy(referenceFontSize = v)
                    }
                },
                range = FONT_SIZE_RANGE,
            )
        }
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(
                label = stringResource(Res.string.color),
                color = if (lowerThird) t.lowerThirdReferenceColor else t.referenceColor,
                onColorChange = { v ->
                    updateAll {
                        if (lowerThird) it.copy(lowerThirdReferenceColor = v) else it.copy(referenceColor = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.position)) {
            PositionControl(
                selected = if (lowerThird) t.lowerThirdReferencePosition else t.referencePosition,
                aboveValue = REFERENCE_ABOVE,
                belowValue = REFERENCE_BELOW,
                onSelect = { v ->
                    updateAll {
                        if (lowerThird) it.copy(lowerThirdReferencePosition = v)
                        else it.copy(referencePosition = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.customize_show_abbreviation), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.customize_show_abbreviation), t.showAbbreviation) { v ->
                updateAll { it.copy(showAbbreviation = v) }
            }
        }
    }
}

@Composable
private fun BibleBlockGroup(
    bs: BibleSettings,
    lowerThird: Boolean,
    updateBible: ((BibleSettings) -> BibleSettings) -> Unit,
) {

    CustomizeGroup(stringResource(Res.string.bible_block_and_transition)) {
        if (lowerThird) {
            CustomizeRow(stringResource(Res.string.lower_third_size), labelInsideControl = true) {
                NumberControl(
                    label = stringResource(Res.string.lower_third_size),
                    value = bs.lowerThirdHeightPercent,
                    onValueChange = { v -> updateBible { it.copy(lowerThirdHeightPercent = v) } },
                    range = BAND_RANGE,
                )
            }
        }
        CustomizeRow(stringResource(Res.string.bible_translation_divider), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.bible_translation_divider), bs.multiTranslationDivider) { v ->
                updateBible { it.copy(multiTranslationDivider = v) }
            }
        }
        CustomizeRow(stringResource(Res.string.bible_translation_spacing), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.bible_translation_spacing),
                value = bs.multiTranslationSpacing,
                onValueChange = { v -> updateBible { it.copy(multiTranslationSpacing = v) } },
                range = SPACING_RANGE_MIN..SPACING_RANGE_MAX,
            )
        }
        CustomizeRow(stringResource(Res.string.animation_crossfade), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.animation_crossfade), bs.crossfade) { v ->
                updateBible { it.copy(crossfade = v) }
            }
        }
    }
}

@Composable
private fun BibleTransitionsGroup(
    bs: BibleSettings,
    updateBible: ((BibleSettings) -> BibleSettings) -> Unit,
) {

    TransitionsGroup(
        fadeIn = bs.fadeIn,
        fadeOut = bs.fadeOut,
        durationMs = bs.transitionDuration,
        onFadeIn = { v -> updateBible { it.copy(fadeIn = v) } },
        onFadeOut = { v -> updateBible { it.copy(fadeOut = v) } },
        onDuration = { v -> updateBible { it.copy(transitionDuration = v) } },
    )
}

@Composable
private fun BibleMarginsGroup(
    bs: BibleSettings,
    updateBible: ((BibleSettings) -> BibleSettings) -> Unit,
) {

    MarginsGroup(
        top = bs.marginTop,
        bottom = bs.marginBottom,
        left = bs.marginLeft,
        right = bs.marginRight,
        onTop = { v -> updateBible { it.copy(marginTop = v) } },
        onBottom = { v -> updateBible { it.copy(marginBottom = v) } },
        onLeft = { v -> updateBible { it.copy(marginLeft = v) } },
        onRight = { v -> updateBible { it.copy(marginRight = v) } },
    )
}
