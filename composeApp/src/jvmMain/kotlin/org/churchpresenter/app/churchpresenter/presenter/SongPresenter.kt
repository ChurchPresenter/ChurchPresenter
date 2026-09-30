package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.io.File
import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.app.churchpresenter.composables.CameraDeviceCatalog
import org.churchpresenter.app.churchpresenter.composables.ChordChart
import org.churchpresenter.app.churchpresenter.composables.cameraResolves
import org.churchpresenter.app.churchpresenter.usesBibleLottieBand
import org.churchpresenter.app.churchpresenter.utils.calculateChordChartFontSize
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.songchords.ChordTransposer

internal const val SONG_SHADOW_OFFSET_PX = 6f
internal const val SONG_INDICATOR_REPEAT_COUNT = 3

/** The look-ahead spacer, `12` at every render call site. */
internal const val SONG_LOOK_AHEAD_SPACER_GAP = 12

/** The app's own background-type name for one of [SongBackgroundType]'s. */
internal fun songBackgroundTypeConstant(type: String): String = when (type) {
    SongBackgroundType.IMAGE -> Constants.BACKGROUND_IMAGE
    SongBackgroundType.VIDEO -> Constants.BACKGROUND_VIDEO
    SongBackgroundType.CAMERA -> Constants.BACKGROUND_CAMERA
    else -> Constants.BACKGROUND_COLOR
}

/**
 * Whether [background] can actually be drawn **here**: a colour always can, a picture or a clip
 * only while the file it names is still on this machine, and a camera only while this machine has
 * the device it names. A song travels; neither the media it points at nor the hardware it points at
 * travels with it.
 *
 * The camera arm asks [cameraResolves] against the catalog's **last known** device list rather than
 * enumerating: this runs inside a `remember` on the composition thread of every presenter output,
 * and enumerating shells out to ffmpeg. Before anything has enumerated the answer is yes — see
 * [cameraResolves] for why accepting is the safe direction there.
 *
 * [knownCameras] defaults to that catalog and is passed explicitly only by tests. It is a parameter
 * rather than a read of the singleton because the catalog is process-global and a *composition*
 * fills it: opening a camera property panel enumerates, so a suite that renders one leaves every
 * later test in that fork looking at a populated catalog. Taking it as an argument is what makes
 * this decision testable without either faking a singleton or depending on what ran first.
 */
internal fun songBackgroundResolves(
    background: SongBackground,
    knownCameras: List<CameraDevice>? = CameraDeviceCatalog.devices.value,
): Boolean = when (background.type) {
    SongBackgroundType.COLOR, SongBackgroundType.GRADIENT -> true
    SongBackgroundType.IMAGE, SongBackgroundType.VIDEO ->
        background.mediaPath.isNotBlank() && File(background.mediaPath).exists()
    SongBackgroundType.CAMERA -> cameraResolves(background.camera, knownCameras)
    else -> false
}

