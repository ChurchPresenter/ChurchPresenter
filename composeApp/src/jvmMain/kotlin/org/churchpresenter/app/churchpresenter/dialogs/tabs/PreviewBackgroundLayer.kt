package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.BackgroundConfigFill
import org.churchpresenter.app.churchpresenter.composables.CheckerboardFill
import org.churchpresenter.app.churchpresenter.presenter.ABOVE_BAND_OVERLAP_FRACTION
import org.churchpresenter.app.churchpresenter.presenter.AboveBand
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.backgroundBlurRadius
import org.churchpresenter.app.churchpresenter.presenter.lowerThirdBandFraction
import org.churchpresenter.app.churchpresenter.presenter.resolveAboveBand
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.profile_preview_note_above_band
import churchpresenter.composeapp.generated.resources.profile_preview_note_camera
import churchpresenter.composeapp.generated.resources.profile_preview_note_matches
import churchpresenter.composeapp.generated.resources.profile_preview_note_video

/**
 * What the preview draws behind the text: the real background, nothing, or a checkerboard in its
 * place.
 *
 * Preview-only state, never stored. Off is there to judge the type on its own, and Checker to judge
 * it against a busy ground -- a full screen shows the checkerboard where its background was, and a
 * lower third keeps its band with the checkerboard above it, which is what a keyer downstream cuts.
 */
internal enum class PreviewBackgroundMode { ACTUAL, OFF, CHECKER }

/**
 * Whether the page's preview has a ground to switch -- every page but Stage layout, which draws a
 * diagram of zones rather than an output.
 */
internal fun CustomizePane?.hasPreviewBackground(): Boolean = this != null && this != CustomizePane.STAGE_MONITOR

/** Which content's background the preview stands for. */
internal enum class PreviewBackgroundSurface { BIBLE, SONGS }

/** How coarse the preview's checkerboard is: fine enough to read as one, coarse enough to see. */
internal val PREVIEW_CHECKER_SQUARE = 14.dp

/**
 * The preview checkerboard's two greys: mid-tones, whatever the app's theme, so white text and black
 * text both read against it -- the theme's own light surfaces all but hid white verse text.
 */
private val PREVIEW_CHECKER_LIGHT = Color(0xFF8C8C8C)
private val PREVIEW_CHECKER_DARK = Color(0xFF6B6B6B)

/** The checkerboard [PreviewBackgroundMode.CHECKER] puts in place of a background. */
@Composable
internal fun PreviewCheckerboard(modifier: Modifier) {
    CheckerboardFill(modifier, square = PREVIEW_CHECKER_SQUARE, colors = PREVIEW_CHECKER_LIGHT to PREVIEW_CHECKER_DARK)
}

/**
 * The background an output on [profile] draws behind [surface], as a still, filling the preview.
 *
 * Drawn from the same chain the presenter walks -- the surface's own config, falling through its
 * `Default` to the profile's default and on to the Background tab's ([resolvedConfigFor]) -- and
 * gated on the profile's own background switches exactly as the output gates them, so a profile
 * that draws no Bible background previews none.
 *
 * **A lower third is drawn as the band it is**: the surface fills a band [lowerThirdBandFraction]
 * tall at the bottom, and above it goes the wash the surface is set to ([resolveAboveBand]) over
 * black. In [PreviewBackgroundMode.CHECKER] the checkerboard replaces the background -- all of it on
 * a full screen, the wash above the band on a lower third. A Lottie band is left to the presenter,
 * which draws it with the text because it *is* the text.
 *
 * [settings] is already resolved for the profile (`resolvedFor`); nothing is resolved twice.
 */
