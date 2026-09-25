/*
 * What a presenter draws behind its text, and who gets to decide it.
 *
 * SongPresenter and BiblePresenter are the only two presenters that draw a background from
 * settings, and they resolved it with the same forty lines twice — which is why the per-song
 * background's gradient, dim and blur reached songs only. They are one function now, so a
 * background that can be drawn on a song can be drawn on a verse.
 */
package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.composables.CameraBackground
import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.app.churchpresenter.composables.CameraDeviceCatalog
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.utils.PictureDecoder
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import java.io.File

/** How far a blurred background is scaled up so its faded edge lands off screen. */
internal const val BACKGROUND_BLUR_OVERSCAN = 1.08f

/**
 * How far past its own edge a blurred layer has to be drawn for the fade to land out of sight,
 * as a multiple of the blur radius.
 *
 * [BACKGROUND_BLUR_OVERSCAN] is a percentage of the layer, which works on a full screen and does
 * not on a lower-third band: 8% of a band a third of the output tall is a few pixels, far less
 * than the fade a blur of any size produces, so the layer behind showed through along the band's
 * top edge. A multiple of the radius is the right measure — the fade is the radius' size, not the
 * layer's.
 */
internal const val BLUR_EDGE_BLEED = 3f

/**
 * How far past the band's own top edge [AboveBandFill] is grown when [BackgroundConfig
 * .aboveBandFillsBehindBand] is off, as a fraction of the output's full height — a few pixels on
 * any realistic output, hidden under the band drawn after it. Guards against the two
 * independently-rounded complementary fractions leaving a real gap.
 */
internal const val ABOVE_BAND_OVERLAP_FRACTION = 0.006f

/** A percentage as a fraction. */
internal const val PERCENT = 100f

/** The output width every stored background size is measured against. */
internal const val BACKGROUND_REFERENCE_WIDTH = 1920f

/**
 * How tall the lower-third band is, as a fraction of the output — which is exactly how much of the
 * screen a lower-third background paints, the default one included.
 *
 * Bible and Songs each carry their own band height, so the answer depends on which content type is
 * asking. A caller with none — the Background tab's two Default surfaces, which sit behind both
 * bands — takes the taller of the two, the largest area a default can be showing in.
 *
 * The presenters do not go through this: each draws its own band from its own setting. It is here
 * so a preview measures the band the same way the output does.
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
 * [presenterScale] measures the same way and for the same reason.
 */
internal fun backgroundBlurRadius(blurReferencePx: Int, width: Dp): Dp =
    (blurReferencePx * (width.value / BACKGROUND_REFERENCE_WIDTH)).dp

/** The output height every stored size is measured against, as [BACKGROUND_REFERENCE_WIDTH] is. */
internal const val REFERENCE_HEIGHT = 1080f

/**
 * The range a presenter's scale is held to, so an absurd output cannot produce absurd type.
 *
 * [MIN_PRESENTER_SCALE] used to be 0.5 -- comfortably below every 16:9-family preset
 * ([org.churchpresenter.app.churchpresenter.utils.OutputGeometry], 1280x720 up), so nobody noticed it
 * was also above the ratio a genuinely narrow output computes. `presenterScale` takes the *smaller*
 * of the width and height ratio against the 1920x1080 reference specifically so a mismatched aspect
 * ratio is respected; flooring that result at 0.5 threw the answer away for anything narrower than
 * that, which every vertical/mobile output is. A 720x1280 target computed 0.375 and was floored up to
 * 0.5 -- 33% larger than the space actually available -- which is what let lyrics overflow a portrait
 * output while a landscape one of any shipped size never showed the bug.
 *
 * 0.15 is chosen against [org.churchpresenter.app.churchpresenter.composables.RESOLUTION_RANGE]'s own
 * floor of 16: a width or height of 16 against the 1920x1080 reference computes a ratio near 0.008,
 * far below any legible floor, so a genuinely pathological output (not merely narrow, but tiny) still
 * needs a floor to keep type from vanishing -- it will still overflow that output, same as it always
 * has, because there is no scale that both fits 16px and stays readable. Every realistic vertical
 * preset and custom resolution sits above 0.15, so the floor no longer fires for them.
 */
