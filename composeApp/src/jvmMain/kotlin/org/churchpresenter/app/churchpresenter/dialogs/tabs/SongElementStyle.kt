package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants

/**
 * One of the five things a song slide draws.
 *
 * The settings tab edits one at a time rather than showing all five stacked, which is what lets a
 * single set of controls stand for what used to be four scrolling columns.
 */
internal enum class SongStyleElement { NUMBER, TITLE, LYRICS, LOOK_AHEAD, NEXT_SECTION }

/** Which output the styling being edited belongs to. */
internal enum class SongStyleTarget { FULL_SCREEN, LOWER_THIRD }

internal val SongStyleTarget.isLowerThird: Boolean get() = this == SongStyleTarget.LOWER_THIRD

/** Only the number and the title sit above or below the lyrics; the rest have nowhere to go. */
internal val SongStyleElement.hasPosition: Boolean
    get() = this == SongStyleElement.NUMBER || this == SongStyleElement.TITLE

/**
 * The three elements whose size the presenter can fit for them.
 *
 * A stored toggle, unlike the Bible tab's one-shot Auto button: `SongPresenter` re-fits on every
 * slide while it is on. The number and the title have no such field -- they are short enough that
 * they never needed one.
 */
internal val SongStyleElement.hasAutoFit: Boolean
    get() = this == SongStyleElement.LYRICS ||
        this == SongStyleElement.LOOK_AHEAD ||
        this == SongStyleElement.NEXT_SECTION

/** Chords are drawn over the lyrics and nowhere else, so only the lyrics carry a chord colour. */
internal val SongStyleElement.hasChordColor: Boolean
    get() = this == SongStyleElement.LYRICS

/** The two elements that only ever appear on a look-ahead slide. */
internal val SongStyleElement.onLookAheadSlide: Boolean
    get() = this == SongStyleElement.LOOK_AHEAD || this == SongStyleElement.NEXT_SECTION

/**
 * The appearance of one element on one output, lifted out of [SongSettings].
 *
 * [SongSettings] stores these ten profiles as ten parallel families of flat fields, which is right
 * for a serialized settings file and wrong for a panel that edits whichever one the operator has
 * selected. This is the shape the panel wants: read one out with [elementStyle], hand the edited
 * copy back with [withElementStyle], and no control has to know which of the two hundred fields it
 * is actually writing.
 *
 * [position] and [chordColor] are stored only by the profiles that have somewhere to keep them --
 * see [SongStyleElement.hasPosition] and [SongStyleElement.hasChordColor]. The others read the
 * default and drop it on write, so a control for them is hidden rather than silently ineffective.
 */
internal data class SongElementStyle(
    val color: String = "#FFFFFF",
    /** Blank means "whatever the app falls back to", which is what an unset face has always meant. */
    val fontType: String = "",
    val fontSize: Int = 70,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 90,
    val horizontalAlignment: String = Constants.CENTER,
    val position: String = Constants.BELOW_VERSE,
    val letterSpacing: Int = 0,
    val wordSpacing: Int = 0,
    val transform: String = Constants.TEXT_TRANSFORM_NONE,
    val chordColor: String = "#4FD3E8",
    /** Only meaningful where [SongStyleElement.hasAutoFit]; the rest read and write nothing. */
    val autoFit: Boolean = true,
)

/** What this song draws [element] with on [target]. */
internal fun SongSettings.elementStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
): SongElementStyle = when (element) {
    SongStyleElement.NUMBER -> numberStyle(target)
    SongStyleElement.TITLE -> titleStyle(target)
    SongStyleElement.LYRICS -> lyricsStyle(target)
    SongStyleElement.LOOK_AHEAD -> lookAheadStyle(target)
    SongStyleElement.NEXT_SECTION -> nextSectionStyle(target)
}

/**
 * These settings with [element] on [target] set to [style], and everything else untouched.
 *
 * The inverse of [elementStyle] -- read, `copy` the one property a control changed, write back.
 */
