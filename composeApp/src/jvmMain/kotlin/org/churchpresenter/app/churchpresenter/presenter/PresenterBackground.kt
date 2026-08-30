/*
 * What a presenter draws behind its text, and who gets to decide it.
 *
 * SongPresenter and BiblePresenter are the only two presenters that draw a background from
 * settings, and they resolved it with the same forty lines twice — which is why the per-song
 * background's gradient, dim and blur reached songs only. They are one function now, so a
 * background that can be drawn on a song can be drawn on a verse.
 */
package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.utils.PictureDecoder
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File

/** How far a blurred background is scaled up so its faded edge lands off screen. */
internal const val BACKGROUND_BLUR_OVERSCAN = 1.08f

/** A percentage as a fraction. */
internal const val PERCENT = 100f

/** The output width every stored background size is measured against. */
internal const val BACKGROUND_REFERENCE_WIDTH = 1920f

/**
 * How tall the lower-third band is, as a fraction of the output — which is exactly how much of the
 * screen a lower-third background paints, the default one included.
 *
 * Bible and Songs each carry their own band height, so the answer depends on what is live. Anything
 * else — nothing on screen, or a content type that has no band — takes the taller of the two, the
 * largest area a default can be showing in.
 */
internal fun AppSettings.lowerThirdBandFraction(mode: Presenting?): Float = when (mode) {
    Presenting.BIBLE -> bibleSettings.lowerThirdHeightPercent
    Presenting.LYRICS -> songSettings.lowerThirdHeightPercent
    else -> maxOf(bibleSettings.lowerThirdHeightPercent, songSettings.lowerThirdHeightPercent)
}.toFloat() / PERCENT

/**
 * The blur radius for a background stored against a 1920-wide output, drawn [width] wide.
 *
 * Takes dp and not pixels on purpose. A blur is a *fraction of the picture* — the same background
 * has to look equally soft on a 1080p projector and on a HiDPI panel — and `Modifier.blur` already
 * multiplies the Dp it is given by the density. Measuring the width in pixels as well counted the
 * density twice, so the same setting came out twice as soft on a retina output as on a projector,
 * and neither agreed with the Background tab's preview.
 *
 * The presenters' `scaleFactor` keeps its pixel measurement: it sizes text and margins, which is a
 * separate question from this one.
 */
internal fun backgroundBlurRadius(blurReferencePx: Int, width: Dp): Dp =
    (blurReferencePx * (width.value / BACKGROUND_REFERENCE_WIDTH)).dp

/** What a presenter actually draws, once every source has had its say. */
internal data class ResolvedBackground(
    val type: String,
    val imagePath: String,
    val videoPath: String,
    val color: Color,
    /** The far end of a gradient; null for everything else. Only a [SongBackground] sets one. */
    val gradientEndColor: Color? = null,
    val opacity: Float = 1f,
    /** Percent of black washed over the background, 0–100. */
    val dimPercent: Int = 0,
    /** Blur radius in the 1920×1080 reference space the presenters measure in. */
    val blurReferencePx: Int = 0,
) {
    val usesVideo: Boolean get() = type == Constants.BACKGROUND_VIDEO && videoPath.isNotEmpty()
    val isBlurred: Boolean get() = blurReferencePx > 0
}

/**
 * Which background wins.
 *
 * In order: a blanked output draws nothing at all; then the quick tray's live pick, which outranks
 * everything because an operator reaching for it mid-service is overriding what is on screen right
 * now; then the content's own background, which today means a song's; then this content type's
 * configured background, falling through to the defaults when it says Default.
 *
 * A picture or clip that no longer resolves on this machine is skipped rather than drawn black —
 * a song file and a settings export both travel, and the media they name may not.
 */
@Composable
internal fun resolveBackground(
    settings: BackgroundSettings,
    config: BackgroundConfig,
    isLowerThird: Boolean,
    showBackground: Boolean,
    transparentWhenBlank: Boolean,
    /** The background this content carries itself, if any. Bible verses carry none. */
    ownBackground: SongBackground = SongBackground(),
): ResolvedBackground {
    val override = if (isLowerThird) settings.quickLowerThirdBackground else settings.quickBackground
    // Both remembered unconditionally: a `&&` short-circuit here would be a conditional remember.
    val overrideDraws = remember(override) { override != null && songBackgroundResolves(override) }
    val ownDraws = remember(ownBackground) { ownBackground.isCustom && songBackgroundResolves(ownBackground) }

    val live = when {
        !showBackground -> null
        overrideDraws -> override
        ownDraws -> ownBackground
        else -> null
    }

    return when {
        // Browser Source scenes blank to transparent (OBS keying); projector windows to black.
        !showBackground -> ResolvedBackground(
            type = if (transparentWhenBlank) Constants.BACKGROUND_TRANSPARENT else Constants.BACKGROUND_COLOR,
            imagePath = "",
            videoPath = "",
            color = Color.Black,
        )
        live != null -> ResolvedBackground(
            type = songBackgroundTypeConstant(live.type),
            imagePath = live.image,
            videoPath = live.video,
            color = parseHexColor(live.color),
            gradientEndColor =
                if (live.type == SongBackgroundType.GRADIENT) parseHexColor(live.colorEnd) else null,
            opacity = live.opacity / PERCENT,
            dimPercent = live.dim,
            blurReferencePx = live.blur,
        )
        config.backgroundType == Constants.BACKGROUND_DEFAULT ->
            defaultBackground(settings, isLowerThird)
        else -> ResolvedBackground(
            type = config.backgroundType,
            imagePath = config.backgroundImage,
            videoPath = config.backgroundVideo,
            color = parseHexColor(config.backgroundColor),
            opacity = config.backgroundOpacity,
            dimPercent = config.dim,
            blurReferencePx = config.blur,
        )
    }
}