internal const val MIN_PRESENTER_SCALE = 0.15f
internal const val MAX_PRESENTER_SCALE = 3.0f

/**
 * The factor the song and Bible presenters multiply every stored size by: type, margins, window
 * insets, shadows and the gaps between blocks, all of which are authored against a 1920x1080
 * output and drawn on one [width] by [height].
 *
 * Takes dp and not pixels, for the reason [backgroundBlurRadius] states. Everything it scales is
 * applied as `.dp` or `.sp`, which the platform already multiplies by the output's density, so
 * measuring the output in pixels counted that density a second time and a HiDPI output -- a Retina
 * Mac, or Windows at 150% -- drew everything `density` times too large. Auto-fit was not the part
 * that was wrong: it computes in the 1920x1080 reference space and picked a size that genuinely
 * fitted, which was then drawn at twice that and ran off the side of the screen.
 *
 * The two presenters had this inline and identical; it is one function so it can be checked
 * directly rather than only through a rendered screen.
 */
internal fun presenterScale(width: Dp, height: Dp): Float = minOf(
    width.value / BACKGROUND_REFERENCE_WIDTH,
    height.value / REFERENCE_HEIGHT,
).coerceIn(MIN_PRESENTER_SCALE, MAX_PRESENTER_SCALE)

/** What a presenter actually draws, once every source has had its say. */
internal data class ResolvedBackground(
    val type: String,
    val imagePath: String,
    val videoPath: String,
    val color: Color,
    /** The far end of a gradient; null for everything else. */
    val gradientEndColor: Color? = null,
    /**
     * Where the gradient finishes its transition, 0..1 down the frame. Everything past it is the
     * far colour flat, which is what the band's own gradient has always drawn.
     */
    val gradientPosition: Float = 1f,
    val opacity: Float = 1f,
    /** Percent of black washed over the background, 0–100. */
    val dimPercent: Int = 0,
    /** Blur radius in the 1920×1080 reference space the presenters measure in. */
    val blurReferencePx: Int = 0,
    /** The device a camera background draws. Unset for every other type. */
    val camera: CameraDeviceRef = CameraDeviceRef(),
) {
    val usesVideo: Boolean get() = type == Constants.BACKGROUND_VIDEO && videoPath.isNotEmpty()
    val usesCamera: Boolean get() = type == Constants.BACKGROUND_CAMERA && camera.isSet
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
    /** The cameras this machine has; see [songBackgroundResolves]. Passed explicitly only by tests. */
    knownCameras: List<CameraDevice>? = CameraDeviceCatalog.devices.value,
): ResolvedBackground {
    val override = if (isLowerThird) settings.quickLowerThirdBackground else settings.quickBackground
    // Both remembered unconditionally: a `&&` short-circuit here would be a conditional remember.
    val overrideDraws = remember(override, knownCameras) {
        override != null && songBackgroundResolves(override, knownCameras)
    }
    val ownDraws = remember(ownBackground, knownCameras) {
        ownBackground.isCustom && songBackgroundResolves(ownBackground, knownCameras)
    }

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
            camera = live.camera,
        )
        config.backgroundType == Constants.BACKGROUND_DEFAULT ->
            defaultBackground(settings, isLowerThird)
        else -> ResolvedBackground(
            type = config.backgroundType,
            imagePath = config.backgroundImage,
            videoPath = config.backgroundVideo,
            // A gradient's near colour is its top, not the surface's flat colour -- without this the
            // full screen drew nothing at all for a gradient, which is why the type was withheld
            // from every surface but the two bands.
            color = if (config.backgroundType == Constants.BACKGROUND_GRADIENT) {
                parseHexColor(config.gradientTopColor).copy(alpha = config.gradientTopOpacity)
            } else {
                parseHexColor(config.backgroundColor)
            },
            gradientEndColor = if (config.backgroundType == Constants.BACKGROUND_GRADIENT) {
                parseHexColor(config.gradientBottomColor).copy(alpha = config.gradientBottomOpacity)
            } else {
                null
            },
            gradientPosition = config.gradientPosition,
            opacity = config.backgroundOpacity,
            dimPercent = config.dim,
            blurReferencePx = config.blur,
            camera = config.camera,
        )
    }
}

