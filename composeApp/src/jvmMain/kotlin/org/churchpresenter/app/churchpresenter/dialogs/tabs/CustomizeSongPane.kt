package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.auto_fit
import churchpresenter.composeapp.generated.resources.color
import churchpresenter.composeapp.generated.resources.song_transition_and_markers
import churchpresenter.composeapp.generated.resources.look_ahead_next_lower_third
import churchpresenter.composeapp.generated.resources.look_ahead_next_fullscreen
import churchpresenter.composeapp.generated.resources.look_ahead_lower_third
import churchpresenter.composeapp.generated.resources.look_ahead_fullscreen
import churchpresenter.composeapp.generated.resources.lower_third_size
import churchpresenter.composeapp.generated.resources.animation_crossfade
import churchpresenter.composeapp.generated.resources.right
import churchpresenter.composeapp.generated.resources.left
import churchpresenter.composeapp.generated.resources.bottom
import churchpresenter.composeapp.generated.resources.top
import churchpresenter.composeapp.generated.resources.customize_group_chords
import churchpresenter.composeapp.generated.resources.customize_group_lyrics
import churchpresenter.composeapp.generated.resources.customize_group_title_number
import churchpresenter.composeapp.generated.resources.customize_style
import churchpresenter.composeapp.generated.resources.font_size
import churchpresenter.composeapp.generated.resources.font_type
import churchpresenter.composeapp.generated.resources.horizontal_alignment
import churchpresenter.composeapp.generated.resources.show_number
import churchpresenter.composeapp.generated.resources.show_title
import churchpresenter.composeapp.generated.resources.title
import churchpresenter.composeapp.generated.resources.vertical_alignment
import org.churchpresenter.app.churchpresenter.utils.rememberSystemFonts
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.SongSettings
import org.jetbrains.compose.resources.stringResource

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
        SongLyricsGroup(ss, lowerThird, fonts, ::update)
        SongTypographyGroup(ss, lowerThird, ::update)
        SongChordsGroup(ss, lowerThird, ::update)
        SongTitleNumberGroup(ss, lowerThird, ::update)
        SongLookAheadGroup(ss, lowerThird, fonts, ::update)
        SongLookAheadNextGroup(ss, lowerThird, fonts, ::update)
        SongMarkersGroup(ss, lowerThird, ::update)
        SongTransitionsGroup(ss, ::update)
        SongMarginsGroup(ss, ::update)
    }
}


