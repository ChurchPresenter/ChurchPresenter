package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings

/**
 * The ten writers behind [withElementStyle], one per element and output.
 *
 * Split out of the lens for size alone -- a file may hold eleven functions and these are ten of
 * them. Each is the exact inverse of its reader in `SongElementStyle.kt`.
 */

internal fun SongSettings.withNumber(s: SongElementStyle) = copy(
    songNumberColor = s.color,
    songNumberFontType = s.fontType,
    songNumberFontSize = s.fontSize,
    songNumberBold = s.bold,
    songNumberItalic = s.italic,
    songNumberUnderline = s.underline,
    songNumberStrikethrough = s.strikethrough,
    songNumberShadow = s.shadow,
    songNumberShadowColor = s.shadowColor,
    songNumberShadowSize = s.shadowSize,
    songNumberShadowOpacity = s.shadowOpacity,
    songNumberHorizontalAlignment = s.horizontalAlignment,
    songNumberLetterSpacing = s.letterSpacing,
    songNumberWordSpacing = s.wordSpacing,
    songNumberTransform = s.transform,
    songNumberPosition = s.position,
)

internal fun SongSettings.withNumberLowerThird(s: SongElementStyle) = copy(
    songNumberLowerThirdColor = s.color,
    songNumberLowerThirdFontType = s.fontType,
    songNumberLowerThirdFontSize = s.fontSize,
    songNumberLowerThirdBold = s.bold,
    songNumberLowerThirdItalic = s.italic,
    songNumberLowerThirdUnderline = s.underline,
    songNumberLowerThirdStrikethrough = s.strikethrough,
    songNumberLowerThirdShadow = s.shadow,
    songNumberLowerThirdShadowColor = s.shadowColor,
    songNumberLowerThirdShadowSize = s.shadowSize,
    songNumberLowerThirdShadowOpacity = s.shadowOpacity,
    songNumberLowerThirdHorizontalAlignment = s.horizontalAlignment,
    songNumberLowerThirdLetterSpacing = s.letterSpacing,
    songNumberLowerThirdWordSpacing = s.wordSpacing,
    songNumberLowerThirdTransform = s.transform,
    songNumberLowerThirdPosition = s.position,
)

internal fun SongSettings.withTitle(s: SongElementStyle) = copy(
    titleColor = s.color,
    titleFontType = s.fontType,
    titleFontSize = s.fontSize,
    titleBold = s.bold,
    titleItalic = s.italic,
    titleUnderline = s.underline,
    titleStrikethrough = s.strikethrough,
    titleShadow = s.shadow,
    titleShadowColor = s.shadowColor,
    titleShadowSize = s.shadowSize,
    titleShadowOpacity = s.shadowOpacity,
    titleHorizontalAlignment = s.horizontalAlignment,
    titleLetterSpacing = s.letterSpacing,
    titleWordSpacing = s.wordSpacing,
    titleTransform = s.transform,
    titlePosition = s.position,
)

internal fun SongSettings.withTitleLowerThird(s: SongElementStyle) = copy(
    titleLowerThirdColor = s.color,
    titleLowerThirdFontType = s.fontType,
    titleLowerThirdFontSize = s.fontSize,
    titleLowerThirdBold = s.bold,
    titleLowerThirdItalic = s.italic,
    titleLowerThirdUnderline = s.underline,
    titleLowerThirdStrikethrough = s.strikethrough,
    titleLowerThirdShadow = s.shadow,
    titleLowerThirdShadowColor = s.shadowColor,
    titleLowerThirdShadowSize = s.shadowSize,
    titleLowerThirdShadowOpacity = s.shadowOpacity,
    titleLowerThirdHorizontalAlignment = s.horizontalAlignment,
    titleLowerThirdLetterSpacing = s.letterSpacing,
    titleLowerThirdWordSpacing = s.wordSpacing,
    titleLowerThirdTransform = s.transform,
    titleLowerThirdPosition = s.position,
)

internal fun SongSettings.withLyrics(s: SongElementStyle) = copy(
    lyricsColor = s.color,
    lyricsFontType = s.fontType,
    lyricsFontSize = s.fontSize,
    lyricsBold = s.bold,
    lyricsItalic = s.italic,
    lyricsUnderline = s.underline,
    lyricsStrikethrough = s.strikethrough,
    lyricsShadow = s.shadow,
    lyricsShadowColor = s.shadowColor,
    lyricsShadowSize = s.shadowSize,
    lyricsShadowOpacity = s.shadowOpacity,
    lyricsHorizontalAlignment = s.horizontalAlignment,
    lyricsLetterSpacing = s.letterSpacing,
    lyricsWordSpacing = s.wordSpacing,
    lyricsTransform = s.transform,
    lyricsChordColor = s.chordColor,
    lyricsFontSizeAutoFit = s.autoFit,
)

