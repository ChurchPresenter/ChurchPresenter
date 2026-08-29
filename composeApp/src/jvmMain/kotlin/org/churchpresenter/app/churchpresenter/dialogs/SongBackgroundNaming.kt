/*
 * What the Background panel calls the thing it is showing — the name over the preview, the meta
 * line under it, the badge in the preview's corner, and the fill every one of those previews draws.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_background_category_colors
import churchpresenter.composeapp.generated.resources.song_background_category_images
import churchpresenter.composeapp.generated.resources.song_background_category_videos
import churchpresenter.composeapp.generated.resources.song_background_custom_color
import churchpresenter.composeapp.generated.resources.song_background_follows_settings
import churchpresenter.composeapp.generated.resources.song_background_inherited
import churchpresenter.composeapp.generated.resources.song_background_meta
import churchpresenter.composeapp.generated.resources.song_background_video_loop
import churchpresenter.composeapp.generated.resources.unit_px
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** The name over the preview: the file, the swatch, or the fact that nothing is overridden. */
@Composable
internal fun songBackgroundName(background: SongBackground): String = when {
    !background.isCustom -> stringResource(Res.string.song_background_inherited)
    background.mediaPath.isNotBlank() -> File(background.mediaPath).name
    else -> SONG_BACKGROUND_COLORS
        .firstOrNull { !it.own && it.selects(background, SONG_BACKGROUND_NAMED_COLORS) }
        ?.let { stringResource(it.label) }
        ?: stringResource(Res.string.song_background_custom_color)
}

/** The line under it: which category, and what dim and blur are set to. */
@Composable
internal fun songBackgroundMeta(background: SongBackground): String {
    if (!background.isCustom) return stringResource(Res.string.song_background_follows_settings)
    return stringResource(
        Res.string.song_background_meta,
        stringResource(songBackgroundCategoryLabel(background.type)),
        "${background.dim}%",
        "${background.blur}${stringResource(Res.string.unit_px)}",
    )
}

/** The preview's corner badge: "video loop" for a clip, the background's own name otherwise. */
@Composable
internal fun songBackgroundBadge(background: SongBackground): String = when {
    !background.isCustom -> stringResource(Res.string.song_background_inherited)
    background.type == SongBackgroundType.VIDEO -> stringResource(Res.string.song_background_video_loop)
    else -> songBackgroundName(background)
}

private fun songBackgroundCategoryLabel(type: String) = when (type) {
    SongBackgroundType.IMAGE -> Res.string.song_background_category_images
    SongBackgroundType.VIDEO -> Res.string.song_background_category_videos
    else -> Res.string.song_background_category_colors
}

/**
 * Draws whatever [background] points at, blurred and overscanned the way the presenter does it, so
 * the preview and the screen agree. A clip shows as black — spinning up VLC for a thumbnail inside
 * the editor is not worth what it costs.
 */
@Composable
internal fun SongBackgroundFill(background: SongBackground, modifier: Modifier) {
    val shaped = if (background.blur > 0) {
        modifier.graphicsLayer { scaleX = BLUR_OVERSCAN; scaleY = BLUR_OVERSCAN }.blur(background.blur.dp)
    } else {
        modifier
    }
    when {
        !background.isCustom -> Box(shaped.background(Color.Black))
        background.type == SongBackgroundType.GRADIENT -> Box(
            shaped.background(
                Brush.verticalGradient(
                    listOf(parseHexColor(background.color), parseHexColor(background.colorEnd))
                )
            )
        )
        background.type == SongBackgroundType.IMAGE -> ImageFill(background.image, shaped)
        background.type == SongBackgroundType.VIDEO -> Box(shaped.background(Color.Black))
        else -> Box(shaped.background(parseHexColor(background.color)))
    }
}

@Composable
private fun ImageFill(path: String, modifier: Modifier) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { loadThumbnailBitmap(DownloadedEntry(File(path))) }
    }
    val shot = bitmap
    if (shot == null) {
        Box(modifier.background(Color.Black))
    } else {
        Image(bitmap = shot, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
}

/** The presenter overscans a blurred background by the same amount; the preview must match. */
private const val BLUR_OVERSCAN = 1.08f