internal fun SongSettings.withElementStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
    style: SongElementStyle,
): SongSettings = when (element) {
    SongStyleElement.NUMBER ->
        if (target.isLowerThird) withNumberLowerThird(style) else withNumber(style)
    SongStyleElement.TITLE ->
        if (target.isLowerThird) withTitleLowerThird(style) else withTitle(style)
    SongStyleElement.LYRICS ->
        if (target.isLowerThird) withLyricsLowerThird(style) else withLyrics(style)
    SongStyleElement.LOOK_AHEAD ->
        if (target.isLowerThird) withLookAheadLowerThird(style) else withLookAhead(style)
    SongStyleElement.NEXT_SECTION ->
        if (target.isLowerThird) withNextSectionLowerThird(style) else withNextSection(style)
}

/**
 * What this element on this output looks like before anyone has touched it.
 *
 * Read off a default [SongSettings] rather than written out again here, so the Reset button and the
 * stored defaults can never drift apart -- the lower third's own smaller font sizes included.
 */
internal fun defaultSongElementStyle(
    element: SongStyleElement,
    target: SongStyleTarget,
): SongElementStyle = SongSettings().elementStyle(element, target)

private fun SongSettings.numberStyle(target: SongStyleTarget): SongElementStyle =
    if (target.isLowerThird) {
        SongElementStyle(
            color = songNumberLowerThirdColor,
            fontType = songNumberLowerThirdFontType,
            fontSize = songNumberLowerThirdFontSize,
            bold = songNumberLowerThirdBold,
            italic = songNumberLowerThirdItalic,
            underline = songNumberLowerThirdUnderline,
            strikethrough = songNumberLowerThirdStrikethrough,
            shadow = songNumberLowerThirdShadow,
            shadowColor = songNumberLowerThirdShadowColor,
            shadowSize = songNumberLowerThirdShadowSize,
            shadowOpacity = songNumberLowerThirdShadowOpacity,
            horizontalAlignment = songNumberLowerThirdHorizontalAlignment,
            letterSpacing = songNumberLowerThirdLetterSpacing,
            wordSpacing = songNumberLowerThirdWordSpacing,
            transform = songNumberLowerThirdTransform,
            position = songNumberLowerThirdPosition,
        )
    } else {
        SongElementStyle(
            color = songNumberColor,
            fontType = songNumberFontType,
            fontSize = songNumberFontSize,
            bold = songNumberBold,
            italic = songNumberItalic,
            underline = songNumberUnderline,
            strikethrough = songNumberStrikethrough,
            shadow = songNumberShadow,
            shadowColor = songNumberShadowColor,
            shadowSize = songNumberShadowSize,
            shadowOpacity = songNumberShadowOpacity,
            horizontalAlignment = songNumberHorizontalAlignment,
            letterSpacing = songNumberLetterSpacing,
            wordSpacing = songNumberWordSpacing,
            transform = songNumberTransform,
            position = songNumberPosition,
        )
    }

private fun SongSettings.titleStyle(target: SongStyleTarget): SongElementStyle =
    if (target.isLowerThird) {
        SongElementStyle(
            color = titleLowerThirdColor,
            fontType = titleLowerThirdFontType,
            fontSize = titleLowerThirdFontSize,
            bold = titleLowerThirdBold,
            italic = titleLowerThirdItalic,
            underline = titleLowerThirdUnderline,
            strikethrough = titleLowerThirdStrikethrough,
            shadow = titleLowerThirdShadow,
            shadowColor = titleLowerThirdShadowColor,
            shadowSize = titleLowerThirdShadowSize,
            shadowOpacity = titleLowerThirdShadowOpacity,
            horizontalAlignment = titleLowerThirdHorizontalAlignment,
            letterSpacing = titleLowerThirdLetterSpacing,
            wordSpacing = titleLowerThirdWordSpacing,
            transform = titleLowerThirdTransform,
            position = titleLowerThirdPosition,
        )
    } else {
        SongElementStyle(
            color = titleColor,
            fontType = titleFontType,
            fontSize = titleFontSize,
            bold = titleBold,
            italic = titleItalic,
            underline = titleUnderline,
            strikethrough = titleStrikethrough,
            shadow = titleShadow,
            shadowColor = titleShadowColor,
            shadowSize = titleShadowSize,
            shadowOpacity = titleShadowOpacity,
            horizontalAlignment = titleHorizontalAlignment,
            letterSpacing = titleLetterSpacing,
            wordSpacing = titleWordSpacing,
            transform = titleTransform,
            position = titlePosition,
        )
    }

