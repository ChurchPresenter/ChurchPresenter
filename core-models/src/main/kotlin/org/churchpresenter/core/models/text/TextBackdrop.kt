package org.churchpresenter.core.models.text

import kotlinx.serialization.Serializable

/**
 * What is drawn *behind* and *around* a piece of styled text, alongside the bold/italic/underline
 * quartet that styles the glyphs themselves.
 *
 * Two independent things, kept in one record because they are edited together and every element
 * that can have one can have the other:
 *
 *  * **The line background** — a filled band behind each wrapped line, as wide as that line's own
 *    text. It is what makes lyrics readable over a busy photo without dimming the whole picture.
 *    [lineBackgroundHeight] grows the band above and below the line box; [lineBackgroundOffset]
 *    slides it down (negative: up), which is how a band is centred on a face whose glyphs do not
 *    sit centred in their own line box.
 *  * **The border** — one box around the whole block, [borderPadding] away from the text on every
 *    side.
 *
 * With **both** on they stop being two drawings and become one: the box is filled as well as
 * stroked, so the fill is the block's shape rather than a band per line. A stack of outlined bands
 * is not a look anybody sets out to produce — the strokes of one line run through the line above
 * it. The renderer decides that; here they are still just two flags.
 *
 * Both carry their own opacity rather than an alpha in the colour: the colour picker these fields
 * are edited with produces opaque `#RRGGBB`, so a translucent band needs a number of its own —
 * exactly as the shadow settings beside them already do.
 *
 * The measurements are in the same units the element's font size is in, so they scale with the type
 * when the presenter fits it to an output: a band set against a 1080p screen still frames the text
 * on a 4K one.
 */
@Serializable
data class TextBackdrop(
    val lineBackground: Boolean = false,
    val lineBackgroundColor: String = "#000000",
    val lineBackgroundOpacity: Int = DEFAULT_LINE_BACKGROUND_OPACITY,
    /** Added above *and* below each line box, so the band grows by twice this. */
    val lineBackgroundHeight: Int = 0,
    /** Moves the band down; negative moves it up. The text does not move. */
    val lineBackgroundOffset: Int = 0,
    val border: Boolean = false,
    val borderColor: String = "#FFFFFF",
    val borderOpacity: Int = DEFAULT_BORDER_OPACITY,
    val borderWidth: Int = DEFAULT_BORDER_WIDTH,
    /** The gap between the text and the box drawn around it. */
    val borderPadding: Int = DEFAULT_BORDER_PADDING,
    /** Corner rounding of that box. 0 is a square corner. */
    val borderRadius: Int = 0,
) {
    /** True when there is anything at all to draw, which is what lets a renderer skip the work. */
    val isEmpty: Boolean get() = !lineBackground && !border

    companion object {
        const val DEFAULT_LINE_BACKGROUND_OPACITY = 70
        const val DEFAULT_BORDER_OPACITY = 100
        const val DEFAULT_BORDER_WIDTH = 3
        const val DEFAULT_BORDER_PADDING = 12

        /** The range each measurement is clamped to by the fields that edit it. */
        val OPACITY_RANGE = 0..100
        val HEIGHT_RANGE = -100..200
        val OFFSET_RANGE = -200..200
        val WIDTH_RANGE = 0..40
        val PADDING_RANGE = 0..200
        val RADIUS_RANGE = 0..200
    }
}