@Composable
fun SongPresenter(
    modifier: Modifier = Modifier,
    lyricSection: LyricSection,
    appSettings: AppSettings,
    isLowerThird: Boolean = false,
    // Only changes the band's geometry (a right-anchored vertical strip instead of a bottom
    // horizontal band) — isLowerThird alone still selects all the *LowerThird* styling fields
    // for both orientations, so there's one style profile to maintain.
    isLowerThirdVertical: Boolean = false,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transitionAlpha: Float = 1f,
    displayLineIndex: Int = -1,
    lookAheadEnabled: Boolean = false,
    // Auto-fit gates on this being non-empty (see the remember block below); every real caller
    // already passes its own list, so this default only matters to one that has just the section
    // it's showing -- a caller with none used to get no auto-fit at all, silently.
    allLyricSections: List<LyricSection> = listOf(lyricSection),
    displaySectionIndex: Int = -1,
    showBackground: Boolean = true,
    crossfadeEnabled: Boolean = false,
    languageOverride: String = "",
    /**
     * Which of the song's languages this output draws, by position — `0` being the primary.
     *
     * Empty defers to [languageOverride], which is what every output holds until someone picks
     * explicitly, so an installation that never opens the picker presents exactly as it did when a
     * song could only have two languages.
     */
    languageSelection: List<Int> = emptyList(),
    showChords: Boolean = false,
    /** Full screen: the region the text alone is placed in, the background filling the screen -- see [textOnly]. */
    textRegion: ContentRegion? = null,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val ss = appSettings.songSettings
    val look = SongLook(
        lyricSection = lyricSection,
        appSettings = appSettings,
        isLowerThird = isLowerThird,
        isLowerThirdVertical = isLowerThirdVertical,
        outputRole = outputRole,
        transitionAlpha = transitionAlpha,
        displayLineIndex = displayLineIndex,
        lookAheadEnabled = lookAheadEnabled,
        allLyricSections = allLyricSections,
        displaySectionIndex = displaySectionIndex,
        showBackground = showBackground,
        crossfadeEnabled = crossfadeEnabled,
        languageOverride = languageOverride,
        languageSelection = languageSelection,
        showChords = showChords,
        resources = rememberSongLookResources(ss, isLowerThird, lookAheadEnabled, isKey),
    )
    with(look) {
        // A Lottie band draws the whole band itself — text included — so it replaces everything
        // below; a file that is missing or is not a template falls through to the classic band.
        if (isLowerThird && usesBibleLottieBand(bgConfig)) {
            val template by rememberBibleLottieTemplate(bgConfig.backgroundLottie)
            val loaded = template
            if (loaded != null) {
                SongLottieBandLayer(loaded, modifier)
                return
            }
        }

        // A song can carry its own background in its .song file; while that song is live it wins over
        // the Background settings tab, and the quick tray's live pick wins over both. A media path that
        // no longer resolves falls back exactly as an unset one does — a song file is portable, the
        // picture it names is not. See resolveBackground for the whole order.
        val resolvedBg = resolveBackground(
            settings = appSettings.backgroundSettings,
            config = bgConfig,
            isLowerThird = isLowerThird,
            showBackground = showBackground,
            transparentWhenBlank = LocalTransparentBlanking.current,
            ownBackground = if (isLowerThird) lyricSection.lowerThirdBackground else lyricSection.background,
        )
        val backdrop = SongBackdrop(resolvedBg, rememberBackgroundBitmap(resolvedBg, isLowerThird))

        // Fade-in on first appearance (covers background + text)
        val fadeInDuration = appSettings.songSettings.transitionDuration.toInt().coerceAtLeast(100)
        var enterAlpha by remember { mutableStateOf(if (appSettings.songSettings.fadeIn) 0f else 1f) }
        LaunchedEffect(Unit) {
            if (appSettings.songSettings.fadeIn && enterAlpha < 1f) {
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(durationMillis = fadeInDuration)) {
                    enterAlpha = this.value
                }
                enterAlpha = 1f
            }
        }

        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = transitionAlpha * enterAlpha }
                .then(if (!isLowerThird && !backdrop.blurred) backdrop.bgModifier else Modifier)
        ) {
            // The stored radius is in the 1920x1080 reference space the rest of the presenter measures in.
            val blurRadius = backgroundBlurRadius(backdrop.bgBlurReferencePx, maxWidth)
            PresenterBackgroundLayers(
                background = resolvedBg,
                backgroundModifier = backdrop.bgModifier,
                isLowerThird = isLowerThird,
                blurRadius = blurRadius,
            )
            // Everything but the background, in the region when the background stays full screen.
            TextRegionBox(textRegion) {
                SongFrameContent(look, backdrop, blurRadius)
            }
        }
    }
}

/** A Lottie band draws the whole band itself — text included — so it replaces everything else. */
@Composable
private fun SongLook.SongLottieBandLayer(loaded: BibleLottieTemplate, modifier: Modifier) {
    val lowerThirdFraction = ss.lowerThirdHeightPercent / PERCENT
    val above = resolveAboveBand(appSettings.backgroundSettings, bgConfig)
    BoxWithConstraints(modifier.fillMaxSize()) {
        AboveBandFill(
            above = above,
            show = showBackground,
            bandFraction = effectiveBandFraction(
                canvasAspectRatio = maxWidth / maxHeight,
                bandFraction = lowerThirdFraction,
                templateAspectRatio = loaded.width / loaded.height,
            ),
        )
        val outgoing = LocalBandOutgoing.current
        SongLottieBand(
            template = loaded,
            section = lyricSection,
            settings = ss,
            languageDisplay = effectiveLangDisplay,
            lineIndex = LocalBandSongLineIndex.current.takeIf { it >= 0 } ?: displayLineIndex,
            outgoingSection = outgoing.lyricSection,
            outgoingLineIndex = outgoing.lyricLineIndex,
            allSections = allLyricSections,
            displaySectionIndex = displaySectionIndex,
            bandFraction = lowerThirdFraction,
            bandClock = LocalLottieBandClock.current,
            isKey = isKey,
            showBackground = showBackground,
        )
    }
}

/**
 * The chart row carrying the words of [lineIndex], or null when there is none.
 *
 * Rows map to lyric lines by position among the rows that have words: a header, or a row of chords
 * with nothing under it, puts a row in the chart but no line on the slide, so the two lists are not
 * index-for-index. A section with no chords falls back to its plain words.
 */