private fun SongSettings.lyricsStyle(target: SongStyleTarget): SongElementStyle =
    if (target.isLowerThird) {
        SongElementStyle(
            color = lyricsLowerThirdColor,
            fontType = lyricsLowerThirdFontType,
            fontSize = lyricsLowerThirdFontSize,
            bold = lyricsLowerThirdBold,
            italic = lyricsLowerThirdItalic,
            underline = lyricsLowerThirdUnderline,
            strikethrough = lyricsLowerThirdStrikethrough,
            shadow = lyricsLowerThirdShadow,
            shadowColor = lyricsLowerThirdShadowColor,
            shadowSize = lyricsLowerThirdShadowSize,
            shadowOpacity = lyricsLowerThirdShadowOpacity,
            horizontalAlignment = lyricsLowerThirdHorizontalAlignment,
            letterSpacing = lyricsLowerThirdLetterSpacing,
            wordSpacing = lyricsLowerThirdWordSpacing,
            transform = lyricsLowerThirdTransform,
            chordColor = lyricsLowerThirdChordColor,
        autoFit = lyricsLowerThirdFontSizeAutoFit,
        )
    } else {
        SongElementStyle(
            color = lyricsColor,
            fontType = lyricsFontType,
            fontSize = lyricsFontSize,
            bold = lyricsBold,
            italic = lyricsItalic,
            underline = lyricsUnderline,
            strikethrough = lyricsStrikethrough,
            shadow = lyricsShadow,
            shadowColor = lyricsShadowColor,
            shadowSize = lyricsShadowSize,
            shadowOpacity = lyricsShadowOpacity,
            horizontalAlignment = lyricsHorizontalAlignment,
            letterSpacing = lyricsLetterSpacing,
            wordSpacing = lyricsWordSpacing,
            transform = lyricsTransform,
            chordColor = lyricsChordColor,
        autoFit = lyricsFontSizeAutoFit,
        )
    }

private fun SongSettings.lookAheadStyle(target: SongStyleTarget): SongElementStyle =
    if (target.isLowerThird) {
        SongElementStyle(
            color = lowerThirdLookAheadColor,
            fontType = lowerThirdLookAheadFontType,
            fontSize = lowerThirdLookAheadFontSize,
            bold = lowerThirdLookAheadBold,
            italic = lowerThirdLookAheadItalic,
            underline = lowerThirdLookAheadUnderline,
            strikethrough = lowerThirdLookAheadStrikethrough,
            shadow = lowerThirdLookAheadShadow,
            shadowColor = lowerThirdLookAheadShadowColor,
            shadowSize = lowerThirdLookAheadShadowSize,
            shadowOpacity = lowerThirdLookAheadShadowOpacity,
            horizontalAlignment = lowerThirdLookAheadHorizontalAlignment,
            letterSpacing = lowerThirdLookAheadLetterSpacing,
            wordSpacing = lowerThirdLookAheadWordSpacing,
            transform = lowerThirdLookAheadTransform,
            autoFit = lowerThirdLookAheadFontSizeAutoFit,
        )
    } else {
        SongElementStyle(
            color = lookAheadColor,
            fontType = lookAheadFontType,
            fontSize = lookAheadFontSize,
            bold = lookAheadBold,
            italic = lookAheadItalic,
            underline = lookAheadUnderline,
            strikethrough = lookAheadStrikethrough,
            shadow = lookAheadShadow,
            shadowColor = lookAheadShadowColor,
            shadowSize = lookAheadShadowSize,
            shadowOpacity = lookAheadShadowOpacity,
            horizontalAlignment = lookAheadHorizontalAlignment,
            letterSpacing = lookAheadLetterSpacing,
            wordSpacing = lookAheadWordSpacing,
            transform = lookAheadTransform,
            autoFit = lookAheadFontSizeAutoFit,
        )
    }

