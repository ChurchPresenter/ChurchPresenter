package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.app.churchpresenter.composables.keySignal
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.presenter.BACKGROUND_BLUR_OVERSCAN
import org.churchpresenter.app.churchpresenter.presenter.backgroundBlurRadius
import org.churchpresenter.app.churchpresenter.presenter.LocalTransparentBlanking
import org.churchpresenter.app.churchpresenter.presenter.lowerThirdBandFraction
import org.churchpresenter.app.churchpresenter.presenter.PERCENT
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.PictureDecoder
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File

@Composable
fun PresenterScreen(
    modifier: Modifier = Modifier,
    appSettings: AppSettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    isLowerThird: Boolean = false,
    showBackground: Boolean = true,
    /**
     * How much of the output the lower-third band covers, as a fraction — the only part of the
     * screen this background paints when [isLowerThird]. Null when the caller cannot know which
     * content type is live, which falls back to the taller of the two configured bands.
     */
    lowerThirdBandFraction: Float? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    // Browser Source scenes blank to transparent pixels (OBS keys the video underneath);
    // projector windows blank to black — that's what "nothing" looks like on a display.
    val transparentBlanking = LocalTransparentBlanking.current

    val card = appSettings.backgroundSettings.defaultCardFor(isLowerThird)
    val bgType = card.type
    val bgImagePath = card.imagePath
    val bgVideoPath = card.videoPath
    val bgOpacity = card.opacity
    val bgDim = card.dim
    val bgBlur = card.blur
    val backgroundColor = if (!showBackground) Color.Black else parseHexColor(card.colorHex)

    val backgroundImageBitmap = remember(bgType, bgImagePath, showBackground) {
        if (showBackground && bgType == Constants.BACKGROUND_IMAGE && bgImagePath.isNotEmpty()) {
            // Through PictureDecoder, not Skia directly: a background is a file the operator
            // chose, so it can be a CMYK JPEG, a TIFF, or a HEIC named .jpg — all of which Skia
            // alone refuses, leaving a black screen on the output with nothing said.
            val file = File(bgImagePath)
            if (file.exists()) PictureDecoder.decodeOrNull(file)?.toComposeImageBitmap() else null
        } else null
    }

    // A lower third is a band across the bottom, not a full screen: everything above it belongs to
    // whatever the output is keyed over, so the default lower-third background is drawn over the
    // band alone. It used to fill the whole output, which put its color on the two thirds of the
    // screen that are supposed to stay blank.
    val bandFraction = (lowerThirdBandFraction ?: appSettings.lowerThirdBandFraction(null)).coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isBlurred = showBackground && bgBlur > 0
        // Read here, not inside the band Box below: that Box's own scope shadows this one, and the
        // blur is measured against the whole output's width the way the presenters measure it.
        val outputWidth = maxWidth
        // Everything above the band is blank, and blank means black on a projector and genuinely
        // transparent in a Browser Source or NDI alpha scene — the same two answers the blanked
        // and Transparent cases below already give, applied to the part of a lower-third output
        // no background reaches.
        if (isLowerThird && !transparentBlanking) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        }
        Box(
            modifier = if (isLowerThird) {
                Modifier.fillMaxWidth().fillMaxHeight(bandFraction).align(Alignment.BottomCenter)
            } else Modifier.fillMaxSize()
        ) {
        Box(
            modifier = Modifier.fillMaxSize().then(
                // Overscanned so the blur's own faded edge falls outside the screen rather than
                // showing as a light border down each side — the same 8% PresenterBackgroundLayers
                // uses, so a background looks the same here as it does under a verse.
                if (isBlurred) Modifier
                    .graphicsLayer { scaleX = BACKGROUND_BLUR_OVERSCAN; scaleY = BACKGROUND_BLUR_OVERSCAN }
                    .blur(backgroundBlurRadius(bgBlur, outputWidth))
                else Modifier
            )
        ) {
        // Background layer — black when backgrounds disabled (transparent in Browser Source scenes)
        if (!showBackground) {
            if (!transparentBlanking) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        } else {
            when (bgType) {
                Constants.BACKGROUND_IMAGE -> {
                    if (backgroundImageBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(Modifier.background(Color.Black))
                        ) {
                            Image(
                                painter = BitmapPainter(backgroundImageBitmap),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().alpha(bgOpacity)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }
                }
                Constants.BACKGROUND_VIDEO -> {
                    LoopingVideoBackground(
                        videoPath = bgVideoPath,
                        modifier = Modifier.fillMaxSize().alpha(bgOpacity)
                    )
                }
                Constants.BACKGROUND_TRANSPARENT -> {
                    // No visible background — black (appears as "nothing" on a projector/display);
                    // genuinely transparent in Browser Source scenes so OBS can key through it
                    if (!transparentBlanking) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize().background(backgroundColor.copy(alpha = bgOpacity)))
                }
            }
        }
        }
        if (showBackground && bgDim > 0) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgDim / PERCENT)))
        }
        }
        // Content layer — apply key modifier if key mode
        if (isKey) {
            Box(modifier = Modifier.fillMaxSize().keySignal()) {
                content()
            }
        } else {
            content()
        }
    }
}


/**
 * One of the two Default cards, flattened.
 *
 * This layer is what the output shows whenever nothing is drawing a background of its own —
 * nothing live, or Pictures/Media/Canvas, none of which resolve a background — so it carries the
 * card's dim and blur too, which otherwise only appeared while a verse or a lyric was on screen.
 */
private data class DefaultBackgroundCard(
    val type: String,
    val colorHex: String,
    val imagePath: String,
    val videoPath: String,
    val opacity: Float,
    val dim: Int,
    val blur: Int,
)

/**
 * Which Default card an output draws: the lower-third one when it is a lower third, the
 * full-screen one otherwise.
 *
 * `FollowDefault` makes the lower third take the full-screen card outright, which is why this is
 * one decision rather than a field-by-field choice at each use — half a card from each would be a
 * look neither of them describes.
 */
private fun BackgroundSettings.defaultCardFor(isLowerThird: Boolean): DefaultBackgroundCard =
    if (isLowerThird && defaultLowerThirdBackgroundType != Constants.BACKGROUND_FOLLOW_DEFAULT) {
        DefaultBackgroundCard(
            type = defaultLowerThirdBackgroundType,
            colorHex = defaultLowerThirdBackgroundColor,
            imagePath = defaultLowerThirdBackgroundImage,
            videoPath = defaultLowerThirdBackgroundVideo,
            opacity = defaultLowerThirdBackgroundOpacity,
            dim = defaultLowerThirdBackgroundDim,
            blur = defaultLowerThirdBackgroundBlur,
        )
    } else {
        DefaultBackgroundCard(
            type = defaultBackgroundType,
            colorHex = defaultBackgroundColor,
            imagePath = defaultBackgroundImage,
            videoPath = defaultBackgroundVideo,
            opacity = defaultBackgroundOpacity,
            dim = defaultBackgroundDim,
            blur = defaultBackgroundBlur,
        )
    }
