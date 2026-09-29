package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * The home for a new song setting, whatever it is about -- layout, as the name says, and by now
 * styling too.
 *
 * Several unrelated pieces folded into one field rather than one each.
 *
 * [SongSettings] was already within a few slots of the JVM's 255-constructor-parameter ceiling --
 * see [SongOutlines] -- so a single field here costs it one slot no matter how many of these are
 * added later, where four flat fields would have cost it four and risked the same
 * `ClassFormatError: Too many arguments in method signature` [SongOutlines] describes. Group new
 * settings here rather than adding another top-level field to [SongSettings].
 */
@Serializable
data class SongLayoutExtras(
    /** Shrinks/repositions the whole lyrics block -- see [ContentRegion]. */
    val contentRegion: ContentRegion = ContentRegion(),
    /** The current section's own label, drawn with the lyrics -- see [SongSectionLabel]. */
    val sectionLabel: SongSectionLabel = SongSectionLabel(),
    /** Fine X/Y nudge on top of [SongSettings.songNumberCorner], for the full-screen output. */
    val numberOffset: SongNumberOffset = SongNumberOffset(),
    /** [numberOffset] for the lower third. */
    val numberLowerThirdOffset: SongNumberOffset = SongNumberOffset(),
    /**
     * Where the lyrics block sits, when it is positioned rather than aligned -- see [ElementOffset].
     *
     * Null, the default, leaves `SongSettings.lyricsAlignment` placing it exactly as it always has.
     * Full screen only, like [contentRegion] beside it, so there is no lower-third twin.
     */
    val lyricsOffset: ElementOffset? = null,
    /**
     * The title slide's own song number -- its look, its corner and its offset.
     *
     * Separate from the lyric slides' number, which the title slide used to share; see
     * [SongTitleSlideNumber].
     */
    val titleSlideNumber: SongTitleSlideNumber = SongTitleSlideNumber(),
    /**
     * Where the title slide's other five elements sit -- see [SongTitleSlideOffsets].
     *
     * The number is not among them: [titleSlideNumber] above already carries its own corner and
     * nudge, which is a richer placement than an offset and the one that slide's number wants.
     */
    val titleSlideOffsets: SongTitleSlideOffsets = SongTitleSlideOffsets(),
    /**
     * Whether Auto sizes the full-screen lyrics for **each slide on its own** rather than once for
     * the whole song.
     *
     * False, the default, is the song-wide fit Auto has always done: one size at which every line of
     * every section fits, so the text does not change size from slide to slide -- and one tall
     * section, or look-ahead measuring each section together with the next, holds the whole song
     * down to what that one slide needs. True fits only the slide on screen, up to the configured
     * size, so a short verse fills the frame and a long one shrinks. Lines stay on one row either way.
     *
     * One value per output rather than per element: the lyrics and the next-section line are drawn
     * at the one fitted size, so there is only one fit to scope.
     */
    val autoFitEachSlide: Boolean = false,
    /** [autoFitEachSlide] for the lower third. */
    val autoFitEachSlideLowerThird: Boolean = false,
    /** The All look of a song's languages, where the first language has values of its own -- see [SongAllLanguages]. */
    val allLanguages: SongAllLanguages = SongAllLanguages(),
    /**
     * Each element's own move, in output pixels at 1080 lines, keyed by [songElementShiftKey]:
     * the number, the title, a language's lyrics, the look-ahead, a credit -- dragged on the
     * Profiles preview, on top of wherever the layout and the element's own position put it.
     */
    val elementShifts: Map<String, SongElementShift> = emptyMap(),
    /**
     * Where the next-section lines sit, per output. They have always been drawn under each
     * language's lyrics, which is the default; [SongSettings] has no slot left for a flat field.
     */
    val nextSectionPosition: SongElementPosition = SongElementPosition(),
    /**
     * The space between languages drawn together, in output pixels at 1080 lines -- above and below
     * when they are stacked, beside when they sit side by side.
     *
     * Null, the default, is the spacing songs have always had: [DEFAULT_STACKED_LANGUAGE_GAP] between
     * stacked languages and none between side-by-side columns. A value applies in both directions.
     */
    val languageGap: Int? = null,
    /**
     * Whether auto-fit sizes each language on its own rather than all of them together.
     *
     * False, the default, is the fit songs have always had: one size -- the one the longest language
     * needs -- for every language, with the one Auto-fit switch deciding for all of them. True fits
     * every language to its own room with its own font, and each language's own Auto-fit switch then
     * decides for that language alone.
     */
    val fitLanguagesSeparately: Boolean = false,
    /**
     * Each element's own text box, keyed by [textBoxKey] with the element's name, the language for
     * an element drawn once per language, and the output -- `LYRICS#1@LT`. An element with none, or
     * with one turned off, is laid out as it always was.
     */
    val textBoxes: Map<String, TextBox> = emptyMap(),
    /** How the song page's boxes behave -- see [TextBoxOptions]. */
    val textBoxOptions: TextBoxOptions = TextBoxOptions(),
)

