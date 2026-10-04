package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.AnnouncementsSettings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.operatorSongSettings
import org.churchpresenter.settings.operatorBibleSettings
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.app.churchpresenter.presenter.BibleBandClock
import org.churchpresenter.app.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.app.churchpresenter.presenter.BibleLottieTemplate
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.app.churchpresenter.presenter.rememberBibleLottieTemplate
import org.churchpresenter.app.churchpresenter.viewmodel.DisplayedSongPosition
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
    appSettings.operatorSongSettings().lowerThirdDisplayMode == Constants.SONG_DISPLAY_MODE_LINE
fun templateFor(mode: Presenting): BibleLottieTemplate? = when (mode) {
    Presenting.BIBLE -> bibleTemplate
    Presenting.LYRICS -> songTemplate
    else -> null
}

val clearRequested by presenterManager.clearDisplayRequested
LaunchedEffect(clearRequested) {
    if (clearRequested) clearDisplay(presenterManager, appSettings, templateFor(presenterManager.presentingMode.value))
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
    var applied: SongBandTarget? = null
    snapshotFlow {
        SongBandTarget(
            presenterManager.lyricSection.value,
            presenterManager.lyricSectionVersion.value,
            presenterManager.bandLineIndex(songBandLineMode),
            presenterManager.liveSongPosition(),
        )
    }.collect { target ->
        // A move within what is already displayed -- a line the band does not draw, or the
        // section list re-sent -- only follows the position: no swap, and no reset of a fade.
        if (applied?.copy(position = target.position) == target) {
            presenterManager.setDisplayedSongPosition(target.position)
        } else {
            presenterManager.applySongTarget(target, songTemplate)
        }
        applied = target
    }
}

LaunchedEffect(selectedImagePath) {
    animatePictureChange(presenterManager, selectedImagePath, animationType, transitionDuration)
}

LaunchedEffect(Unit) {
    presenterManager.runPresentationClock()
}

LaunchedEffect(selectedSlide) {
    animateSlideChange(presenterManager, selectedSlide, animationType, transitionDuration)
}

LaunchedEffect(announcementText) {
    showAnnouncementText(
        presenterManager,
        announcementText,
        appSettings.announcementsSettings,
    )
}
}

/** What the Bible band should be showing, and whether hold is staging the selection instead. */
internal data class BibleBandTarget(val verses: List<SelectedVerse>, val hold: Boolean)

/**
 * What the song band should be showing. The version makes re-picking the same section a change;
 * [position] is published with the section, so no output pairs one with the other's place.
 */
internal data class SongBandTarget(
    val section: LyricSection,
    val version: Int,
    val lineIndex: Int,
    val position: DisplayedSongPosition,
)

/** Moves the output to [selectedImagePath] by the configured picture transition. */
private suspend fun animatePictureChange(
    presenterManager: PresenterManager,
    selectedImagePath: String?,
    animationType: AnimationType,
    transitionDuration: Int,
) {
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

/** Moves the output to [selectedSlide] by the configured slide transition. */
private suspend fun animateSlideChange(
    presenterManager: PresenterManager,
    selectedSlide: ImageBitmap?,
    animationType: AnimationType,
    transitionDuration: Int,
) {
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

/**
 * Puts [announcementText] up -- fading it in or out where the animation fades -- and, for a finite
 * loop, takes it down again once it has played.
 */
private suspend fun showAnnouncementText(
    presenterManager: PresenterManager,
    announcementText: String,
    annSettings: AnnouncementsSettings,
) {
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

/**
 * Takes the live content down: the band plays its exit when [template] is one and the mode is not
 * locked to a screen, the classic outputs fade where their settings fade, and then nothing is live.
 */
private suspend fun clearDisplay(
    presenterManager: PresenterManager,
    appSettings: AppSettings,
    template: BibleLottieTemplate?,
) {
    val mode = presenterManager.presentingMode.value
    val modeIsLocked = isAnyScreenLockedTo(presenterManager.screenLocks.value, mode)
    // The fade is one clock for every output, so it follows the profile the main window does.
    val operatorBible = appSettings.operatorBibleSettings()
    val operatorSongs = appSettings.operatorSongSettings()
    if (template != null && !modeIsLocked) {
        presenterManager.runBandPhase(
            BibleBandPhase.EXIT,
            template.segmentMs(BibleLottieTemplate.SEGMENT_TEXT_OUT, BibleLottieTemplate.SEGMENT_BG_OUT),
        )
        presenterManager.setLottieBandClock(BibleBandClock(BibleBandPhase.IDLE, 0f))
    } else if (shouldFadeOnClear(mode, modeIsLocked, operatorBible, operatorSongs)) {
        val duration = fadeOutDuration(mode, operatorBible, operatorSongs)
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
