package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

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
    val showBible: Boolean get() = bibleMode != Constants.SONG_LANG_OFF
    val showSongs: Boolean get() = songMode != Constants.SONG_LANG_OFF

    /** True if [displayMode] is either lower-third band orientation (horizontal or vertical). */
    val isLowerThird: Boolean
        get() = displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL ||
            displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL
    val isLowerThirdVertical: Boolean get() = displayMode == Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL

    /** Whether a key output target is configured */
    val hasKeyOutput: Boolean get() = keyTargetDisplay >= 0

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
