package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.auto_fit
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.customize_align_bottom
import churchpresenter.composeapp.generated.resources.customize_align_center
import churchpresenter.composeapp.generated.resources.customize_align_left
import churchpresenter.composeapp.generated.resources.customize_align_middle
import churchpresenter.composeapp.generated.resources.customize_align_right
import churchpresenter.composeapp.generated.resources.customize_align_top
import churchpresenter.composeapp.generated.resources.customize_background_opacity
import churchpresenter.composeapp.generated.resources.customize_background_type
import churchpresenter.composeapp.generated.resources.customize_card_background
import churchpresenter.composeapp.generated.resources.customize_group_card
import churchpresenter.composeapp.generated.resources.customize_group_background
import churchpresenter.composeapp.generated.resources.customize_group_background_lower_third
import churchpresenter.composeapp.generated.resources.customize_group_chords
import churchpresenter.composeapp.generated.resources.customize_group_definition
import churchpresenter.composeapp.generated.resources.customize_group_lyrics
import churchpresenter.composeapp.generated.resources.customize_group_margins
import churchpresenter.composeapp.generated.resources.customize_group_reference
import churchpresenter.composeapp.generated.resources.customize_group_title_number
import churchpresenter.composeapp.generated.resources.customize_group_typography
import churchpresenter.composeapp.generated.resources.customize_group_verse_text
import churchpresenter.composeapp.generated.resources.customize_group_word
import churchpresenter.composeapp.generated.resources.customize_letter_spacing
import churchpresenter.composeapp.generated.resources.customize_margin_horizontal
import churchpresenter.composeapp.generated.resources.customize_margin_vertical
import churchpresenter.composeapp.generated.resources.customize_reference_above
import churchpresenter.composeapp.generated.resources.customize_reference_below
import churchpresenter.composeapp.generated.resources.customize_show_abbreviation
import churchpresenter.composeapp.generated.resources.customize_style
import churchpresenter.composeapp.generated.resources.customize_text_transform
import churchpresenter.composeapp.generated.resources.customize_transform_capitalize
import churchpresenter.composeapp.generated.resources.customize_transform_lowercase
import churchpresenter.composeapp.generated.resources.customize_transform_none
import churchpresenter.composeapp.generated.resources.customize_transform_uppercase
import churchpresenter.composeapp.generated.resources.customize_word_spacing
import churchpresenter.composeapp.generated.resources.customize_type_color
import churchpresenter.composeapp.generated.resources.customize_type_image
import churchpresenter.composeapp.generated.resources.customize_type_transparent
import churchpresenter.composeapp.generated.resources.customize_type_video
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.every_page
import churchpresenter.composeapp.generated.resources.first_page
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.none
import churchpresenter.composeapp.generated.resources.position
import churchpresenter.composeapp.generated.resources.show_number
import churchpresenter.composeapp.generated.resources.show_title
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.vertical_alignment
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.textTransformOptions
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * The compact per-output panes of the Customize dialog, built from [CustomizeForm]'s controls.
 *
 * These cover what is worth varying from one screen to the next — the face, size, colour, alignment
 * and typography of the text, its margins, and the handful of show/hide decisions that go with
 * them. The exhaustive per-element controls (every shadow's colour, size and opacity; each
 * translation styled apart from the others) stay on the global settings tabs: a screen that needs
 * its own shadow opacity is not a case this dialog is for, and offering forty rows here would bury
 * the five that matter.
 *
 * Each pane reads the profile the output actually draws with — full-screen or lower-third — from
 * [LocalOutputStyleScope], so one set of controls edits whichever half applies.
 */

private const val SPACING_RANGE_MIN = -20
private const val SPACING_RANGE_MAX = 100
private val PANE_PADDING = 16.dp