@Composable
internal fun BoxScope.PreviewBackgroundLayer(
    settings: AppSettings,
    profile: OutputProfile,
    surface: PreviewBackgroundSurface,
    lowerThird: Boolean,
    mode: PreviewBackgroundMode,
) {
    if (mode == PreviewBackgroundMode.OFF) return
    val backgrounds = settings.backgroundSettings
    val scope = when (surface) {
        PreviewBackgroundSurface.BIBLE -> if (lowerThird) BackgroundScope.BIBLE_LOWER_THIRD else BackgroundScope.BIBLE
        PreviewBackgroundSurface.SONGS -> if (lowerThird) BackgroundScope.SONG_LOWER_THIRD else BackgroundScope.SONG
    }
    val shown = (if (lowerThird) profile.showLowerThirdBackground else profile.showFullscreenBackground) &&
        (if (surface == PreviewBackgroundSurface.BIBLE) profile.showBibleBackground else profile.showSongsBackground)
    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        val previewWidth = maxWidth
        val checker = mode == PreviewBackgroundMode.CHECKER
        if (checker) {
            PreviewCheckerboard(Modifier.fillMaxSize())
        }
        if (!shown) return@BoxWithConstraints
        val band = backgrounds.resolvedConfigFor(scope)
        if (!lowerThird) {
            if (!checker) SurfaceStill(band, previewWidth, Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val fraction = settings.lowerThirdBandFraction(
            if (surface == PreviewBackgroundSurface.BIBLE) Presenting.BIBLE else Presenting.LYRICS,
        )
        val above = if (checker) AboveBand(fill = null, fillsBehindBand = false) else
            resolveAboveBand(backgrounds, backgrounds.configFor(scope))
        val aboveArea = if (above.fillsBehindBand) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .fillMaxHeight((1f - fraction + ABOVE_BAND_OVERLAP_FRACTION).coerceAtMost(1f))
                .align(Alignment.TopCenter)
        }
        above.fill?.let { Box(modifier = aboveArea.background(it)) }
        above.media?.let { media ->
            Box(modifier = aboveArea.clipToBounds()) {
                SurfaceStill(
                    BackgroundConfig(
                        backgroundType = media.type,
                        backgroundImage = media.imagePath,
                        backgroundVideo = media.videoPath,
                        backgroundOpacity = media.opacity,
                        camera = media.camera,
                    ),
                    previewWidth,
                    Modifier.fillMaxSize(),
                )
            }
        }
        if (band.backgroundType != Constants.BACKGROUND_LOTTIE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .align(Alignment.BottomCenter)
                    .clipToBounds(),
            ) {
                SurfaceStill(band, previewWidth, Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * One surface as a still, or nothing for a transparent one -- the checkerboard or the black plate
 * beneath already says what a transparent output shows.
 */
@Composable
private fun SurfaceStill(config: BackgroundConfig, previewWidth: Dp, modifier: Modifier) {
    if (config.backgroundType == Constants.BACKGROUND_TRANSPARENT) return
    BackgroundConfigFill(
        config = config,
        modifier = modifier,
        blurRadius = backgroundBlurRadius(config.blur, previewWidth),
        stills = true,
    )
}

/**
 * The line under the preview: what about the picture differs from the output, if anything.
 *
 * A clip shows its first frame and a camera one snapshot, and the area above a lower third's band
 * is often not drawn at all -- each of which reads as a fault unless the preview says so.
 */
@Composable
internal fun previewNote(pane: CustomizePane, settings: AppSettings, profile: OutputProfile): String {
    val matches = stringResource(
        Res.string.profile_preview_note_matches,
        previewShapeLabel(profile.previewWidth, profile.previewHeight),
    )
    val lowerThird = profile.isLowerThird
    val scope = when (pane) {
        CustomizePane.BIBLE -> if (lowerThird) BackgroundScope.BIBLE_LOWER_THIRD else BackgroundScope.BIBLE
        CustomizePane.SONGS -> if (lowerThird) BackgroundScope.SONG_LOWER_THIRD else BackgroundScope.SONG
        else -> return matches
    }
    val backgrounds = settings.backgroundSettings
    if (lowerThird) {
        val above = resolveAboveBand(backgrounds, backgrounds.configFor(scope))
        if (above.fill == null && above.media == null) {
            return stringResource(Res.string.profile_preview_note_above_band)
        }
    }
    return when (backgrounds.resolvedConfigFor(scope).backgroundType) {
        Constants.BACKGROUND_VIDEO -> stringResource(Res.string.profile_preview_note_video)
        Constants.BACKGROUND_CAMERA -> stringResource(Res.string.profile_preview_note_camera)
        else -> matches
    }
}
