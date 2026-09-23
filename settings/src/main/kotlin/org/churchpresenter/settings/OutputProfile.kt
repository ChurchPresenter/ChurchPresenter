package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * The id every fresh install's one factory profile is given, and the id every newly detected or
 * added output defaults [ScreenAssignment.activeProfileId] to -- see [ProjectionSettings]'s own
 * defaults. Not otherwise special: this profile is exactly as editable and deletable as any other
 * once it exists (see [deleteOutputProfile]'s in-use guard) -- it is a starting point, not a
 * protected one.
 */
const val DEFAULT_OUTPUT_PROFILE_ID = "default"

/**
 * A named, reusable bundle of everything that decides how an output behaves: its display mode,
 * what content it shows, and its full Stage Monitor/Bible/Song/Background/Dictionary appearance.
 *
 * An output only ever *assigns* one of these -- via [ScreenAssignment.activeProfileId] -- it never
 * carries any styling of its own. Editing a profile changes every output currently assigned to it,
 * which is what lets two outputs be kept identical by assigning them the same profile rather than
 * copying settings between them, and what lets an operator switch what one output is doing by
 * picking a different profile rather than reconfiguring it.
 *
 * [bibleSettings]/[songSettings] are carried here **whole**, exactly like [BibleSettings]/
 * [SongSettings] themselves -- there is no separate "style-only" type. Resolving what an output
 * actually renders with ([resolvedFor]) keeps [BIBLE_GLOBAL_KEYS]/[SONG_GLOBAL_KEYS] -- the library
 * folder, file lists, translation-stack membership, column widths -- from the *global* document
 * and takes every other field from here; a profile that stored its own copy of the library folder
 * would go stale the moment the folder moved. [backgroundSettings]/[dictionarySettings]/
 * [stageMonitorSettings] have no such "one per install" fields, so they are carried wholesale.
 *
 * Deliberately excludes every field [ScreenAssignment] carries about the output's own identity or
 * physical wiring -- which monitor, key output, NDI/Browser Source network settings, names. Those
 * stay one per output even while it follows a shared profile.
 */
@Serializable
data class OutputProfile(
    val id: String = "",
    val name: String = "",
    val displayMode: String = "fullscreen",
    val bibleMode: String = Constants.SONG_LANG_BOTH,
    val bibleTranslations: List<Int> = emptyList(),
    val songMode: String = Constants.SONG_LANG_BOTH,
    val songTranslations: List<Int> = emptyList(),
    val showPictures: Boolean = true,
    val showMedia: Boolean = true,
    val showSubtitles: Boolean = true,
    val showStreaming: Boolean = true,
    val showAnnouncements: Boolean = true,
    val showWebsite: Boolean = true,
    val songLookAhead: Boolean = false,
    val showChords: Boolean = true,
    val showQA: Boolean = true,
    val showSTT: Boolean = true,
    val showDictionary: Boolean = true,
    val showCanvas: Boolean = true,
    val showFullscreenBackground: Boolean = true,
    val showLowerThirdBackground: Boolean = true,
    val showBibleBackground: Boolean = true,
    val showSongsBackground: Boolean = true,
    /**
     * The shape the Profiles tab previews this profile at -- a profile is not tied to one output's
     * real size, so this is a stand-in the operator picks (their real screen's resolution, say),
     * not anything an output is actually set to. Defaults to 1080p.
     */
    val previewWidth: Int = 1920,
    val previewHeight: Int = 1080,
    /**
     * Which background surfaces this profile carries its own copy of, by [BackgroundSurface] name.
     *
     * Empty -- the default -- means every surface follows the Background tab, so a fresh profile
     * inherits the house background rather than starting black. See [resolveBackgroundSurfaces].
     */
    val backgroundOverrides: Set<String> = emptySet(),
    val stageMonitorSettings: StageMonitorSettings = StageMonitorSettings(),
    val bibleSettings: BibleSettings = BibleSettings(),
    val songSettings: SongSettings = SongSettings(),
    val backgroundSettings: BackgroundSettings = BackgroundSettings(),
) {
    val showBible: Boolean get() = bibleMode != Constants.SONG_LANG_OFF
    val showSongs: Boolean get() = songMode != Constants.SONG_LANG_OFF

    /** The languages this profile draws, in order, for a song carrying [available] of them. */
    fun songLanguages(available: Int): List<Int> = songLanguageSelection(songMode, songTranslations, available)

    /**
     * True if [displayMode] is a lower-third band.
     *
     * Still accepts the legacy `DISPLAY_MODE_LOWER_THIRD_VERTICAL`, which a hand-edited document
     * could carry. Nothing has ever written it -- see [isLowerThirdVertical].
     */
    val isLowerThird: Boolean
        get() = displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL ||
            displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL

    /**
     * Whether this band stacks its parallel translations instead of setting them side by side.
     *
     * Derived from the shape, not chosen. The presenters use this purely as a text-stacking flag --
     * "same band geometry for horizontal and vertical", as `BiblePresenter` and `SongPresenter`
     * both put it -- so the question it answers is really "is there width here to split?", and a
     * portrait output answers that itself. It was a display mode of its own for a while, and in all
     * that time nothing anywhere could set it: the orientation control it needed was never built,
     * so the whole vertical branch was unreachable.
     *
     * Read off the profile's own preview shape rather than a live output's, so that what the
     * Profiles tab draws and what the screen draws cannot disagree -- the operator sets that shape
     * themselves, and a profile pointed at a portrait screen is what asks for a stacked band.
     */
    val isLowerThirdVertical: Boolean get() = isLowerThird && previewHeight > previewWidth
}

/**
 * Which of [count] stacked translations this profile actually draws, as positions in the stack.
 *
 * [bibleTranslations] is a list of positions where **empty means all of them**, including any added
 * later, so an untouched profile follows the stack rather than freezing it. Positions past the end
 * are ignored rather than counted: a settings file can outlive the translations it names.
 *
 * One definition because the preview and the screen must not disagree about it -- the Profiles
 * tab's preview drew the whole stack whatever the picker said, so an operator narrowing an output
 * to one translation watched all four keep appearing while the output itself was already correct.
 */
fun OutputProfile.bibleTranslationPositions(count: Int): List<Int> =
    if (bibleTranslations.isEmpty()) (0 until count).toList()
    else bibleTranslations.filter { it in 0 until count }
