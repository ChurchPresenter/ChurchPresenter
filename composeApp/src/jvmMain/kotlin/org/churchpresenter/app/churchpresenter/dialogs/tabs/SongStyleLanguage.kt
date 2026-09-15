package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.SongLyricStyle
import org.churchpresenter.settings.SongSettings

/**
 * Which language of a bilingual song the tab is styling.
 *
 * The Bible tab has one of these per translation, each with a full profile of its own; a song has
 * two languages rather than a list, so this is a pair rather than an index -- but it is the same
 * move, and the switch above the preview is the same control.
 *
 * Only the lyrics have a second profile. Everything else on the slide -- the number, the title, the
 * look-ahead -- is drawn once whatever language the words are in, so [SECONDARY] shows the lyrics
 * alone rather than a strip of elements that cannot answer it.
 */
internal enum class SongStyleLanguage { PRIMARY, SECONDARY }

internal val SongStyleLanguage.isSecondary: Boolean get() = this == SongStyleLanguage.SECONDARY

/**
 * The second language's lyrics profile as the panel should show it: its own once it has one, and the
 * first language's until then.
 *
 * Falling back rather than showing stored defaults is what lets the switch be free to press. Picking
 * Secondary on a song whose second language has never been styled shows exactly what is on the
 * slide, because that is what is on the slide.
 */
internal fun SongSettings.secondaryLyricsStyle(target: SongStyleTarget): SongElementStyle =
    if (secondaryLanguage.enabled) {
        secondaryLanguage.styleFor(target.isLowerThird).toElementStyle()
    } else {
        lyricsStyleFor(target)
    }

/**
 * These settings with the second language's lyrics set to [style].
 *
 * Writing turns [org.churchpresenter.settings.SongSecondaryLanguage.enabled] on, and the profile
 * written is the one the panel was already showing -- the first language's, where the second had
 * none -- so the edit changes the one property the operator touched and nothing else.
 *
 * **Both outputs are seeded on that first edit**, not only the one being styled. The flag covers the
 * whole record, so the moment it goes on the other output starts reading its stored profile too --
 * and that profile is the class default until something is put in it. Styling the second language on
 * the screen used to leave the band's drawn in white Arial 28, which is nobody's setting: the band
 * had been following its own first language and was never asked to stop.
 */
internal fun SongSettings.withSecondaryLyricsStyle(
    target: SongStyleTarget,
    style: SongElementStyle,
): SongSettings {
    val seeded = if (secondaryLanguage.enabled) {
        secondaryLanguage
    } else {
        secondaryLanguage.copy(
            fullScreen = lyricsStyleFor(SongStyleTarget.FULL_SCREEN).toLyricStyle(),
            lowerThird = lyricsStyleFor(SongStyleTarget.LOWER_THIRD).toLyricStyle(),
        )
    }
    return copy(secondaryLanguage = seeded.withStyle(target.isLowerThird, style.toLyricStyle()))
}

/**
 * The second language's stored profile, or the first's where it has none -- the same fallback
 * [secondaryLyricsStyle] makes, in the record's own shape for callers that write one field of it.
 */
internal fun SongSettings.effectiveSecondaryLyricStyle(lowerThird: Boolean): SongLyricStyle =
    if (secondaryLanguage.enabled) {
        secondaryLanguage.styleFor(lowerThird)
    } else {
        val target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN
        lyricsStyleFor(target).toLyricStyle()
    }

/**
 * Back to being drawn like the first language, which is what Reset means for the second.
 *
 * Both outputs, because the flag is the record's: "the second language has a look of its own" is one
 * statement covering the screen and the band, and there is no half of it to keep.
 */
internal fun SongSettings.withSecondaryLyricsFollowingPrimary(): SongSettings =
    copy(secondaryLanguage = secondaryLanguage.copy(enabled = false))

/** The first language's lyrics profile, which the second falls back to and is seeded from. */
private fun SongSettings.lyricsStyleFor(target: SongStyleTarget): SongElementStyle =
    elementStyle(SongStyleElement.LYRICS, target)

private fun SongLyricStyle.toElementStyle() = SongElementStyle(
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
    autoFit = fontSizeAutoFit,
    backdrop = backdrop,
    outline = outline,
)

private fun SongElementStyle.toLyricStyle() = SongLyricStyle(
    color = color,
    fontType = fontType,
    fontSize = fontSize,
    fontSizeAutoFit = autoFit,
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
    outline = outline,
)
