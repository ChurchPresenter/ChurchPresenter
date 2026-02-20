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
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.failed_to_load_image
import churchpresenter.composeapp.generated.resources.no_images
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
        val imageBitmap = remember(currentImagePath) {
            loadAndDownscaleImage(currentImagePath)
        }

        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Presented Image",
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

private fun loadAndDownscaleImage(imagePath: String): ImageBitmap? {
    return try {
        val file = File(imagePath)
        if (!file.exists()) {
            println("PicturePresenter: File not found: $imagePath")
            return null
        }

        val bytes = file.readBytes()

        // Try to decode with Skia first
        val originalImage = try {
            Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            println("PicturePresenter: Skia failed to decode, trying ImageIO for HEIC")

            // If it's a HEIC file, try converting with ImageIO
            if (file.extension.lowercase() in listOf("heic", "heif")) {
                try {
                    val bufferedImage = ImageIO.read(file)
                    if (bufferedImage != null) {
                        val outputStream = ByteArrayOutputStream()
                        ImageIO.write(bufferedImage, "jpg", outputStream)
                        val jpegBytes = outputStream.toByteArray()
                        Image.makeFromEncoded(jpegBytes)
                    } else {
                        println("PicturePresenter: ImageIO returned null")
                        return null
                    }
                } catch (heicError: Exception) {
                    println("PicturePresenter: HEIC conversion failed: ${heicError.message}")
                    return null
                }
            } else {
                println("PicturePresenter: Failed to decode image: ${e.message}")
                return null
            }
        }

        // Downscale to max 1080p (1920x1080)
        val maxWidth = 1920
        val maxHeight = 1080

        val widthScale = maxWidth.toFloat() / originalImage.width
        val heightScale = maxHeight.toFloat() / originalImage.height
        val scale = minOf(widthScale, heightScale, 1.0f) // Don't upscale

        if (scale < 1.0f) {
            // Downscale the image
            val newWidth = (originalImage.width * scale).toInt()
            val newHeight = (originalImage.height * scale).toInt()

            println("PicturePresenter: Downscaling ${originalImage.width}x${originalImage.height} to ${newWidth}x${newHeight}")

            val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(newWidth, newHeight)
            val canvas = surface.canvas
            canvas.scale(scale, scale)
            canvas.drawImage(originalImage, 0f, 0f)

            surface.makeImageSnapshot().toComposeImageBitmap()
        } else {
            // Use original size if already smaller than 1080p
            println("PicturePresenter: Using original size ${originalImage.width}x${originalImage.height}")
            originalImage.toComposeImageBitmap()
        }
    } catch (e: Exception) {
        println("PicturePresenter: Failed to load image: ${e.message}")
        e.printStackTrace()
        null
    }
}

