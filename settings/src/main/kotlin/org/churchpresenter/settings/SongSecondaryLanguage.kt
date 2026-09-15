package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants

/** The lower third's smaller default, the one thing the two outputs' profiles start out differing on. */
private const val LOWER_THIRD_FONT_SIZE = 28

/**
 * How the *second* language of a bilingual song is drawn, on each of the two outputs.
 *
 * A whole profile rather than a colour. The two languages share a slide but are not the same text:
 * one is often a script with different metrics that wants a face and a size of its own, and a
 * congregation reading both needs to be able to tell at a glance which is which.
 *
 * [enabled] off means the second language is drawn with the *first's* profile, which is what every
 * install has always done and what a settings file written before this existed keeps doing. The
 * settings tab turns it on the moment anything here is edited, seeding these profiles from the
 * first language's so that one edit changes one thing — and the Reset button turns it back off,
 * which is how "the same as the first language again" is expressed.
 *
 * Only the lyrics. The look-ahead and next-section lines are already a colour apart from the lyrics
 * they preview, and a look-ahead output splitting by language as well would be four profiles on one
 * slide -- so a secondary look-ahead line keeps the look-ahead's own.
 */
@Serializable
data class SongSecondaryLanguage(
    val enabled: Boolean = false,
    val fullScreen: SongLyricStyle = SongLyricStyle(),
    val lowerThird: SongLyricStyle = SongLyricStyle(fontSize = LOWER_THIRD_FONT_SIZE),
) {
    /** This language's profile on [lowerThird] or not, without the caller repeating the branch. */
    fun styleFor(lowerThird: Boolean): SongLyricStyle = if (lowerThird) this.lowerThird else fullScreen

    /** A copy with [style] written to whichever output [lowerThird] names, and [enabled] on. */
    fun withStyle(lowerThird: Boolean, style: SongLyricStyle): SongSecondaryLanguage =
        if (lowerThird) copy(enabled = true, lowerThird = style) else copy(enabled = true, fullScreen = style)
}

/**
 * One language's lyrics, everywhere the presenter draws them.
 *
 * The same fields the lyrics' own flat family on [SongSettings] carries, in a record: a second full
 * profile could not be added there as flat fields -- see [SongOutlines] for the ceiling that stops
 * it -- and nothing about them wants to be flat anyway.
 *
 * No chord colour and no above/below position: a chart is drawn only on the stage monitor and is
 * never bilingual, and lyrics have nowhere to sit but where the lyrics sit.
 */
@Serializable
data class SongLyricStyle(
    val color: String = "#FFFFFF",
    val fontType: String = "Arial",
    val fontSize: Int = 70,
    val fontSizeAutoFit: Boolean = true,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 90,
    val horizontalAlignment: String = Constants.CENTER,
    val letterSpacing: Int = 0,
    val wordSpacing: Int = 0,
    val transform: String = Constants.TEXT_TRANSFORM_NONE,
    val backdrop: TextBackdrop = TextBackdrop(),
    val outline: TextOutline = TextOutline(),
)
