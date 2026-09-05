package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

/**
 * A monitor's identity for the purpose of naming it: its geometry, as `1920x1080@0,0`.
 *
 * Not the device index -- that is a position in a list the window system reorders when a cable
 * moves. Two monitors cannot occupy the same bounds at once, so this tells them apart, and it is
 * the same thing `ScreenAssignment` matches its target on.
 *
 * Bounds that were never resolved (an unassigned slot, a DeckLink device, the dev fallback window)
 * have no geometry and so no key: they answer with the empty string, which
 * [ProjectionSettings.withScreenName] refuses to store against.
 */
fun screenKey(boundsX: Int, boundsY: Int, boundsW: Int, boundsH: Int): String {
    val hasSize = boundsW > 0 && boundsH > 0
    val hasOrigin = boundsX != Int.MIN_VALUE && boundsY != Int.MIN_VALUE
    return if (hasSize && hasOrigin) "${boundsW}x$boundsH@$boundsX,$boundsY" else ""
}

@Serializable
data class ScreenAssignment(
    val targetDisplay: Int = -1,  // -1 = auto (resolved at runtime), -2 = none, 0+ = specific display (legacy)
    val targetType: String = "screen",  // "screen" or "decklink"
    val targetBoundsX: Int = Int.MIN_VALUE,  // screen bounds for reliable mapping (MIN_VALUE = unset)
    val targetBoundsY: Int = Int.MIN_VALUE,
    val targetBoundsW: Int = 0,
    val targetBoundsH: Int = 0,
    val keyTargetDisplay: Int = Constants.KEY_TARGET_NONE,  // -2 = none (disabled), 0+ = specific display/device
    val keyTargetType: String = "screen",  // "screen" or "decklink"
    val keyTargetBoundsX: Int = Int.MIN_VALUE,
    val keyTargetBoundsY: Int = Int.MIN_VALUE,
    val keyTargetBoundsW: Int = 0,
    val keyTargetBoundsH: Int = 0,
    /** Whether this output shows the Bible at all: "off" or "both". It no longer says *which*
     *  translations — see [bibleTranslations]. Songs still use the full primary/secondary vocabulary
     *  in [songMode], which is unrelated. */
    val bibleMode: String = Constants.SONG_LANG_BOTH,
    /**
     * Which translations this output shows, by position in the stack.
     *
     * Empty means all of them, including any added later — the behaviour "both" used to have. A
     * non-empty list is an explicit choice and is left alone when the stack grows.
     */
    val bibleTranslations: List<Int> = emptyList(),
    val songMode: String = Constants.SONG_LANG_BOTH,   // "off" | "primary" | "secondary" | "both"
    /**
     * Which of a song's languages this output shows, by position — `0` being the primary.
     *
     * Empty means "whatever [songMode] says", which is what every output written before songs had
     * more than two languages holds, and what a new one holds until someone picks. That is the only
     * reason [songMode] is still read: the two are not kept in step, the list simply wins when it
     * has anything in it. [songLanguages] is the one place that resolves the pair, and nothing
     * should be deciding it a second time.
     *
     * Shaped after [bibleTranslations] deliberately — the same question, the same answer.
     */
    val songTranslations: List<Int> = emptyList(),
    val showPictures: Boolean = true,
    val showMedia: Boolean = true,
    val showStreaming: Boolean = true,
    val showAnnouncements: Boolean = true,
    val showWebsite: Boolean = true,
    val displayMode: String = "fullscreen", // Constants.DISPLAY_MODE_FULLSCREEN or DISPLAY_MODE_LOWER_THIRD_HORIZONTAL
    val songLookAhead: Boolean = false, // enable look-ahead for songs on this output
    // Whether a chorded song is drawn as a chart on this output, rather than the words alone.
    val showChords: Boolean = true,
    val showQA: Boolean = true,
    val showSTT: Boolean = true,
    val showDictionary: Boolean = true,
    val showCanvas: Boolean = true,
    val showFullscreenBackground: Boolean = true, // show configured background in fullscreen mode
    val showLowerThirdBackground: Boolean = true, // show configured background in lower third mode
    // Both are an additional layer on top of showFullscreenBackground/showLowerThirdBackground.
    val showBibleBackground: Boolean = true,
    val showSongsBackground: Boolean = true,
    /**
     * What the operator calls this Browser Source output — "Stage", "Choir", "Chords".
     *
     * Blank means it has never been renamed, and every label falls back to the numbered default.
     * Stored rather than derived because the number is a position: removing the second of three
     * outputs renumbers the third, and a name the operator chose must survive that.
     *
     * Only used by ProjectionSettings.browserSourceOutputs entries.
     */
    val browserSourceName: String = "",
    /**
     * What the operator calls this output slot when it drives no monitor to hang the name on.
     *
     * The preferred home for a screen's name is [ProjectionSettings.screenNames], keyed by the
     * monitor's own geometry, so that it follows the hardware rather than the row. A row set to
     * None, pointed at a DeckLink device, or standing in as the dev-fallback window has no
     * geometry — and it is still a row the operator wants to label, which is what this is for.
     * Read through [ProjectionSettings.screenLabelOr], which prefers the monitor's name.
     */
    val screenName: String = "",
    /**
     * This output's own Stage Monitor settings, or null to follow the global ones.
     *
     * Null is not "no stage monitor" — it is "the same one everybody else uses". An output only
     * gets an entry here once the operator has customized it, so an install that never opens the
     * Customize dialog carries none of these and keeps behaving exactly as it did.
     */
    val stageMonitorOverride: StageMonitorSettings? = null,
    /**
     * This output's own Bible appearance, or null to follow the global settings.
     *
     * Stored as a whole [BibleSettings] rather than a diff so the settings tab can edit it
     * unchanged, but only its *appearance* is ever read: the library folder, the file list and the
     * translation stack come from the global document — see `BibleSettings.withAppearanceOf`.
     */
    val bibleOverride: BibleSettings? = null,
    /** This output's own Song appearance, or null to follow the global settings. Mirrors
     *  [bibleOverride], including that only its appearance is read. */
    val songOverride: SongSettings? = null,
    /**
     * This output's own Strong's dictionary appearance, or null to follow the global settings.
     *
     * Replaced whole rather than merged: unlike Bible and Song, [DictionarySettings] is nothing but
     * appearance — it names no folder and selects no content — so there is nothing in it that has
     * to stay one per install.
     */
    val dictionaryOverride: DictionarySettings? = null,
    /**
     * This output's own backgrounds, or null to follow the global settings.
     *
     * Replaced whole, like [dictionaryOverride]: [BackgroundSettings] is colours, images, videos and
     * opacities and nothing else. Note this wins over the backgrounds an Instance Link follower
     * mirrors from its primary — a background chosen for *this* screen is a local decision, and the
     * mirror is the fallback for screens that have not made one.
     */
    val backgroundOverride: BackgroundSettings? = null,
    val browserSourceApiKeyRequired: Boolean = false, // only used by ProjectionSettings.browserSourceOutputs entries
    val browserSourceEnabled: Boolean = true, // only used by ProjectionSettings.browserSourceOutputs entries
    val browserSourceWidth: Int = 1920, // only used by ProjectionSettings.browserSourceOutputs entries
    val browserSourceHeight: Int = 1080, // only used by ProjectionSettings.browserSourceOutputs entries
    val browserSourceFps: Int = 30, // max sampling fps; only changed frames are actually encoded
    /**
     * What the operator calls this NDI output — the name receivers see on the network.
     *
     * Blank means it has never been renamed and the numbered default is used, exactly as
     * [browserSourceName] works. Unlike that one this name is also visible outside this app: it is
     * what an OBS or vMix operator picks from a source list, so it is worth their while to set it.
     *
     * Only used by ProjectionSettings.ndiOutputs entries.
     */
    val ndiName: String = "",
    val ndiEnabled: Boolean = true, // only used by ProjectionSettings.ndiOutputs entries
    val ndiWidth: Int = 1920, // only used by ProjectionSettings.ndiOutputs entries
    val ndiHeight: Int = 1080, // only used by ProjectionSettings.ndiOutputs entries
    val ndiFps: Int = 30, // only used by ProjectionSettings.ndiOutputs entries
    /**
     * One of `Constants.NDI_MODE_*`. Defaults to alpha, which is the mode worth defaulting to: it
     * is the one SDI cannot do, and it is what makes a lower third arrive in OBS already keyed.
     *
     * Only used by ProjectionSettings.ndiOutputs entries.
     */
    val ndiMode: String = Constants.NDI_MODE_ALPHA,
) {
    /** The key of the monitor this output drives, or blank when it drives none. */
    val targetScreenKey: String
        get() = if (targetType != "screen") ""
        else screenKey(targetBoundsX, targetBoundsY, targetBoundsW, targetBoundsH)

    val showBible: Boolean get() = bibleMode != Constants.SONG_LANG_OFF
    val showSongs: Boolean get() = songMode != Constants.SONG_LANG_OFF

    /**
     * The languages this output draws, in order, for a song carrying [available] of them.
     *
     * Positions the song does not have are dropped rather than drawn blank — an output configured
     * for languages 1 and 3 shows one language when handed a monolingual song, not one language and
     * two empty columns.
     *
     * Falls back to [songMode] while [songTranslations] is empty, which is what keeps every existing
     * output presenting exactly as it did: "primary" is the first language, "secondary" the second,
     * "both" everything the song has. Note "both" reaching past two is deliberate — an output left
     * on the default should show a four-language song's four languages, not silently drop two.
     */
    fun songLanguages(available: Int): List<Int> =
        songLanguageSelection(songMode, songTranslations, available)

    /** True if [displayMode] is either lower-third band orientation (horizontal or vertical). */
    val isLowerThird: Boolean
        get() = displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL ||
            displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL
    val isLowerThirdVertical: Boolean get() = displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL

    /** Whether a key output target is configured */
    val hasKeyOutput: Boolean get() = keyTargetDisplay >= 0

    /**
     * True once this output has appearance of its own rather than the global settings.
     *
     * What the Customize button reads to show that a row has been customized, and what
     * `AppSettings.resolvedFor` short-circuits on so an untouched output renders from the very
     * same settings instance it always did.
     */
    val isCustomized: Boolean
        get() = stageMonitorOverride != null || bibleOverride != null || songOverride != null ||
            dictionaryOverride != null || backgroundOverride != null

    /** Primary window role: "fill" if key output is configured, "normal" otherwise */
    val primaryOutputRole: String get() = if (hasKeyOutput) Constants.OUTPUT_ROLE_FILL else Constants.OUTPUT_ROLE_NORMAL

    /**
     * This Browser Source output's display name: the operator's own if they gave it one, otherwise
     * [default] — the numbered "Browser Source N" label, which is localized and so has to be
     * resolved by the caller.
     *
     * Trimmed, so a name of nothing but spaces reads as no name at all rather than as a blank label
     * on every screen that shows one.
     */
    fun browserSourceLabelOr(default: String): String = browserSourceName.trim().ifBlank { default }

    /**
     * This NDI output's name on the network: the operator's own if they gave it one, otherwise
     * [default] — the numbered "NDI Output N" label, which is localized and so has to be resolved
     * by the caller.
     *
     * Trimmed for the same reason as [browserSourceLabelOr], and it matters more here: a source
     * advertised under a name of nothing but spaces is one an operator cannot pick out of a list.
     */
    fun ndiLabelOr(default: String): String = ndiName.trim().ifBlank { default }
}

