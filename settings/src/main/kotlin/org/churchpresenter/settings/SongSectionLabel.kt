package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * The current section's own label -- "Verse 1", "Chorus" -- drawn above the lyrics while it is on
 * screen.
 *
 * Off by default: a song written with no section headers would show nothing but the humanized
 * section type ("Verse", "Chorus") for every slide, which is not what every installation wants
 * turned on unasked. Nested rather than flat fields on [SongSettings]: see [SongOutlines] for why a
 * new song setting goes in a record.
 */
@Serializable
data class SongSectionLabel(
    val enabled: Boolean = false,
    val fontSize: Int = 32,
    val color: String = "#FFFFFF",
) {
    companion object {
        val FONT_SIZE_RANGE = 8..150
    }
}
