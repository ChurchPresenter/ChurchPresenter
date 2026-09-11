package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.utils.Constants

/** The shadow's default size and opacity, in the whole percentages every element's shadow stores. */
private const val DEFAULT_SHADOW_SIZE = 100
private const val DEFAULT_SHADOW_OPACITY = 90
private const val DEFAULT_FONT = "Arial"

/**
 * How one credit line of a song's title slide is drawn -- the author, the composer, the CCLI
 * number or the tempo -- on one output.
 *
 * The number and the title keep the flat fields they always had on [SongSettings], since the title
 * slide draws them with the same look the lyric slides do. The credits are new, appear on the title
 * slide alone, and are four elements on two outputs: eight profiles, which as flat fields would
 * have been a hundred and thirty more on a class that already cannot be read top to bottom. So each
 * is one record, the way [TextBackdrop] already is.
 *
 * The fields are the ones every song element carries -- see the app's `SongElementStyle`, which
 * reads this record straight into the panel that edits it. Nothing here has a position: a credit
 * has one place on the slide, under the title.
 */
@Serializable
data class SongCreditStyle(
    val color: String = "#FFFFFF",
    /** The face the title and the lyrics default to, so the slide opens in one typeface. */
    val fontType: String = DEFAULT_FONT,
    val fontSize: Int = 34,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = DEFAULT_SHADOW_SIZE,
    val shadowOpacity: Int = DEFAULT_SHADOW_OPACITY,
    val horizontalAlignment: String = Constants.CENTER,
    val letterSpacing: Int = 0,
    val wordSpacing: Int = 0,
    val transform: String = Constants.TEXT_TRANSFORM_NONE,
    val backdrop: TextBackdrop = TextBackdrop(),
)
