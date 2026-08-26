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
    val browserSourceApiKeyRequired: Boolean = false, // only used by ProjectionSettings.browserSourceOutputs entries
    val browserSourceEnabled: Boolean = true, // only used by ProjectionSettings.browserSourceOutputs entries
    val browserSourceWidth: Int = 1920, // only used by ProjectionSettings.browserSourceOutputs entries
    val browserSourceHeight: Int = 1080, // only used by ProjectionSettings.browserSourceOutputs entries
    val browserSourceFps: Int = 30 // max sampling fps; only changed frames are actually encoded
) {
    /** The key of the monitor this output drives, or blank when it drives none. */
    val targetScreenKey: String
        get() = if (targetType != "screen") ""
        else screenKey(targetBoundsX, targetBoundsY, targetBoundsW, targetBoundsH)

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
}