@Composable
private fun SongLyricsGroup(
    ss: SongSettings,
    lowerThird: Boolean,
    fonts: List<String>,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {
    CustomizeGroup(stringResource(Res.string.customize_group_lyrics)) {
        CustomizeRow(stringResource(Res.string.font_type), labelInsideControl = true) {
            FontControl(
                label = stringResource(Res.string.font_type),
                value = if (lowerThird) ss.lyricsLowerThirdFontType else ss.lyricsFontType,
                fonts = fonts,
                onValueChange = { v ->
                    update {
                        if (lowerThird) it.copy(lyricsLowerThirdFontType = v)
                        else it.copy(lyricsFontType = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = if (lowerThird) ss.lyricsLowerThirdFontSize else ss.lyricsFontSize,
                onValueChange = { v ->
                    update {
                        if (lowerThird) it.copy(lyricsLowerThirdFontSize = v)
                        else it.copy(lyricsFontSize = v)
                    }
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
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(
                label = stringResource(Res.string.color),
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
            HorizontalAlignControl(
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
            VerticalAlignControl(ss.lyricsAlignment) { v ->
                update { it.copy(lyricsAlignment = v) }
            }
        }
    }
}

@Composable
private fun SongTypographyGroup(
    ss: SongSettings,
    lowerThird: Boolean,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {

    TypographyGroup(
        letterSpacing = if (lowerThird) ss.lyricsLowerThirdLetterSpacing else ss.lyricsLetterSpacing,
        wordSpacing = if (lowerThird) ss.lyricsLowerThirdWordSpacing else ss.lyricsWordSpacing,
        transform = if (lowerThird) ss.lyricsLowerThirdTransform else ss.lyricsTransform,
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
                if (lowerThird) it.copy(lyricsLowerThirdTransform = v) else it.copy(lyricsTransform = v)
            }
        },
    )
}

@Composable
private fun SongChordsGroup(
    ss: SongSettings,
    lowerThird: Boolean,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {

    CustomizeGroup(stringResource(Res.string.customize_group_chords)) {
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(
                label = stringResource(Res.string.color),
                color = if (lowerThird) ss.lyricsLowerThirdChordColor else ss.lyricsChordColor,
                onColorChange = { v ->
                    update {
                        if (lowerThird) it.copy(lyricsLowerThirdChordColor = v) else it.copy(lyricsChordColor = v)
                    }
                },
            )
        }
    }
}

@Composable
private fun SongTitleNumberGroup(
    ss: SongSettings,
    lowerThird: Boolean,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {

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
        val titleSizeLabel = stringResource(Res.string.title) + " " + stringResource(Res.string.font_size)
        CustomizeRow(titleSizeLabel, labelInsideControl = true) {
            NumberControl(
                label = titleSizeLabel,
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
}

@Composable
private fun SongLookAheadGroup(
    ss: SongSettings,
    lowerThird: Boolean,
    fonts: List<String>,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {
    val title = stringResource(if (lowerThird) Res.string.look_ahead_lower_third else Res.string.look_ahead_fullscreen)
    CustomizeGroup(title) {
        CustomizeRow(stringResource(Res.string.font_type), labelInsideControl = true) {
            FontControl(
                label = stringResource(Res.string.font_type),
                value = if (lowerThird) ss.lowerThirdLookAheadFontType else ss.lookAheadFontType,
                fonts = fonts,
                onValueChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadFontType = v)
                        else it.copy(lookAheadFontType = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = if (lowerThird) ss.lowerThirdLookAheadFontSize else ss.lookAheadFontSize,
                onValueChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadFontSize = v)
                        else it.copy(lookAheadFontSize = v)
                    }
                },
                range = FONT_SIZE_RANGE,
                autoLabel = stringResource(Res.string.auto_fit),
                auto = if (lowerThird) ss.lowerThirdLookAheadFontSizeAutoFit
                else ss.lookAheadFontSizeAutoFit,
                onAutoChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadFontSizeAutoFit = v)
                        else it.copy(lookAheadFontSizeAutoFit = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(
                label = stringResource(Res.string.color),
                color = if (lowerThird) ss.lowerThirdLookAheadColor else ss.lookAheadColor,
                onColorChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadColor = v)
                        else it.copy(lookAheadColor = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.customize_style)) {
            StyleControl(
                bold = if (lowerThird) ss.lowerThirdLookAheadBold else ss.lookAheadBold,
                italic = if (lowerThird) ss.lowerThirdLookAheadItalic else ss.lookAheadItalic,
                underline = if (lowerThird) ss.lowerThirdLookAheadUnderline else ss.lookAheadUnderline,
                shadow = if (lowerThird) ss.lowerThirdLookAheadShadow else ss.lookAheadShadow,
                onBoldChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadBold = v)
                        else it.copy(lookAheadBold = v)
                    }
                },
                onItalicChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadItalic = v)
                        else it.copy(lookAheadItalic = v)
                    }
                },
                onUnderlineChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadUnderline = v)
                        else it.copy(lookAheadUnderline = v)
                    }
                },
                onShadowChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadShadow = v)
                        else it.copy(lookAheadShadow = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.horizontal_alignment)) {
            HorizontalAlignControl(
                selected = if (lowerThird) ss.lowerThirdLookAheadHorizontalAlignment
                else ss.lookAheadHorizontalAlignment,
                onSelect = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadHorizontalAlignment = v)
                        else it.copy(lookAheadHorizontalAlignment = v)
                    }
                },
            )
        }
    }
}

