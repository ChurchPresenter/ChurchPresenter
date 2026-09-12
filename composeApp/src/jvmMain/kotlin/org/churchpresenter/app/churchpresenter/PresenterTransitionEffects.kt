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
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.app.churchpresenter.presenter.BibleBandClock
import org.churchpresenter.app.churchpresenter.presenter.BibleBandPhase
import org.churchpresenter.app.churchpresenter.presenter.BibleLottieTemplate
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.rememberBibleLottieTemplate
import org.churchpresenter.app.churchpresenter.utils.isSongLineMode
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
    val selectedVerses by presenterManager.selectedVerses
    val lyricSection by presenterManager.lyricSection
    val lyricSectionVersion by presenterManager.lyricSectionVersion
    val selectedImagePath by presenterManager.selectedImagePath
    val selectedSlide by presenterManager.selectedSlide
    val animationType by presenterManager.animationType
    val transitionDuration by presenterManager.transitionDuration
    val announcementText by presenterManager.announcementText
// The Bible Lottie band, when one is configured: its entrance on Go Live, its text swap on a
// verse change and its exit on clear are all played from here, so every output — and the live
// preview — reads one clock and stays in step. Null means the classic band and the code it had.
val bandPath = bibleLottieBandPath(appSettings)
val bandTemplate by rememberBibleLottieTemplate(bandPath.orEmpty())
val presentingMode by presenterManager.presentingMode

val clearRequested by presenterManager.clearDisplayRequested
LaunchedEffect(clearRequested) {
    if (!clearRequested) return@LaunchedEffect
    val mode = presenterManager.presentingMode.value
    val modeIsLocked = isAnyScreenLockedTo(presenterManager.screenLocks.value, mode)
    val template = bandTemplate
    if (mode == Presenting.BIBLE && template != null && !modeIsLocked) {
        presenterManager.runBandPhase(
            BibleBandPhase.EXIT,
            template.segmentMs(BibleLottieTemplate.SEGMENT_TEXT_OUT, BibleLottieTemplate.SEGMENT_BG_OUT),
        )
        presenterManager.setBibleBandClock(BibleBandClock(BibleBandPhase.IDLE, 0f))
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

LaunchedEffect(presentingMode, bandTemplate) {
    val template = bandTemplate ?: return@LaunchedEffect
    if (presentingMode != Presenting.BIBLE) {
        // A screen locked to the Bible keeps its band up while the rest of the outputs move on.
        if (!isAnyScreenLockedTo(presenterManager.screenLocks.value, Presenting.BIBLE)) {
            presenterManager.setBibleBandClock(BibleBandClock(BibleBandPhase.IDLE, 0f))
        }
        return@LaunchedEffect
    }
    presenterManager.setDisplayedVerses(presenterManager.selectedVerses.value)
    presenterManager.setBibleTransitionAlpha(1f)
    presenterManager.runBandPhase(
        BibleBandPhase.ENTER,
        template.segmentMs(BibleLottieTemplate.SEGMENT_BG_IN, BibleLottieTemplate.SEGMENT_TEXT_IN),
    )
    presenterManager.setBibleBandClock(BibleBandClock(BibleBandPhase.HOLD, 1f))
}

val bibleHold by presenterManager.bibleHold
LaunchedEffect(selectedVerses, bibleHold) {
    if (bibleHold) return@LaunchedEffect
    val template = bandTemplate
    val phase = presenterManager.bibleBandClock.value.phase
    val animatesChange = template != null &&
        presenterManager.presentingMode.value == Presenting.BIBLE &&
        phase != BibleBandPhase.IDLE && phase != BibleBandPhase.EXIT &&
        presenterManager.displayedVerses.value != selectedVerses
    if (!animatesChange) {
        presenterManager.setDisplayedVerses(selectedVerses)
        presenterManager.setBibleTransitionAlpha(1f)
        return@LaunchedEffect
    }
    // Let the entrance land first. A change that interrupted an earlier change picks up the
    // old verse's exit where it was rather than snapping it back to fully shown.
    snapshotFlow { presenterManager.bibleBandClock.value.phase }.first { it != BibleBandPhase.ENTER }
    val start = presenterManager.bibleBandClock.value
    val outFrom = if (start.phase == BibleBandPhase.TEXT_OUT) start.progress else 0f
    val outMs = template.segmentMs(BibleLottieTemplate.SEGMENT_TEXT_OUT)
    presenterManager.runBandPhase(BibleBandPhase.TEXT_OUT, (outMs * (1f - outFrom)).toLong(), outFrom)
    presenterManager.setDisplayedVerses(selectedVerses)
    presenterManager.runBandPhase(BibleBandPhase.TEXT_IN, template.segmentMs(BibleLottieTemplate.SEGMENT_TEXT_IN))
    presenterManager.setBibleBandClock(BibleBandClock(BibleBandPhase.HOLD, 1f))
}

LaunchedEffect(lyricSection, lyricSectionVersion) {
    val ss = appSettings.songSettings
    if (lyricSection == presenterManager.displayedLyricSection.value) {
        presenterManager.setSongTransitionAlpha(1f)
        return@LaunchedEffect
    }
    val isLineMode = isSongLineMode(ss)
    if (isLineMode) {
        presenterManager.setDisplayedLyricSection(lyricSection)
        presenterManager.setSongTransitionAlpha(1f)
        return@LaunchedEffect
    }
    presenterManager.setDisplayedLyricSection(lyricSection)
    presenterManager.setSongTransitionAlpha(1f)
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
private suspend fun PresenterManager.runBandPhase(phase: BibleBandPhase, durationMs: Long, from: Float = 0f) {
    setBibleBandClock(BibleBandClock(phase, from))
    val anim = Animatable(from)
    anim.animateTo(1f, tween(durationMillis = durationMs.toInt().coerceAtLeast(1), easing = LinearEasing)) {
        setBibleBandClock(BibleBandClock(phase, this.value))
    }
    setBibleBandClock(BibleBandClock(phase, 1f))
}