internal fun chartRowFor(section: LyricSection, lineIndex: Int): String? {
    if (section.chordLines.isEmpty()) return section.lines.getOrNull(lineIndex)
    return section.chordLines
        .filter { !ChordTransposer.isSectionHeader(it) && ChordTransposer.stripChords(it).isNotBlank() }
        .getOrNull(lineIndex)
}

/**
 * Rows drawn as a chord chart, stepped down to whatever size fits the space given.
 *
 * The words keep the output's lyric font, color, shadow and size ceiling; only the chord tokens are
 * monospace, and they take their own configured color so the two rows read apart.
 */
@Composable
internal fun SectionChordChart(
    lines: List<String>,
    color: Color,
    chordColor: Color,
    horizontalAlignment: Alignment.Horizontal,
    maxFontSize: Int,
    scaleFactor: Float,
    fontFamily: FontFamily?,
    textStyle: TextStyle,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val baseStyle = textStyle.copy(fontFamily = fontFamily, color = color)
        val fitted = remember(lines, maxFontSize, scaleFactor, maxWidth, maxHeight) {
            calculateChordChartFontSize(
                textMeasurer = measurer,
                lines = lines,
                baseStyle = baseStyle,
                availableWidth = (maxWidth.value / scaleFactor).toInt(),
                availableHeight = (maxHeight.value / scaleFactor).toInt(),
                maxFontSize = maxFontSize,
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = horizontalAlignment) {
            ChordChart(
                lines = lines,
                textColor = color,
                chordColor = chordColor,
                fontSize = (fitted * scaleFactor).sp,
                textStyle = baseStyle,
            )
        }
    }
}

/**
 * [shouldShowText], deciding "first page" from where [lyricSection] sits in [allSections] when it
 * is one of them: the first page is the song's first lyric section, whatever its heading says.
 *
 * The heading rule below is the fallback for a section that is not in the list -- the whole-song
 * slide, or a section pushed on its own. It reads a heading with no number in it as the opening
 * slide, which is right for `[Verse]` and wrong for `[Chorus]` written in square brackets, and a
 * song that starts on `[Verse 1.1]` and `[Verse 2.1]` gets the title back on every verse. With
 * the song's own order to hand there is no need to guess.
 */
internal fun shouldShowText(
    display: String,
    lyricSection: LyricSection,
    allSections: List<LyricSection>,
    displaySectionIndex: Int,
): Boolean {
    if (display != Constants.FIRST_PAGE) return shouldShowText(display, lyricSection)
    val position = displaySectionIndex.takeIf { allSections.getOrNull(it)?.isSamePageAs(lyricSection) == true }
        ?: allSections.indexOfFirst { it.isSamePageAs(lyricSection) }
    if (position < 0) return shouldShowText(display, lyricSection)
    // The title slide is not a lyric page: with one in front, verse 1 is still the first page.
    return allSections.subList(0, position).none { it.type != Constants.SECTION_TYPE_TITLE_SLIDE }
}

/**
 * Whether [other] is this section as pushed to the presenter. Compared on what identifies a page
 * rather than with `==`, because the section that goes out is stamped with the song's tempo and
 * capo and the list it came from is not.
 */
internal fun LyricSection.isSamePageAs(other: LyricSection): Boolean =
    header == other.header && slideIndex == other.slideIndex && type == other.type && lines == other.lines

internal fun shouldShowText(display: String, lyricSection: LyricSection): Boolean {
    return when (display) {
        Constants.EVERY_PAGE -> true
        Constants.FIRST_PAGE -> {
            // Show only on the first verse section (header null, ends with "1", or verse with no number)
            val header = lyricSection.header ?: return lyricSection.slideIndex == 0 // null = first section
            // Chorus/bridge sections are not "first page"; nor is the second slide of a section
            // broken by a manual [---], which is the same page continued.
            if (lyricSection.type == Constants.SECTION_TYPE_CHORUS || lyricSection.slideIndex > 0) return false
            val inner = header.trim().removePrefix("[").removePrefix("{").removeSuffix("]").removeSuffix("}").trim()
            // The trailing number is compared as a number, not as a string ending in "1" — that read
            // verses 11, 21 and 31 as the opening slide, so the title came back over them part-way
            // through any hymn long enough to have eleven sections.
            val sectionNumber = inner.takeLastWhile { it.isDigit() }.toIntOrNull()
            sectionNumber == 1 || !inner.any { it.isDigit() }
        }

        else -> false
    }
}

internal fun getTextAlign(alignment: String): TextAlign {
    return when (alignment) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }
}

/** One crossfade layer's page: the section it draws and the line within it, kept together. */
internal data class SongCrossfadePage(val section: LyricSection, val lineIndex: Int)
