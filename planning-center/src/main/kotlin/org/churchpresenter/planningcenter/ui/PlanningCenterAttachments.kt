package org.churchpresenter.planningcenter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as SkiaImage

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

private val VIDEO_EXTENSIONS = setOf("mp4", "avi", "mov", "mkv", "webm")

private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac")

private val PRESENTATION_EXTENSIONS = setOf("ppt", "pptx", "key", "pdf")

/** Mirrors [PlanningCenterImportViewModel.importAttachment]'s extension classification. */
internal fun isSupportedAttachment(filename: String): Boolean {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS || ext in VIDEO_EXTENSIONS || ext in AUDIO_EXTENSIONS ||
        ext in PRESENTATION_EXTENSIONS
}

/** Material icon for the file kind an attachment will become. */
internal fun attachmentExtensionIcon(ext: String): ImageVector = when (ext) {
    in PRESENTATION_EXTENSIONS -> Icons.Filled.Slideshow
    in IMAGE_EXTENSIONS -> Icons.Filled.Image
    in VIDEO_EXTENSIONS, in AUDIO_EXTENSIONS -> Icons.Filled.Movie
    else -> Icons.Filled.AttachFile
}

/** Small thumbnail preview for an image attachment in the import picker, fetched on demand. */
@Composable
internal fun AttachmentThumbnail(thumbnailUrl: String, viewModel: PlanningCenterImportViewModel) {
    var bitmap by remember(thumbnailUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(thumbnailUrl) {
        val bytes = viewModel.fetchThumbnailBytes(thumbnailUrl)
        bitmap = bytes?.let {
            try {
                SkiaImage.makeFromEncoded(it).toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
    }
}

/** Whether a file of extension [ext] is an image, so its thumbnail can be shown. */
internal fun isImageExtension(ext: String): Boolean = ext in IMAGE_EXTENSIONS