@Composable
private fun PaneScaffold(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PANE_PADDING, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun alignmentOptions(): List<Pair<String, String>> = listOf(
    Constants.LEFT to stringResource(Res.string.customize_align_left),
    Constants.CENTER to stringResource(Res.string.customize_align_center),
    Constants.RIGHT to stringResource(Res.string.customize_align_right),
)

@Composable
private fun verticalOptions(): List<Pair<String, String>> = listOf(
    Constants.TOP to stringResource(Res.string.customize_align_top),
    Constants.MIDDLE to stringResource(Res.string.customize_align_middle),
    Constants.BOTTOM to stringResource(Res.string.customize_align_bottom),
)

/** The transform picker, labelled by example — AA, aa, Aa — rather than by name. */
@Composable
private fun transformOptions(): List<Pair<String, String>> {
    val labels = listOf(
        stringResource(Res.string.customize_transform_none),
        stringResource(Res.string.customize_transform_uppercase),
        stringResource(Res.string.customize_transform_lowercase),
        stringResource(Res.string.customize_transform_capitalize),
    )
    return textTransformOptions().zip(labels)
}

/** Letter spacing, word spacing and case — the three rows every text pane carries. */
@Composable
private fun TypographyGroup(
    letterSpacing: Int,
    wordSpacing: Int,
    transform: String,
    onLetterSpacing: (Int) -> Unit,
    onWordSpacing: (Int) -> Unit,
    onTransform: (String) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.customize_group_typography)) {
        CustomizeRow(stringResource(Res.string.customize_letter_spacing)) {
            NumberControl(letterSpacing, onLetterSpacing, SPACING_RANGE_MIN..SPACING_RANGE_MAX)
        }
        CustomizeRow(stringResource(Res.string.customize_word_spacing)) {
            NumberControl(wordSpacing, onWordSpacing, SPACING_RANGE_MIN..SPACING_RANGE_MAX)
        }
        CustomizeRow(stringResource(Res.string.customize_text_transform)) {
            ChoiceControl(transformOptions(), transform, onTransform)
        }
    }
}

