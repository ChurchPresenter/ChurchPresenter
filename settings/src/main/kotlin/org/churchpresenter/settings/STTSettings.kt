package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.utils.Constants

@Serializable
data class STTSettings(
    val serverUrl: String = "http://localhost:80",
    val lastConnectedUrl: String = "", // URL of the last successful connection; gates the Bible-tab connect button
    val displayMode: String = "transcribe", // "transcribe", "translate", "both"
    val layout: String = "stacked", // "stacked", "stacked_inverse", "side_by_side", "side_by_side_inverse"
    val showWordHighlighting: Boolean = false,
    val maxSegments: Int = 5, // last N segments shown on an output and in the STT tab (0 = unlimited)
    val maxLines: Int = 3, // max visible lines on projection display (0 = unlimited)
    val showInProgress: Boolean = false,
    val showTranslationInProgress: Boolean = false,
    val dripFeedEnabled: Boolean = true,
    val dripFeedSpeed: Int = 25, // ms per character
    val textColor: String = "#FFFFFF",
    val translationTextColor: String = "#FFFFFF",
    val backgroundColor: String = Constants.COLOR_VALUE_TRANSPARENT,
    val backgroundOpacity: Int = 0,
    val fontSize: Int = 42,
    val lineSpacing: Int = 130, // line height as percentage of font size (100 = no extra spacing)
    val fontType: String = "Arial",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 78,
    /** The band behind each line and the box around the block. */
    val backdrop: TextBackdrop = TextBackdrop(),
    /** The stroke drawn around the glyphs, under the fill. */
    val outline: TextOutline = TextOutline(),
    val horizontalAlignment: String = Constants.CENTER,
    val position: String = Constants.BOTTOM,
    /**
     * Each item's own text box, keyed by [textBoxKey] with [CAPTION_TRANSCRIPT_BOX] or [CAPTION_TRANSLATION_BOX].
     * An item with none, or with one turned off, is drawn where it always was.
     */
    val textBoxes: Map<String, TextBox> = emptyMap(),
    /** How this page's boxes behave -- see [TextBoxOptions]. */
    val textBoxOptions: TextBoxOptions = TextBoxOptions(),
    /** How the words flow onto the screen and leave it -- see [CaptionReading]. */
    val reading: CaptionReading = CaptionReading(),
    /** The translation's own size, 0 for the transcript's; and its own weight and slant. */
    val translationFontSize: Int = 0,
    val translationBold: Boolean = false,
    val translationItalic: Boolean = false,
    val transcriptAllCaps: Boolean = false,
    val translationAllCaps: Boolean = false,
    /** Extra room between letters and at each word break, in pixels at the font size, as songs have. */
    val letterSpacing: Int = 0,
    val wordSpacing: Int = 0,
    /** How far the captions stay in from each edge, in pixels. 32 is the inset they always had. */
    val marginTop: Int = DEFAULT_CAPTION_MARGIN,
    val marginBottom: Int = DEFAULT_CAPTION_MARGIN,
    val marginLeft: Int = DEFAULT_CAPTION_MARGIN,
    val marginRight: Int = DEFAULT_CAPTION_MARGIN,
)

const val DEFAULT_CAPTION_MARGIN = 32

/**
 * What makes live captions easier to follow: when they leave the screen, how fast they may arrive,
 * how the lines move and break, and how the newest words stand out. Every default draws captions
 * exactly as they were before these were settings.
 */
@Serializable
data class CaptionReading(
    /** Fade the captions out once nothing new has been said for [clearAfterSeconds]. */
    val clearAfterSilence: Boolean = false,
    val clearAfterSeconds: Int = 8,
    val clearFadeMillis: Int = 600,
    /** Never bring words on faster than [readingSpeedCps] characters a second. */
    val readingSpeedLimit: Boolean = false,
    val readingSpeedCps: Int = 17,
    /** Slide the lines up when a new one arrives instead of jumping. */
    val rollUp: Boolean = false,
    val rollUpMillis: Int = 250,
    /** Each older segment, or line when lines break, drawn [dimStepPercent] fainter, never below [dimFloorPercent]. */
    val dimOlderLines: Boolean = false,
    val dimStepPercent: Int = 25,
    val dimFloorPercent: Int = 40,
    /** [CAPTION_BREAK_NONE], [CAPTION_BREAK_SEGMENT] or [CAPTION_BREAK_SENTENCE]. */
    val lineBreaks: String = CAPTION_BREAK_NONE,
    /** A blank line between the units [lineBreaks] starts on new lines. */
    val blankLineBetween: Boolean = false,
    /** Wrap at this many characters as well as at the edge; 0 wraps at the edge only. */
    val maxCharsPerLine: Int = 0,
)

const val CAPTION_BREAK_NONE = "none"
const val CAPTION_BREAK_SEGMENT = "segment"
const val CAPTION_BREAK_SENTENCE = "sentence"
