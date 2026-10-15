package org.churchpresenter.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.slides.utils.PictureDecoder
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.canvas_image_not_found
import org.jetbrains.compose.resources.stringResource
import java.io.File

@Composable
internal fun ImageSourceContent(
    source: SceneSource.ImageSource,
    modifier: Modifier,
    // Reads the picture; a test passes one it can hold back.
    load: suspend (path: String) -> ScenePicture = { path -> withContext(Dispatchers.IO) { loadScenePicture(path) } },
) {
    // Read off the event thread: a large photo takes long enough to decode that every output showing
    // the scene would stop on its first draw. Nothing is drawn until it is ready.
    val picture by produceState<ScenePicture>(ScenePicture.Loading, source.filePath) {
        value = load(source.filePath)
    }

    when (val loaded = picture) {
        ScenePicture.Loading -> Box(modifier.fillMaxSize())
        is ScenePicture.Ready -> {
            val scale = when (source.contentScale) {
                "FILL" -> ContentScale.Crop
                "STRETCH" -> ContentScale.FillBounds
                "NONE" -> ContentScale.None
                else -> ContentScale.Fit
            }
            Image(
                painter = BitmapPainter(loaded.bitmap),
                contentDescription = source.name,
                contentScale = scale,
                modifier = modifier.fillMaxSize()
            )
        }
        ScenePicture.Missing -> Box(
            modifier = modifier.fillMaxSize().background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(Res.string.canvas_image_not_found), color = Color.White, fontSize = 12.sp)
        }
    }
}

/** Where a scene picture's read has got to. */
internal sealed interface ScenePicture {
    data object Loading : ScenePicture
    data class Ready(val bitmap: ImageBitmap) : ScenePicture

    /** No file there, or none any decoder can read. */
    data object Missing : ScenePicture
}

/**
 * Reads the picture at [path]. PictureDecoder, not Skia directly: a scene image is a file the
 * operator chose, and the formats Skia refuses are ordinary camera and print output.
 */
internal fun loadScenePicture(path: String): ScenePicture {
    val file = File(path)
    val bitmap = if (file.exists()) PictureDecoder.decodeOrNull(file)?.toComposeImageBitmap() else null
    return if (bitmap != null) ScenePicture.Ready(bitmap) else ScenePicture.Missing
}