// ── Bible ───────────────────────────────────────────────────────────────────────────────────────

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
        CustomizeGroup(stringResource(Res.string.customize_group_verse_text)) {
            CustomizeRow(stringResource(Res.string.font_type)) {
                FontControl(
                    value = if (lowerThird) t.lowerThirdTextFontType else t.textFontType,
                    fonts = fonts,
                    onValueChange = { v ->
                        updateAll { if (lowerThird) it.copy(lowerThirdTextFontType = v) else it.copy(textFontType = v) }
                    },
                )
            }
            CustomizeRow(stringResource(Res.string.font_size)) {
                NumberControl(
                    value = if (lowerThird) t.lowerThirdTextFontSize else t.textFontSize,
                    onValueChange = { v ->
                        updateAll { if (lowerThird) it.copy(lowerThirdTextFontSize = v) else it.copy(textFontSize = v) }
                    },
                    range = FONT_SIZE_RANGE,
                )
            }
            CustomizeRow(stringResource(Res.string.color)) {
                ColorControl(
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
                    shadowLabel = SHADOW_GLYPH,
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
                ChoiceControl(
                    options = alignmentOptions(),
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
                ChoiceControl(verticalOptions(), bs.verticalAlignment) { v ->
                    updateBible { it.copy(verticalAlignment = v) }
                }
            }
        }

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

        CustomizeGroup(stringResource(Res.string.customize_group_reference)) {
            CustomizeRow(stringResource(Res.string.font_size)) {
                NumberControl(
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
            CustomizeRow(stringResource(Res.string.color)) {
                ColorControl(
                    color = if (lowerThird) t.lowerThirdReferenceColor else t.referenceColor,
                    onColorChange = { v ->
                        updateAll {
                            if (lowerThird) it.copy(lowerThirdReferenceColor = v) else it.copy(referenceColor = v)
                        }
                    },
                )
            }
            CustomizeRow(stringResource(Res.string.position)) {
                ChoiceControl(
                    options = listOf(
                        REFERENCE_ABOVE to stringResource(Res.string.customize_reference_above),
                        REFERENCE_BELOW to stringResource(Res.string.customize_reference_below),
                    ),
                    selected = if (lowerThird) t.lowerThirdReferencePosition else t.referencePosition,
                    onSelect = { v ->
                        updateAll {
                            if (lowerThird) it.copy(lowerThirdReferencePosition = v)
                            else it.copy(referencePosition = v)
                        }
                    },
                )
            }
            CustomizeRow(stringResource(Res.string.customize_show_abbreviation)) {
                ToggleControl(t.showAbbreviation) { v -> updateAll { it.copy(showAbbreviation = v) } }
            }
        }

        MarginsGroup(
            vertical = bs.marginTop,
            horizontal = bs.marginLeft,
            onVertical = { v -> updateBible { it.copy(marginTop = v, marginBottom = v) } },
            onHorizontal = { v -> updateBible { it.copy(marginLeft = v, marginRight = v) } },
        )
    }
}

// ── Song ────────────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SongCustomizePane(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val scope = LocalOutputStyleScope.current
    val lowerThird = scope == OutputStyleScope.LOWER_THIRD
    val fonts = rememberSystemFonts()
    val ss = settings.songSettings

    fun update(transform: (SongSettings) -> SongSettings) {
        onSettingsChange { s -> s.copy(songSettings = transform(s.songSettings)) }
    }

    PaneScaffold {
        CustomizeGroup(stringResource(Res.string.customize_group_lyrics)) {
            CustomizeRow(stringResource(Res.string.font_type)) {
                FontControl(
                    value = if (lowerThird) ss.lyricsLowerThirdFontType else ss.lyricsFontType,
                    fonts = fonts,
                    onValueChange = { v ->
                        update { if (lowerThird) it.copy(lyricsLowerThirdFontType = v) else it.copy(lyricsFontType = v) }
                    },
                )
            }
            CustomizeRow(stringResource(Res.string.font_size)) {
                NumberControl(
                    value = if (lowerThird) ss.lyricsLowerThirdFontSize else ss.lyricsFontSize,
                    onValueChange = { v ->
                        update { if (lowerThird) it.copy(lyricsLowerThirdFontSize = v) else it.copy(lyricsFontSize = v) }
                    },
                    range = FONT_SIZE_RANGE,
                    autoLabel = stringResource(Res.string.auto_fit),
                    auto = if (lowerThird) ss.lyricsLowerThirdFontSizeAutoFit else ss.lyricsFontSizeAutoFit,
                    onAutoChange = { v ->
                        update {
                            if (lowerThird) it.copy(lyricsLowerThirdFontSizeAutoFit = v)
                            else it.copy(lyricsFontSizeAutoFit = v)
                        }
                    },
                )
            }
            CustomizeRow(stringResource(Res.string.color)) {
                ColorControl(
                    color = if (lowerThird) ss.lyricsLowerThirdColor else ss.lyricsColor,
                    onColorChange = { v ->
                        update { if (lowerThird) it.copy(lyricsLowerThirdColor = v) else it.copy(lyricsColor = v) }
                    },
                )
            }
            CustomizeRow(stringResource(Res.string.customize_style)) {
                StyleControl(
                    bold = if (lowerThird) ss.lyricsLowerThirdBold else ss.lyricsBold,
                    italic = if (lowerThird) ss.lyricsLowerThirdItalic else ss.lyricsItalic,
                    underline = if (lowerThird) ss.lyricsLowerThirdUnderline else ss.lyricsUnderline,
                    shadow = if (lowerThird) ss.lyricsLowerThirdShadow else ss.lyricsShadow,
                    shadowLabel = SHADOW_GLYPH,
                    onBoldChange = { v ->
                        update { if (lowerThird) it.copy(lyricsLowerThirdBold = v) else it.copy(lyricsBold = v) }
                    },
                    onItalicChange = { v ->
                        update { if (lowerThird) it.copy(lyricsLowerThirdItalic = v) else it.copy(lyricsItalic = v) }
                    },
                    onUnderlineChange = { v ->
                        update {
                            if (lowerThird) it.copy(lyricsLowerThirdUnderline = v) else it.copy(lyricsUnderline = v)
                        }
                    },
                    onShadowChange = { v ->
                        update { if (lowerThird) it.copy(lyricsLowerThirdShadow = v) else it.copy(lyricsShadow = v) }
                    },
                )
            }
            CustomizeRow(stringResource(Res.string.horizontal_alignment)) {
                ChoiceControl(
                    options = alignmentOptions(),
                    selected = if (lowerThird) ss.lyricsLowerThirdHorizontalAlignment
                    else ss.lyricsHorizontalAlignment,
                    onSelect = { v ->
                        update {
                            if (lowerThird) it.copy(lyricsLowerThirdHorizontalAlignment = v)
                            else it.copy(lyricsHorizontalAlignment = v)
                        }
                    },
                )
            }
            CustomizeRow(stringResource(Res.string.vertical_alignment)) {
                ChoiceControl(verticalOptions(), ss.lyricsAlignment) { v ->
                    update { it.copy(lyricsAlignment = v) }
                }
            }
        }

        TypographyGroup(
            letterSpacing = if (lowerThird) ss.lyricsLowerThirdLetterSpacing else ss.lyricsLetterSpacing,
            wordSpacing = if (lowerThird) ss.lyricsLowerThirdWordSpacing else ss.lyricsWordSpacing,
            transform = if (lowerThird) ss.lyricsLowerThirdTextTransform else ss.lyricsTextTransform,
            onLetterSpacing = { v ->
                update {
                    if (lowerThird) it.copy(lyricsLowerThirdLetterSpacing = v) else it.copy(lyricsLetterSpacing = v)
                }
            },
            onWordSpacing = { v ->
                update { if (lowerThird) it.copy(lyricsLowerThirdWordSpacing = v) else it.copy(lyricsWordSpacing = v) }
            },
            onTransform = { v ->
                update {
                    if (lowerThird) it.copy(lyricsLowerThirdTextTransform = v) else it.copy(lyricsTextTransform = v)
                }
            },
        )

        CustomizeGroup(stringResource(Res.string.customize_group_chords)) {
            CustomizeRow(stringResource(Res.string.color)) {
                ColorControl(
                    color = if (lowerThird) ss.lyricsLowerThirdChordColor else ss.lyricsChordColor,
                    onColorChange = { v ->
                        update {
                            if (lowerThird) it.copy(lyricsLowerThirdChordColor = v) else it.copy(lyricsChordColor = v)
                        }
                    },
                )
            }
        }

        CustomizeGroup(stringResource(Res.string.customize_group_title_number)) {
            CustomizeRow(stringResource(Res.string.show_title)) {
                ChoiceControl(
                    options = showOptions(),
                    selected = if (lowerThird) ss.titleLowerThirdDisplay else ss.titleDisplay,
                    onSelect = { v ->
                        update { if (lowerThird) it.copy(titleLowerThirdDisplay = v) else it.copy(titleDisplay = v) }
                    },
                )
            }
            CustomizeRow(stringResource(Res.string.title) + " " + stringResource(Res.string.font_size)) {
                NumberControl(
                    value = if (lowerThird) ss.titleLowerThirdFontSize else ss.titleFontSize,
                    onValueChange = { v ->
                        update { if (lowerThird) it.copy(titleLowerThirdFontSize = v) else it.copy(titleFontSize = v) }
                    },
                    range = FONT_SIZE_RANGE,
                )
            }
            CustomizeRow(stringResource(Res.string.show_number)) {
                ChoiceControl(
                    options = showOptions(),
                    selected = if (lowerThird) ss.showNumberLowerThird else ss.showNumber,
                    onSelect = { v ->
                        update { if (lowerThird) it.copy(showNumberLowerThird = v) else it.copy(showNumber = v) }
                    },
                )
            }
        }

        MarginsGroup(
            vertical = ss.marginTop,
            horizontal = ss.marginLeft,
            onVertical = { v -> update { it.copy(marginTop = v, marginBottom = v) } },
            onHorizontal = { v -> update { it.copy(marginLeft = v, marginRight = v) } },
        )
    }
}

// ── Dictionary ──────────────────────────────────────────────────────────────────────────────────

@Composable
internal fun DictionaryCustomizePane(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val fonts = rememberSystemFonts()
    val ds = settings.dictionarySettings

    fun update(transform: (DictionarySettings) -> DictionarySettings) {
        onSettingsChange { s -> s.copy(dictionarySettings = transform(s.dictionarySettings)) }
    }

    PaneScaffold {
        CustomizeGroup(stringResource(Res.string.customize_group_word)) {
            CustomizeRow(stringResource(Res.string.font_type)) {
                FontControl(ds.wordFontType, fonts) { v -> update { it.copy(wordFontType = v) } }
            }
            CustomizeRow(stringResource(Res.string.font_size)) {
                NumberControl(ds.wordFontSize, { v -> update { it.copy(wordFontSize = v) } }, FONT_SIZE_RANGE)
            }
            CustomizeRow(stringResource(Res.string.color)) {
                ColorControl(ds.wordColor) { v -> update { it.copy(wordColor = v) } }
            }
        }
        CustomizeGroup(stringResource(Res.string.customize_group_definition)) {
            CustomizeRow(stringResource(Res.string.font_size)) {
                NumberControl(
                    ds.definitionFontSize,
                    { v -> update { it.copy(definitionFontSize = v) } },
                    FONT_SIZE_RANGE,
                )
            }
            CustomizeRow(stringResource(Res.string.color)) {
                ColorControl(ds.definitionColor) { v -> update { it.copy(definitionColor = v) } }
            }
        }
        CustomizeGroup(stringResource(Res.string.customize_group_card)) {
            CustomizeRow(stringResource(Res.string.customize_card_background)) {
                ColorControl(ds.cardBackgroundColor) { v -> update { it.copy(cardBackgroundColor = v) } }
            }
            CustomizeRow(stringResource(Res.string.customize_background_opacity)) {
                NumberControl(
                    value = (ds.cardBackgroundOpacity * PERCENT).toInt(),
                    onValueChange = { v -> update { it.copy(cardBackgroundOpacity = v / PERCENT) } },
                    range = 0..PERCENT.toInt(),
                )
            }
        }
    }
}

// ── Background ──────────────────────────────────────────────────────────────────────────────────

/**
 * The Background pane: what this screen shows behind whatever is live.
 *
 * Type, colour and opacity for the full-screen background and for the lower-third band. The image
 * and video pickers, the stock-photo browser and the gradient controls stay on the global
 * Background tab — those choose a *file*, which is a library decision, where this pane is about how
 * one screen uses what the library already holds. An output set to Image or Video here keeps
 * showing the globally chosen file.
 */
@Composable
internal fun BackgroundCustomizePane(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
) {
    val bg = settings.backgroundSettings

    fun update(transform: (BackgroundSettings) -> BackgroundSettings) {
        onSettingsChange { s -> s.copy(backgroundSettings = transform(s.backgroundSettings)) }
    }

    PaneScaffold {
        CustomizeGroup(stringResource(Res.string.customize_group_background)) {
            CustomizeRow(stringResource(Res.string.customize_background_type)) {
                ChoiceControl(backgroundTypeOptions(), bg.defaultBackgroundType) { v ->
                    update { it.copy(defaultBackgroundType = v) }
                }
            }
            if (bg.defaultBackgroundType == Constants.BACKGROUND_COLOR) {
                CustomizeRow(stringResource(Res.string.color)) {
                    ColorControl(bg.defaultBackgroundColor) { v ->
                        update { it.copy(defaultBackgroundColor = v) }
                    }
                }
            }
            if (bg.defaultBackgroundType != Constants.BACKGROUND_TRANSPARENT) {
                CustomizeRow(stringResource(Res.string.customize_background_opacity)) {
                    NumberControl(
                        value = (bg.defaultBackgroundOpacity * PERCENT).toInt(),
                        onValueChange = { v -> update { it.copy(defaultBackgroundOpacity = v / PERCENT) } },
                        range = 0..PERCENT.toInt(),
                    )
                }
            }
        }

        CustomizeGroup(stringResource(Res.string.customize_group_background_lower_third)) {
            CustomizeRow(stringResource(Res.string.customize_background_type)) {
                ChoiceControl(backgroundTypeOptions(), bg.defaultLowerThirdBackgroundType) { v ->
                    update { it.copy(defaultLowerThirdBackgroundType = v) }
                }
            }
            if (bg.defaultLowerThirdBackgroundType == Constants.BACKGROUND_COLOR) {
                CustomizeRow(stringResource(Res.string.color)) {
                    ColorControl(bg.defaultLowerThirdBackgroundColor) { v ->
                        update { it.copy(defaultLowerThirdBackgroundColor = v) }
                    }
                }
            }
            if (bg.defaultLowerThirdBackgroundType != Constants.BACKGROUND_TRANSPARENT) {
                CustomizeRow(stringResource(Res.string.customize_background_opacity)) {
                    NumberControl(
                        value = (bg.defaultLowerThirdBackgroundOpacity * PERCENT).toInt(),
                        onValueChange = { v ->
                            update { it.copy(defaultLowerThirdBackgroundOpacity = v / PERCENT) }
                        },
                        range = 0..PERCENT.toInt(),
                    )
                }
            }
        }
    }
}

/** Colour, image, video or nothing at all — the four a screen can be set to here. */
@Composable
private fun backgroundTypeOptions(): List<Pair<String, String>> = listOf(
    Constants.BACKGROUND_COLOR to stringResource(Res.string.customize_type_color),
    Constants.BACKGROUND_IMAGE to stringResource(Res.string.customize_type_image),
    Constants.BACKGROUND_VIDEO to stringResource(Res.string.customize_type_video),
    Constants.BACKGROUND_TRANSPARENT to stringResource(Res.string.customize_type_transparent),
)

// ── Shared bits ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun MarginsGroup(
    vertical: Int,
    horizontal: Int,
    onVertical: (Int) -> Unit,
    onHorizontal: (Int) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.customize_group_margins)) {
        CustomizeRow(stringResource(Res.string.customize_margin_vertical)) {
            NumberControl(vertical, onVertical, MARGIN_RANGE)
        }
        CustomizeRow(stringResource(Res.string.customize_margin_horizontal)) {
            NumberControl(horizontal, onHorizontal, MARGIN_RANGE)
        }
    }
}

/** None / first page / every page, the vocabulary the title and number already use. */
@Composable
private fun showOptions(): List<Pair<String, String>> = listOf(
    Constants.NONE to stringResource(Res.string.none),
    Constants.FIRST_PAGE to stringResource(Res.string.first_page),
    Constants.EVERY_PAGE to stringResource(Res.string.every_page),
)

private val FONT_SIZE_RANGE = 8..150
private val MARGIN_RANGE = 0..500
private const val PERCENT = 100f
private const val REFERENCE_ABOVE = "Above"
private const val REFERENCE_BELOW = "Below"
private const val SHADOW_GLYPH = "S"