/**
 * Which of a song's [available] languages to draw, given a [songMode] and a [songTranslations] list.
 *
 * Free-standing because `SongPresenter` resolves the same question from what it was handed rather
 * than from a [ScreenAssignment] — the preview and the offscreen outputs pass the two values
 * straight in. One implementation, so an output and its preview can never disagree.
 *
 * [songTranslations] wins whenever it has anything in it; [songMode] is the fallback that keeps
 * every output written before songs had more than two languages presenting exactly as it did.
 */
fun songLanguageSelection(songMode: String, songTranslations: List<Int>, available: Int): List<Int> {
    if (songMode == Constants.SONG_LANG_OFF || available <= 0) return emptyList()
    val chosen = when {
        songTranslations.isNotEmpty() -> songTranslations
        songMode == Constants.SONG_LANG_PRIMARY -> listOf(0)
        songMode == Constants.SONG_LANG_SECONDARY -> listOf(1)
        // "both" reaching past two is deliberate: an output left on the default should show a
        // four-language song's four languages, not silently drop two of them.
        else -> List(available) { it }
    }
    // A "secondary" output handed a monolingual song falls back to the primary rather than going
    // blank -- `SongPresenter` has always done this (`mainSecondaryLines.ifEmpty { … }`), and a
    // screen showing nothing because the song was never translated is a worse answer than one
    // showing the words.
    return chosen.filter { it in 0 until available }.distinct().ifEmpty { listOf(0) }
}
