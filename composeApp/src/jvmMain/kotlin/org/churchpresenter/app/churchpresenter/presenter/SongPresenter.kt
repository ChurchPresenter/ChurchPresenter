package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.settings.AppSettings

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.settings.songLanguageSelection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.utils.bilingualGrid
import org.churchpresenter.app.churchpresenter.composables.ChordChart
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitForAllSections
import org.churchpresenter.app.churchpresenter.utils.calculateChordChartFontSize
import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.app.churchpresenter.composables.CameraDeviceCatalog
import org.churchpresenter.app.churchpresenter.composables.cameraResolves
import androidx.compose.ui.text.AnnotatedString
import org.churchpresenter.app.churchpresenter.composables.OutlinedText
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import androidx.compose.ui.unit.em
import org.churchpresenter.app.churchpresenter.composables.rememberTextBackdropPainter
import org.churchpresenter.app.churchpresenter.composables.rememberTextBlockBackdrop
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleTarget
import org.churchpresenter.app.churchpresenter.dialogs.tabs.elementStyle
import org.churchpresenter.app.churchpresenter.utils.combinedTextDecoration
import org.churchpresenter.app.churchpresenter.usesBibleLottieBand
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.app.churchpresenter.utils.styledDisplayText
import kotlinx.coroutines.channels.Channel
import java.io.File

private const val SHADOW_OFFSET_PX = 6f
private const val INDICATOR_REPEAT_COUNT = 3