/** The space between stacked languages while [SongLayoutExtras.languageGap] is not set. */
const val DEFAULT_STACKED_LANGUAGE_GAP = 12

/** The range [SongLayoutExtras.languageGap] may be set to. */
val LANGUAGE_GAP_RANGE = 0..400

/** The space between stacked languages, in output pixels at 1080 lines. */
fun SongLayoutExtras.stackedLanguageGap(): Int = languageGap ?: DEFAULT_STACKED_LANGUAGE_GAP

/** The space between side-by-side languages, in output pixels at 1080 lines. */
fun SongLayoutExtras.sideBySideLanguageGap(): Int = languageGap ?: 0

/** Where one song element sits on each output -- each one of [SONG_ELEMENT_POSITIONS]. */
@Serializable
data class SongElementPosition(
    val fullScreen: String = Constants.BELOW_LYRICS,
    val lowerThird: String = Constants.BELOW_LYRICS,
) {
    fun positionFor(lowerThird: Boolean): String = if (lowerThird) this.lowerThird else fullScreen

    fun withPosition(lowerThird: Boolean, position: String): SongElementPosition =
        if (lowerThird) copy(lowerThird = position) else copy(fullScreen = position)
}

/**
 * The All look of a song's languages, stored only where it has to be.
 *
 * The first language stores its look in [SongSettings]' own fields, and every other language follows
 * it until it has a look of its own -- so while the first language has no values of its own, All
 * *is* the first language's look and nothing is stored here. Once it does, [style] holds All's value
 * at each of [firstLanguageOwnKeys], so All can still be read, edited and put back there.
 *
 * Keys are `<property>.<field>` -- `lyricsLowerThird.fontSize` -- naming a [SongTranslationSettings]
 * profile and a [SongTextStyle] field.
 */
@Serializable
data class SongAllLanguages(
    val style: SongTranslationSettings = SongTranslationSettings(),
    val firstLanguageOwnKeys: Set<String> = emptySet(),
)

/** One element's own move, x and y in output pixels at 1080 lines. */
@Serializable
data class SongElementShift(val x: Int = 0, val y: Int = 0)

/**
 * The key an element's move is stored under: its name, the language's slot where the element is
 * drawn once per language (`0` the first), and the output -- `LYRICS#1@LT`.
 */
fun songElementShiftKey(element: String, lowerThird: Boolean, language: Int? = null): String =
    element + (language?.let { "#$it" } ?: "") + if (lowerThird) "@LT" else ""

/** How far the element stored under [key] is moved; none when it has not been. */
fun SongSettings.elementShift(key: String): SongElementShift = layoutExtras.elementShifts[key] ?: SongElementShift()

/** [this] with the element under [key] moved by [shift]; no move drops the entry. */
fun SongSettings.withElementShift(key: String, shift: SongElementShift): SongSettings {
    val shifts = layoutExtras.elementShifts
    return copy(
        layoutExtras = layoutExtras.copy(
            elementShifts = if (shift == SongElementShift()) shifts - key else shifts + (key to shift),
        ),
    )
}