@Composable
private fun SongLookAheadNextGroup(
    ss: SongSettings,
    lowerThird: Boolean,
    fonts: List<String>,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {
    val title = stringResource(
        if (lowerThird) Res.string.look_ahead_next_lower_third else Res.string.look_ahead_next_fullscreen,
    )
    CustomizeGroup(title) {
        CustomizeRow(stringResource(Res.string.font_type), labelInsideControl = true) {
            FontControl(
                label = stringResource(Res.string.font_type),
                value = if (lowerThird) ss.lowerThirdLookAheadNextFontType else ss.lookAheadNextFontType,
                fonts = fonts,
                onValueChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadNextFontType = v)
                        else it.copy(lookAheadNextFontType = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.font_size), labelInsideControl = true) {
            NumberControl(
                label = stringResource(Res.string.font_size),
                value = if (lowerThird) ss.lowerThirdLookAheadNextFontSize else ss.lookAheadNextFontSize,
                onValueChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadNextFontSize = v)
                        else it.copy(lookAheadNextFontSize = v)
                    }
                },
                range = FONT_SIZE_RANGE,
            )
        }
        CustomizeRow(stringResource(Res.string.color), labelInsideControl = true) {
            ColorControl(
                label = stringResource(Res.string.color),
                color = if (lowerThird) ss.lowerThirdLookAheadNextColor else ss.lookAheadNextColor,
                onColorChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadNextColor = v)
                        else it.copy(lookAheadNextColor = v)
                    }
                },
            )
        }
        CustomizeRow(stringResource(Res.string.customize_style)) {
            StyleControl(
                bold = if (lowerThird) ss.lowerThirdLookAheadNextBold else ss.lookAheadNextBold,
                italic = if (lowerThird) ss.lowerThirdLookAheadNextItalic else ss.lookAheadNextItalic,
                underline = if (lowerThird) ss.lowerThirdLookAheadNextUnderline
                else ss.lookAheadNextUnderline,
                shadow = if (lowerThird) ss.lowerThirdLookAheadNextShadow else ss.lookAheadNextShadow,
                onBoldChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadNextBold = v)
                        else it.copy(lookAheadNextBold = v)
                    }
                },
                onItalicChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadNextItalic = v)
                        else it.copy(lookAheadNextItalic = v)
                    }
                },
                onUnderlineChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadNextUnderline = v)
                        else it.copy(lookAheadNextUnderline = v)
                    }
                },
                onShadowChange = { v ->
                    update {
                        if (lowerThird) it.copy(lowerThirdLookAheadNextShadow = v)
                        else it.copy(lookAheadNextShadow = v)
                    }
                },
            )
        }
    }
}

@Composable
private fun SongMarkersGroup(
    ss: SongSettings,
    lowerThird: Boolean,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {

    CustomizeGroup(stringResource(Res.string.song_transition_and_markers)) {
        if (lowerThird) {
            CustomizeRow(stringResource(Res.string.lower_third_size), labelInsideControl = true) {
                NumberControl(
                    label = stringResource(Res.string.lower_third_size),
                    value = ss.lowerThirdHeightPercent,
                    onValueChange = { v -> update { it.copy(lowerThirdHeightPercent = v) } },
                    range = BAND_RANGE,
                )
            }
        }
        CustomizeRow(stringResource(Res.string.animation_crossfade), labelInsideControl = true) {
            ToggleControl(stringResource(Res.string.animation_crossfade), ss.crossfade) { v ->
                update { it.copy(crossfade = v) }
            }

        }
    }
}

@Composable
private fun SongTransitionsGroup(
    ss: SongSettings,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {

    TransitionsGroup(
        fadeIn = ss.fadeIn,
        fadeOut = ss.fadeOut,
        durationMs = ss.transitionDuration,
        onFadeIn = { v -> update { it.copy(fadeIn = v) } },
        onFadeOut = { v -> update { it.copy(fadeOut = v) } },
        onDuration = { v -> update { it.copy(transitionDuration = v) } },
    )
}

@Composable
private fun SongMarginsGroup(
    ss: SongSettings,
    update: ((SongSettings) -> SongSettings) -> Unit,
) {

    MarginsGroup(
        top = ss.marginTop,
        bottom = ss.marginBottom,
        left = ss.marginLeft,
        right = ss.marginRight,
        onTop = { v -> update { it.copy(marginTop = v) } },
        onBottom = { v -> update { it.copy(marginBottom = v) } },
        onLeft = { v -> update { it.copy(marginLeft = v) } },
        onRight = { v -> update { it.copy(marginRight = v) } },
    )
}