private fun SongSettings.nextSectionStyle(target: SongStyleTarget): SongElementStyle =
    if (target.isLowerThird) {
        SongElementStyle(
            color = lowerThirdLookAheadNextColor,
            fontType = lowerThirdLookAheadNextFontType,
            fontSize = lowerThirdLookAheadNextFontSize,
            bold = lowerThirdLookAheadNextBold,
            italic = lowerThirdLookAheadNextItalic,
            underline = lowerThirdLookAheadNextUnderline,
            strikethrough = lowerThirdLookAheadNextStrikethrough,
            shadow = lowerThirdLookAheadNextShadow,
            shadowColor = lowerThirdLookAheadNextShadowColor,
            shadowSize = lowerThirdLookAheadNextShadowSize,
            shadowOpacity = lowerThirdLookAheadNextShadowOpacity,
            horizontalAlignment = lowerThirdLookAheadNextHorizontalAlignment,
            letterSpacing = lowerThirdLookAheadNextLetterSpacing,
            wordSpacing = lowerThirdLookAheadNextWordSpacing,
            transform = lowerThirdLookAheadNextTransform,
            autoFit = lowerThirdLookAheadNextFontSizeAutoFit,
        )
    } else {
        SongElementStyle(
            color = lookAheadNextColor,
            fontType = lookAheadNextFontType,
            fontSize = lookAheadNextFontSize,
            bold = lookAheadNextBold,
            italic = lookAheadNextItalic,
            underline = lookAheadNextUnderline,
            strikethrough = lookAheadNextStrikethrough,
            shadow = lookAheadNextShadow,
            shadowColor = lookAheadNextShadowColor,
            shadowSize = lookAheadNextShadowSize,
            shadowOpacity = lookAheadNextShadowOpacity,
            horizontalAlignment = lookAheadNextHorizontalAlignment,
            letterSpacing = lookAheadNextLetterSpacing,
            wordSpacing = lookAheadNextWordSpacing,
            transform = lookAheadNextTransform,
            autoFit = lookAheadNextFontSizeAutoFit,
        )
    }

/**
 * How much of a song one slide holds, for the slide [element] appears on.
 *
 * The look-ahead slide keeps its own chunk and language settings, and `SongPresenter` reads *those*
 * whenever it is drawing one -- so the look-ahead and next-section elements have to read and write
 * that pair, or their controls would appear to do nothing while the preview showed the truth.
 */
internal fun SongSettings.chunkFor(element: SongStyleElement, target: SongStyleTarget): String = when {
    element.onLookAheadSlide && target.isLowerThird -> lowerThirdLookAheadDisplayMode
    element.onLookAheadSlide -> lookAheadDisplayMode
    target.isLowerThird -> lowerThirdDisplayMode
    else -> fullscreenDisplayMode
}

/** The inverse of [chunkFor]. */
internal fun SongSettings.withChunk(
    element: SongStyleElement,
    target: SongStyleTarget,
    mode: String,
): SongSettings = when {
    element.onLookAheadSlide && target.isLowerThird -> copy(lowerThirdLookAheadDisplayMode = mode)
    element.onLookAheadSlide -> copy(lookAheadDisplayMode = mode)
    target.isLowerThird -> copy(lowerThirdDisplayMode = mode)
    else -> copy(fullscreenDisplayMode = mode)
}
