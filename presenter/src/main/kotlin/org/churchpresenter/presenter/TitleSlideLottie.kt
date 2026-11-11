package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.churchpresenter.settings.titleSlideLottie
import org.churchpresenter.settings.utils.Constants

/**
 * The clock a title slide's own Lottie plays to (#971) -- its own, beside the lyric band's
 * [LocalLottieBandClock], so a title template and a lyric band never pull one clock two ways. Like
 * that one it carries the state holder, read at draw time; the default is a settled title, which is
 * what a settings preview composed with no output around it shows.
 */
val LocalTitleSlideClock = compositionLocalOf<State<BibleBandClock>> { SETTLED_TITLE_CLOCK }

private val SETTLED_TITLE_CLOCK: State<BibleBandClock> = mutableStateOf(BibleBandClock())

/**
 * The template this output's title slide plays, once loaded -- or null while the section on screen
 * is not a title slide, no template is set, or the file is gone or not a Lottie: then the title slide
 * is drawn as it always was.
 */
@Composable
internal fun SongLook.rememberTitleSlideTemplate(): BibleLottieTemplate? {
    val path = if (lyricSection.type == Constants.SECTION_TYPE_TITLE_SLIDE) ss.titleSlideLottie(isLowerThird) else ""
    val template by rememberBibleLottieTemplate(path)
    return template.takeIf { path.isNotBlank() }
}

/**
 * The title slide as its own Lottie over a whole output: the number and title in the template's
 * text layers, the credits in its reference layers, in the full screen's title look. The template
 * keeps its own shape, centred, so an output of another shape letterboxes it rather than stretching
 * it; the song's background is drawn under it as for any slide.
 */
@Composable
internal fun SongLook.TitleSlideLottieFullScreen(template: BibleLottieTemplate) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(template.width / template.height)) {
            SongLottieBand(
                template = template,
                section = lyricSection,
                settings = ss,
                languageDisplay = effectiveLangDisplay,
                lineIndex = displayLineIndex,
                outgoingSection = null,
                outgoingLineIndex = -1,
                allSections = allLyricSections,
                displaySectionIndex = displaySectionIndex,
                bandFraction = 1f,
                bandClock = LocalTitleSlideClock.current,
                isKey = isKey,
                showBackground = true,
                fullScreen = true,
            )
        }
    }
}

/**
 * The title slide as its own Lottie on a lower third, in the band's place: the wash above the band
 * as the lyric band has it, then this template, on the title slide's clock.
 */
@Composable
internal fun SongLook.TitleSlideLottieBand(template: BibleLottieTemplate, modifier: Modifier) {
    val lowerThirdFraction = ss.lowerThirdHeightPercent / PERCENT
    val above = resolveAboveBand(appSettings.backgroundSettings, bgConfig)
    BoxWithConstraints(modifier.fillMaxSize()) {
        AboveBandFill(
            above = above,
            show = showBackground,
            bandFraction = effectiveBandFraction(
                canvasAspectRatio = maxWidth / maxHeight,
                bandFraction = lowerThirdFraction,
                templateAspectRatio = template.width / template.height,
            ),
        )
        SongLottieBand(
            template = template,
            section = lyricSection,
            settings = ss,
            languageDisplay = effectiveLangDisplay,
            lineIndex = displayLineIndex,
            outgoingSection = null,
            outgoingLineIndex = -1,
            allSections = allLyricSections,
            displaySectionIndex = displaySectionIndex,
            bandFraction = lowerThirdFraction,
            bandClock = LocalTitleSlideClock.current,
            isKey = isKey,
            showBackground = showBackground,
        )
    }
}
