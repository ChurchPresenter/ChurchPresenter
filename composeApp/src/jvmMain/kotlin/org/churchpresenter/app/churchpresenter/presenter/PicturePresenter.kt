package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.failed_to_load_image
import churchpresenter.composeapp.generated.resources.no_images
import churchpresenter.composeapp.generated.resources.presented_image
import churchpresenter.composeapp.generated.resources.presented_slide
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image
import java.io.File
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.PictureDecoder
import org.churchpresenter.diagnostics.CrashReporter

/** The white key-output's alpha for [PicturePresenter]/[SlidePresenter] — a slide's own translation
 *  carries the transition, not a fade, so slides always key fully opaque. */
internal fun pictureKeyAlpha(animationType: AnimationType, transitionAlpha: Float): Float =
    if (animationType == AnimationType.SLIDE_LEFT || animationType == AnimationType.SLIDE_RIGHT) 1f else transitionAlpha

@Composable
fun PicturePresenter(
    modifier: Modifier = Modifier,
    imagePath: String?,
    previousImagePath: String? = null,
    transitionAlpha: Float = 1f,
    slideOffset: Float = 1f,
    animationType: AnimationType = AnimationType.FADE,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat().takeIf { it > 0 } ?: 1920f

    when {
        // Key mode: always solid white at the appropriate alpha
        isKey -> {
            Box(modifier = modifier.fillMaxSize().background(Color.White).alpha(pictureKeyAlpha(animationType, transitionAlpha)))
        }

        // Crossfade: both images visible simultaneously, old fades out as new fades in
        animationType == AnimationType.CROSSFADE && previousImagePath != null -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - transitionAlpha }) {
                    ImageContent(previousImagePath)
                }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = transitionAlpha }) {
                    ImageContent(imagePath)
                }
            }
        }

        // Slide Left: new image slides in from the right, old slides out to the left
        animationType == AnimationType.SLIDE_LEFT && previousImagePath != null -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = -slideOffset * screenWidthPx }) {
                    ImageContent(previousImagePath)
                }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = (1f - slideOffset) * screenWidthPx }) {
                    ImageContent(imagePath)
                }
            }
        }

        // Slide Right: new image slides in from the left, old slides out to the right
        animationType == AnimationType.SLIDE_RIGHT && previousImagePath != null -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = slideOffset * screenWidthPx }) {
                    ImageContent(previousImagePath)
                }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = -(1f - slideOffset) * screenWidthPx }) {
                    ImageContent(imagePath)
                }
            }
        }

        // Default: single image with alpha (FADE, NONE, or no previous image)
        else -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .alpha(transitionAlpha),
                contentAlignment = Alignment.Center
            ) {
                ImageContent(imagePath)
            }
        }
    }
}

@Composable
private fun ImageContent(currentImagePath: String?) {
    if (currentImagePath != null) {
        // Use actual presenter window size so image is never loaded larger than what's displayed
        val windowInfo = LocalWindowInfo.current
        val containerSize = windowInfo.containerSize
        val screenWidth = containerSize.width.takeIf { it > 0 } ?: 1920
        val screenHeight = containerSize.height.takeIf { it > 0 } ?: 1080

        val imageBitmap = remember(currentImagePath, screenWidth, screenHeight) {
            loadAndDownscaleImage(currentImagePath, screenWidth, screenHeight)
        }

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = stringResource(Res.string.presented_image),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = null
            )
        } else {
            Text(
                text = stringResource(Res.string.failed_to_load_image),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
    } else {
        Text(
            text = stringResource(Res.string.no_images),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
    }
}

internal fun loadAndDownscaleImage(imagePath: String, maxWidth: Int = 1920, maxHeight: Int = 1080): ImageBitmap? {
    val file = File(imagePath)
    if (!file.exists()) return null

    return try {
        PictureDecoder.decodeScaled(file, maxWidth, maxHeight).toComposeImageBitmap()
    } catch (e: Exception) {
        CrashReporter.reportException(e, "Decoding picture for presenter")
        null
    }
}

@Composable
fun SlidePresenter(
    modifier: Modifier = Modifier,
    slide: ImageBitmap?,
    previousSlide: ImageBitmap? = null,
    transitionAlpha: Float = 1f,
    slideOffset: Float = 1f,
    animationType: AnimationType = AnimationType.FADE,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat().takeIf { it > 0 } ?: 1920f

    when {
        isKey -> {
            Box(modifier = modifier.fillMaxSize().background(Color.White).alpha(pictureKeyAlpha(animationType, transitionAlpha)))
        }

        animationType == AnimationType.CROSSFADE && previousSlide != null -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - transitionAlpha }) {
                    SlideBitmapContent(previousSlide)
                }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = transitionAlpha }) {
                    SlideBitmapContent(slide)
                }
            }
        }

        animationType == AnimationType.SLIDE_LEFT && previousSlide != null -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = -slideOffset * screenWidthPx }) {
                    SlideBitmapContent(previousSlide)
                }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = (1f - slideOffset) * screenWidthPx }) {
                    SlideBitmapContent(slide)
                }
            }
        }

        animationType == AnimationType.SLIDE_RIGHT && previousSlide != null -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = slideOffset * screenWidthPx }) {
                    SlideBitmapContent(previousSlide)
                }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { translationX = -(1f - slideOffset) * screenWidthPx }) {
                    SlideBitmapContent(slide)
                }
            }
        }

        else -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .alpha(transitionAlpha),
                contentAlignment = Alignment.Center
            ) {
                SlideBitmapContent(slide)
            }
        }
    }
}

@Composable
private fun SlideBitmapContent(slide: ImageBitmap?) {
    if (slide != null) {
        // Get physical pixel dimensions of the presenter window
        val windowInfo = LocalWindowInfo.current
        val containerSize = windowInfo.containerSize
        Image(
            bitmap = slide,
            contentDescription = stringResource(Res.string.presented_slide),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    }
}
