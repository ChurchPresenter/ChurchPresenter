package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.failed_to_load_image
import churchpresenter.composeapp.generated.resources.no_images
import churchpresenter.composeapp.generated.resources.presented_image
import churchpresenter.composeapp.generated.resources.presented_slide
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

@Composable
fun PicturePresenter(
    modifier: Modifier = Modifier,
    imagePath: String?,
    animationType: AnimationType = AnimationType.CROSSFADE,
    transitionDuration: Int = 500
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (animationType) {
            AnimationType.CROSSFADE -> {
                Crossfade(
                    targetState = imagePath,
                    animationSpec = tween(durationMillis = transitionDuration),
                    label = "Image Crossfade"
                ) { currentImagePath ->
                    ImageContent(currentImagePath)
                }
            }
            AnimationType.FADE -> {
                AnimatedContent(
                    targetState = imagePath,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(transitionDuration)) togetherWith
                                fadeOut(animationSpec = tween(transitionDuration))
                    },
                    label = "Image Fade"
                ) { currentImagePath ->
                    ImageContent(currentImagePath)
                }
            }
            AnimationType.SLIDE_LEFT -> {
                AnimatedContent(
                    targetState = imagePath,
                    transitionSpec = {
                        slideInHorizontally(
                            animationSpec = tween(transitionDuration),
                            initialOffsetX = { fullWidth -> fullWidth }
                        ) togetherWith slideOutHorizontally(
                            animationSpec = tween(transitionDuration),
                            targetOffsetX = { fullWidth -> -fullWidth }
                        )
                    },
                    label = "Image Slide Left"
                ) { currentImagePath ->
                    ImageContent(currentImagePath)
                }
            }
            AnimationType.SLIDE_RIGHT -> {
                AnimatedContent(
                    targetState = imagePath,
                    transitionSpec = {
                        slideInHorizontally(
                            animationSpec = tween(transitionDuration),
                            initialOffsetX = { fullWidth -> -fullWidth }
                        ) togetherWith slideOutHorizontally(
                            animationSpec = tween(transitionDuration),
                            targetOffsetX = { fullWidth -> fullWidth }
                        )
                    },
                    label = "Image Slide Right"
                ) { currentImagePath ->
                    ImageContent(currentImagePath)
                }
            }
            AnimationType.NONE -> {
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
                contentScale = ContentScale.Fit
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

private fun loadAndDownscaleImage(imagePath: String, maxWidth: Int = 1920, maxHeight: Int = 1080): ImageBitmap? {
    return try {
        val file = File(imagePath)
        if (!file.exists()) return null

        val bytes = file.readBytes()

        val originalImage = try {
            Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            if (file.extension.lowercase() in listOf("heic", "heif")) {
                try {
                    val bufferedImage = ImageIO.read(file)
                    if (bufferedImage != null) {
                        val outputStream = ByteArrayOutputStream()
                        ImageIO.write(bufferedImage, "jpg", outputStream)
                        Image.makeFromEncoded(outputStream.toByteArray())
                    } else return null
                } catch (_: Exception) { return null }
            } else return null
        }

        // Cap at actual screen resolution — no point storing more pixels than the display can show
        val widthScale = maxWidth.toFloat() / originalImage.width
        val heightScale = maxHeight.toFloat() / originalImage.height
        val scale = minOf(widthScale, heightScale, 1.0f) // never upscale

        if (scale < 1.0f) {
            val newWidth = (originalImage.width * scale).toInt()
            val newHeight = (originalImage.height * scale).toInt()

            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(newWidth, newHeight)
            val canvas = surface.canvas

            // High-quality downscale using Mitchell filter via SamplingMode
            val paint = org.jetbrains.skia.Paint()
            val srcRect = org.jetbrains.skia.Rect.makeWH(
                originalImage.width.toFloat(),
                originalImage.height.toFloat()
            )
            val dstRect = org.jetbrains.skia.Rect.makeWH(newWidth.toFloat(), newHeight.toFloat())
            canvas.drawImageRect(
                originalImage,
                srcRect,
                dstRect,
                org.jetbrains.skia.SamplingMode.MITCHELL,
                paint,
                true
            )

            surface.makeImageSnapshot().toComposeImageBitmap()
        } else {
            originalImage.toComposeImageBitmap()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun SlidePresenter(
    modifier: Modifier = Modifier,
    slide: ImageBitmap?,
    animationType: AnimationType = AnimationType.CROSSFADE,
    transitionDuration: Int = 500
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (animationType) {
            AnimationType.CROSSFADE -> {
                Crossfade(
                    targetState = slide,
                    animationSpec = tween(durationMillis = transitionDuration),
                    label = "Slide Crossfade"
                ) { currentSlide -> SlideBitmapContent(currentSlide) }
            }
            AnimationType.FADE -> {
                AnimatedContent(
                    targetState = slide,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(transitionDuration)) togetherWith
                                fadeOut(animationSpec = tween(transitionDuration))
                    },
                    label = "Slide Fade"
                ) { currentSlide -> SlideBitmapContent(currentSlide) }
            }
            AnimationType.SLIDE_LEFT -> {
                AnimatedContent(
                    targetState = slide,
                    transitionSpec = {
                        slideInHorizontally(
                            animationSpec = tween(transitionDuration),
                            initialOffsetX = { fullWidth -> fullWidth }
                        ) togetherWith slideOutHorizontally(
                            animationSpec = tween(transitionDuration),
                            targetOffsetX = { fullWidth -> -fullWidth }
                        )
                    },
                    label = "Slide Slide Left"
                ) { currentSlide -> SlideBitmapContent(currentSlide) }
            }
            AnimationType.SLIDE_RIGHT -> {
                AnimatedContent(
                    targetState = slide,
                    transitionSpec = {
                        slideInHorizontally(
                            animationSpec = tween(transitionDuration),
                            initialOffsetX = { fullWidth -> -fullWidth }
                        ) togetherWith slideOutHorizontally(
                            animationSpec = tween(transitionDuration),
                            targetOffsetX = { fullWidth -> fullWidth }
                        )
                    },
                    label = "Slide Slide Right"
                ) { currentSlide -> SlideBitmapContent(currentSlide) }
            }
            AnimationType.NONE -> {
                SlideBitmapContent(slide)
            }
        }
    }
}

@Composable
private fun SlideBitmapContent(slide: ImageBitmap?) {
    if (slide != null) {
        Image(
            bitmap = slide,
            contentDescription = stringResource(Res.string.presented_slide),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
    }
}
