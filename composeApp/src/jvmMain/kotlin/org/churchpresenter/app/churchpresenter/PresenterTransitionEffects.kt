package org.churchpresenter.app.churchpresenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.BandOutgoing
import org.churchpresenter.app.churchpresenter.presenter.BibleBandClock
import org.churchpresenter.app.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.app.churchpresenter.presenter.BibleLottieTemplate
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.rememberBibleLottieTemplate
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager

/**
 * The cross-fades and slide transitions that move selected content to displayed content: the fade
 * on clear, and the per-type animations for verses, lyrics, pictures, slides and announcements.
 *
 * Split out of `PresenterWindows`, which cannot be composed in a test — it reads
 * `GraphicsEnvironment` and builds AWT windows. None of that is needed here: these are ordinary
 * effects over `PresenterManager` state, so on their own they are testable.
 */
@Composable
internal fun PresenterTransitionEffects(
    presenterManager: PresenterManager,
    appSettings: AppSettings,
) {
    val selectedImagePath by presenterManager.selectedImagePath
    val selectedSlide by presenterManager.selectedSlide
    val animationType by presenterManager.animationType
    val transitionDuration by presenterManager.transitionDuration
    val announcementText by presenterManager.announcementText
// The Lottie lower-third band, when one is configured for the Bible or for songs: its entrance
// on Go Live, its text swap on a verse, section or line change and its exit on clear are all
// played from here, so every output — and the live preview — reads one clock and stays in step.
// A null template means the classic band and the code it had.
val bibleTemplate by rememberBibleLottieTemplate(lottieBandPath(appSettings, Presenting.BIBLE).orEmpty())
val songTemplate by rememberBibleLottieTemplate(lottieBandPath(appSettings, Presenting.LYRICS).orEmpty())
val presentingMode by presenterManager.presentingMode
// Whether the selected line is part of what the song band shows. Outside line mode the band draws
// the whole section, so picking a different line changes nothing on the output — and a swap driven
// by it crossfades the section to an identical copy of itself.
val songBandLineMode =
    appSettings.songSettings.lowerThirdDisplayMode == Constants.SONG_DISPLAY_MODE_LINE
fun templateFor(mode: Presenting): BibleLottieTemplate? = when (mode) {
    Presenting.BIBLE -> bibleTemplate
    Presenting.LYRICS -> songTemplate
    else -> null
}

val clearRequested by presenterManager.clearDisplayRequested
LaunchedEffect(clearRequested) {
    if (!clearRequested) return@LaunchedEffect
    val mode = presenterManager.presentingMode.value
    val modeIsLocked = isAnyScreenLockedTo(presenterManager.screenLocks.value, mode)
    val template = templateFor(mode)
    if (template != null && !modeIsLocked) {
        presenterManager.runBandPhase(
            BibleBandPhase.EXIT,
            template.segmentMs(BibleLottieTemplate.SEGMENT_TEXT_OUT, BibleLottieTemplate.SEGMENT_BG_OUT),
        )
        presenterManager.setLottieBandClock(BibleBandClock(BibleBandPhase.IDLE, 0f))
    } else if (shouldFadeOnClear(mode, modeIsLocked, appSettings.bibleSettings, appSettings.songSettings)) {
        val duration = fadeOutDuration(mode, appSettings.bibleSettings, appSettings.songSettings)
        val anim = Animatable(1f)
        anim.animateTo(0f, tween(durationMillis = duration)) {
            when (mode) {
                Presenting.BIBLE -> presenterManager.setBibleTransitionAlpha(this.value)
                Presenting.LYRICS -> presenterManager.setSongTransitionAlpha(this.value)
                else -> {}
            }
        }
    }
    presenterManager.setPresentingMode(Presenting.NONE)
}

LaunchedEffect(presentingMode, bibleTemplate, songTemplate) {
    val template = templateFor(presentingMode)
    if (template == null) {
        // A screen locked to the content keeps its band up while the rest of the outputs move on.
        val locks = presenterManager.screenLocks.value
        val anyBandLocked = isAnyScreenLockedTo(locks, Presenting.BIBLE) ||
            isAnyScreenLockedTo(locks, Presenting.LYRICS)
        if (!anyBandLocked) presenterManager.setLottieBandClock(BibleBandClock(BibleBandPhase.IDLE, 0f))
        return@LaunchedEffect
    }
    when (presentingMode) {
        Presenting.BIBLE -> {
            presenterManager.setDisplayedVerses(presenterManager.selectedVerses.value)
            presenterManager.setBibleTransitionAlpha(1f)
        }
        Presenting.LYRICS -> {
            presenterManager.setDisplayedLyricSection(presenterManager.lyricSection.value)
            presenterManager.setBandSongLineIndex(presenterManager.bandLineIndex(songBandLineMode))
            presenterManager.setSongTransitionAlpha(1f)
        }
        else -> Unit
    }
    presenterManager.runBandPhase(
        BibleBandPhase.ENTER,
        template.segmentMs(BibleLottieTemplate.SEGMENT_BG_IN, BibleLottieTemplate.SEGMENT_TEXT_IN),
    )
    presenterManager.setLottieBandClock(BibleBandClock(BibleBandPhase.HOLD, 1f))
}

// Both bands are driven by a collector rather than by a content-keyed LaunchedEffect: a swap owns
// the shared band clock for as long as it runs, and an effect restarting on one of its keys would
// cancel it mid-animation and replay it from the first frame. Reading the inputs inside snapshotFlow
// also means the several writes one operator action makes — a section and its line index — arrive
// as ONE target rather than as one restart each.
//
// collect, not collectLatest: a change arriving mid-swap waits. The fade in flight runs to its end
// and the band settles before the next one starts, because cutting a fade off part-way reads as the
// words jumping rather than changing. snapshotFlow conflates while the collector is busy and re-reads
// when it resumes, so a pile-up of changes lands on one crossfade to the newest — the band never
// walks through every verse an operator skimmed past.
LaunchedEffect(presenterManager, bibleTemplate) {
    snapshotFlow {
        BibleBandTarget(presenterManager.selectedVerses.value, presenterManager.bibleHold.value)
    }.collect { target -> presenterManager.applyBibleTarget(target, bibleTemplate) }
}

LaunchedEffect(presenterManager, songTemplate, songBandLineMode) {
    snapshotFlow {
        SongBandTarget(
            presenterManager.lyricSection.value,
            presenterManager.lyricSectionVersion.value,
            presenterManager.bandLineIndex(songBandLineMode),
        )
    }.collect { target -> presenterManager.applySongTarget(target, songTemplate) }
}

LaunchedEffect(selectedImagePath) {
    val current = presenterManager.displayedImagePath.value
    when {
        current == null || animationType == AnimationType.NONE -> {
            presenterManager.setDisplayedImagePath(selectedImagePath)
            presenterManager.setPictureTransitionAlpha(1f)
            presenterManager.setPreviousDisplayedImagePath(null)
        }
        animationType == AnimationType.FADE -> {
            val halfDuration = transitionDuration / 2
            val anim = Animatable(1f)
            anim.animateTo(0f, tween(halfDuration)) {
                presenterManager.setPictureTransitionAlpha(value)
            }
            presenterManager.setDisplayedImagePath(selectedImagePath)
            anim.animateTo(1f, tween(halfDuration)) {
                presenterManager.setPictureTransitionAlpha(value)
            }
        }
        animationType == AnimationType.CROSSFADE -> {
            presenterManager.setPreviousDisplayedImagePath(current)
            presenterManager.setDisplayedImagePath(selectedImagePath)
            presenterManager.setPictureTransitionAlpha(0f)
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(transitionDuration)) {
                presenterManager.setPictureTransitionAlpha(value)
            }
            presenterManager.setPreviousDisplayedImagePath(null)
        }
        animationType == AnimationType.SLIDE_LEFT || animationType == AnimationType.SLIDE_RIGHT -> {
            presenterManager.setPreviousDisplayedImagePath(current)
            presenterManager.setDisplayedImagePath(selectedImagePath)
            presenterManager.setPictureTransitionAlpha(1f)
            presenterManager.setPictureSlideOffset(0f)
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(transitionDuration)) {
                presenterManager.setPictureSlideOffset(value)
            }
            presenterManager.setPreviousDisplayedImagePath(null)
            presenterManager.setPictureSlideOffset(1f)
        }
    }
}

