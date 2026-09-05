package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.utils.Constants

/**
 * The appearance of one text element on one output.
 *
 * This is the shape the song settings panel has always edited — it lived in `:composeApp` as
 * `SongElementStyle`, read out of [SongSettings]' flat field families by `elementStyle` and written
 * back by `withElementStyle`. It moved here, and gained `@Serializable`, when a song grew more than
 * two languages: languages beyond the primary store their styling as whole profiles rather than as
 * yet more parallel families of flat fields, and a stored profile has to serialize.
 *
 * `:composeApp` still calls it `SongElementStyle`, through a typealias, so every control that reads
 * and writes one is untouched.
 */
@Serializable
data class SongTextStyle(
    val color: String = "#FFFFFF",
    /** Blank means "whatever the app falls back to", which is what an unset face has always meant. */
    val fontType: String = "",
    val fontSize: Int = 70,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val shadow: Boolean = false,
    val shadowColor: String = "#000000",
    val shadowSize: Int = 100,
    val shadowOpacity: Int = 90,
    val horizontalAlignment: String = Constants.CENTER,
    val position: String = Constants.BELOW_VERSE,
    val letterSpacing: Int = 0,
    val wordSpacing: Int = 0,
    val transform: String = Constants.TEXT_TRANSFORM_NONE,
    val chordColor: String = "#4FD3E8",
    /** Only meaningful where the element has an auto-fit; the rest read and write nothing. */
    val autoFit: Boolean = true,
    /** The line background and the border box, both drawn behind and around this element. */
    val backdrop: TextBackdrop = TextBackdrop(),
)

/**
 * Which of a song slide's elements can be styled per language.
 *
 * All four are the *words of a language*: the lyrics, that language's own title, and the two
 * look-ahead elements, which are the next section's lyrics in the same language. The song number is
 * deliberately absent — a number is the same digits in every language, and giving it four profiles
 * would offer a choice with nothing behind it.
 */
enum class SongTranslationElement { TITLE, LYRICS, LOOK_AHEAD, NEXT_SECTION }

/**
 * How one language of a song is presented.
 *
 * [label] is what the operator calls it — "Ukrainian" — and is what the editor's pane tabs and the
 * per-output picker show. It is stored here rather than only in the `.song` file so an output can
 * be configured for "language 3" before any song that has one is loaded.
 *
 * ### Why [overrideStyle] rather than eight always-live profiles
 *
 * Before this existed both languages of a bilingual song were drawn with the one lyrics profile, and
 * that has to keep being true after the upgrade or every bilingual church's screens change on the
 * day they update. So a language draws exactly like the primary until someone says otherwise, and
 * [overrideStyle] is that "otherwise". Turning it on seeds the profiles from the primary's, so the
 * first thing the operator sees is what was already on screen rather than a jump to the defaults.
 */
@Serializable
data class SongTranslationSettings(
    val label: String = "",
    val overrideStyle: Boolean = false,
    val title: SongTextStyle = SongTextStyle(),
    val titleLowerThird: SongTextStyle = SongTextStyle(),
    val lyrics: SongTextStyle = SongTextStyle(),
    val lyricsLowerThird: SongTextStyle = SongTextStyle(),
    val lookAhead: SongTextStyle = SongTextStyle(),
    val lookAheadLowerThird: SongTextStyle = SongTextStyle(),
    val nextSection: SongTextStyle = SongTextStyle(),
    val nextSectionLowerThird: SongTextStyle = SongTextStyle(),
) {
    /** This language's stored profile for [element] on [target]. */
    fun style(element: SongTranslationElement, lowerThird: Boolean): SongTextStyle = when (element) {
        SongTranslationElement.TITLE -> if (lowerThird) titleLowerThird else title
        SongTranslationElement.LYRICS -> if (lowerThird) lyricsLowerThird else lyrics
        SongTranslationElement.LOOK_AHEAD -> if (lowerThird) lookAheadLowerThird else lookAhead
        SongTranslationElement.NEXT_SECTION -> if (lowerThird) nextSectionLowerThird else nextSection
    }

    /** The inverse of [style]. */
    fun withStyle(
        element: SongTranslationElement,
        lowerThird: Boolean,
        value: SongTextStyle,
    ): SongTranslationSettings = when (element) {
        SongTranslationElement.TITLE ->
            if (lowerThird) copy(titleLowerThird = value) else copy(title = value)
        SongTranslationElement.LYRICS ->
            if (lowerThird) copy(lyricsLowerThird = value) else copy(lyrics = value)
        SongTranslationElement.LOOK_AHEAD ->
            if (lowerThird) copy(lookAheadLowerThird = value) else copy(lookAhead = value)
        SongTranslationElement.NEXT_SECTION ->
            if (lowerThird) copy(nextSectionLowerThird = value) else copy(nextSection = value)
    }

    /**
     * This language with every profile filled from [seed], which is how [overrideStyle] is switched
     * on without the screen jumping: the copy it starts from is what was already being drawn.
     */
    fun seededFrom(seed: (SongTranslationElement, Boolean) -> SongTextStyle): SongTranslationSettings {
        var result = copy(overrideStyle = true)
        for (element in SongTranslationElement.entries) {
            for (lowerThird in listOf(false, true)) {
                result = result.withStyle(element, lowerThird, seed(element, lowerThird))
            }
        }
        return result
    }
}