/**
 * The Background tab's own default, which a content type inherits by saying Default.
 *
 * A lower third takes the lower-third card unless that card says `FollowDefault`, which hands it
 * the full-screen one — the same fall-through the Background tab's rail walks. Without that check
 * `FollowDefault` reached [backgroundModifier] as an unknown type and was drawn as a flat color.
 */
private fun defaultBackground(settings: BackgroundSettings, isLowerThird: Boolean): ResolvedBackground =
    if (isLowerThird &&
        settings.defaultLowerThirdBackgroundType != Constants.BACKGROUND_FOLLOW_DEFAULT
    ) ResolvedBackground(
        type = settings.defaultLowerThirdBackgroundType,
        imagePath = settings.defaultLowerThirdBackgroundImage,
        videoPath = settings.defaultLowerThirdBackgroundVideo,
        color = parseHexColor(settings.defaultLowerThirdBackgroundColor),
        opacity = settings.defaultLowerThirdBackgroundOpacity,
        dimPercent = settings.defaultLowerThirdBackgroundDim,
        blurReferencePx = settings.defaultLowerThirdBackgroundBlur,
        camera = settings.defaultLowerThirdBackgroundCamera,
    ) else ResolvedBackground(
        type = settings.defaultBackgroundType,
        imagePath = settings.defaultBackgroundImage,
        videoPath = settings.defaultBackgroundVideo,
        color = parseHexColor(settings.defaultBackgroundColor),
        opacity = settings.defaultBackgroundOpacity,
        dimPercent = settings.defaultBackgroundDim,
        blurReferencePx = settings.defaultBackgroundBlur,
        camera = settings.defaultBackgroundCamera,
    )

/**
 * The wash's colour (or null for nothing) and whether it is painted behind the band too — see
 * [resolveAboveBand].
 */
internal data class AboveBand(
    val fill: Color?,
    val fillsBehindBand: Boolean,
    /** The picture, clip or camera drawn there instead of [fill]; null for a colour or nothing. */
    val media: ResolvedBackground? = null,
)

/**
 * What to wash over the part of a lower-third output the band does not cover, or null for nothing,
 * and whether that wash is also painted behind the band itself.
 *
 * The band and the area above it are two different decisions, so this does not go through
 * [resolveBackground]: a quick-tray pick or a song's own background replaces the *band*, and
 * neither of them says anything about the two thirds above it, which stay as the surface was
 * configured.
 *
 * **It follows its own type, not the band's.** `Default` on a content lower third means "follow
 * the Default Lower Third's wash", exactly as `Default` in the band's type row means "follow the
 * Default's band" — so a wash set once reaches Bible and Songs without being set twice, and a
 * surface can carry a picture of its own while still taking the shared wash above it. Reading the
 * band's type instead would have made "has its own picture" silently mean "loses the wash".
 *
 * `Image`, `Video` and `Camera` come back as [AboveBand.media] with no fill, and only when there is
 * something to draw — a picture or clip that is gone, or no device chosen, is nothing rather than a
 * black rectangle over two thirds of the output.
 *
 * `Transparent` at the end of the chain returns a null fill rather than [Color.Transparent]:
 * nothing is drawn there at all, which is what a Browser Source or NDI alpha output needs in order
 * to key. [AboveBand.fillsBehindBand] follows the same defer chain independently of the colour, so
 * a surface can inherit the Default's wash colour while overriding just the behind-band choice.
 */
