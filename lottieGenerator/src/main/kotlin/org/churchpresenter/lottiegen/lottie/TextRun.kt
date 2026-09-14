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
    val italic: Boolean = false,
) {
    /** Lottie names a face "Family-Style"; weight 700 and up is the bold cut, and a slant is "Italic". */
    internal val fontName: String get() = "$fontFamily-" + fontStyleName(fontWeight >= BOLD_WEIGHT, italic)

    /** The string as it is drawn, which is not the string as it was configured. */
    internal val displayText: String get() = if (transform == "uppercase") text.uppercase() else text
}

/** Lottie's numeric weights: 700 and above is bold. */
private const val BOLD_WEIGHT = 700

/** The style a face is filed under: "Regular", "Bold", "Italic" or "Bold Italic", as Lottie spells them. */
fun fontStyleName(bold: Boolean, italic: Boolean): String = when {
    bold && italic -> "Bold Italic"
    bold -> "Bold"
    italic -> "Italic"
    else -> "Regular"
}

/** The AWT style bits a Lottie style name asks for. */
fun awtStyleOf(styleName: String): Int =
    (if ("Bold" in styleName) java.awt.Font.BOLD else 0) or (if ("Italic" in styleName) java.awt.Font.ITALIC else 0)
