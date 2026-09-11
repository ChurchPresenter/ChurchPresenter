package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongCreditStyle
import org.churchpresenter.settings.SongSettings

// The four credit lines of the title slide are stored as one [SongCreditStyle] record each rather
// than as families of flat fields, so their side of [elementStyle] / [withElementStyle] is a plain
// conversion in each direction. The two records carry the same fields under the same names.

/** The stored record for a credit line on [target]; only meaningful where [SongStyleElement.isCredit]. */
internal fun SongSettings.creditStyle(element: SongStyleElement, target: SongStyleTarget): SongCreditStyle =
    when (element) {
        SongStyleElement.AUTHOR -> if (target.isLowerThird) titleSlideAuthorLowerThird else titleSlideAuthor
        SongStyleElement.COMPOSER -> if (target.isLowerThird) titleSlideComposerLowerThird else titleSlideComposer
        SongStyleElement.CCLI -> if (target.isLowerThird) titleSlideCcliLowerThird else titleSlideCcli
        else -> if (target.isLowerThird) titleSlideTempoLowerThird else titleSlideTempo
    }

internal fun SongSettings.withCreditStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    style: SongCreditStyle,
): SongSettings = when (element) {
    SongStyleElement.AUTHOR ->
        if (target.isLowerThird) copy(titleSlideAuthorLowerThird = style) else copy(titleSlideAuthor = style)
    SongStyleElement.COMPOSER ->
        if (target.isLowerThird) copy(titleSlideComposerLowerThird = style) else copy(titleSlideComposer = style)
    SongStyleElement.CCLI ->
        if (target.isLowerThird) copy(titleSlideCcliLowerThird = style) else copy(titleSlideCcli = style)
    else -> if (target.isLowerThird) copy(titleSlideTempoLowerThird = style) else copy(titleSlideTempo = style)
}

/**
 * [creditStyle] as the panel edits it.
 *
 * A blank face is read as the title's, on the same output. The record's default face is the
 * title's default, but a settings file written while the field defaulted to blank keeps that blank
 * -- `SettingsManager` writes defaults out -- and it would otherwise show as an empty font box and
 * draw in whatever the platform falls back to. The number already follows the title this way.
 */
internal fun SongCreditStyle.toElementStyle(titleFont: String) = SongElementStyle(
    color = color,
    fontType = fontType.ifBlank { titleFont },
    fontSize = fontSize,
    bold = bold,
    italic = italic,
    underline = underline,
    strikethrough = strikethrough,
    shadow = shadow,
    shadowColor = shadowColor,
    shadowSize = shadowSize,
    shadowOpacity = shadowOpacity,
    horizontalAlignment = horizontalAlignment,
    letterSpacing = letterSpacing,
    wordSpacing = wordSpacing,
    transform = transform,
    backdrop = backdrop,
)

internal fun SongElementStyle.toCreditStyle() = SongCreditStyle(
    color = color,
    fontType = fontType,
    fontSize = fontSize,
    bold = bold,
    italic = italic,
    underline = underline,
    strikethrough = strikethrough,
    shadow = shadow,
    shadowColor = shadowColor,
    shadowSize = shadowSize,
    shadowOpacity = shadowOpacity,
    horizontalAlignment = horizontalAlignment,
    letterSpacing = letterSpacing,
    wordSpacing = wordSpacing,
    transform = transform,
    backdrop = backdrop,
)