internal fun resolveAboveBand(settings: BackgroundSettings, config: BackgroundConfig): AboveBand {
    val defers = config.aboveBandType == Constants.BACKGROUND_DEFAULT
    val fillsBehindBand =
        if (defers) settings.defaultLowerThirdAboveBandFillsBehindBand else config.aboveBandFillsBehindBand
    val type = if (defers) settings.defaultLowerThirdAboveBandType else config.aboveBandType
    val opacity = (if (defers) settings.defaultLowerThirdAboveBandOpacity else config.aboveBandOpacity)
        .coerceIn(0f, 1f)
    return when (type) {
        Constants.BACKGROUND_COLOR -> {
            val hex = if (defers) settings.defaultLowerThirdAboveBandColor else config.aboveBandColor
            AboveBand(parseHexColor(hex).copy(alpha = opacity), fillsBehindBand)
        }
        Constants.BACKGROUND_IMAGE, Constants.BACKGROUND_VIDEO, Constants.BACKGROUND_CAMERA -> {
            val media = ResolvedBackground(
                type = type,
                imagePath = if (defers) settings.defaultLowerThirdAboveBandImage else config.aboveBandImage,
                videoPath = if (defers) settings.defaultLowerThirdAboveBandVideo else config.aboveBandVideo,
                color = Color.Black,
                opacity = opacity,
                camera = if (defers) settings.defaultLowerThirdAboveBandCamera else config.aboveBandCamera,
            )
            val drawable = media.usesVideo || media.usesCamera ||
                (type == Constants.BACKGROUND_IMAGE && media.imagePath.isNotEmpty())
            AboveBand(null, fillsBehindBand, media.takeIf { drawable })
        }
        else -> AboveBand(null, fillsBehindBand)
    }
}

/**
 * [fill] painted over the part of a lower-third output above the band, and — when
 * [fillsBehindBand] is set — behind the band too.
 *
 * [fillsBehindBand] is the default. A one-pixel gap at the exact `1f - bandFraction` boundary was
 * the first theory here (two `fillMaxHeight` calls rounding independently and not always summing
 * back to the parent's exact height), and that alone was worth closing, but it was not the actual
 * defect: a band whose own fill fades toward transparent at its edge — any gradient stop that
 * starts at less than full alpha, which is the *default* shape for the gradient styles, not an
 * edge case — showed several pixels of raw black there, not a hairline, because nothing behind the
 * band down to the true background was ever anything but the window's own clear colour.
 * `bandFraction` said nothing about it either way; the band was simply never backed by this fill
 * in the first place. With [fillsBehindBand] on, [fill] covers the full frame and the band —
 * drawn afterward, in front — paints over it wherever the band itself is opaque, exactly as
 * before; where the band fades toward transparent, this fill shows through instead of black, on
 * this opaque preview window and on a real alpha output alike (a fade to transparent still keys
 * to nothing, it just no longer keys through a black fringe first — #561).
 *
 * [fillsBehindBand] off restores the original split, for a surface that wants the wash to stop
 * exactly at the band: sized as the band's complement plus a small guaranteed overlap — grown past
 * the exact boundary and hidden under the band, drawn after it — rather than the bare
 * `fillMaxHeight(1f - bandFraction)` this once was, which could round to a real gap the same way.
 * This is the setting to turn off for a band that must key clean on its own — a `Transparent` band
 * with a wash configured above it — where any colour bleeding through a translucent part of the
 * band would be wrong for that specific setup.
 */
@Composable
internal fun BoxScope.AboveBandFill(above: AboveBand, bandFraction: Float, show: Boolean = true) {
    if (!show || (above.fill == null && above.media == null)) return
    val area = if (above.fillsBehindBand) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxWidth()
            .fillMaxHeight((1f - bandFraction + ABOVE_BAND_OVERLAP_FRACTION).coerceAtMost(1f))
            .align(Alignment.TopCenter)
    }
    above.fill?.let { Box(modifier = area.background(it)) }
    above.media?.let { media ->
        Box(modifier = area.clipToBounds()) {
            val bitmap = rememberBackgroundBitmap(media, isLowerThird = false)
            if (bitmap != null) {
                Image(
                    painter = BitmapPainter(bitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(media.opacity),
                )
            }
            BandMediaLayers(media)
        }
    }
}

