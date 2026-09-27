package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * The current section's own label -- "Verse 1", "Chorus" -- drawn with the lyrics while that section
 * is on screen.
 *
 * Off by default: a song written with no section headers would show nothing but the humanized
 * section type ("Verse", "Chorus") for every slide, which is not what every installation wants
 * turned on unasked. Nested rather than flat fields on [SongSettings]: see [SongOutlines] for why a
 * new song setting goes in a record.
 *
 * A song element like any other, so its look is a whole [SongCreditStyle] per output -- the record
 * the title slide's number and credits already use -- and the app edits it with the same panel.
 * [SongCreditStyle.fontType] blank keeps the title's face, which is what the label drew in before it
 * had one of its own.
 *
 * [position] is one of [SONG_ELEMENT_POSITIONS]. The default holds it directly above the lyrics and
 * moving with them. Any finer placement is the element's drag move, stored with every other song
 * element's in [SongLayoutExtras.elementShifts].
 */
@Serializable
data class SongSectionLabel(
    val enabled: Boolean = false,
    val fullScreen: SongCreditStyle = SongCreditStyle(fontType = "", fontSize = DEFAULT_FONT_SIZE),
    val lowerThird: SongCreditStyle = SongCreditStyle(fontType = "", fontSize = DEFAULT_FONT_SIZE),
    val position: String = Constants.ABOVE_LYRICS,
    val lowerThirdPosition: String = Constants.ABOVE_LYRICS,
) {
    /** The style this output draws the label with. */
    fun styleFor(lowerThird: Boolean): SongCreditStyle = if (lowerThird) this.lowerThird else fullScreen

    /** Where this output places the label -- one of [SONG_ELEMENT_POSITIONS]. */
    fun positionFor(lowerThird: Boolean): String = if (lowerThird) lowerThirdPosition else position

    companion object {
        /** The size the label has always opened at, on either output. */
        const val DEFAULT_FONT_SIZE = 32
    }
}