internal fun SongSettings.withLyricsLowerThird(s: SongElementStyle) = copy(
    lyricsLowerThirdColor = s.color,
    lyricsLowerThirdFontType = s.fontType,
    lyricsLowerThirdFontSize = s.fontSize,
    lyricsLowerThirdBold = s.bold,
    lyricsLowerThirdItalic = s.italic,
    lyricsLowerThirdUnderline = s.underline,
    lyricsLowerThirdStrikethrough = s.strikethrough,
    lyricsLowerThirdShadow = s.shadow,
    lyricsLowerThirdShadowColor = s.shadowColor,
    lyricsLowerThirdShadowSize = s.shadowSize,
    lyricsLowerThirdShadowOpacity = s.shadowOpacity,
    lyricsLowerThirdHorizontalAlignment = s.horizontalAlignment,
    lyricsLowerThirdLetterSpacing = s.letterSpacing,
    lyricsLowerThirdWordSpacing = s.wordSpacing,
    lyricsLowerThirdTransform = s.transform,
    lyricsLowerThirdChordColor = s.chordColor,
    lyricsLowerThirdFontSizeAutoFit = s.autoFit,
)

internal fun SongSettings.withLookAhead(s: SongElementStyle) = copy(
    lookAheadColor = s.color,
    lookAheadFontType = s.fontType,
    lookAheadFontSize = s.fontSize,
    lookAheadBold = s.bold,
    lookAheadItalic = s.italic,
    lookAheadUnderline = s.underline,
    lookAheadStrikethrough = s.strikethrough,
    lookAheadShadow = s.shadow,
    lookAheadShadowColor = s.shadowColor,
    lookAheadShadowSize = s.shadowSize,
    lookAheadShadowOpacity = s.shadowOpacity,
    lookAheadHorizontalAlignment = s.horizontalAlignment,
    lookAheadLetterSpacing = s.letterSpacing,
    lookAheadWordSpacing = s.wordSpacing,
    lookAheadTransform = s.transform,
    lookAheadFontSizeAutoFit = s.autoFit,
)

internal fun SongSettings.withLookAheadLowerThird(s: SongElementStyle) = copy(
    lowerThirdLookAheadColor = s.color,
    lowerThirdLookAheadFontType = s.fontType,
    lowerThirdLookAheadFontSize = s.fontSize,
    lowerThirdLookAheadBold = s.bold,
    lowerThirdLookAheadItalic = s.italic,
    lowerThirdLookAheadUnderline = s.underline,
    lowerThirdLookAheadStrikethrough = s.strikethrough,
    lowerThirdLookAheadShadow = s.shadow,
    lowerThirdLookAheadShadowColor = s.shadowColor,
    lowerThirdLookAheadShadowSize = s.shadowSize,
    lowerThirdLookAheadShadowOpacity = s.shadowOpacity,
    lowerThirdLookAheadHorizontalAlignment = s.horizontalAlignment,
    lowerThirdLookAheadLetterSpacing = s.letterSpacing,
    lowerThirdLookAheadWordSpacing = s.wordSpacing,
    lowerThirdLookAheadTransform = s.transform,
    lowerThirdLookAheadFontSizeAutoFit = s.autoFit,
)

internal fun SongSettings.withNextSection(s: SongElementStyle) = copy(
    lookAheadNextColor = s.color,
    lookAheadNextFontType = s.fontType,
    lookAheadNextFontSize = s.fontSize,
    lookAheadNextBold = s.bold,
    lookAheadNextItalic = s.italic,
    lookAheadNextUnderline = s.underline,
    lookAheadNextStrikethrough = s.strikethrough,
    lookAheadNextShadow = s.shadow,
    lookAheadNextShadowColor = s.shadowColor,
    lookAheadNextShadowSize = s.shadowSize,
    lookAheadNextShadowOpacity = s.shadowOpacity,
    lookAheadNextHorizontalAlignment = s.horizontalAlignment,
    lookAheadNextLetterSpacing = s.letterSpacing,
    lookAheadNextWordSpacing = s.wordSpacing,
    lookAheadNextTransform = s.transform,
    lookAheadNextFontSizeAutoFit = s.autoFit,
)

internal fun SongSettings.withNextSectionLowerThird(s: SongElementStyle) = copy(
    lowerThirdLookAheadNextColor = s.color,
    lowerThirdLookAheadNextFontType = s.fontType,
    lowerThirdLookAheadNextFontSize = s.fontSize,
    lowerThirdLookAheadNextBold = s.bold,
    lowerThirdLookAheadNextItalic = s.italic,
    lowerThirdLookAheadNextUnderline = s.underline,
    lowerThirdLookAheadNextStrikethrough = s.strikethrough,
    lowerThirdLookAheadNextShadow = s.shadow,
    lowerThirdLookAheadNextShadowColor = s.shadowColor,
    lowerThirdLookAheadNextShadowSize = s.shadowSize,
    lowerThirdLookAheadNextShadowOpacity = s.shadowOpacity,
    lowerThirdLookAheadNextHorizontalAlignment = s.horizontalAlignment,
    lowerThirdLookAheadNextLetterSpacing = s.letterSpacing,
    lowerThirdLookAheadNextWordSpacing = s.wordSpacing,
    lowerThirdLookAheadNextTransform = s.transform,
    lowerThirdLookAheadNextFontSizeAutoFit = s.autoFit,
)
