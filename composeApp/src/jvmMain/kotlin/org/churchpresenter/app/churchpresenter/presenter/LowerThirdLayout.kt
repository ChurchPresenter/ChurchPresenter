/*
 * How a lower-third output lays out the content that has no band of its own.
 *
 * Every dispatcher from Presenting to a presenter — the output windows, the live preview and the
 * off-screen Browser Source / NDI render — wraps its content in [LowerThirdLayout], so the three
 * cannot disagree about where a video or a slide sits on a lower third.
 */
package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import org.churchpresenter.app.churchpresenter.composables.CameraBackground
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.LowerThirdPlacement
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PlaceableContent
import org.churchpresenter.settings.utils.Constants

/** Which [PlaceableContent] [this] mode is, or null for one that places itself or draws nothing. */
internal fun Presenting.placeableContent(): PlaceableContent? = when (this) {
    Presenting.MEDIA -> PlaceableContent.MEDIA
    Presenting.PRESENTATION -> PlaceableContent.PRESENTATION
    Presenting.PICTURES -> PlaceableContent.PICTURES
    Presenting.WEBSITE -> PlaceableContent.WEBSITE
    Presenting.CANVAS -> PlaceableContent.CANVAS
    else -> null
}

/**
 * The band's height as a fraction of the output, for content that has no band of its own.
 *
 * The Bible band's height, because there is no Default Lower Third height to take instead and the
 * Bible band is the one every lower-third output has.
 */
internal fun placedBandFraction(appSettings: AppSettings): Float =
    appSettings.bibleSettings.lowerThirdHeightPercent / PERCENT

/**
 * [content] as [mode] sits on this output.
 *
 * Off a lower third this is [content] and nothing else. On one:
 * - the standalone Lottie lower third gets the Default Lower Third's area-above-the-band drawn
 *   behind it — the Lottie draws only its band, so without this the rest of the output was black;
 * - media, slides, pictures, web pages and canvas scenes sit wherever [OutputProfile.placementFor]
 *   says: the whole output, or clipped to the band's rectangle at the bottom.
 */
@Composable
internal fun LowerThirdLayout(
    mode: Presenting,
    profile: OutputProfile,
    appSettings: AppSettings,
    showBackground: Boolean,
    content: @Composable () -> Unit,
) {
    if (!profile.isLowerThird) {
        content()
        return
    }
    val bandFraction = placedBandFraction(appSettings)
    val placement = mode.placeableContent()?.let(profile::placementFor)
    when {
        mode == Presenting.LOWER_THIRD -> Box(Modifier.fillMaxSize()) {
            AboveBandFill(
                above = resolveAboveBand(
                    appSettings.backgroundSettings,
                    BackgroundConfig(aboveBandType = Constants.BACKGROUND_DEFAULT),
                ),
                bandFraction = bandFraction,
                show = showBackground,
            )
            content()
        }
        placement == LowerThirdPlacement.IN_BAND -> Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(bandFraction)
                    .align(Alignment.BottomCenter)
                    .clipToBounds(),
            ) { content() }
        }
        else -> content()
    }
}

/**
 * The clip or live camera [background] names, filling the box it is called in at the background's
 * own opacity. Nothing for any other type — a colour, a gradient and a picture are all drawn by
 * [backgroundModifier] or an `Image`, which need no player behind them.
 *
 * The one copy of this for everything drawn inside a lower-third band or above it. The band used
 * to carry only the clip, so a camera band drew [backgroundModifier]'s black placeholder and never
 * the camera itself.
 */
@Composable
internal fun BandMediaLayers(background: ResolvedBackground) {
    if (background.usesVideo) {
        LoopingVideoBackground(
            videoPath = background.videoPath,
            modifier = Modifier.fillMaxSize().alpha(background.opacity),
        )
    }
    if (background.usesCamera) {
        CameraBackground(
            camera = background.camera,
            modifier = Modifier.fillMaxSize().alpha(background.opacity),
        )
    }
}
