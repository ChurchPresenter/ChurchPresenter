package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * A rectangle one text item is laid out and fitted in, instead of wherever its page's layout would
 * put it -- the item's own place and move stop applying while it has one.
 *
 * The four numbers are percentages of the area the page's [TextBoxOptions] measure boxes against:
 * the whole screen or the part inside the margins, and on a lower third the band or the screen. A
 * percentage rather than pixels keeps one profile right on outputs of any shape.
 *
 * Off ([enabled] false) is the default everywhere, and means the item is drawn exactly as it was
 * before boxes existed. A box that is turned off keeps its numbers, so turning it back on puts it
 * where it was.
 */
@Serializable
data class TextBox(
    val enabled: Boolean = false,
    val xPercent: Float = DEFAULT_INSET,
    val yPercent: Float = DEFAULT_INSET,
    val widthPercent: Float = DEFAULT_SIZE,
    val heightPercent: Float = DEFAULT_SIZE,
    /** Where text shorter than the box sits in it: [Constants.TOP], [Constants.MIDDLE] or [Constants.BOTTOM]. */
    val vertical: String = Constants.MIDDLE,
    /** What happens when the text is too big for the box -- see [TextBoxOverflow]. */
    val overflow: TextBoxOverflow = TextBoxOverflow.SHRINK,
    /**
     * Whether short text grows to fill the box. False keeps the item's configured size as the
     * largest it is drawn at, which is how auto-fit has always behaved.
     */
    val fill: Boolean = false,
) {
    /** The box's right edge, in percent of its area. */
    val rightPercent: Float get() = xPercent + widthPercent

    /** The box's bottom edge, in percent of its area. */
    val bottomPercent: Float get() = yPercent + heightPercent

    companion object {
        /** Where a box starts when nothing better is known. */
        const val DEFAULT_INSET = 10f
        const val DEFAULT_SIZE = 80f

        /** The smallest a box may be made on either axis, in percent. */
        const val MIN_SIZE_PERCENT = 2f
        const val FULL_PERCENT = 100f
    }
}

/** What a [TextBox] does with text too big for it. */
@Serializable
enum class TextBoxOverflow {
    /** The text is made smaller until it fits -- auto-fit, confined to the box. */
    SHRINK,

    /** The text is drawn at its size and cut off at the box's edge. */
    CUT,

    /** The text is drawn at its size and runs past the box. */
    SPILL,
}

/**
 * How a page's boxes behave, for every box on it.
 *
 * All defaults are what a page without boxes already does, so a page is unaffected until one of
 * its items is given a box.
 */
@Serializable
data class TextBoxOptions(
    /** False: box percentages span the whole screen. True: only the part inside the margins. */
    val insideMargins: Boolean = false,
    /** On a lower third -- false: box percentages span the band. True: the whole screen. */
    val lowerThirdWholeScreen: Boolean = false,
    /** Where an item is drawn once per language -- false: each language has its own box. True: they share one. */
    val sharedLanguageBox: Boolean = false,
    /** Whether an item's text stops short of any other box it overlaps. */
    val keepClear: Boolean = false,
    /** Whether a box being dragged on the preview snaps to guides. */
    val snap: Boolean = true,
)

/**
 * The key one box is stored under: the [item] it belongs to, the language or translation it is
 * drawn for when it is drawn once per language, and the output -- `LYRICS#1@LT`.
 */
fun textBoxKey(item: String, lowerThird: Boolean, language: String? = null): String =
    item + (language?.let { "#$it" } ?: "") + if (lowerThird) "@LT" else ""

/** The box stored under [key] in [this], or an unused one where there is none. */
fun Map<String, TextBox>.boxAt(key: String): TextBox = this[key] ?: TextBox()

/** [this] with [box] stored under [key]; an untouched box is dropped rather than stored. */
fun Map<String, TextBox>.withBox(key: String, box: TextBox): Map<String, TextBox> =
    if (box == TextBox()) this - key else this + (key to box)

/** The item names the single-form pages' boxes are stored under. */
const val CAPTION_TRANSCRIPT_BOX = "TRANSCRIPT"
const val CAPTION_TRANSLATION_BOX = "TRANSLATION"
const val SUBTITLE_BOX = "SUBTITLE"
const val QA_QUESTION_BOX = "QUESTION"
const val QA_QR_CODE_BOX = "QR_CODE"
const val QA_QR_MESSAGE_BOX = "QR_MESSAGE"
const val DICTIONARY_WORD_BOX = "WORD"
const val DICTIONARY_REFERENCE_BOX = "REFERENCE"
const val DICTIONARY_DEFINITION_BOX = "DEFINITION"
const val DICTIONARY_KJV_BOX = "KJV_USAGE"
