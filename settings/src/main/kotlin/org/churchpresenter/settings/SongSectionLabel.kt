package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants

/**
 * The current section's own label -- "Verse 1", "Chorus" -- drawn above the lyrics while it is on
 * screen.
 *
 * Off by default: a song written with no section headers would show nothing but the humanized
 * section type ("Verse", "Chorus") for every slide, which is not what every installation wants
 * turned on unasked. Nested rather than flat fields on [SongSettings]: see [SongOutlines] for why a
 * new song setting goes in a record.
 *
 * Styling matches what every other song element already has -- weight, slant, underline, shadow, a
 * stroke and a face of its own -- because the label had only a size and a colour, and the presenter
 * hard-coded the rest. [fontType] blank keeps the title's face, which is what it borrowed before
 * there was anywhere to say otherwise.
 *
 * [offset] is null until the operator positions the label, at which point it leaves the flow above
 * the lyrics; see [ElementOffset]. Full screen only, like [ContentRegion] and for the same reason,
 * so there is no lower-third twin. [fontSize] and [color] are shared by both outputs, which pre-dates
 * the convention its neighbours follow and is left as it is rather than split in passing.
 */
@Serializable
data class SongSectionLabel(
    val enabled: Boolean = false,
    val fontSize: Int = 32,
    val color: String = "#FFFFFF",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val shadow: Boolean = false,
    val outline: TextOutline = TextOutline(),
    /** Blank keeps the title's face, which is what the label drew in before it had its own. */
    val fontType: String = "",
    /** [Constants.LEFT], [Constants.CENTER] or [Constants.RIGHT] within the content width. */
    val horizontalAlignment: String = Constants.CENTER,
    val offset: ElementOffset? = null,
) {
    companion object {
        /**
         * Matches the Bible and song body text, and the announcements text that always allowed it:
         * one ceiling across every element rather than 150 here and 200 there.
         */
        val FONT_SIZE_RANGE = 8..200
    }
}
