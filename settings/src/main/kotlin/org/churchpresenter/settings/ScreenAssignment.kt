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

/**
 * One output's own identity and physical wiring: which monitor or DeckLink device it drives, its
 * key output, its Browser Source/NDI network settings, and what the operator calls it.
 *
 * **Never carries any behavioral or style state of its own.** How this output looks and what it
 * shows is entirely [activeProfileId]'s -- an [OutputProfile], which every field of that shape used
 * to live on this class directly. Two outputs that should behave identically are kept that way by
 * pointing both at the same profile, not by copying settings between two `ScreenAssignment`s.
 */
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
     * The size a dev fallback window stands for, and reports as its bounds.
     *
     * A dev slot drives no display, so it has no `targetBounds*` to read -- which used to mean it
     * reported the PRIMARY monitor's shape to every preview, a screen nothing is ever sent to.
     * Per slot rather than per app so a single-monitor machine can simulate a 16:9 projector beside
     * a 4:3 foyer TV, which is the whole point of running more than one of them.
     *
     * Ignored by any slot that does drive a display: real bounds win.
     */
    val devWindowWidth: Int = 1920,
    val devWindowHeight: Int = 1080,
    /**
     * One of `Constants.NDI_MODE_*`. Defaults to alpha, which is the mode worth defaulting to: it
     * is the one SDI cannot do, and it is what makes a lower third arrive in OBS already keyed.
     *
     * Only used by ProjectionSettings.ndiOutputs entries.
     */
    val ndiMode: String = Constants.NDI_MODE_ALPHA,
    /**
     * The [OutputProfile] this output follows -- every behavioral and style decision this output
     * draws with comes from there; see `AppSettings.resolvedFor(profile)`
     * (`OutputProfileResolution.kt`) and [ProjectionSettings.profileFor].
     *
     * Nullable only for decode safety against a document written before profiles existed, or one
     * hand-edited into an inconsistent state -- never a real "no profile" choice an operator makes.
     * `SettingsManager`'s migration and every fresh install both guarantee a real profile id here.
     */
    val activeProfileId: String? = null,
) {
    /** The key of the monitor this output drives, or blank when it drives none. */
    val targetScreenKey: String
        get() = if (targetType != "screen") ""
        else screenKey(targetBoundsX, targetBoundsY, targetBoundsW, targetBoundsH)

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

/**
 * Which of a song's [available] languages to draw, given a profile's [songMode] and
 * [songTranslations].
 *
 * Free-standing because `SongPresenter` resolves the same question from what it was handed rather
 * than from an [OutputProfile] -- the preview and the offscreen outputs pass the two values
 * straight in. One implementation, so an output and its preview can never disagree.
 *
 * [songTranslations] wins whenever it has anything in it; [songMode] is the fallback that keeps
 * every profile written before songs had more than two languages presenting exactly as it did.
 */
fun songLanguageSelection(songMode: String, songTranslations: List<Int>, available: Int): List<Int> {
    if (songMode == Constants.SONG_LANG_OFF || available <= 0) return emptyList()
    val chosen = when {
        songTranslations.isNotEmpty() -> songTranslations
        songMode == Constants.SONG_LANG_PRIMARY -> listOf(0)
        songMode == Constants.SONG_LANG_SECONDARY -> listOf(1)
        songMode == Constants.SONG_LANG_THIRD -> listOf(2)
        songMode == Constants.SONG_LANG_FOURTH -> listOf(3)
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
