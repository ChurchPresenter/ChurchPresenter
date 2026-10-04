package org.churchpresenter.profiles

import org.churchpresenter.presenter.BibleElementStyle
import org.churchpresenter.presenter.SongStyleElement
import org.churchpresenter.presenter.hasAutoFit
import org.churchpresenter.presenter.hasChordColor
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.SongTextStyle

/**
 * What the Text group edits, whichever content it belongs to.
 *
 * The Bible's [BibleElementStyle] and a song's [SongTextStyle] carry the same look under the same
 * names, so the rows are written once against this and each page converts to and from its own. The
 * two fields only one of them has are nullable: [autoFit] (the song presenter fits text; the Bible
 * one does not) and [chordColor] (only lyrics carry chords).
 */
internal data class TextLook(
    val fontType: String,
    val fontSize: Int,
    val autoFit: Boolean?,
    val color: String,
    val chordColor: String?,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikethrough: Boolean,
    val alignment: String,
    val transform: String,
    val letterSpacing: Int,
    val wordSpacing: Int,
    val outline: TextOutline,
    val backdrop: TextBackdrop,
    val shadow: Boolean,
    val shadowColor: String,
    val shadowSize: Int,
    val shadowOpacity: Int,
)

internal fun BibleElementStyle.toLook(): TextLook = TextLook(
    fontType = fontType, fontSize = fontSize, autoFit = null, color = color, chordColor = null,
    bold = bold, italic = italic, underline = underline, strikethrough = strikethrough,
    alignment = horizontalAlignment, transform = transform,
    letterSpacing = letterSpacing, wordSpacing = wordSpacing, outline = outline, backdrop = backdrop,
    shadow = shadow, shadowColor = shadowColor, shadowSize = shadowSize, shadowOpacity = shadowOpacity,
)

internal fun BibleElementStyle.withLook(look: TextLook): BibleElementStyle = copy(
    fontType = look.fontType, fontSize = look.fontSize, color = look.color,
    bold = look.bold, italic = look.italic, underline = look.underline, strikethrough = look.strikethrough,
    horizontalAlignment = look.alignment, transform = look.transform,
    letterSpacing = look.letterSpacing, wordSpacing = look.wordSpacing,
    outline = look.outline, backdrop = look.backdrop,
    shadow = look.shadow, shadowColor = look.shadowColor,
    shadowSize = look.shadowSize, shadowOpacity = look.shadowOpacity,
)

internal fun SongTextStyle.toLook(element: SongStyleElement): TextLook = TextLook(
    fontType = fontType, fontSize = fontSize,
    autoFit = autoFit.takeIf { element.hasAutoFit },
    color = color,
    chordColor = chordColor.takeIf { element.hasChordColor },
    bold = bold, italic = italic, underline = underline, strikethrough = strikethrough,
    alignment = horizontalAlignment, transform = transform,
    letterSpacing = letterSpacing, wordSpacing = wordSpacing, outline = outline, backdrop = backdrop,
    shadow = shadow, shadowColor = shadowColor, shadowSize = shadowSize, shadowOpacity = shadowOpacity,
)

internal fun SongTextStyle.withLook(look: TextLook): SongTextStyle = copy(
    fontType = look.fontType, fontSize = look.fontSize, autoFit = look.autoFit ?: autoFit,
    color = look.color, chordColor = look.chordColor ?: chordColor,
    bold = look.bold, italic = look.italic, underline = look.underline, strikethrough = look.strikethrough,
    horizontalAlignment = look.alignment, transform = look.transform,
    letterSpacing = look.letterSpacing, wordSpacing = look.wordSpacing,
    outline = look.outline, backdrop = look.backdrop,
    shadow = look.shadow, shadowColor = look.shadowColor,
    shadowSize = look.shadowSize, shadowOpacity = look.shadowOpacity,
)
