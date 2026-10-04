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
 * The reserved id an output picks to show nothing at all -- see [BLANK_OUTPUT_PROFILE]. No stored
 * profile can collide with it: new ones are numbered `profileN` (see [newOutputProfile]).
 */
const val BLANK_OUTPUT_PROFILE_ID = "blank"

/**
 * What an output on [BLANK_OUTPUT_PROFILE_ID] renders with: every content type and every background
 * off, so it stays black -- or transparent over Browser Source and NDI. Built in rather than stored
 * in [ProjectionSettings.outputProfiles], so it cannot be edited, linked or deleted.
 */
val BLANK_OUTPUT_PROFILE = OutputProfile(
    id = BLANK_OUTPUT_PROFILE_ID,
    bibleMode = Constants.SONG_LANG_OFF,
    songMode = Constants.SONG_LANG_OFF,
    showPictures = false,
    showMedia = false,
    showSubtitles = false,
    showStreaming = false,
    showAnnouncements = false,
    showWebsite = false,
    showChords = false,
    showQA = false,
    showSTT = false,
    showDictionary = false,
    showCanvas = false,
    showFullscreenBackground = false,
    showLowerThirdBackground = false,
    showBibleBackground = false,
    showSongsBackground = false,
)

/**
 * A named, reusable bundle of everything that decides how an output behaves: its display mode,
 * what content it shows, and its full Stage Monitor/Bible/Song/Background/captions/Dictionary/Q&A/
 * subtitle appearance.
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
 * would go stale the moment the folder moved. [sttSettings] and [qaSettings] work the same way,
 * keeping [STT_GLOBAL_KEYS]/[QA_GLOBAL_KEYS] (the caption server, the Q&A rate limit) from the
 * document. [backgroundSettings]/[dictionarySettings]/[mediaSettings]/[stageMonitorSettings] have
 * no such "one per install" fields, so they are carried wholesale.
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
    // Whether a lower third, an announcement or captions go up over what is on screen here, or
    // replace it as they always did (the default).
    val lowerThirdOverContent: Boolean = false,
    val announcementsOverContent: Boolean = false,
    val captionsOverContent: Boolean = false,
    val showAnnouncements: Boolean = true,
    val showWebsite: Boolean = true,
    val songLookAhead: Boolean = false,
    val showChords: Boolean = true,
    /**
     * Whether a Browser Source page on this profile carries the musicians' −1 / 0 / +1 transpose
     * buttons. Off by default: the page OBS or vMix loads must never show a control nobody can
     * press, so a band's tablets use their own output on a profile with this on. Only meaningful
     * for a Stage Monitor with [showChords]; screens and NDI outputs have no page to put it on.
     */
    val showTransposeControls: Boolean = false,
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
    /** How live captions look on this output -- see [STT_GLOBAL_KEYS] for what stays install-wide. */
    val sttSettings: STTSettings = STTSettings(),
    /** How a Strong's dictionary card looks on this output. */
    val dictionarySettings: DictionarySettings = DictionarySettings(),
    /** How a Q&A question and its QR code look on this output -- see [QA_GLOBAL_KEYS]. */
    val qaSettings: QASettings = QASettings(),
    /** How video subtitles look on this output. */
    val mediaSettings: MediaSettings = MediaSettings(),
    /** How a picture or slide meets this output's shape: fit, fill or stretch. */
    val pictureScaleMode: OutputScaleMode = OutputScaleMode.FIT,
    /** How a video meets this output's shape: fit, fill or stretch. */
    val mediaScaleMode: OutputScaleMode = OutputScaleMode.FIT,
    /**
     * Where each kind of band-less content sits when [isLowerThird]. Sparse: a kind not named here
     * is [LowerThirdPlacement.FULL_SCREEN], which is what every profile did before this existed, so
     * an existing settings file needs no migration. Read through [placementFor].
     */
    val lowerThirdPlacements: Map<PlaceableContent, LowerThirdPlacement> = emptyMap(),
    /**
     * The master this profile follows, or null for a profile that follows nothing -- a master in
     * its own right, or standalone. One level only: a master is never itself linked.
     *
     * A linked profile still stores every value in full (see `LinkedProfiles.kt`), so the outputs
     * and resolution never need to know it is linked; [overrides] says which of those values are
     * its own rather than its master's.
     */
    val parentId: String? = null,
    /**
     * The settings a linked profile has changed from its master, as paths into the profile's own
     * serialized settings -- `bibleSettings.marginTop`, `bibleSettings.translations[kjv.spb].textFontSize`.
     * Every other value is its master's, rewritten whenever the master changes. Empty on a profile
     * that follows nothing.
     */
    val overrides: Set<String> = emptySet(),
    /**
     * The sections of a linked profile that follow another master than [parentId], by
     * [ProfileSection.id]: a master's id, or [OWN_SECTION] for a section that follows nobody. A
     * section not named here follows [parentId] -- so a file written before sections could follow
     * apart reads as every section following it, exactly as before. Empty on a profile that follows
     * nothing; [parentId] stays the main master, which places the profile in the list and decides its
     * display mode.
     */
    val sectionMasters: Map<String, String> = emptyMap(),
    /**
     * This profile's outputs merged into one picture, or null when each draws on its own -- see
     * `OutputMerge.kt`. Never followed from a master: its members are the outputs following *this*
     * profile, which a linked profile does not share. See `LinkedProfilePaths.IDENTITY_KEYS`.
     */
    val merge: OutputMerge? = null,
) {
    /** Where [content] sits on this profile — always [LowerThirdPlacement.FULL_SCREEN] off a lower third. */
    fun placementFor(content: PlaceableContent): LowerThirdPlacement =
        if (isLowerThird) lowerThirdPlacements[content] ?: LowerThirdPlacement.FULL_SCREEN
        else LowerThirdPlacement.FULL_SCREEN

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
