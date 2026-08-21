package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.app.churchpresenter.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment

/**
 * Whether what is going live right now is genuinely a *multi-language* presentation.
 *
 * Two halves are always required, and either one alone is a false positive: content that carries a
 * second language shown on outputs set to the primary only is not bilingual worship, and an output
 * set to show everything is not bilingual when the content only ever had one language.
 *
 * Outputs with no display chosen are skipped throughout, for the same reason they are in
 * [LiveMapReporter.setupFacts] — the operator has switched them off.
 */
internal fun ScreenAssignment.isLiveOutput(): Boolean = targetDisplay != Constants.KEY_TARGET_NONE

/**
 * True when [song] carries a second language and some live output is set to show it.
 *
 * Each output's `songMode` is the whole answer rather than one input to it: every real call site
 * passes it to `SongPresenter` as `languageOverride`, which takes precedence over the global
 * fullscreen/lower-third language setting whenever it is set, and it always is.
 */
internal fun isDualLanguagePresentation(song: SongItem, outputs: List<ScreenAssignment>): Boolean {
    if (song.secondaryLyrics.isEmpty()) return false
    return outputs.any {
        it.isLiveOutput() &&
            (it.songMode == Constants.SONG_LANG_BOTH || it.songMode == Constants.SONG_LANG_SECONDARY)
    }
}

/**
 * True when the Bible stack holds at least two translations and some live output shows two or more
 * of them at once.
 *
 * An output's `bibleTranslations` is a list of positions in the stack; empty means "all of them,
 * including any added later", so an empty list shows the whole stack. Positions past the end of the
 * stack are ignored rather than counted — a settings file can outlive the translations it names.
 *
 * @param translationCount size of `BibleSettings.translationList()`.
 */
internal fun isMultiTranslationPresentation(translationCount: Int, outputs: List<ScreenAssignment>): Boolean {
    if (translationCount < 2) return false
    return outputs.any { output ->
        if (!output.isLiveOutput() || !output.showBible) return@any false
        val shown =
            if (output.bibleTranslations.isEmpty()) translationCount
            else output.bibleTranslations.count { it in 0 until translationCount }
        shown >= 2
    }
}

/**
 * True when [song] carries chords and they are actually being drawn for the platform.
 *
 * Chords reach only one surface — the stage monitor, gated by `StageMonitorSettings.showChords`
 * (see `StageMonitorScreen`) — so a chord-carrying song is a chord chart in use only when a live
 * output is in stage-monitor mode and that switch is on. Without both, the same song is just lyrics.
 *
 * @param showChords `StageMonitorSettings.showChords`.
 */
internal fun isChordChartPresentation(
    song: SongItem,
    showChords: Boolean,
    outputs: List<ScreenAssignment>,
): Boolean {
    if (!showChords || song.lyrics.none { ChordTransposer.hasChords(it) }) return false
    return outputs.any { it.isLiveOutput() && it.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR }
}

/**
 * True when the live outputs are set up to show *different* content to different rooms — the
 * split-screen setup: one translation on one screen and a different one on the next, rather than
 * every screen carrying the same thing.
 *
 * Measured as "the live outputs do not all agree": two or more distinct selections among the
 * outputs that are showing this content type at all. An output switched off, or one showing nothing
 * of this type, is not a disagreement — it is an absence, and would otherwise report every ordinary
 * single-screen setup that happens to have a spare output configured.
 */
internal fun isSplitScreenBible(translationCount: Int, outputs: List<ScreenAssignment>): Boolean {
    if (translationCount < 2) return false
    val selections = outputs
        .filter { it.isLiveOutput() && it.showBible }
        .map { output ->
            val chosen =
                if (output.bibleTranslations.isEmpty()) (0 until translationCount).toList()
                else output.bibleTranslations.filter { it in 0 until translationCount }
            chosen.toSet()
        }
        .filter { it.isNotEmpty() }
    return selections.distinct().size >= 2
}

/**
 * True when the live outputs disagree about which song language they show — the same split-screen
 * setup as [isSplitScreenBible], for songs. `songMode` is per output and is what actually reaches
 * `SongPresenter`, so two outputs with different modes are showing two different things.
 */
internal fun isSplitScreenSong(outputs: List<ScreenAssignment>): Boolean =
    outputs.filter { it.isLiveOutput() && it.showSongs }
        .map { it.songMode }
        .distinct().size >= 2

/**
 * Whether this install has somewhere real to put content in front of an audience.
 *
 * The point of the check is that "went live" on its own proves nothing: an operator can press go
 * live on a laptop with no projector attached and see it only in the preview panel. That is exactly
 * the install that looks active and has never actually shown anything to anyone, which is the case
 * worth being able to see.
 *
 * A screen output needs a *second* display — with one, there is no audience screen, only the
 * operator's own. A DeckLink output needs a device fitted. Browser Source outputs are deliberately
 * not counted: nothing proves anything is receiving them.
 */
internal fun hasAudienceOutput(
    outputs: List<ScreenAssignment>,
    screenCount: Int,
    deckLinkDeviceCount: Int,
): Boolean = outputs.any {
    if (!it.isLiveOutput()) false
    else if (it.targetType == Constants.TARGET_TYPE_DECKLINK) deckLinkDeviceCount > 0
    else screenCount >= 2
}
