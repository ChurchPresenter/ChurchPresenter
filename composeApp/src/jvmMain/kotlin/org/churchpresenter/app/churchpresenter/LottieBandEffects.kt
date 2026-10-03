package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import org.churchpresenter.app.churchpresenter.presenter.BandOutgoing
import org.churchpresenter.app.churchpresenter.presenter.BibleBandClock
import org.churchpresenter.app.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.app.churchpresenter.presenter.BibleLottieTemplate
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.app.churchpresenter.presenter.isRestatedAs
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

/** Plays one band phase from [from] to 1 over [durationMs], publishing every frame to the clock. */
internal suspend fun PresenterManager.runBandPhase(phase: BibleBandPhase, durationMs: Long) {
    setLottieBandClock(BibleBandClock(phase, 0f))
    val anim = Animatable(0f)
    anim.animateTo(1f, tween(durationMillis = durationMs.toInt().coerceAtLeast(1), easing = LinearEasing)) {
        setLottieBandClock(BibleBandClock(phase, this.value))
    }
    setLottieBandClock(BibleBandClock(phase, 1f))
}

/** Whether the band is on screen and not on its way out — the only time a text swap is animated. */
private fun PresenterManager.bandIsUp(): Boolean {
    val phase = lottieBandClock.value.phase
    return phase != BibleBandPhase.IDLE && phase != BibleBandPhase.EXIT
}

/**
 * Crossfades to the new text: publishes [outgoing] as the words to play out, applies [swap] and
 * starts the phase that plays the two together, then settles on the hold. The three writes are
 * contiguous, so one recomposition sees all of them and no output ever draws the new text at the
 * settled frame for a frame before the swap begins.
 *
 * The entrance is allowed to land first: both drivers write the one band clock, and a swap
 * starting under a running `ENTER` would fight it for every frame.
 */
private suspend fun PresenterManager.swapBandText(
    template: BibleLottieTemplate,
    outgoing: BandOutgoing,
    swap: () -> Unit,
) {
    snapshotFlow { lottieBandClock.value.phase }.first { it != BibleBandPhase.ENTER }
    setBandOutgoing(outgoing)
    swap()
    runBandPhase(BibleBandPhase.TEXT_SWAP, template.swapMs())
    setLottieBandClock(BibleBandClock(BibleBandPhase.HOLD, 1f))
    setBandOutgoing(BandOutgoing())
}

/** The line the band is on, or [WHOLE_SECTION] when it is not showing one line at a time. */
internal fun PresenterManager.bandLineIndex(lineMode: Boolean): Int =
    if (lineMode) songDisplayLineIndex.value else WHOLE_SECTION

/** Moves the Bible band — and the classic band's displayed verses — onto [target]. */
internal suspend fun PresenterManager.applyBibleTarget(target: BibleBandTarget, template: BibleLottieTemplate?) {
    if (target.hold) return
    val animating = template?.takeIf {
        presentingMode.value == Presenting.BIBLE && bandIsUp() && displayedVerses.value != target.verses
    }
    if (animating == null) {
        setDisplayedVerses(target.verses)
        setBibleTransitionAlpha(1f)
        return
    }
    swapBandText(animating, BandOutgoing(verses = displayedVerses.value)) { setDisplayedVerses(target.verses) }
}

/** Moves the song band — and the classic band's displayed section — onto [target]. */
internal suspend fun PresenterManager.applySongTarget(target: SongBandTarget, template: BibleLottieTemplate?) {
    val animating = template?.takeIf {
        // A section re-sent with only its tuning changed reads the same, and swapping it plays the
        // band's text out and back in over identical words.
        val settled = displayedLyricSection.value.isRestatedAs(target.section) &&
            target.lineIndex == bandSongLineIndex.value
        presentingMode.value == Presenting.LYRICS && bandIsUp() && !settled
    }
    if (animating == null) {
        setDisplayedLyricSection(target.section, target.position)
        setBandSongLineIndex(target.lineIndex)
        setSongTransitionAlpha(1f)
        return
    }
    val outgoing = BandOutgoing(
        lyricSection = displayedLyricSection.value,
        lyricLineIndex = bandSongLineIndex.value,
    )
    swapBandText(animating, outgoing) {
        setDisplayedLyricSection(target.section, target.position)
        setBandSongLineIndex(target.lineIndex)
    }
}

/** No single line: the band is drawing the section entire, so the selected line does not reach it. */
private const val WHOLE_SECTION = -1