/** The look-ahead spacer and the stacked-language block gap, both `12` at every render call site. */
private const val LOOK_AHEAD_SPACER_REFERENCE_GAP = 12
private const val LANGUAGE_BLOCK_REFERENCE_GAP = 12

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
) {
    // When languageOverride is set by the per-screen songMode, use it instead of the global setting.
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val ss = appSettings.songSettings
    val effectiveLangDisplay = if (languageOverride.isNotBlank()) languageOverride else {
        if (lookAheadEnabled) {
            if (isLowerThird) ss.lowerThirdLookAheadLanguageDisplay else ss.lookAheadLanguageDisplay
        } else {
            if (isLowerThird) ss.lowerThirdLanguageDisplay else ss.fullscreenLanguageDisplay
        }
    }

    // How many languages the song carries, and which of them this output draws.
    //
    // The whole song *and* the slide in hand: the auto-fit below measures every section, so it has
    // to divide the frame the way every slide will be laid out -- but `allLyricSections` is only
    // supplied by outputs that offer look-ahead, and reading it alone left every other caller
    // believing a bilingual song had one language and drawing only the primary.
    //
    // Counted by the last translation that has anything to draw, a title on the title slide or words
    // on a lyric slide: an empty one is drawn as nothing, so it must not divide the frame the fit
    // measures against either.
    fun SectionTranslation.isDrawn() = lines.isNotEmpty() || title.isNotBlank()
    val availableLanguages = maxOf(
        lyricSection.translations.indexOfLast { it.isDrawn() },
        allLyricSections.maxOfOrNull { section -> section.translations.indexOfLast { it.isDrawn() } } ?: -1,
    ) + 2
    val activeLanguages = songLanguageSelection(effectiveLangDisplay, languageSelection, availableLanguages)

    // Resolve font families per fullscreen / lower third
    val titleFontFamily = remember(ss.titleFontType, ss.titleLowerThirdFontType, isLowerThird) {
        systemFontFamilyOrDefault(if (isLowerThird) ss.titleLowerThirdFontType else ss.titleFontType)
    }
    val lyricsFontFamily = remember(ss.lyricsFontType, ss.lyricsLowerThirdFontType,
        ss.lookAheadFontType, ss.lowerThirdLookAheadFontType, isLowerThird, lookAheadEnabled) {
        if (lookAheadEnabled) {
            systemFontFamilyOrDefault(if (isLowerThird) ss.lowerThirdLookAheadFontType else ss.lookAheadFontType)
        } else {
            systemFontFamilyOrDefault(if (isLowerThird) ss.lyricsLowerThirdFontType else ss.lyricsFontType)
        }
    }

    // Resolve colors — key mode forces white for a proper key signal
    val titleColor = remember(ss.titleColor, ss.titleLowerThirdColor, isLowerThird, isKey) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) ss.titleLowerThirdColor else ss.titleColor)
    }
    val sectionLabelColor = remember(ss.layoutExtras.sectionLabel.color, isKey) {
        if (isKey) Color.White else parseHexColor(ss.layoutExtras.sectionLabel.color)
    }
    val lyricsColor = remember(ss.lyricsColor, ss.lyricsLowerThirdColor,
        ss.lookAheadColor, ss.lowerThirdLookAheadColor, isLowerThird, lookAheadEnabled, isKey) {
        if (isKey) Color.White
        else if (lookAheadEnabled) {
            parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadColor else ss.lookAheadColor)
        } else {
            parseHexColor(if (isLowerThird) ss.lyricsLowerThirdColor else ss.lyricsColor)
        }
    }
    /**
     * The element's outline as this output draws it.
     *
     * A key output carries the shape of the fill as a white matte, and the outline is part of that
     * shape -- so it is kept and painted white rather than dropped, which would key out a hole the
     * width of the stroke around every letter.
     */
    fun keyedOutline(outline: TextOutline): TextOutline =
        if (isKey && outline.isVisible) outline.copy(color = "#FFFFFF") else outline
    val chordColor = remember(ss.lyricsChordColor, ss.lyricsLowerThirdChordColor, isLowerThird, isKey) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) ss.lyricsLowerThirdChordColor else ss.lyricsChordColor)
    }
    // Look-ahead next section preview font settings (resolved per fullscreen / lower third)
    val laColor = remember(ss.lookAheadNextColor, ss.lowerThirdLookAheadNextColor, isLowerThird, isKey) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadNextColor else ss.lookAheadNextColor)
    }
    val laFontFamily = remember(ss.lookAheadNextFontType, ss.lowerThirdLookAheadNextFontType, isLowerThird) {
        systemFontFamilyOrDefault(if (isLowerThird) ss.lowerThirdLookAheadNextFontType else ss.lookAheadNextFontType)
    }
    val laFontSize = if (isLowerThird) ss.lowerThirdLookAheadNextFontSize else ss.lookAheadNextFontSize
    val laBold = if (isLowerThird) ss.lowerThirdLookAheadNextBold else ss.lookAheadNextBold
    val laItalic = if (isLowerThird) ss.lowerThirdLookAheadNextItalic else ss.lookAheadNextItalic
    val laUnderline = if (isLowerThird) ss.lowerThirdLookAheadNextUnderline else ss.lookAheadNextUnderline
    val laShadowEnabled = if (isLowerThird) ss.lowerThirdLookAheadNextShadow else ss.lookAheadNextShadow
    val laShadowColor = parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadNextShadowColor else ss.lookAheadNextShadowColor)
    val laShadowSizeMul = (if (isLowerThird) ss.lowerThirdLookAheadNextShadowSize else ss.lookAheadNextShadowSize) / 100f
    val laShadowAlpha = ((if (isLowerThird) ss.lowerThirdLookAheadNextShadowOpacity else ss.lookAheadNextShadowOpacity) / 100f).coerceIn(0f, 1f)

    // Per-element shadow customization (resolved per fullscreen / lower third)
    fun makeSongShadow(color: String, size: Int, opacity: Int, alphaScale: Float = 0.78f): Shadow {
        val base = parseHexColor(color)
        val mul = size / 100f
        val alpha = (opacity / 100f).coerceIn(0f, 1f)
        return Shadow(
            color = base.copy(alpha = alpha * alphaScale),
            offset = Offset(2f * mul, 2f * mul),
            blurRadius = 4f * mul
        )
    }
    val titleBaseShadow = makeSongShadow(
        if (isLowerThird) ss.titleLowerThirdShadowColor else ss.titleShadowColor,
        if (isLowerThird) ss.titleLowerThirdShadowSize else ss.titleShadowSize,
        if (isLowerThird) ss.titleLowerThirdShadowOpacity else ss.titleShadowOpacity
    )
    val lyricsBaseShadow = makeSongShadow(
        if (isLowerThird) ss.lyricsLowerThirdShadowColor else ss.lyricsShadowColor,
        if (isLowerThird) ss.lyricsLowerThirdShadowSize else ss.lyricsShadowSize,
        if (isLowerThird) ss.lyricsLowerThirdShadowOpacity else ss.lyricsShadowOpacity
    )

    val songTarget = if (isLowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN

    // Text styles derived from settings (resolved per fullscreen / lower third)
    val effectiveTitleBold = if (isLowerThird) ss.titleLowerThirdBold else ss.titleBold
    val effectiveTitleItalic = if (isLowerThird) ss.titleLowerThirdItalic else ss.titleItalic
    val effectiveTitleUnderline = if (isLowerThird) ss.titleLowerThirdUnderline else ss.titleUnderline
    val effectiveTitleShadow = if (isLowerThird) ss.titleLowerThirdShadow else ss.titleShadow
    val titleStyleProfile = ss.elementStyle(SongStyleElement.TITLE, songTarget)
    val titleTextStyle = TextStyle(
        fontWeight = if (effectiveTitleBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (effectiveTitleItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(effectiveTitleUnderline, titleStyleProfile.strikethrough),
        letterSpacing = spacingEm(titleStyleProfile.letterSpacing, titleStyleProfile.fontSize).em,
        shadow = if (effectiveTitleShadow) titleBaseShadow else null
    )

    // The song number is drawn from its own profile now. It used to borrow the title's font, colour
    // and face, which left its own stored fields unread -- see `SongSettings.migrateSongNumberStyle`,
    // which carries a styled title across so a settings file written then still looks the same.
    val numberStyleProfile = ss.elementStyle(SongStyleElement.NUMBER, songTarget)
    val songNumberBaseShadow = makeSongShadow(
        numberStyleProfile.shadowColor,
        numberStyleProfile.shadowSize,
        numberStyleProfile.shadowOpacity,
    )
    val songNumberTextStyle = TextStyle(
        fontWeight = if (numberStyleProfile.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (numberStyleProfile.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(numberStyleProfile.underline, numberStyleProfile.strikethrough),
        letterSpacing = spacingEm(numberStyleProfile.letterSpacing, numberStyleProfile.fontSize).em,
        shadow = if (numberStyleProfile.shadow) songNumberBaseShadow else null,
    )
    val songNumberColor = if (isKey) Color.White else parseHexColor(numberStyleProfile.color)
    val songNumberFontFamily = systemFontFamilyOrDefault(
        numberStyleProfile.fontType.ifBlank { if (isLowerThird) ss.titleLowerThirdFontType else ss.titleFontType },
    )
    val effectiveLyricsBold = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadBold else ss.lookAheadBold
    } else if (isLowerThird) ss.lyricsLowerThirdBold else ss.lyricsBold
    val effectiveLyricsItalic = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadItalic else ss.lookAheadItalic
    } else if (isLowerThird) ss.lyricsLowerThirdItalic else ss.lyricsItalic
    val effectiveLyricsUnderline = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadUnderline else ss.lookAheadUnderline
    } else if (isLowerThird) ss.lyricsLowerThirdUnderline else ss.lyricsUnderline
    val effectiveLyricsShadow = if (lookAheadEnabled) {
        if (isLowerThird) ss.lowerThirdLookAheadShadow else ss.lookAheadShadow
    } else if (isLowerThird) ss.lyricsLowerThirdShadow else ss.lyricsShadow
    // Which profile the body text is drawn from: the look-ahead slide styles its lines separately.
    val lyricsStyleProfile = ss.elementStyle(
        if (lookAheadEnabled) SongStyleElement.LOOK_AHEAD else SongStyleElement.LYRICS,
        songTarget,
    )
    val lyricsTextStyle = TextStyle(
        fontWeight = if (effectiveLyricsBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (effectiveLyricsItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(effectiveLyricsUnderline, lyricsStyleProfile.strikethrough),
        letterSpacing = spacingEm(lyricsStyleProfile.letterSpacing, lyricsStyleProfile.fontSize).em,
        shadow = if (effectiveLyricsShadow) lyricsBaseShadow else null
    )
    val chartHorizontalAlignment = when (
        if (isLowerThird) ss.lyricsLowerThirdHorizontalAlignment else ss.lyricsHorizontalAlignment
    ) {
        Constants.LEFT -> Alignment.Start
        Constants.RIGHT -> Alignment.End
        else -> Alignment.CenterHorizontally
    }
    val contentAlignment = when (appSettings.songSettings.lyricsAlignment) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    val lyricsHorizontalAlignment = getTextAlign(
        if (lookAheadEnabled) {
            if (isLowerThird) ss.lowerThirdLookAheadHorizontalAlignment else ss.lookAheadHorizontalAlignment
        } else {
            if (isLowerThird) ss.lyricsLowerThirdHorizontalAlignment else ss.lyricsHorizontalAlignment
        }
    )
    val titleHorizontalAlignment = getTextAlign(
        if (isLowerThird) ss.titleLowerThirdHorizontalAlignment else ss.titleHorizontalAlignment
    )
    val songNumberHorizontalAlignment = getTextAlign(
        if (isLowerThird) ss.songNumberLowerThirdHorizontalAlignment else ss.songNumberHorizontalAlignment
    )
    val bgConfig = if (isLowerThird) appSettings.backgroundSettings.songLowerThirdBackground
    else appSettings.backgroundSettings.songBackground

    // A Lottie band draws the whole band itself — text included — so it replaces everything
    // below; a file that is missing or is not a template falls through to the classic band.
    if (isLowerThird && usesBibleLottieBand(bgConfig)) {
        val template by rememberBibleLottieTemplate(bgConfig.backgroundLottie)
        val loaded = template
        if (loaded != null) {
            val lowerThirdFraction = ss.lowerThirdHeightPercent / PERCENT
            val above = resolveAboveBand(appSettings.backgroundSettings, bgConfig)
            BoxWithConstraints(modifier.fillMaxSize()) {
                AboveBandFill(
                    fill = if (showBackground) above.fill else null,
                    bandFraction = effectiveBandFraction(
                        canvasAspectRatio = maxWidth / maxHeight,
                        bandFraction = lowerThirdFraction,
                        templateAspectRatio = loaded.width / loaded.height,
                    ),
                    fillsBehindBand = above.fillsBehindBand,
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
    val bgDimPercent = resolvedBg.dimPercent
    val bgBlurReferencePx = resolvedBg.blurReferencePx

    val backgroundImageBitmap = rememberBackgroundBitmap(resolvedBg, isLowerThird)
    val useVideoBackground = resolvedBg.usesVideo
    val effectiveOpacity = resolvedBg.opacity
    val bgModifier: Modifier = backgroundModifier(resolvedBg, backgroundImageBitmap)

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

    // A blurred background has to be its own layer — blurring the box the lyrics sit in would blur
    // the lyrics with it. Only a song background can ask for blur, so an unblurred one keeps the
    // original single-box shape and its behaviour exactly.
    val blurred = bgBlurReferencePx > 0
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = transitionAlpha * enterAlpha }
            .then(if (!isLowerThird && !blurred) bgModifier else Modifier)
    ) {
        val scaleFactor = presenterScale(maxWidth, maxHeight)
        // The stored radius is in the 1920x1080 reference space the rest of the presenter measures in.
        val blurRadius = backgroundBlurRadius(bgBlurReferencePx, maxWidth)
        PresenterBackgroundLayers(
            background = resolvedBg,
            backgroundModifier = bgModifier,
            isLowerThird = isLowerThird,
            blurRadius = blurRadius,
        )

        // Scale shadow to be visible at projection resolution
        fun scaleElementShadow(color: String, size: Int, opacity: Int): Shadow {
            val base = parseHexColor(color)
            val mul = size / 100f
            val alpha = (opacity / 100f).coerceIn(0f, 1f)
            return Shadow(
                color = base.copy(alpha = alpha),
                offset = Offset(SHADOW_OFFSET_PX * scaleFactor * mul, SHADOW_OFFSET_PX * scaleFactor * mul),
                blurRadius = 12f * scaleFactor * mul
            )
        }
        val titleTextStyleScaled = if (effectiveTitleShadow)
            titleTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) ss.titleLowerThirdShadowColor else ss.titleShadowColor,
                if (isLowerThird) ss.titleLowerThirdShadowSize else ss.titleShadowSize,
                if (isLowerThird) ss.titleLowerThirdShadowOpacity else ss.titleShadowOpacity
            )) else titleTextStyle
        val songNumberTextStyleScaled = if (numberStyleProfile.shadow) {
            songNumberTextStyle.copy(
                shadow = scaleElementShadow(
                    numberStyleProfile.shadowColor,
                    numberStyleProfile.shadowSize,
                    numberStyleProfile.shadowOpacity,
                ),
            )
        } else {
            songNumberTextStyle
        }
        val lyricsTextStyleScaled = if (effectiveLyricsShadow)
            lyricsTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) ss.lyricsLowerThirdShadowColor else ss.lyricsShadowColor,
                if (isLowerThird) ss.lyricsLowerThirdShadowSize else ss.lyricsShadowSize,
                if (isLowerThird) ss.lyricsLowerThirdShadowOpacity else ss.lyricsShadowOpacity
            )) else lyricsTextStyle
        val effectiveTitleFontSize = if (isLowerThird) ss.titleLowerThirdFontSize else ss.titleFontSize
        val scaledTitleFontSize = (effectiveTitleFontSize * scaleFactor).sp
        val settingsLyricsFontSize = if (lookAheadEnabled) {
            if (isLowerThird) ss.lowerThirdLookAheadFontSize else ss.lookAheadFontSize
        } else if (isLowerThird) appSettings.songSettings.lyricsLowerThirdFontSize else appSettings.songSettings.lyricsFontSize
        val effectiveSongNumberFontSize =
            if (isLowerThird) appSettings.songSettings.songNumberLowerThirdFontSize else appSettings.songSettings.songNumberFontSize

        // Auto-fit: compute the largest font size that fits ALL sections without line wrapping.
        // Uses the reference 1920×1080 coordinate space (margins subtracted).
        val autoFitTextMeasurer = rememberTextMeasurer()
        val autoFitFontSize = remember(
            allLyricSections,
            isLowerThird,
            lookAheadEnabled,
            languageOverride,
            appSettings.songSettings,
            appSettings.projectionSettings,
            // The fit now measures against the real output's own aspect ratio (see referenceBoxWidth/
            // Height below), so a resize that changes scaleFactor without changing anything else above
            // must also invalidate this memo -- otherwise a live resize (or a screenshot test moving
            // between box sizes) would keep the previous size's stale fit.
            scaleFactor,
        ) {
            if (allLyricSections.isEmpty()) null
            else {
                // How many blocks the frame is actually divided into. One language fills it; more
                // split it, in whichever direction the layout says.
                val drawnLanguages = activeLanguages.size.coerceAtLeast(1)
                // [bilingualLayout]'s value as the grid it lays blocks out in. A row (1 × N) or a
                // column (N × 1) still divides by however many languages are actually drawn, exactly
                // as it always did; only a real two-dimensional grid (2 × 2 today) divides by its own
                // fixed row/column counts instead.
                val (gridRows, gridCols) = bilingualGrid(ss.bilingualLayout)
                val sideBySide = drawnLanguages > 1 && gridRows == 1 && gridCols > 1
                val topBottom = drawnLanguages > 1 && gridCols == 1 && gridRows > 1
                val grid2x2 = drawnLanguages > 1 && gridRows == 2 && gridCols == 2

                // The real output's own box, in the same reference space the rest of this fit and
                // scaleFactor's own 1920x1080 assumption are measured in -- not the literal 1920x1080
                // itself. A narrow/portrait output has a different aspect ratio than 16:9, and
                // scaleFactor is only ever the *limiting* dimension's ratio, so the reference-space box
                // implied by dividing back out by it is 1920 wide exactly when width is the constraint
                // but taller than 1080 when it is not -- which is what lets a portrait output's real
                // extra headroom reach this search instead of the fit being computed for a box the
                // output was never actually going to have (issue: lyrics overflowing on vertical
                // outputs, since the post-hoc scale-down by scaleFactor cannot add back room the fit
                // never knew it had).
                val referenceBoxWidth = maxWidth.value / scaleFactor
                val referenceBoxHeight = maxHeight.value / scaleFactor
                val fullWidth = referenceBoxWidth.toInt() - appSettings.projectionSettings.windowLeft -
                        appSettings.projectionSettings.windowRight -
                        appSettings.songSettings.marginLeft - appSettings.songSettings.marginRight
                // Side by side, each language gets a column; the fit has to hold in the narrowest
                // of them, which with equal weights is every one of them. A 2x2 grid's columns are
                // narrower still, but only ever two of them regardless of how many languages fill it.
                val refWidth = when {
                    sideBySide -> fullWidth / drawnLanguages
                    grid2x2 -> fullWidth / gridCols
                    else -> fullWidth
                }
                val fullHeight = if (isLowerThird) {
                    (referenceBoxHeight * appSettings.songSettings.lowerThirdHeightPercent / 100).toInt() -
                            appSettings.projectionSettings.windowTop - appSettings.projectionSettings.windowBottom -
                            appSettings.songSettings.marginTop - appSettings.songSettings.marginBottom
                } else {
                    referenceBoxHeight.toInt() - appSettings.projectionSettings.windowTop -
                            appSettings.projectionSettings.windowBottom -
                            appSettings.songSettings.marginTop - appSettings.songSettings.marginBottom
                }
                // Stacked, each language gets a band of the height on the same reasoning.
                val refHeight = when {
                    topBottom -> fullHeight / drawnLanguages
                    grid2x2 -> fullHeight / gridRows
                    else -> fullHeight
                }
                // The same tracking the lines are drawn with. Spacing is stored in pixels against
                // the profile's own font size and converted to `em`, so the value does not change
                // as the search tries sizes -- it scales with whichever one it settles on, exactly
                // as the rendered line does.
                val fitLetterEm = spacingEm(lyricsStyleProfile.letterSpacing, lyricsStyleProfile.fontSize)
                val fitWordEm = spacingEm(lyricsStyleProfile.wordSpacing, lyricsStyleProfile.fontSize)
                val baseStyle = TextStyle(
                    fontWeight = if (effectiveLyricsBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (effectiveLyricsItalic) FontStyle.Italic else FontStyle.Normal,
                    letterSpacing = fitLetterEm.em,
                    fontFamily = lyricsFontFamily
                )
                // Resolve display mode to know if we're in line mode
                val fitDisplayMode = if (lookAheadEnabled) {
                    if (isLowerThird) ss.lowerThirdLookAheadDisplayMode else ss.lookAheadDisplayMode
                } else {
                    if (isLowerThird) ss.lowerThirdDisplayMode else ss.fullscreenDisplayMode
                }
                val fitIsLineMode = fitDisplayMode == Constants.SONG_DISPLAY_MODE_LINE

                // For lookahead: combine each section with its next section so auto-fit
                // accounts for displaying both simultaneously at the same font size.
                // In line mode, only 2 lines are shown (1 main + 1 lookahead), so create
                // 2-line sections pairing each line with the next.
                val sectionsForFit = if (lookAheadEnabled && fitIsLineMode) {
                    // Line mode: pair each line with the next line across all sections
                    val allLines = allLyricSections.flatMap { it.lines }
                    // Every language's lines end to end, in the same order, so line `i` of one is
                    // line `i` of the others. Built per language rather than for the secondary
                    // alone -- the fit has to measure the longest line of whichever language has
                    // it, not of the first two.
                    val allLanguageLines = List(availableLanguages) { language ->
                        allLyricSections.flatMap { it.allLanguageLines().getOrElse(language) { emptyList() } }
                    }
                    allLines.indices.map { i ->
                        val nextLine = allLines.getOrElse(i + 1) { allLines[i] }
                        LyricSection(
                            lines = listOf(allLines[i], nextLine),
                            translations = allLanguageLines.drop(1).map { languageLines ->
                                if (languageLines.isEmpty()) SectionTranslation()
                                else {
                                    val line = languageLines.getOrElse(i) { "" }
                                    SectionTranslation(lines = listOf(line, languageLines.getOrElse(i + 1) { line }))
                                }
                            },
                        )
                    }
                } else if (lookAheadEnabled) {
                    // Verse mode: combine full section with next section
                    allLyricSections.mapIndexed { i, section ->
                        val next = allLyricSections.getOrNull(i + 1)
                        if (next != null) {
                            section.copy(
                                lines = section.lines + next.lines,
                                translations = List(availableLanguages - 1) { language ->
                                    val own = section.translations.getOrNull(language)
                                    val following = next.translations.getOrNull(language)
                                    val joined = own?.lines.orEmpty() + following?.lines.orEmpty()
                                    SectionTranslation(title = own?.title.orEmpty(), lines = joined)
                                },
                            )
                        } else section
                    }
                } else allLyricSections
                // Compute reserved height for title/song number above the verse
                val referenceDensity = Density(1f)
                val fitTitleDisplay = if (isLowerThird) ss.titleLowerThirdDisplay else ss.titleDisplay
                val fitTitlePosition = if (isLowerThird) ss.titleLowerThirdPosition else ss.titlePosition
                val fitNumberDisplay = if (isLowerThird) ss.showNumberLowerThird else ss.showNumber
                val fitNumberPosition = if (isLowerThird) ss.songNumberLowerThirdPosition else ss.songNumberPosition
                val fitNumberCorner = if (isLowerThird) ss.songNumberLowerThirdCorner else ss.songNumberCorner
                val fitTitleFontSize = if (isLowerThird) ss.titleLowerThirdFontSize else ss.titleFontSize
                val fitNumberFontSize = if (isLowerThird) ss.songNumberLowerThirdFontSize else ss.songNumberFontSize

                var reserved = 0
                if (fitTitleDisplay != Constants.NONE && fitTitlePosition == Constants.ABOVE_VERSE) {
                    val titleStyle = TextStyle(fontSize = fitTitleFontSize.sp, fontFamily = titleFontFamily)
                    val longestTitle = allLyricSections.maxOfOrNull { it.title.length }?.let { len ->
                        allLyricSections.first { it.title.length == len }.title
                    } ?: ""
                    if (longestTitle.isNotEmpty()) {
                        reserved += autoFitTextMeasurer.measure(longestTitle, titleStyle, density = referenceDensity).size.height
                    }
                }
                // A cornered number is drawn over the slide rather than in the row above it, so it
                // takes no height from the lyrics and reserves none here.
                if (fitNumberDisplay != Constants.NONE && fitNumberCorner == Constants.NONE &&
                    fitNumberPosition == Constants.ABOVE_VERSE
                ) {
                    val numStyle = TextStyle(fontSize = fitNumberFontSize.sp, fontFamily = titleFontFamily)
                    val maxNum = allLyricSections.maxOfOrNull { it.songNumber } ?: 0
                    if (maxNum > 0) {
                        reserved += autoFitTextMeasurer.measure(maxNum.toString(), numStyle, density = referenceDensity).size.height
                    }
                }
                // The fixed dp gaps the real layout draws that a section's own measured lines don't
                // account for: the spacer before the look-ahead line (`LookAheadSpacer`, drawn once
                // per language block) and the gaps between stacked-language blocks (`grid2x2`'s row
                // gap and `topBottom`'s band gap). At low font sizes these are negligible against the
                // text; near the real ceiling they are not, and a search that never reserved them
                // chose a size the real layout then clipped by exactly this much. Both are `12` in
                // this same reference space at every one of their call sites below -- see
                // `LookAheadSpacer`, `LookAheadPlaceholder` and the two per-language `Spacer`s in the
                // grid2x2/topBottom render branches.
                if (lookAheadEnabled && !fitIsLineMode) {
                    reserved += LOOK_AHEAD_SPACER_REFERENCE_GAP
                }
                if (drawnLanguages > 1 && (topBottom || grid2x2)) {
                    reserved += (drawnLanguages - 1) * LANGUAGE_BLOCK_REFERENCE_GAP
                }

                calculateAutoFitForAllSections(
                    textMeasurer = autoFitTextMeasurer,
                    sections = sectionsForFit,
                    baseStyle = baseStyle,
                    availableWidth = refWidth,
                    availableHeight = refHeight,
                    reservedHeight = reserved,
                    includeEndIndicator = true,
                    // Measure what `LyricLine` draws, not the stored line: an uppercase transform
                    // and the word spacing below are both applied at render, and a fit that did not
                    // include them chose a size whose lines then ran off the side of the output.
                    styleText = { styledDisplayText(it, lyricsStyleProfile.transform, fitLetterEm, fitWordEm) },
                )
            }
        }
        val autoFitEnabled = if (lookAheadEnabled) {
            if (isLowerThird) ss.lowerThirdLookAheadFontSizeAutoFit else ss.lookAheadFontSizeAutoFit
        } else {
            if (isLowerThird) ss.lyricsLowerThirdFontSizeAutoFit else ss.lyricsFontSizeAutoFit
        }
        val effectiveLyricsFontSize = if (autoFitEnabled) {
            (autoFitFontSize ?: settingsLyricsFontSize).coerceAtMost(settingsLyricsFontSize)
        } else settingsLyricsFontSize

        val scaledLyricsFontSize = (effectiveLyricsFontSize * scaleFactor).sp
        val scaledSongNumberFontSize = (effectiveSongNumberFontSize * scaleFactor).sp

        val leftOffSet = ((appSettings.projectionSettings.windowLeft + appSettings.songSettings.marginLeft) * scaleFactor).dp
        val rightOffSet = ((appSettings.projectionSettings.windowRight + appSettings.songSettings.marginRight) * scaleFactor).dp
        val topOffSet = ((appSettings.projectionSettings.windowTop + appSettings.songSettings.marginTop) * scaleFactor).dp
        val bottomOffSet = ((appSettings.projectionSettings.windowBottom + appSettings.songSettings.marginBottom) * scaleFactor).dp
        // Captured here, not read from inside the nested Box below: BoxScope and
        // BoxWithConstraintsScope both carry @LayoutScopeMarker, which hides this outer
        // BoxWithConstraints' maxWidth/maxHeight from a Box nested inside it.
        val outputWidth = maxWidth
        val outputHeight = maxHeight

        if (isLowerThird) {
            val lowerThirdFraction = appSettings.songSettings.lowerThirdHeightPercent / 100f
            // Background stretches full width at bottom third, text respects padding on top —
            // same band geometry for horizontal and vertical; isLowerThirdVertical only forces
            // bilingual content to stack instead of side-by-side, see TextContent below.
            // `Modifier.blur` fades a layer's own edge to transparent, so blurring the band
            // itself let whatever is behind — the default lower third's own color — show through
            // along the band's top as a hairline the width of the blur.
            // The fill is drawn larger than the band and the band clips it, so the fade
            // `Modifier.blur` leaves around a layer's own edge falls out of sight. Grown rather
            // than scaled: the picture is cropped from a slightly larger rectangle instead of
            // being stretched, which a band is wide enough to show.
            // The wash behind the whole lower third, band included — see AboveBandFill.
            val above = resolveAboveBand(appSettings.backgroundSettings, bgConfig)
            AboveBandFill(
                fill = if (showBackground) above.fill else null,
                bandFraction = lowerThirdFraction,
                fillsBehindBand = above.fillsBehindBand,
            )
            val bandBleed = if (blurred) blurRadius * BLUR_EDGE_BLEED else 0.dp
            // Read out here: the band Box's own scope shadows this one.
            val bandFillWidth = maxWidth + bandBleed * 2
            val bandFillHeight = maxHeight * lowerThirdFraction + bandBleed * 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(lowerThirdFraction)
                    .align(Alignment.BottomCenter)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(width = bandFillWidth, height = bandFillHeight)
                        .then(if (blurred) Modifier.blur(blurRadius) else Modifier)
                        .then(if (resolvedBg.type == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) Modifier else bgModifier)
                ) {
                    if (resolvedBg.type == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) {
                        Image(
                            painter = BitmapPainter(backgroundImageBitmap),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // Cropped from the middle of the picture, not its bottom edge. Scaled
                            // to the band's width a photo is several times the band's height, so
                            // anchoring it to the bottom showed the strip below the subject — the
                            // desk under a photo of someone reading — and never the photo itself.
                            // The Background tab's preview crops from the center; this is what
                            // makes the two agree.
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize().alpha(effectiveOpacity)
                        )
                    }
                    if (useVideoBackground) {
                        LoopingVideoBackground(
                            videoPath = resolvedBg.videoPath,
                            modifier = Modifier.fillMaxSize().alpha(effectiveOpacity),
                        )
                    }
                }
            }
            if (bgDimPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(lowerThirdFraction)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = bgDimPercent / 100f))
                )
            }
            // Gradient overlay
            if (bgConfig.gradientEnabled) {
                val gradientTop = parseHexColor(bgConfig.gradientTopColor).copy(alpha = bgConfig.gradientTopOpacity)
                val gradientBottom = parseHexColor(bgConfig.gradientBottomColor).copy(alpha = bgConfig.gradientBottomOpacity)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(lowerThirdFraction)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to gradientTop,
                                    bgConfig.gradientPosition to gradientBottom,
                                    1.0f to gradientBottom
                                )
                            )
                        )
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                // For a lower third, the padding moves inside the band's own box below instead of
                // applying here -- see the comment there for why.
                .then(
                    if (isLowerThird) Modifier
                    else Modifier.padding(start = leftOffSet, end = rightOffSet, top = topOffSet, bottom = bottomOffSet)
                ),
            contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
        ) {
            val innerModifier = if (isLowerThird) {
                // Capped at a quarter of the band's own height/width each, so top+bottom (or
                // left+right) can never consume more than half of it: a shallow band (a low
                // lowerThirdHeightPercent) combined with the operator's ordinary window/margin
                // insets can ask for more padding than the band is tall, and an uncapped padding
                // that exceeds its own box collapses it to zero size -- which is a real
                // misconfiguration to render as small type crowding the band, not a reason to
                // report every line at y=0 of the whole screen, which is what a collapsed box does.
                val bandHeight = outputHeight * (appSettings.songSettings.lowerThirdHeightPercent / 100f)
                val bandTopOffSet = topOffSet.coerceAtMost(bandHeight / 4)
                val bandBottomOffSet = bottomOffSet.coerceAtMost(bandHeight / 4)
                val bandLeftOffSet = leftOffSet.coerceAtMost(outputWidth / 4)
                val bandRightOffSet = rightOffSet.coerceAtMost(outputWidth / 4)
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(appSettings.songSettings.lowerThirdHeightPercent / 100f)
                    .align(Alignment.BottomCenter)
                    // Sized and positioned first, against this Box's own full (unpadded) bounds --
                    // the same bounds the band's background above measures its fraction against --
                    // and only then padded. Padding used to apply before the fraction was taken, so
                    // it shrank *and* shifted this box relative to the background: with a window
                    // inset and a margin on top (32 + 54 by default), the content box's own top
                    // edge sat ~86px above the background band's, so a title positioned at the top
                    // of this box -- as "above the verse" always is -- rendered above the visible
                    // band rather than inside it. Padding here now insets the text within the band
                    // exactly as it was always meant to, without moving the band itself.
                    .padding(start = bandLeftOffSet, end = bandRightOffSet, top = bandTopOffSet, bottom = bandBottomOffSet)
            } else {
                Modifier
            }

            // Only animate the text content — background is never inside this block
            @Composable
            fun TextContent(section: LyricSection, lineIndex: Int) {
                val titleDisplay = if (isLowerThird) ss.titleLowerThirdDisplay else ss.titleDisplay
                val numberDisplay = if (isLowerThird) ss.showNumberLowerThird else ss.showNumber
                // The title slide's lines are the song's title and credit, so they take the Title
                // element's style -- what the settings tab's Title tab edits -- rather than the
                // lyrics', and the title row above the lyrics stays out of it: it would repeat the
                // slide.
                val isTitleSlide = section.type == Constants.SECTION_TYPE_TITLE_SLIDE
                val shouldShowTitle =
                    shouldShowText(titleDisplay, section, allLyricSections, displaySectionIndex) && !isTitleSlide
                val shouldShowSongNumber =
                    shouldShowText(numberDisplay, section, allLyricSections, displaySectionIndex) &&
                        section.songNumber > 0 && !isTitleSlide
                // "Configured" means not set to "None" — title/number could appear on some slides
                val titleConfigured = titleDisplay != Constants.NONE
                val numberConfigured = numberDisplay != Constants.NONE && section.songNumber > 0
                val effectiveTitlePosition = if (isLowerThird) ss.titleLowerThirdPosition else ss.titlePosition
                val effectiveSongNumberPosition = if (isLowerThird) ss.songNumberLowerThirdPosition else ss.songNumberPosition
                // Which corner the number is pinned to, or NONE for the row it shares with the title.
                val songNumberCorner = if (isLowerThird) ss.songNumberLowerThirdCorner else ss.songNumberCorner
                val numberInCorner = numberConfigured && songNumberCorner != Constants.NONE
                // isLowerThirdVertical forces bilingual content to stack (one below the other)
                // instead of side-by-side — see the useSideBySide gate further below — same
                // band/geometry as horizontal otherwise.
                // The title slide has no verse to fit, no look-ahead and no chart -- it is a
                // heading and its credits, each drawn in its own element's profile -- so it is
                // drawn by its own composable, in the same box the lyrics would have had.
                if (isTitleSlide) {
                    SongTitleSlideContent(
                        section = section,
                        settings = ss,
                        target = songTarget,
                        languages = activeLanguages,
                        isKey = isKey,
                        scaleFactor = scaleFactor,
                        contentAlignment = if (isLowerThird) {
                            Alignment.BottomCenter
                        } else {
                            when (ss.titleSlideVerticalAlignment) {
                                Constants.TOP -> Alignment.TopCenter
                                Constants.BOTTOM -> Alignment.BottomCenter
                                else -> Alignment.Center
                            }
                        },
                        modifier = innerModifier,
                    )
                    return
                }
                BoxWithConstraints(
                    modifier = innerModifier,
                    contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
                ) {

                    val allDisplayLines = section.lines
                    val hasChart = showChords && section.chordLines.isNotEmpty()
                    // Resolve per-mode settings based on fullscreen vs lower third
                    // When lookAheadEnabled, the entire screen uses lookahead's own display mode
                    val displayMode = if (lookAheadEnabled) {
                        if (isLowerThird) ss.lowerThirdLookAheadDisplayMode else ss.lookAheadDisplayMode
                    } else {
                        if (isLowerThird) ss.lowerThirdDisplayMode else ss.fullscreenDisplayMode
                    }
                    // Look-ahead portion uses same display mode as the screen
                    val laDisplayMode = displayMode
                    val laIsLineMode = laDisplayMode == Constants.SONG_DISPLAY_MODE_LINE

                    val isLineMode = displayMode == Constants.SONG_DISPLAY_MODE_LINE
                    val effectiveLineIndex = if (isLineMode && lineIndex < 0) 0 else lineIndex

                    // Get next section for look-ahead
                    val nextSection: LyricSection? = if (lookAheadEnabled && displaySectionIndex >= 0) {
                        allLyricSections.getOrNull(displaySectionIndex + 1)?.takeIf { it.lines.isNotEmpty() }
                    } else null

                    // Every language this output draws, sliced the same way: the words now and the
                    // words next. One call rather than the four parallel `val`s this replaced --
                    // primary main, primary look-ahead, secondary main, secondary look-ahead --
                    // which could not grow past two languages without becoming eight.
                    val languageBlocks = songLanguageBlocks(
                        section = section,
                        nextSection = nextSection,
                        languages = activeLanguages,
                        modes = SongSlideModes(
                            lookAheadEnabled = lookAheadEnabled,
                            isLineMode = isLineMode,
                            laIsLineMode = laIsLineMode,
                            lineIndex = effectiveLineIndex,
                        ),
                    )

                    // Sliced the way the words are: one row in line mode, the section in verse
                    // mode, the look-ahead's own row after it.
                    val mainChartRows: List<String> = when {
                        !hasChart -> emptyList()
                        // The section as written, including a chord-only intro folded onto it.
                        !isLineMode -> section.chordLines
                        else -> listOfNotNull(chartRowFor(section, effectiveLineIndex.coerceAtLeast(0)))
                    }
                    // The next line of this section, when line mode has one left to show.
                    val nextLineHere = if (lookAheadEnabled && isLineMode && laIsLineMode) {
                        effectiveLineIndex.takeIf { it in 0 until allDisplayLines.size - 1 }?.plus(1)
                    } else {
                        null
                    }
                    val laChartRows: List<String> = when {
                        !hasChart -> emptyList()
                        nextLineHere != null -> listOfNotNull(chartRowFor(section, nextLineHere))
                        nextSection == null -> emptyList()
                        laIsLineMode -> listOfNotNull(chartRowFor(nextSection, 0))
                        else -> nextSection.chordLines.ifEmpty { nextSection.lines }
                    }

                    // The title row shows the leading drawn language's title, so an output set to
                    // one language shows that language's title rather than the primary's. Falls
                    // back to the song's own whenever that language has none, which is the common
                    // case: a second language is often lyrics with no separate title.
                    val titles = section.allLanguageTitles()
                    val effectiveTitle = languageBlocks.firstOrNull()
                        ?.let { titles.getOrNull(it.index) }
                        ?.takeIf { it.isNotEmpty() }
                        ?: section.title

                    // The title is drawn in the leading language's own title profile once that
                    // language has a look of its own, and in the primary's otherwise -- the same
                    // rule the lyric lines follow.
                    val titleLanguage = languageBlocks.firstOrNull()?.index ?: 0
                    val titleOwnStyling = if (ss.languageOverridesStyle(titleLanguage)) {
                        songLineStyling(
                            profile = ss.elementStyle(SongStyleElement.TITLE, songTarget, titleLanguage),
                            autoFitFontSize = null,
                            scaleFactor = scaleFactor,
                            isKey = isKey,
                            shadowOf = ::scaleElementShadow,
                        )
                    } else {
                        null
                    }
                    val titleProfileHere = titleOwnStyling?.profile ?: titleStyleProfile
                    val titleFontFamilyHere = titleOwnStyling?.fontFamily ?: titleFontFamily
                    val titleColorHere = titleOwnStyling?.color ?: titleColor
                    val titleFontSizeHere = titleOwnStyling?.fontSize ?: scaledTitleFontSize
                    val titleTextStyleHere = titleOwnStyling?.textStyle ?: titleTextStyleScaled
                    val titleAlignHere = titleOwnStyling?.let { getTextAlign(it.profile.horizontalAlignment) }
                        ?: titleHorizontalAlignment

                    val isMultiLanguage = languageBlocks.size > 1
                    // Row, column or 2x2 grid, from the same [bilingualGrid] mapping the auto-fit
                    // above already read. A vertical lower third is too narrow for a row or a 2x2
                    // grid -- both fall through to the stacked branch below, which already
                    // special-cases isLowerThird (true for vertical too) with a compact stack.
                    val (gridRows, gridCols) = bilingualGrid(appSettings.songSettings.bilingualLayout)
                    val useSideBySide = gridRows == 1 && gridCols > 1 && !isLowerThirdVertical
                    val useGrid2x2 = gridRows == 2 && gridCols == 2 && !isLowerThirdVertical

                    // Look-ahead text style with full font controls
                    val laBaseShadow = Shadow(
                        color = laShadowColor.copy(alpha = laShadowAlpha),
                        offset = Offset(6f * scaleFactor * laShadowSizeMul, 6f * scaleFactor * laShadowSizeMul),
                        blurRadius = 12f * scaleFactor * laShadowSizeMul
                    )
                    val laStyleProfile = ss.elementStyle(SongStyleElement.NEXT_SECTION, songTarget)

                    val lookAheadTextStyle = TextStyle(
                        fontWeight = if (laBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (laItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = combinedTextDecoration(laUnderline, laStyleProfile.strikethrough),
                        letterSpacing = spacingEm(laStyleProfile.letterSpacing, laStyleProfile.fontSize).em,
                        shadow = if (laShadowEnabled) laBaseShadow else null
                    )
                    // Look-ahead next uses auto-fit capped at its own configured max
                    val laAutoFitEnabled = if (isLowerThird) ss.lowerThirdLookAheadNextFontSizeAutoFit else ss.lookAheadNextFontSizeAutoFit
                    val effectiveLaFontSize = if (laAutoFitEnabled) {
                        (autoFitFontSize ?: laFontSize).coerceAtMost(laFontSize)
                    } else laFontSize
                    val scaledLaFontSize = (effectiveLaFontSize * scaleFactor).sp

                    // How each language draws its lyric lines and its look-ahead lines.
                    //
                    // Language 0, and every language that has not asked for a look of its own, get
                    // the values already resolved above rather than a freshly derived copy of them.
                    // That is not just an optimisation: those values carry the look-ahead slide's
                    // own overrides and the key-output white, and rebuilding them from the stored
                    // profile alone would quietly drop both.
                    val primaryLyricStyling = SongLineStyling(
                        profile = lyricsStyleProfile,
                        color = lyricsColor,
                        fontFamily = lyricsFontFamily,
                        fontSize = scaledLyricsFontSize,
                        textStyle = lyricsTextStyleScaled,
                    )
                    val primaryLaStyling = SongLineStyling(
                        profile = laStyleProfile,
                        color = laColor,
                        fontFamily = laFontFamily,
                        fontSize = scaledLaFontSize,
                        textStyle = lookAheadTextStyle,
                    )
                    val lyricsElement = if (lookAheadEnabled) SongStyleElement.LOOK_AHEAD else SongStyleElement.LYRICS
                    val languageLyricStyling = List(MAX_SONG_TRANSLATIONS) { language ->
                        if (!ss.languageOverridesStyle(language)) primaryLyricStyling
                        else songLineStyling(
                            profile = ss.elementStyle(lyricsElement, songTarget, language),
                            autoFitFontSize = if (autoFitEnabled) autoFitFontSize else null,
                            scaleFactor = scaleFactor,
                            isKey = isKey,
                            shadowOf = ::scaleElementShadow,
                        )
                    }
                    val languageLaStyling = List(MAX_SONG_TRANSLATIONS) { language ->
                        if (!ss.languageOverridesStyle(language)) primaryLaStyling
                        else songLineStyling(
                            profile = ss.elementStyle(SongStyleElement.NEXT_SECTION, songTarget, language),
                            autoFitFontSize = if (laAutoFitEnabled) autoFitFontSize else null,
                            scaleFactor = scaleFactor,
                            isKey = isKey,
                            shadowOf = ::scaleElementShadow,
                        )
                    }

                    // Two blocks per language, because two things divide the lines and each division
                    // wants its own box. A lyric line and a look-ahead line are drawn by one
                    // composable but styled by two profiles; and each language is its own block of
                    // text, so they get a box each rather than one box drawn around all of them.
                    // Every container goes on the same column below -- each paints only the lines
                    // that reported to it, and a block nobody reported to draws nothing.
                    //
                    // Always [MAX_SONG_TRANSLATIONS] of each, never `languageBlocks.size`: these are
                    // `remember`ed, and a list whose length changes with the song would shift every
                    // later block's slot in the composition and hand a language the box that had
                    // been painting another one's lines.
                    val lyricsBlocks = List(MAX_SONG_TRANSLATIONS) {
                        rememberTextBlockBackdrop(languageLyricStyling[it].profile.backdrop)
                    }
                    val laBlocks = List(MAX_SONG_TRANSLATIONS) {
                        rememberTextBlockBackdrop(languageLaStyling[it].profile.backdrop)
                    }

                    /**
                     * [language] says which language this line belongs to, and so which styling
                     * draws it and which backdrop block it reports to. Every language is drawn by
                     * this one composable with the same `lineIdx`, so sharing a block would have
                     * each overwrite the last line for line -- and would frame all of them as one
                     * block of text, which they are not.
                     */
                    @Composable
                    fun LyricLine(lineIdx: Int, line: String, laStart: Int, language: Int = 0) {
                        val isLookAheadLine = laStart >= 0 && lineIdx >= laStart
                        val styling =
                            if (isLookAheadLine) languageLaStyling[language] else languageLyricStyling[language]
                        val lineProfile = styling.profile
                        // The next-section lines take their own alignment once one is set; blank
                        // keeps them following the look-ahead's, which is what they always did.
                        val lineAlign = if (isLookAheadLine && lineProfile.horizontalAlignment.isNotBlank()) {
                            getTextAlign(lineProfile.horizontalAlignment)
                        } else {
                            lyricsHorizontalAlignment
                        }
                        val lineBlock = if (isLookAheadLine) laBlocks[language] else lyricsBlocks[language]
                        OutlinedText(
                            modifier = Modifier.fillMaxWidth().then(lineBlock.lineModifier(lineIdx)),
                            outline = keyedOutline(lineProfile.outline),
                            scaleFactor = scaleFactor,
                            textAlign = lineAlign,
                            fontFamily = styling.fontFamily,
                            fontSize = styling.fontSize,
                            softWrap = appSettings.songSettings.wordWrap,
                            text = styledDisplayText(
                                line,
                                lineProfile.transform,
                                spacingEm(lineProfile.letterSpacing, lineProfile.fontSize),
                                spacingEm(lineProfile.wordSpacing, lineProfile.fontSize),
                            ),
                            color = styling.color,
                            style = styling.textStyle,
                            onTextLayout = { lineBlock.onTextLayout(lineIdx, it) },
                        )
                    }

                    @Composable
                    fun LookAheadSpacer(idx: Int, laStart: Int) {
                        if (laStart >= 0 && idx == laStart && !laIsLineMode) {
                            Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                        }
                    }

                    @Composable
                    fun EndOfSongIndicator() {
                        if (!ss.showEndOfSongIndicator) return
                        // Always reserve space so lyrics don't shift when the indicator appears on the last section
                        val visible = section.isLastSection && (!isLineMode || effectiveLineIndex >= allDisplayLines.size - 1)
                        val indicatorAlpha = if (visible) 1f else 0f
                        Spacer(modifier = Modifier.padding(top = (4 * scaleFactor).dp))
                        val indicatorPad = " ".repeat(ss.endOfSongIndicatorSpacing)
                        val indicatorText = "$indicatorPad*$indicatorPad"
                        Row(modifier = Modifier.fillMaxWidth().alpha(indicatorAlpha), horizontalArrangement = Arrangement.Center) {
                            repeat(INDICATOR_REPEAT_COUNT) {
                                OutlinedText(
                                    text = AnnotatedString(indicatorText),
                                    outline = keyedOutline(lyricsStyleProfile.outline),
                                    scaleFactor = scaleFactor,
                                    fillWidth = false,
                                    fontSize = scaledLyricsFontSize,
                                    color = lyricsColor,
                                    style = lyricsTextStyleScaled,
                                )
                            }
                        }
                    }

                    // Invisible placeholder to reserve space for missing lookahead on last section
                    @Composable
                    fun LookAheadPlaceholder(block: SongLanguageBlock) {
                        if (lookAheadEnabled && block.lookAheadLines.isEmpty() && block.lines.isNotEmpty()) {
                            if (!laIsLineMode) {
                                Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                            }
                            val placeholderStyling = languageLaStyling[block.index]
                            Column(modifier = Modifier.alpha(0f)) {
                                block.lines.forEach { line ->
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = lyricsHorizontalAlignment,
                                        fontFamily = placeholderStyling.fontFamily,
                                        fontSize = placeholderStyling.fontSize,
                                        softWrap = appSettings.songSettings.wordWrap,
                                        text = line,
                                        color = placeholderStyling.color,
                                        style = placeholderStyling.textStyle,
                                    )
                                }
                            }
                        }
                    }

                    /**
                     * One language's lines — as a chord chart where this output draws one, and as
                     * plain lines everywhere else.
                     *
                     * The chart is the primary's alone: chords are written against the primary's
                     * words, and a chart drawn over a translation would put them over syllables
                     * they do not belong to.
                     */
                    @Composable
                    fun LanguageLines(block: SongLanguageBlock) {
                        if (block.index != 0 || mainChartRows.isEmpty()) {
                            block.allLines.forEachIndexed { idx, line ->
                                LookAheadSpacer(idx, block.lookAheadStart)
                                LyricLine(idx, line, block.lookAheadStart, block.index)
                            }
                            return
                        }
                        SectionChordChart(
                            lines = mainChartRows,
                            color = lyricsColor,
                            chordColor = chordColor,
                            horizontalAlignment = chartHorizontalAlignment,
                            maxFontSize = effectiveLyricsFontSize,
                            scaleFactor = scaleFactor,
                            fontFamily = lyricsFontFamily,
                            textStyle = lyricsTextStyleScaled,
                        )
                        if (laChartRows.isNotEmpty()) {
                            if (!laIsLineMode) Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                            SectionChordChart(
                                lines = laChartRows,
                                color = laColor,
                                chordColor = chordColor,
                                horizontalAlignment = chartHorizontalAlignment,
                                maxFontSize = effectiveLaFontSize,
                                scaleFactor = scaleFactor,
                                fontFamily = laFontFamily,
                                textStyle = lookAheadTextStyle,
                            )
                        }
                    }

                    // Renders title and/or song number for a given position (ABOVE_VERSE or BELOW_VERSE)
                    val samePosition = effectiveTitlePosition == effectiveSongNumberPosition
                    val sameHorizontal = (if (isLowerThird) ss.songNumberLowerThirdHorizontalAlignment else ss.songNumberHorizontalAlignment) ==
                            (if (isLowerThird) ss.titleLowerThirdHorizontalAlignment else ss.titleHorizontalAlignment)
                    val numberBeforeTitle = ss.songNumberBeforeTitle

                    @Composable
                    fun NumberPart(
                        modifier: Modifier = Modifier,
                        visibilityAlpha: Float = 1f,
                        /** False in a corner, where filling the width would drag the number out of it. */
                        fillWidth: Boolean = true,
                    ) {
                        val numberPainter = rememberTextBackdropPainter(numberStyleProfile.backdrop)
                        OutlinedText(
                            modifier = modifier.alpha(visibilityAlpha).then(numberPainter.modifier),
                            outline = keyedOutline(numberStyleProfile.outline),
                            scaleFactor = scaleFactor,
                            fillWidth = fillWidth,
                            onTextLayout = numberPainter::onTextLayout,
                            textAlign = songNumberHorizontalAlignment,
                            fontFamily = songNumberFontFamily,
                            fontSize = scaledSongNumberFontSize,
                            text = styledDisplayText(
                                section.songNumber.toString(),
                                numberStyleProfile.transform,
                                spacingEm(numberStyleProfile.letterSpacing, numberStyleProfile.fontSize),
                                spacingEm(numberStyleProfile.wordSpacing, numberStyleProfile.fontSize),
                            ),
                            color = songNumberColor,
                            style = songNumberTextStyleScaled
                        )
                    }

                    @Composable
                    fun SectionLabelPart() {
                        if (!ss.layoutExtras.sectionLabel.enabled || isTitleSlide) return
                        val label = section.header?.takeIf { it.isNotBlank() }
                            ?: section.type.replaceFirstChar { it.uppercase() }.takeIf { it.isNotBlank() }
                            ?: return
                        OutlinedText(
                            modifier = Modifier.fillMaxWidth(),
                            outline = TextOutline(),
                            scaleFactor = scaleFactor,
                            textAlign = TextAlign.Center,
                            fontFamily = titleFontFamily,
                            fontSize = (ss.layoutExtras.sectionLabel.fontSize * scaleFactor).sp,
                            text = label,
                            color = sectionLabelColor,
                            style = TextStyle.Default,
                        )
                    }

                    @Composable
                    fun TitlePart(
                        modifier: Modifier = Modifier,
                        visibilityAlpha: Float = 1f,
                        /** False beside the number in a row, where the two share the width. */
                        fillWidth: Boolean = true,
                    ) {
                        val titlePainter = rememberTextBackdropPainter(titleProfileHere.backdrop)
                        OutlinedText(
                            modifier = modifier.alpha(visibilityAlpha).then(titlePainter.modifier),
                            outline = keyedOutline(titleProfileHere.outline),
                            scaleFactor = scaleFactor,
                            fillWidth = fillWidth,
                            onTextLayout = titlePainter::onTextLayout,
                            textAlign = titleAlignHere,
                            fontFamily = titleFontFamilyHere,
                            fontSize = titleFontSizeHere,
                            text = styledDisplayText(
                                effectiveTitle,
                                titleProfileHere.transform,
                                spacingEm(titleProfileHere.letterSpacing, titleProfileHere.fontSize),
                                spacingEm(titleProfileHere.wordSpacing, titleProfileHere.fontSize),
                            ),
                            color = titleColorHere,
                            style = titleTextStyleHere
                        )
                    }

                    @Composable
                    fun TitleAndNumberRow(position: String, invisible: Boolean = false) {
                        // "configured" = setting is not None (could appear on some slides)
                        val hasTitleHere = titleConfigured && effectiveTitlePosition == position
                        val hasNumberHere = numberConfigured && !numberInCorner &&
                                effectiveSongNumberPosition == position
                        if (!hasTitleHere && !hasNumberHere) return

                        // Alpha: fully invisible when used as a balancing spacer,
                        // otherwise visible on this slide or invisible (reserving space)
                        val titleAlpha = if (invisible) 0f else if (shouldShowTitle) 1f else 0f
                        val numberAlpha = if (invisible) 0f else if (shouldShowSongNumber) 1f else 0f

                        if (hasTitleHere && hasNumberHere && samePosition) {
                            if (sameHorizontal) {
                                val sharedHAlign = if (isLowerThird) ss.songNumberLowerThirdHorizontalAlignment else ss.songNumberHorizontalAlignment
                                val arrangement = when (sharedHAlign) {
                                    Constants.LEFT -> Arrangement.Start
                                    Constants.CENTER -> Arrangement.Center
                                    else -> Arrangement.End
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = arrangement) {
                                    if (numberBeforeTitle) {
                                        NumberPart(visibilityAlpha = numberAlpha, fillWidth = false)
                                        Spacer(Modifier.padding(horizontal = (4 * scaleFactor).dp))
                                        TitlePart(visibilityAlpha = titleAlpha, fillWidth = false)
                                    } else {
                                        TitlePart(visibilityAlpha = titleAlpha, fillWidth = false)
                                        Spacer(Modifier.padding(horizontal = (4 * scaleFactor).dp))
                                        NumberPart(visibilityAlpha = numberAlpha, fillWidth = false)
                                    }
                                }
                            } else {
                                NumberPart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = numberAlpha)
                                TitlePart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = titleAlpha)
                            }
                        } else if (hasNumberHere) {
                            NumberPart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = numberAlpha)
                        } else if (hasTitleHere) {
                            TitlePart(modifier = Modifier.fillMaxWidth(), visibilityAlpha = titleAlpha)
                        }
                    }

                    // Determine which positions have content for balancing
                    val hasBottomContent = (titleConfigured && effectiveTitlePosition == Constants.BELOW_VERSE) ||
                            (numberConfigured && !numberInCorner &&
                                    effectiveSongNumberPosition == Constants.BELOW_VERSE)

                    // Outer column fills the content area; title/number at edges, lyrics centered.
                    // Every language's two containers go on it -- each paints only the lines that
                    // reported to it, so the ones for languages this slide does not draw cost a
                    // modifier and nothing else.
                    val blockContainers = (lyricsBlocks + laBlocks)
                        .fold(Modifier as Modifier) { acc, block -> acc.then(block.containerModifier) }
                    Column(modifier = Modifier.fillMaxSize().then(blockContainers)) {
                        SectionLabelPart()
                        // Top section: items positioned "above verse"
                        TitleAndNumberRow(Constants.ABOVE_VERSE)

                        // Lyrics area + bottom title/number overlaid (z-stacked).
                        // The bottom title/number floats over the lyrics so it doesn't
                        // steal vertical space and cut off lyrics text.
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            // Lyrics fill the entire remaining space
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
                            ) {
                                if (isMultiLanguage) {
                                    if (useGrid2x2) {
                                        // Two rows of up to two languages each. `chunked(2)` on
                                        // however many blocks there are: a third or fourth language
                                        // starts the second row, and a lone third fills it alone
                                        // rather than waiting on a fourth that was never selected.
                                        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                            languageBlocks.chunked(2).forEachIndexed { rowIndex, row ->
                                                if (rowIndex > 0) {
                                                    Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                                ) {
                                                    row.forEach { block ->
                                                        Column(
                                                            modifier = Modifier.weight(1f),
                                                            verticalArrangement = Arrangement.Bottom,
                                                        ) {
                                                            LanguageLines(block)
                                                            EndOfSongIndicator()
                                                            LookAheadPlaceholder(block)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (useSideBySide) {
                                        // A column each, equally weighted. `SpaceEvenly` and equal
                                        // weights agree at any count, so three and four languages
                                        // divide the width the way two always did.
                                        Row(
                                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            languageBlocks.forEach { block ->
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.Bottom,
                                                ) {
                                                    LanguageLines(block)
                                                    EndOfSongIndicator()
                                                    LookAheadPlaceholder(block)
                                                }
                                            }
                                        }
                                    } else if (isLowerThird) {
                                        // Lower third: compact stack, no height splitting -- a band
                                        // is too short to give each language a share of it.
                                        Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                            languageBlocks.forEachIndexed { position, block ->
                                                if (position > 0) {
                                                    Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                                                }
                                                LanguageLines(block)
                                                EndOfSongIndicator()
                                                LookAheadPlaceholder(block)
                                            }
                                        }
                                    } else {
                                        // Full screen: a band of the height each, equally weighted.
                                        val bandAlignment = contentAlignment
                                        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                                            languageBlocks.forEachIndexed { position, block ->
                                                if (position > 0) {
                                                    Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                                                }
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                                    contentAlignment = bandAlignment,
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                                        LanguageLines(block)
                                                        EndOfSongIndicator()
                                                        LookAheadPlaceholder(block)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Single language layout
                                    val onlyBlock = languageBlocks.firstOrNull()
                                    Column(
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                        verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top
                                    ) {
                                        if (onlyBlock != null) {
                                            LanguageLines(onlyBlock)
                                            EndOfSongIndicator()
                                            LookAheadPlaceholder(onlyBlock)
                                        }
                                    }
                                }
                            }

                            // Bottom title/number overlaid at the bottom of the lyrics area
                            if (hasBottomContent) {
                                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                                    TitleAndNumberRow(Constants.BELOW_VERSE)
                                }
                            }
                        }
                    }

                    // The number pinned to a corner, drawn over the slide rather than in the row it
                    // would otherwise share with the title: it costs the lyrics no height and does
                    // not shift when the title's row grows or is left off a slide.
                    if (numberInCorner && shouldShowSongNumber) {
                        val numberOffset =
                            if (isLowerThird) ss.layoutExtras.numberLowerThirdOffset else ss.layoutExtras.numberOffset
                        NumberPart(
                            modifier = Modifier.songNumberCornerOffset(songNumberCorner, numberOffset),
                            fillWidth = false,
                        )
                    }
                }
            }

            if (crossfadeEnabled || ss.fadeIn || ss.fadeOut) {
                val duration = ss.transitionDuration.toInt().coerceAtLeast(100)
                val isCrossfade = crossfadeEnabled
                // Each layer carries the line it draws, not just its section. Sharing one live line
                // index made the outgoing layer redraw its old section at the incoming line for as
                // long as the crossfade ran, which is a flash of a line that was never on that page.
                var displayedCurrent by remember {
                    mutableStateOf(SongCrossfadePage(lyricSection, displayLineIndex))
                }
                var displayedPrevious by remember { mutableStateOf(SongCrossfadePage(LyricSection(), -1)) }
                var currentAlpha by remember { mutableStateOf(1f) }
                var previousAlpha by remember { mutableStateOf(0f) }
                val pendingQueue = remember { Channel<SongCrossfadePage>(Channel.CONFLATED) }

                // Queue section changes
                LaunchedEffect(lyricSection, displayLineIndex) {
                    val target = SongCrossfadePage(lyricSection, displayLineIndex)
                    when {
                        displayedCurrent == target -> Unit
                        // Stepping a line inside the section already up has never crossfaded, and
                        // queueing it would fade the section out against itself.
                        displayedCurrent.section == target.section -> displayedCurrent = target
                        else -> pendingQueue.send(target)
                    }
                }

                // Process section switches (crossfade between sections)
                LaunchedEffect(Unit) {
                    for (nextPage in pendingQueue) {
                        if (displayedCurrent == nextPage) continue

                        if (isCrossfade) {
                            displayedPrevious = displayedCurrent
                            displayedCurrent = nextPage
                            previousAlpha = 1f
                            currentAlpha = 0f
                            val anim = Animatable(0f)
                            anim.animateTo(1f, tween(durationMillis = duration)) {
                                currentAlpha = this.value
                                previousAlpha = 1f - this.value
                            }
                        } else {
                            displayedCurrent = nextPage
                        }
                        currentAlpha = 1f
                        previousAlpha = 0f
                        displayedPrevious = SongCrossfadePage(LyricSection(), -1)
                    }
                }

                Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = transitionAlpha }) {
                    if (displayedPrevious.section.lines.isNotEmpty() && previousAlpha > 0f) {
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previousAlpha }) {
                            TextContent(displayedPrevious.section, displayedPrevious.lineIndex)
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = currentAlpha }) {
                        TextContent(displayedCurrent.section, displayedCurrent.lineIndex)
                    }
                }
            } else {
                Box(modifier = Modifier.graphicsLayer { alpha = transitionAlpha }) {
                    TextContent(lyricSection, displayLineIndex)
                }
            }
        }
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
private fun SectionChordChart(
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
private fun LyricSection.isSamePageAs(other: LyricSection): Boolean =
    header == other.header && slideIndex == other.slideIndex && type == other.type && lines == other.lines

private fun shouldShowText(display: String, lyricSection: LyricSection): Boolean {
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

private fun getTextAlign(alignment: String): TextAlign {
    return when (alignment) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }
}

/** One crossfade layer's page: the section it draws and the line within it, kept together. */
private data class SongCrossfadePage(val section: LyricSection, val lineIndex: Int)
