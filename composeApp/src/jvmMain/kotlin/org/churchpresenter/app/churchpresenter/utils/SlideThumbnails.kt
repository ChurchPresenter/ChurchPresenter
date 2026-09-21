package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.presentationengine.DeckRasterizer
import org.churchpresenter.presentationengine.LoadResult
import org.churchpresenter.presentationengine.PresentationLoader
import java.io.File

/**
 * The first [max] slides of the deck at [filePath], rendered at thumbnail width — what the
 * Calendar Manager's preset preview shows. Empty when the file cannot be opened; a preview is not
 * worth an error.
 */
suspend fun slideThumbnails(filePath: String, max: Int): List<ImageBitmap> = withContext(Dispatchers.IO) {
    val deck = (PresentationLoader.load(File(filePath)) as? LoadResult.Success)?.deck ?: return@withContext emptyList()
    DeckRasterizer(deck, THUMBNAIL_WIDTH_PX).use { rasterizer ->
        (0 until minOf(max, deck.slideCount)).mapNotNull { index ->
            runCatching { rasterizer.renderFinalFrame(index).toComposeImageBitmap() }.getOrNull()
        }
    }
}

private const val THUMBNAIL_WIDTH_PX = 320
