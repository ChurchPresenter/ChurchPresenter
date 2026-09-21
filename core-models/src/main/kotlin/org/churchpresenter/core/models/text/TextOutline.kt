package org.churchpresenter.core.models.text

import kotlinx.serialization.Serializable

/**
 * A stroke drawn around the glyphs themselves, under the fill.
 *
 * The third thing that can sit behind a piece of styled text, beside the shadow and [TextBackdrop].
 * Where a backdrop puts a band behind the *line* and a shadow throws the *block* forward, an outline
 * follows the letter shapes -- which is what keeps white lyrics legible over a bright, busy picture
 * without darkening any of it.
 *
 * Drawn as a second pass: the text is stroked in [color] first and the ordinary filled text is drawn
 * on top of it, so the inner half of the stroke is hidden behind the fill and [width] reads as the
 * thickness of the visible edge times two. Kept as a record rather than three flat fields per
 * profile for the reason [TextBackdrop] is: ten profiles times three fields is thirty more fields
 * on a settings class that already cannot be read top to bottom.
 *
 * [width] is in the same units as the element's font size, so it scales with the type when the
 * presenter fits it to an output -- an outline set against a 1080p screen is the same weight
 * relative to the letters on a 4K one.
 */
@Serializable
data class TextOutline(
    val enabled: Boolean = false,
    val color: String = "#000000",
    val width: Int = DEFAULT_WIDTH,
) {
    /** True when there is a stroke to draw; a zero width is off however the flag reads. */
    val isVisible: Boolean get() = enabled && width > 0

    companion object {
        const val DEFAULT_WIDTH = 4

        /** The range the field that edits it clamps to. */
        val WIDTH_RANGE = 1..40
    }
}