LaunchedEffect(Unit) {
    presenterManager.runPresentationClock()
}

LaunchedEffect(selectedSlide) {
    val current = presenterManager.displayedSlide.value
    when {
        current == null || animationType == AnimationType.NONE -> {
            presenterManager.setDisplayedSlide(selectedSlide)
            presenterManager.setSlideTransitionAlpha(1f)
            presenterManager.setPreviousDisplayedSlide(null)
        }
        animationType == AnimationType.FADE -> {
            val halfDuration = transitionDuration / 2
            val anim = Animatable(1f)
            anim.animateTo(0f, tween(halfDuration)) {
                presenterManager.setSlideTransitionAlpha(value)
            }
            presenterManager.setDisplayedSlide(selectedSlide)
            anim.animateTo(1f, tween(halfDuration)) {
                presenterManager.setSlideTransitionAlpha(value)
            }
        }
        animationType == AnimationType.CROSSFADE -> {
            presenterManager.setPreviousDisplayedSlide(current)
            presenterManager.setDisplayedSlide(selectedSlide)
            presenterManager.setSlideTransitionAlpha(0f)
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(transitionDuration)) {
                presenterManager.setSlideTransitionAlpha(value)
            }
            presenterManager.setPreviousDisplayedSlide(null)
        }
        animationType == AnimationType.SLIDE_LEFT || animationType == AnimationType.SLIDE_RIGHT -> {
            presenterManager.setPreviousDisplayedSlide(current)
            presenterManager.setDisplayedSlide(selectedSlide)
            presenterManager.setSlideTransitionAlpha(1f)
            presenterManager.setSlideSlideOffset(0f)
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(transitionDuration)) {
                presenterManager.setSlideSlideOffset(value)
            }
            presenterManager.setPreviousDisplayedSlide(null)
            presenterManager.setSlideSlideOffset(1f)
        }
    }
}