/** The Background tab's own default, which a content type inherits by saying Default. */
private fun defaultBackground(settings: BackgroundSettings, isLowerThird: Boolean): ResolvedBackground =
    if (isLowerThird) ResolvedBackground(
        type = settings.defaultLowerThirdBackgroundType,
        imagePath = settings.defaultLowerThirdBackgroundImage,
        videoPath = settings.defaultLowerThirdBackgroundVideo,
        color = parseHexColor(settings.defaultLowerThirdBackgroundColor),
        opacity = settings.defaultLowerThirdBackgroundOpacity,
        dimPercent = settings.defaultLowerThirdBackgroundDim,
        blurReferencePx = settings.defaultLowerThirdBackgroundBlur,
    ) else ResolvedBackground(
        type = settings.defaultBackgroundType,
        imagePath = settings.defaultBackgroundImage,
        videoPath = settings.defaultBackgroundVideo,
        color = parseHexColor(settings.defaultBackgroundColor),
        opacity = settings.defaultBackgroundOpacity,
        dimPercent = settings.defaultBackgroundDim,
        blurReferencePx = settings.defaultBackgroundBlur,
    )

/** [background]'s picture, decoded once per path. Null unless it is an image that still exists. */
@Composable
internal fun rememberBackgroundBitmap(background: ResolvedBackground, isLowerThird: Boolean): ImageBitmap? =
    remember(background.type, background.imagePath, isLowerThird) {
        if (background.type == Constants.BACKGROUND_IMAGE && background.imagePath.isNotEmpty()) {
            // PictureDecoder, not Skia directly — see PresenterScreen for why.
            val file = File(background.imagePath)
            if (file.exists()) PictureDecoder.decodeOrNull(file)?.toComposeImageBitmap() else null
        } else null
    }

/** The modifier that paints [background] — the colour, the gradient or the picture itself. */
internal fun backgroundModifier(background: ResolvedBackground, bitmap: ImageBitmap?): Modifier = when {
    background.gradientEndColor != null ->
        Modifier.background(Brush.verticalGradient(listOf(background.color, background.gradientEndColor)))
    background.type == Constants.BACKGROUND_TRANSPARENT -> Modifier
    background.type == Constants.BACKGROUND_GRADIENT -> Modifier
    // The clip is drawn as an overlay by PresenterBackgroundLayers; this is what sits under it.
    background.usesVideo -> Modifier.background(Color.Black)
    background.type == Constants.BACKGROUND_IMAGE && bitmap != null ->
        Modifier.alpha(background.opacity)
            .paint(painter = BitmapPainter(bitmap), contentScale = ContentScale.Crop)
    background.type == Constants.BACKGROUND_IMAGE -> Modifier.background(Color.Black)
    else -> Modifier.background(background.color.copy(alpha = background.opacity))
}

/**
 * The layers that cannot be a modifier on the content's own box: the blurred copy, the video, and
 * the dim wash over both.
 *
 * A blurred background has to be its own layer — blurring the box the text sits in would blur the
 * text with it — so an unblurred background keeps the original single-box shape and its behaviour
 * exactly, and [backgroundModifier] stays on the caller's box in that case.
 *
 * None of it applies to a lower third: the band is drawn by the caller, over whatever is behind it.
 */
@Composable
internal fun BoxScope.PresenterBackgroundLayers(
    background: ResolvedBackground,
    backgroundModifier: Modifier,
    isLowerThird: Boolean,
    blurRadius: Dp,
) {
    if (isLowerThird) return
    if (background.isBlurred) {
        Box(
            Modifier
                .matchParentSize()
                // Overscanned so the blur's own faded edge falls outside the screen instead of
                // showing as a light border down each side.
                .graphicsLayer { scaleX = BACKGROUND_BLUR_OVERSCAN; scaleY = BACKGROUND_BLUR_OVERSCAN }
                .blur(blurRadius)
                .then(backgroundModifier)
        )
    }
    if (background.usesVideo) {
        LoopingVideoBackground(
            videoPath = background.videoPath,
            modifier = Modifier.fillMaxSize().alpha(background.opacity)
                .then(if (background.isBlurred) Modifier.blur(blurRadius) else Modifier),
        )
    }
    if (background.dimPercent > 0) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = background.dimPercent / PERCENT)))
    }
}
