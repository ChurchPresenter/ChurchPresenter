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
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.min
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.settings.AppSettings

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.MAX_SONG_TRANSLATIONS
import org.churchpresenter.settings.songLanguageSelection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.composables.ChordChart
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitForAllSections
import org.churchpresenter.app.churchpresenter.utils.calculateChordChartFontSize
import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.app.churchpresenter.composables.CameraDeviceCatalog
import org.churchpresenter.app.churchpresenter.composables.cameraResolves
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import androidx.compose.ui.unit.em
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleTarget
import org.churchpresenter.app.churchpresenter.dialogs.tabs.elementStyle
import org.churchpresenter.app.churchpresenter.utils.combinedTextDecoration
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.app.churchpresenter.utils.styledDisplayText
import java.io.File

private const val SHADOW_OFFSET_PX = 6f
private const val INDICATOR_REPEAT_COUNT = 3

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
    allLyricSections: List<LyricSection> = emptyList(),
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
    val availableLanguages = maxOf(
        lyricSection.translations.size,
        allLyricSections.maxOfOrNull { it.translations.size } ?: 0,
    ) + 1
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
    val lyricsColor = remember(ss.lyricsColor, ss.lyricsLowerThirdColor,
        ss.lookAheadColor, ss.lowerThirdLookAheadColor, isLowerThird, lookAheadEnabled, isKey) {
        if (isKey) Color.White
        else if (lookAheadEnabled) {
            parseHexColor(if (isLowerThird) ss.lowerThirdLookAheadColor else ss.lookAheadColor)
        } else {
            parseHexColor(if (isLowerThird) ss.lyricsLowerThirdColor else ss.lyricsColor)
        }
    }
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
        val density = LocalDensity.current
        val widthScale = with(density) { maxWidth.toPx() / 1920f }
        val heightScale = with(density) { maxHeight.toPx() / 1080f }
        val scaleFactor = min(widthScale, heightScale).coerceIn(0.5f, 3.0f)
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
        val autoFitFontSize = remember(allLyricSections, isLowerThird, lookAheadEnabled, languageOverride, appSettings.songSettings, appSettings.projectionSettings) {
            if (allLyricSections.isEmpty()) null
            else {
                // How many blocks the frame is actually divided into. One language fills it; more
                // split it, in whichever direction the layout says.
                val drawnLanguages = activeLanguages.size.coerceAtLeast(1)
                val sideBySide = drawnLanguages > 1 &&
                        ss.bilingualLayout == Constants.BILINGUAL_SIDE_BY_SIDE
                val topBottom = drawnLanguages > 1 &&
                        ss.bilingualLayout == Constants.BILINGUAL_TOP_BOTTOM

                val fullWidth = 1920 - appSettings.projectionSettings.windowLeft - appSettings.projectionSettings.windowRight -
                        appSettings.songSettings.marginLeft - appSettings.songSettings.marginRight
                // Side by side, each language gets a column; the fit has to hold in the narrowest
                // of them, which with equal weights is every one of them.
                val refWidth = if (sideBySide) fullWidth / drawnLanguages else fullWidth
                val fullHeight = if (isLowerThird) {
                    (1080 * appSettings.songSettings.lowerThirdHeightPercent / 100) -
                            appSettings.projectionSettings.windowTop - appSettings.projectionSettings.windowBottom -
                            appSettings.songSettings.marginTop - appSettings.songSettings.marginBottom
                } else {
                    1080 - appSettings.projectionSettings.windowTop - appSettings.projectionSettings.windowBottom -
                            appSettings.songSettings.marginTop - appSettings.songSettings.marginBottom
                }
<<<<<<< Updated upstream
                // In top/bottom bilingual mode, each language gets half the height
                val refHeight = if (topBottom) fullHeight / 2 else fullHeight
=======
                // Stacked, each language gets a band of the height on the same reasoning.
                val refHeight = if (topBottom) fullHeight / drawnLanguages else fullHeight
                // The same tracking the lines are drawn with. Spacing is stored in pixels against
                // the profile's own font size and converted to `em`, so the value does not change
                // as the search tries sizes -- it scales with whichever one it settles on, exactly
                // as the rendered line does.
                val fitLetterEm = spacingEm(lyricsStyleProfile.letterSpacing, lyricsStyleProfile.fontSize)
                val fitWordEm = spacingEm(lyricsStyleProfile.wordSpacing, lyricsStyleProfile.fontSize)
>>>>>>> Stashed changes
                val baseStyle = TextStyle(
                    fontWeight = if (effectiveLyricsBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (effectiveLyricsItalic) FontStyle.Italic else FontStyle.Normal,
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
                if (fitNumberDisplay != Constants.NONE && fitNumberPosition == Constants.ABOVE_VERSE) {
                    val numStyle = TextStyle(fontSize = fitNumberFontSize.sp, fontFamily = titleFontFamily)
                    val maxNum = allLyricSections.maxOfOrNull { it.songNumber } ?: 0
                    if (maxNum > 0) {
                        reserved += autoFitTextMeasurer.measure(maxNum.toString(), numStyle, density = referenceDensity).size.height
                    }
                }

                calculateAutoFitForAllSections(
                    textMeasurer = autoFitTextMeasurer,
                    sections = sectionsForFit,
                    baseStyle = baseStyle,
                    availableWidth = refWidth,
                    availableHeight = refHeight,
                    reservedHeight = reserved,
                    includeEndIndicator = true
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
                .padding(start = leftOffSet, end = rightOffSet, top = topOffSet, bottom = bottomOffSet),
            contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
        ) {
            val innerModifier = if (isLowerThird)
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(appSettings.songSettings.lowerThirdHeightPercent / 100f)
                    .align(Alignment.BottomCenter)
            else
                Modifier

            // Only animate the text content — background is never inside this block
            @Composable
            fun TextContent(section: LyricSection) {
                val titleDisplay = if (isLowerThird) ss.titleLowerThirdDisplay else ss.titleDisplay
                val numberDisplay = if (isLowerThird) ss.showNumberLowerThird else ss.showNumber
                val shouldShowTitle = shouldShowText(titleDisplay, section)
                val shouldShowSongNumber = shouldShowText(numberDisplay, section) && section.songNumber > 0
                // "Configured" means not set to "None" — title/number could appear on some slides
                val titleConfigured = titleDisplay != Constants.NONE
                val numberConfigured = numberDisplay != Constants.NONE && section.songNumber > 0
                val effectiveTitlePosition = if (isLowerThird) ss.titleLowerThirdPosition else ss.titlePosition
                val effectiveSongNumberPosition = if (isLowerThird) ss.songNumberLowerThirdPosition else ss.songNumberPosition
                // isLowerThirdVertical forces bilingual content to stack (one below the other)
                // instead of side-by-side — see the useSideBySide gate further below — same
                // band/geometry as horizontal otherwise.
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
                    val effectiveLineIndex = if (isLineMode && displayLineIndex < 0) 0 else displayLineIndex

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

                    val isMultiLanguage = languageBlocks.size > 1
                    // A Row-split side-by-side layout doesn't fit a narrow vertical band — falls
                    // through to the stacked branch below, which already special-cases
                    // isLowerThird (true for vertical too) with a compact stacked layout.
                    val useSideBySide = appSettings.songSettings.bilingualLayout == Constants.BILINGUAL_SIDE_BY_SIDE && !isLowerThirdVertical

                    // Look-ahead text style with full font controls
                    val laBaseShadow = Shadow(
                        color = laShadowColor.copy(alpha = laShadowAlpha),
                        offset = Offset(6f * scaleFactor * laShadowSizeMul, 6f * scaleFactor * laShadowSizeMul),
                        blurRadius = 12f * scaleFactor * laShadowSizeMul
                    )
                    val laStyleProfile = ss.elementStyle(SongStyleElement.NEXT_SECTION, songTarget)
<<<<<<< Updated upstream
=======

>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
                    @Composable
                    fun LyricLine(lineIdx: Int, line: String, laStart: Int) {
=======
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
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
=======
                        val lineBlock = if (isLookAheadLine) laBlocks[language] else lyricsBlocks[language]
>>>>>>> Stashed changes
                        Text(
                            modifier = Modifier.fillMaxWidth(),
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
<<<<<<< Updated upstream
                            color = if (isLookAheadLine) laColor else lyricsColor,
                            style = if (isLookAheadLine) lookAheadTextStyle else lyricsTextStyleScaled
=======
                            color = styling.color,
                            style = styling.textStyle,
                            onTextLayout = { lineBlock.onTextLayout(lineIdx, it) },
>>>>>>> Stashed changes
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
                        // Always reserve space so lyrics don't shift when the indicator appears on the last section
                        val visible = section.isLastSection && (!isLineMode || effectiveLineIndex >= allDisplayLines.size - 1)
                        val indicatorAlpha = if (visible) 1f else 0f
                        Spacer(modifier = Modifier.padding(top = (4 * scaleFactor).dp))
                        val indicatorPad = " ".repeat(ss.endOfSongIndicatorSpacing)
                        val indicatorText = "$indicatorPad*$indicatorPad"
                        Row(modifier = Modifier.fillMaxWidth().alpha(indicatorAlpha), horizontalArrangement = Arrangement.Center) {
                            repeat(INDICATOR_REPEAT_COUNT) { Text(text = indicatorText, fontSize = scaledLyricsFontSize, color = lyricsColor, style = lyricsTextStyleScaled) }
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
                    fun NumberPart(modifier: Modifier = Modifier, visibilityAlpha: Float = 1f) {
                        Text(
                            modifier = modifier.alpha(visibilityAlpha),
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
                    fun TitlePart(modifier: Modifier = Modifier, visibilityAlpha: Float = 1f) {
                        Text(
                            modifier = modifier.alpha(visibilityAlpha),
                            textAlign = titleHorizontalAlignment,
                            fontFamily = titleFontFamily,
                            fontSize = scaledTitleFontSize,
                            text = styledDisplayText(
                                effectiveTitle,
                                titleStyleProfile.transform,
                                spacingEm(titleStyleProfile.letterSpacing, titleStyleProfile.fontSize),
                                spacingEm(titleStyleProfile.wordSpacing, titleStyleProfile.fontSize),
                            ),
                            color = titleColor,
                            style = titleTextStyleScaled
                        )
                    }

                    @Composable
                    fun TitleAndNumberRow(position: String, invisible: Boolean = false) {
                        // "configured" = setting is not None (could appear on some slides)
                        val hasTitleHere = titleConfigured && effectiveTitlePosition == position
                        val hasNumberHere = numberConfigured && effectiveSongNumberPosition == position
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
                                        NumberPart(visibilityAlpha = numberAlpha); Spacer(modifier = Modifier.padding(horizontal = (4 * scaleFactor).dp)); TitlePart(visibilityAlpha = titleAlpha)
                                    } else {
                                        TitlePart(visibilityAlpha = titleAlpha); Spacer(modifier = Modifier.padding(horizontal = (4 * scaleFactor).dp)); NumberPart(visibilityAlpha = numberAlpha)
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
                            (numberConfigured && effectiveSongNumberPosition == Constants.BELOW_VERSE)

<<<<<<< Updated upstream
                    // Outer column fills the content area; title/number at edges, lyrics centered
                    Column(modifier = Modifier.fillMaxSize()) {
=======
                    // Outer column fills the content area; title/number at edges, lyrics centered.
                    // Every language's two containers go on it -- each paints only the lines that
                    // reported to it, so the ones for languages this slide does not draw cost a
                    // modifier and nothing else.
                    val blockContainers = (lyricsBlocks + laBlocks)
                        .fold(Modifier as Modifier) { acc, block -> acc.then(block.containerModifier) }
                    Column(modifier = Modifier.fillMaxSize().then(blockContainers)) {
>>>>>>> Stashed changes
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
                                    if (useSideBySide) {
                                        // A column each, equally weighted. `SpaceEvenly` and equal
                                        // weights agree at any count, so three and four languages
                                        // divide the width the way two always did.
                                        Row(
                                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
<<<<<<< Updated upstream
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
                                                PrimaryLines()
                                                EndOfSongIndicator()
                                                LookAheadPlaceholder()
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) {
                                                combinedSecondaryLines.forEachIndexed { idx, line ->
                                                    LookAheadSpacer(idx, secondaryLaStart)
                                                    LyricLine(idx, line, secondaryLaStart)
=======
                                            languageBlocks.forEach { block ->
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.Bottom,
                                                ) {
                                                    LanguageLines(block)
                                                    EndOfSongIndicator()
                                                    LookAheadPlaceholder(block)
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
                                        // Top/bottom bilingual layout
                                        if (isLowerThird) {
                                            // Lower third: compact layout, no height splitting
                                            Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                                PrimaryLines()
                                                EndOfSongIndicator()
                                                LookAheadPlaceholder()
                                                Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                                                combinedSecondaryLines.forEachIndexed { idx, line ->
                                                    LookAheadSpacer(idx, secondaryLaStart)
                                                    LyricLine(idx, line, secondaryLaStart)
=======
                                        // Full screen: a band of the height each, equally weighted.
                                        val bandAlignment = contentAlignment
                                        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                                            languageBlocks.forEachIndexed { position, block ->
                                                if (position > 0) {
                                                    Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
>>>>>>> Stashed changes
                                                }
                                                Box(
                                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                                    contentAlignment = bandAlignment,
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                                        LanguageLines(block)
                                                        EndOfSongIndicator()
<<<<<<< Updated upstream
                                                        LookAheadPlaceholder()
                                                    }
                                                }
                                                Spacer(modifier = Modifier.padding(top = (12 * scaleFactor).dp))
                                                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = halfAlignment) {
                                                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                                        combinedSecondaryLines.forEachIndexed { idx, line ->
                                                            LookAheadSpacer(idx, secondaryLaStart)
                                                            LyricLine(idx, line, secondaryLaStart)
                                                        }
                                                        EndOfSongIndicator()
                                                        LookAheadPlaceholder()
=======
                                                        LookAheadPlaceholder(block)
>>>>>>> Stashed changes
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
                }
            }

            if (crossfadeEnabled || ss.fadeIn || ss.fadeOut) {
                val duration = ss.transitionDuration.toInt().coerceAtLeast(100)
                val isCrossfade = crossfadeEnabled
                var displayedCurrent by remember { mutableStateOf(lyricSection) }
                var displayedPrevious by remember { mutableStateOf(LyricSection()) }
                var currentAlpha by remember { mutableStateOf(1f) }
                var previousAlpha by remember { mutableStateOf(0f) }
                val pendingQueue = remember { kotlinx.coroutines.channels.Channel<LyricSection>(kotlinx.coroutines.channels.Channel.CONFLATED) }

                // Queue section changes
                LaunchedEffect(lyricSection) {
                    if (displayedCurrent != lyricSection) {
                        pendingQueue.send(lyricSection)
                    }
                }

                // Process section switches (crossfade between sections)
                LaunchedEffect(Unit) {
                    for (nextSection in pendingQueue) {
                        if (displayedCurrent == nextSection) continue

                        if (isCrossfade) {
                            displayedPrevious = displayedCurrent
                            displayedCurrent = nextSection
                            previousAlpha = 1f
                            currentAlpha = 0f
                            val anim = Animatable(0f)
                            anim.animateTo(1f, tween(durationMillis = duration)) {
                                currentAlpha = this.value
                                previousAlpha = 1f - this.value
                            }
                        } else {
                            displayedCurrent = nextSection
                        }
                        currentAlpha = 1f
                        previousAlpha = 0f
                        displayedPrevious = LyricSection()
                    }
                }

                Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = transitionAlpha }) {
                    if (displayedPrevious.lines.isNotEmpty() && previousAlpha > 0f) {
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previousAlpha }) {
                            TextContent(displayedPrevious)
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = currentAlpha }) {
                        TextContent(displayedCurrent)
                    }
                }
            } else {
                Box(modifier = Modifier.graphicsLayer { alpha = transitionAlpha }) {
                    TextContent(lyricSection)
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