/**
 * [background]'s picture, decoded once per path. Null unless it is an image that still exists, and
 * briefly null again right after the path changes, while the new one is still decoding.
 *
 * The decode runs on [Dispatchers.IO], not inline in `remember` — a stock/Pexels background is
 * already a small, web-sized download, but an operator's own photo has no such cap, and decoding
 * one at full native resolution synchronously on the composition thread is what turned "add a
 * background" into a multi-second freeze of every output sharing it (#549). The result is also
 * scaled to the output's own pixel size ([PictureDecoder.decodeScaled]) — no point holding, or
 * re-uploading to the GPU, more pixels than this surface will ever show.
 */
@Composable
internal fun rememberBackgroundBitmap(background: ResolvedBackground, isLowerThird: Boolean): ImageBitmap? {
    val containerSize = LocalWindowInfo.current.containerSize
    val maxWidth = containerSize.width.takeIf { it > 0 } ?: BACKGROUND_REFERENCE_WIDTH.toInt()
    val maxHeight = containerSize.height.takeIf { it > 0 } ?: REFERENCE_HEIGHT.toInt()
    var bitmap by remember(background.type, background.imagePath, isLowerThird) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(background.type, background.imagePath, isLowerThird, maxWidth, maxHeight) {
        bitmap = if (background.type == Constants.BACKGROUND_IMAGE && background.imagePath.isNotEmpty()) {
            // PictureDecoder, not Skia directly — see PresenterScreen for why.
            val file = File(background.imagePath)
            if (file.exists()) {
                withContext(Dispatchers.IO) {
                    PictureDecoder.decodeScaledOrNull(file, maxWidth, maxHeight)?.toComposeImageBitmap()
                }
            } else {
                null
            }
        } else {
            null
        }
    }
    return bitmap
}

/** The modifier that paints [background] — the colour, the gradient or the picture itself. */
internal fun backgroundModifier(background: ResolvedBackground, bitmap: ImageBitmap?): Modifier = when {
    // The stops the band's own gradient has always used: the near colour at the top, the far one
    // reached at `gradientPosition` and held flat from there. A SongBackground leaves the position
    // at 1f, which is the plain two-stop gradient it drew before.
    background.gradientEndColor != null ->
        Modifier.background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to background.color,
                    background.gradientPosition.coerceIn(0f, 1f) to background.gradientEndColor,
                    1f to background.gradientEndColor,
                ),
            ),
        )
    background.type == Constants.BACKGROUND_TRANSPARENT -> Modifier
    // The clip and the camera are drawn as overlays by PresenterBackgroundLayers; this is what
    // sits under them, and what a camera with no frame yet shows on its own.
    background.usesVideo || background.usesCamera -> Modifier.background(Color.Black)
    background.type == Constants.BACKGROUND_IMAGE && bitmap != null ->
        Modifier.alpha(background.opacity)
            .paint(painter = BitmapPainter(bitmap), contentScale = ContentScale.Crop)
    background.type == Constants.BACKGROUND_IMAGE -> Modifier.background(Color.Black)
    else -> Modifier.background(background.color.copy(alpha = background.opacity))
}

/**
 * The layers that cannot be a modifier on the content's own box: the blurred copy, the video, the
 * live camera, and the dim wash over all of them.
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
    if (background.usesCamera) {
        CameraBackground(
            camera = background.camera,
            modifier = Modifier.fillMaxSize().alpha(background.opacity)
                .then(if (background.isBlurred) Modifier.blur(blurRadius) else Modifier),
        )
    }
    if (background.dimPercent > 0) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = background.dimPercent / PERCENT)))
    }
}
