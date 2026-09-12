package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.compositionLocalOf

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

    /** The old verse leaving: `text_out`. */
    TEXT_OUT,

    /** The new verse arriving: `text_in` again, with the text already swapped. */
    TEXT_IN,

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
 */
val LocalLottieBandClock = compositionLocalOf { BibleBandClock() }

/**
 * The lyric line the song band shows — the driver's, which lags the selected line while the old
 * one plays out. Negative means "the presenter's own line index", which a preview passes through.
 */
val LocalBandSongLineIndex = compositionLocalOf { -1 }