LaunchedEffect(announcementText) {
    val annSettings = appSettings.announcementsSettings
    val isFade = isFadeAnnouncement(annSettings.animationType)
    val wasEmpty = presenterManager.displayedAnnouncementText.value.isEmpty()
    val fadeDuration = 500
    val sliderSum = 30500L // 500 + 30000, matches AnnouncementsTab speed slider
    val loopCount = annSettings.loopCount

    if (isSlidingAnnouncement(annSettings.animationType)) {
        presenterManager.setDisplayedAnnouncementText(announcementText)
        presenterManager.setAnnouncementTransitionAlpha(1f)
    } else if (announcementText.isEmpty()) {
        if (shouldFadeOutAnnouncement(isFade, wasEmpty)) {
            val anim = Animatable(1f)
            anim.animateTo(0f, tween(fadeDuration)) {
                presenterManager.setAnnouncementTransitionAlpha(value)
            }
        }
        presenterManager.setDisplayedAnnouncementText("")
        presenterManager.setAnnouncementTransitionAlpha(1f)
    } else {
        presenterManager.setDisplayedAnnouncementText(announcementText)

        if (isFade) {
            presenterManager.setAnnouncementTransitionAlpha(0f)
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(fadeDuration)) {
                presenterManager.setAnnouncementTransitionAlpha(value)
            }
        } else {
            presenterManager.setAnnouncementTransitionAlpha(1f)
        }

        if (isFiniteAnnouncementLoop(loopCount)) {
            delay(announcementDisplayMs(sliderSum, annSettings.animationDuration.toLong(), loopCount))

            if (isFade) {
                val anim = Animatable(1f)
                anim.animateTo(0f, tween(fadeDuration)) {
                    presenterManager.setAnnouncementTransitionAlpha(value)
                }
            }
            presenterManager.setAnnouncementText("")
            presenterManager.setDisplayedAnnouncementText("")
            presenterManager.requestClearDisplay()
        }
    }
}
}

/** Plays one band phase from [from] to 1 over [durationMs], publishing every frame to the clock. */
private suspend fun PresenterManager.runBandPhase(phase: BibleBandPhase, durationMs: Long) {
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

/** What the Bible band should be showing, and whether hold is staging the selection instead. */
private data class BibleBandTarget(val verses: List<SelectedVerse>, val hold: Boolean)

/** What the song band should be showing. The version makes re-picking the same section a change. */
private data class SongBandTarget(val section: LyricSection, val version: Int, val lineIndex: Int)

/** The line the band is on, or [WHOLE_SECTION] when it is not showing one line at a time. */
private fun PresenterManager.bandLineIndex(lineMode: Boolean): Int =
    if (lineMode) songDisplayLineIndex.value else WHOLE_SECTION

/** No single line: the band is drawing the section entire, so the selected line does not reach it. */
private const val WHOLE_SECTION = -1

/** Moves the Bible band — and the classic band's displayed verses — onto [target]. */
private suspend fun PresenterManager.applyBibleTarget(target: BibleBandTarget, template: BibleLottieTemplate?) {
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
private suspend fun PresenterManager.applySongTarget(target: SongBandTarget, template: BibleLottieTemplate?) {
    val animating = template?.takeIf {
        val settled = target.section == displayedLyricSection.value && target.lineIndex == bandSongLineIndex.value
        presentingMode.value == Presenting.LYRICS && bandIsUp() && !settled
    }
    if (animating == null) {
        setDisplayedLyricSection(target.section)
        setBandSongLineIndex(target.lineIndex)
        setSongTransitionAlpha(1f)
        return
    }
    val outgoing = BandOutgoing(
        lyricSection = displayedLyricSection.value,
        lyricLineIndex = bandSongLineIndex.value,
    )
    swapBandText(animating, outgoing) {
        setDisplayedLyricSection(target.section)
        setBandSongLineIndex(target.lineIndex)
    }
}
