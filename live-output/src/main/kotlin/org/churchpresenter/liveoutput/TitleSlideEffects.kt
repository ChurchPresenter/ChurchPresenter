package org.churchpresenter.liveoutput

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.presenter.BibleBandClock
import org.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.presenter.BibleLottieTemplate
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.profileFor
import org.churchpresenter.settings.titleSlideLottie
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting

/**
 * The title slide's own Lottie (#971) that the title clock is timed against: the first an output
 * uses, through each screen's profile -- the full screen's or the lower third's, as the profile is.
 * Null when no output plays one, and the title slide is drawn as it always was.
 */
fun titleSlideLottiePath(settings: AppSettings): String? {
    val proj = settings.projectionSettings
    return proj.screenAssignments
        .mapNotNull { proj.profileFor(it) }
        .map { it.songSettings.titleSlideLottie(it.isLowerThird) }
        .firstOrNull { it.isNotBlank() }
}

/** Whether [section] is a song's title slide. */
internal val LyricSection.isTitleSlide: Boolean get() = type == Constants.SECTION_TYPE_TITLE_SLIDE

/** Whether the title slide is what the song on air shows now. */
internal fun PresenterManager.showsTitleSlide(): Boolean =
    slideContent.value == Presenting.LYRICS && displayedLyricSection.value.isTitleSlide

/** Moves the title slide's clock -- see [LiveSongs.titleSlideClock]. */
internal fun PresenterManager.setTitleSlideClock(clock: BibleBandClock) {
    songs.titleSlideClockState.value = clock
}

/** Plays one phase on the title slide's clock over [durationMs], publishing every frame -- see [runBandPhase]. */
internal suspend fun PresenterManager.runTitlePhase(phase: BibleBandPhase, durationMs: Long) {
    setTitleSlideClock(BibleBandClock(phase, 0f))
    val anim = Animatable(0f)
    anim.animateTo(1f, tween(durationMillis = durationMs.toInt().coerceAtLeast(1), easing = LinearEasing)) {
        setTitleSlideClock(BibleBandClock(phase, this.value))
    }
    setTitleSlideClock(BibleBandClock(phase, 1f))
}

/**
 * Plays the title slide's Lottie out -- `text_out` then `bg_out` -- and leaves its clock idle. False,
 * and nothing played, when [template] is null or the title slide is not what is on screen.
 */
internal suspend fun PresenterManager.playTitleSlideOut(template: BibleLottieTemplate?): Boolean {
    if (template == null || !showsTitleSlide()) return false
    if (titleSlideClock.value.phase != BibleBandPhase.IDLE) {
        runTitlePhase(
            BibleBandPhase.EXIT,
            template.segmentMs(BibleLottieTemplate.SEGMENT_TEXT_OUT, BibleLottieTemplate.SEGMENT_BG_OUT),
        )
    }
    setTitleSlideClock(BibleBandClock(BibleBandPhase.IDLE, 0f))
    return true
}

/**
 * Plays the title slide's Lottie in -- `bg_in` then `text_in`, settling on its hold -- each time a
 * title slide comes on screen, however it got there: Go Live, a step back onto it, the next song's.
 * Its way out is played where the section changes ([applySongTarget]) and on clear, before the
 * title slide leaves the screen.
 */
@Composable
internal fun TitleSlideEffects(presenterManager: PresenterManager, template: BibleLottieTemplate?) {
    LaunchedEffect(presenterManager, template) {
        if (template == null) return@LaunchedEffect
        snapshotFlow { presenterManager.displayedLyricSection.value.takeIf { presenterManager.showsTitleSlide() } }
            .collect { title ->
                if (title == null) return@collect
                presenterManager.runTitlePhase(
                    BibleBandPhase.ENTER,
                    template.segmentMs(BibleLottieTemplate.SEGMENT_BG_IN, BibleLottieTemplate.SEGMENT_TEXT_IN),
                )
                presenterManager.setTitleSlideClock(BibleBandClock(BibleBandPhase.HOLD, 1f))
            }
    }
}
