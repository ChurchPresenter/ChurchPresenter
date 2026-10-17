package org.churchpresenter.slides.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.failed_to_load_image
import org.churchpresenter.strings.generated.resources.no_images
import org.churchpresenter.strings.generated.resources.presented_image
import org.churchpresenter.strings.generated.resources.presented_slide
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.slides.utils.PictureDecoder
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
    /** How each picture meets the output -- `PictureSettings.scaleMode`, through `contentScale`. */
    contentScale: ContentScale = ContentScale.Fit,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val windowInfo = LocalWindowInfo.current
    val screenWidthPx = windowInfo.containerSize.width.toFloat().takeIf { it > 0 } ?: 1920f

    when {
        // Key mode: always solid white at the appropriate alpha
        isKey -> {
            Box(
                modifier = modifier.fillMaxSize()
                    .background(Color.White)
                    .alpha(pictureKeyAlpha(animationType, transitionAlpha))
            )
        }

        // Crossfade: both images visible simultaneously, old fades out as new fades in
        animationType == AnimationType.CROSSFADE && previousImagePath != null -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black).clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - transitionAlpha }) {
                    ImageContent(previousImagePath, contentScale)
                }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = transitionAlpha }) {
                    ImageContent(imagePath, contentScale)
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
                    ImageContent(previousImagePath, contentScale)
                }
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { translationX = (1f - slideOffset) * screenWidthPx }
                ) {
                    ImageContent(imagePath, contentScale)
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
                    ImageContent(previousImagePath, contentScale)
                }
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { translationX = -(1f - slideOffset) * screenWidthPx }
                ) {
                    ImageContent(imagePath, contentScale)
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
                ImageContent(imagePath, contentScale)
            }
        }
    }
}

@Composable
private fun ImageContent(currentImagePath: String?, contentScale: ContentScale) {
    if (currentImagePath != null) {
        // Use actual presenter window size so image is never loaded larger than what's displayed
        val windowInfo = LocalWindowInfo.current
        val containerSize = windowInfo.containerSize
        val screenWidth = containerSize.width.takeIf { it > 0 } ?: 1920
        val screenHeight = containerSize.height.takeIf { it > 0 } ?: 1080

        val cover = contentScale != ContentScale.Fit
        val key = PictureKey(currentImagePath, screenWidth, screenHeight, cover)
        val loaded = rememberPicture(key)

        when (loaded) {
            null -> Unit // still decoding: the black behind shows, never the failure text
            is LoadedPicture.Decoded -> Image(
                bitmap = loaded.bitmap,
                contentDescription = stringResource(Res.string.presented_image),
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                colorFilter = null
            )
            LoadedPicture.Failed -> Text(
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

/** One decode the output asks for: the file, the size it is drawn at, and whether it covers. */
internal data class PictureKey(val path: String, val width: Int, val height: Int, val cover: Boolean)

/** What a decode came to: the picture, or that it could not be read. */
internal sealed interface LoadedPicture {
    data class Decoded(val bitmap: ImageBitmap) : LoadedPicture
    data object Failed : LoadedPicture
}

/**
 * The last few pictures the outputs decoded, so the output window, its preview tile and a
 * transition's two halves share one decode, and a picture that was on screen a moment ago comes back
 * at once rather than through the black of a fresh decode.
 */
internal object PictureCache {
    private const val SIZE = 4
    private const val LOAD_FACTOR = 0.75f

    private val entries = object : LinkedHashMap<PictureKey, LoadedPicture.Decoded>(SIZE, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PictureKey, LoadedPicture.Decoded>) =
            size > SIZE
    }

    @Synchronized fun get(key: PictureKey): LoadedPicture? = entries[key]

    /**
     * Decodes [key] -- call this off the UI thread -- and remembers a picture it read. A failure is
     * not remembered, so a file copied in after it was first asked for is read the next time.
     */
    fun load(key: PictureKey): LoadedPicture {
        val bitmap = loadAndDownscaleImage(key.path, key.width, key.height, key.cover)
            ?: return LoadedPicture.Failed
        return LoadedPicture.Decoded(bitmap).also { synchronized(this) { entries[key] = it } }
    }
}

/**
 * The picture for [key], decoded on `Dispatchers.IO`: an output composes on the event thread, where
 * reading and scaling a photo froze the projector for as long as it took. Null while it decodes; a
 * picture decoded before answers at once.
 */
@Composable
private fun rememberPicture(key: PictureKey): LoadedPicture? {
    var loaded by remember(key) { mutableStateOf(PictureCache.get(key)) }
    LaunchedEffect(key) {
        if (loaded == null) loaded = withContext(Dispatchers.IO) { PictureCache.load(key) }
    }
    return loaded
}

internal fun loadAndDownscaleImage(
    imagePath: String,
    maxWidth: Int = 1920,
    maxHeight: Int = 1080,
    cover: Boolean = false,
): ImageBitmap? {
    val file = File(imagePath)
    if (!file.exists()) return null

    return try {
        PictureDecoder.decodeScaled(file, maxWidth, maxHeight, cover).toComposeImageBitmap()
    } catch (e: IOException) {
        // The decoder wraps every way a picture fails to read in one.
        CrashReporter.reportException(e, "Decoding picture for presenter")
        null
    } catch (e: IllegalArgumentException) {
        // Skia refusing the pixels it was handed.
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
            Box(
                modifier = modifier.fillMaxSize()
                    .background(Color.White)
                    .alpha(pictureKeyAlpha(animationType, transitionAlpha))
            )
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
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { translationX = (1f - slideOffset) * screenWidthPx }
                ) {
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
                Box(
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { translationX = -(1f - slideOffset) * screenWidthPx }
                ) {
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
