package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection

/**
 * Where a Bible Lottie band is in its life. The driver in `PresenterTransitionEffects` moves it
 * through these; every output maps the phase onto its own template's markers, so two outputs
 * with different templates stay in step even when their segments differ in length.
 */
enum class BibleBandPhase {
    /** Nothing on screen — before the first Go Live and after the exit has finished. */
    IDLE,

    /** The band and then the text arriving: `bg_in` followed by `text_in`. */
    ENTER,

    /** Pinned on the first hold frame for as long as the verse is up. */
    HOLD,

    /**
     * A crossfade between two verses: the old text plays `text_out` while the new one, already
     * swapped in, plays `text_in` on a layer of its own, over the same span.
     */
    TEXT_SWAP,

    /** Everything leaving: `text_out` then `bg_out`. */
    EXIT,
}

/** A phase and how far through it, 0..1. The default is a settled band, which is what a preview shows. */
data class BibleBandClock(
    val phase: BibleBandPhase = BibleBandPhase.HOLD,
    val progress: Float = 1f,
)

/**
 * The band clock for whatever output is composing, provided by the output content and the live
 * preview around their presenters. The default is a settled band, which is what a settings
 * preview — composed with no output around it — wants to show.
 *
 * It carries the **state holder**, not the value. The clock moves on every animation frame, so an
 * output that unwrapped it and provided the value would subscribe its whole presenter dispatch to a
 * 60 Hz state and recompose a thousand-line composable for each tick — which is what made the band's
 * crossfade drop frames and look steppy. The painter reads the value at draw time, so with the
 * holder provided the animation costs redraws and no recompositions at all.
 */
val LocalLottieBandClock = compositionLocalOf<State<BibleBandClock>> { SETTLED_BAND_CLOCK }

/** The default a settings preview composes against: a band sitting on its hold frame. */
private val SETTLED_BAND_CLOCK: State<BibleBandClock> = mutableStateOf(BibleBandClock())

/**
 * The lyric line the song band shows — the driver's, which lags the selected line while the old
 * one plays out. Negative means "the presenter's own line index", which a preview passes through.
 */
val LocalBandSongLineIndex = compositionLocalOf { -1 }

/**
 * The content a [BibleBandPhase.TEXT_SWAP] is replacing — the words the outgoing layer plays
 * `text_out` with.
 *
 * Published by the driver rather than worked out by each output from watching its own text change.
 * An output that joins mid-swap then crossfades the same two texts as every other one, and an
 * interrupted swap cannot leave a layer playing out the text that was arriving.
 */
data class BandOutgoing(
    val verses: List<SelectedVerse> = emptyList(),
    val lyricSection: LyricSection? = null,
    val lyricLineIndex: Int = -1,
)

/** [BandOutgoing] for whatever output is composing. Empty outside a swap, and in a settings preview. */
val LocalBandOutgoing = compositionLocalOf { BandOutgoing() }
