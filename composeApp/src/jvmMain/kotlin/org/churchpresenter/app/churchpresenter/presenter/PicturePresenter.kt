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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image
import java.io.File
import org.churchpresenter.app.churchpresenter.composables.keyColorFilter
import org.churchpresenter.app.churchpresenter.composables.opaqueColorFilter
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.HeicDecoder

@Composable
fun PicturePresenter(
    modifier: Modifier = Modifier,
    imagePath: String?,
    transitionAlpha: Float = 1f,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    // Key mode: solid white frame (mixer sees "fully visible")
    if (isKey) {
        Box(modifier = modifier.fillMaxSize().background(Color.White).alpha(transitionAlpha))
        return
    }
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

private fun loadAndDownscaleImage(imagePath: String, maxWidth: Int = 1920, maxHeight: Int = 1080): ImageBitmap? {
    return try {
        val file = File(imagePath)
        if (!file.exists()) return null

        val bytes = file.readBytes()

        val originalImage = try {
            Image.makeFromEncoded(bytes)
        } catch (e: Exception) {
            if (file.extension.lowercase() in listOf("heic", "heif")) {
                val jpegBytes = HeicDecoder.toJpegBytes(file) ?: return null
                try {
                    Image.makeFromEncoded(jpegBytes)
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
    transitionAlpha: Float = 1f,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    // Key mode: solid white frame (mixer sees "fully visible")
    if (isKey) {
        Box(modifier = modifier.fillMaxSize().background(Color.White).alpha(transitionAlpha))
        return
    }
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
