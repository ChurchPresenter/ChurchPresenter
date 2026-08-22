package org.churchpresenter.lottiegen.lottie

/**
 * One run of text as Lottie needs it described: the string, the face it is set in, and how it is
 * cased and justified. These seven always travel together -- every caller passed all of them.
 */
class TextRun(
    val text: String,
    val fontFamily: String,
    val fontSizePx: Double,
    val fontWeight: Int,
    val color: List<Double>,
    val transform: String,
    val justify: Int = 0,
) {
    /** Lottie names a face "Family-Style"; weight 700 and up is the bold cut. */
    internal val fontName: String get() = "$fontFamily-" + if (fontWeight >= BOLD_WEIGHT) "Bold" else "Regular"

    /** The string as it is drawn, which is not the string as it was configured. */
    internal val displayText: String get() = if (transform == "uppercase") text.uppercase() else text
}

/** Lottie's numeric weights: 700 and above is bold. */
private const val BOLD_WEIGHT = 700
