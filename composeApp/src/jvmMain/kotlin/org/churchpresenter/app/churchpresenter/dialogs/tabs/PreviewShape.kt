package org.churchpresenter.app.churchpresenter.dialogs.tabs

import kotlin.math.roundToInt

/**
 * The shape a profile's preview is drawn at -- never an output's real resolution.
 *
 * A profile can be assigned to outputs of different sizes, so the operator picks a representative
 * shape to check it against. It is stored as the profile's `previewWidth`/`previewHeight`, which is
 * also what makes a lower third vertical (`isLowerThirdVertical`), so a portrait shape has to stay
 * on offer.
 */
internal enum class PreviewShapePreset(val width: Int, val height: Int, val label: String) {
    WIDE(1920, 1080, "16:9"),
    WIDE_TALL(1920, 1200, "16:10"),
    STANDARD(1440, 1080, "4:3"),
    ULTRAWIDE(2560, 1080, "21:9"),
    PORTRAIT(1080, 1920, "9:16"),
    ;

    companion object {
        /** The preset stored as exactly [width]×[height], or null when the stored size is custom. */
        fun matching(width: Int, height: Int): PreviewShapePreset? =
            entries.firstOrNull { it.width == width && it.height == height }
    }
}

/** How a custom shape is typed: as a ratio (`5 : 4`) or as an exact size (`1280 × 1024`). */
internal enum class CustomShapeMode { RATIO, RESOLUTION }

/** The smallest and largest side a custom shape may have -- no zero, no 1-pixel sliver. */
internal val PREVIEW_SIDE_RANGE = 16..7680

/** The largest number either half of a typed ratio may be. */
internal val PREVIEW_RATIO_RANGE = 1..100

/** What a shape with no size reads as: the preset every profile starts on. */
internal val DEFAULT_RATIO = 16 to 9

/** The height a typed ratio is stored at, so 5:4 is kept as 1350×1080. */
internal const val RATIO_STORED_HEIGHT = 1080

/** [ratioWidth]:[ratioHeight] as a size [RATIO_STORED_HEIGHT] tall, each side kept in [PREVIEW_SIDE_RANGE]. */
internal fun sizeForRatio(ratioWidth: Int, ratioHeight: Int): Pair<Int, Int> {
    val w = ratioWidth.coerceIn(PREVIEW_RATIO_RANGE)
    val h = ratioHeight.coerceIn(PREVIEW_RATIO_RANGE)
    val width = (RATIO_STORED_HEIGHT * w / h.toDouble()).roundToInt().coerceIn(PREVIEW_SIDE_RANGE)
    return width to RATIO_STORED_HEIGHT
}

/** [width]×[height] reduced to its smallest whole ratio -- 1350×1080 is `5 : 4`. */
internal fun reducedRatio(width: Int, height: Int): Pair<Int, Int> {
    if (width <= 0 || height <= 0) return DEFAULT_RATIO
    val divisor = gcd(width, height)
    return width / divisor to height / divisor
}

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/**
 * The short label for a stored shape: the preset's ratio when it is one, the reduced ratio when it
 * was typed as one and stays readable, and the exact size otherwise.
 */
internal fun previewShapeLabel(width: Int, height: Int): String {
    PreviewShapePreset.matching(width, height)?.let { return it.label }
    val (rw, rh) = reducedRatio(width, height)
    return if (height == RATIO_STORED_HEIGHT && rw <= PREVIEW_RATIO_RANGE.last && rh <= PREVIEW_RATIO_RANGE.last) {
        "$rw:$rh"
    } else {
        "$width×$height"
    }
}
