/*
 * What the Background panel calls the thing it is showing — the name over the preview, the meta
 * line under it, the badge in the preview's corner, and the fill every one of those previews draws.
 */
package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import org.churchpresenter.app.churchpresenter.presenter.backgroundBlurRadius
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
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
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.song_background_camera_live
import churchpresenter.composeapp.generated.resources.song_background_category_cameras
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
import androidx.compose.ui.draw.alpha
import org.churchpresenter.core.models.songs.SONG_BACKGROUND_FULL_OPACITY
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.jetbrains.compose.resources.stringResource
import java.io.File

/** The name over the preview: the file, the swatch, or the fact that nothing is overridden. */
@Composable
internal fun songBackgroundName(background: SongBackground): String = when {
    !background.isCustom -> stringResource(Res.string.song_background_inherited)
    // A camera has no mediaPath — that is a file, and File(...).name on "avfoundation://0" is "0".
    background.type == SongBackgroundType.CAMERA ->
        background.camera.deviceName.ifBlank { background.camera.devicePath }
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
    background.type == SongBackgroundType.CAMERA -> stringResource(Res.string.song_background_camera_live)
    else -> songBackgroundName(background)
}

private fun songBackgroundCategoryLabel(type: String) = when (type) {
    SongBackgroundType.IMAGE -> Res.string.song_background_category_images
    SongBackgroundType.VIDEO -> Res.string.song_background_category_videos
    SongBackgroundType.CAMERA -> Res.string.song_background_category_cameras
    else -> Res.string.song_background_category_colors
}

/**
 * Draws whatever [background] points at, blurred and overscanned the way the presenter does it, so
 * the preview and the screen agree. A clip and a camera show as black — spinning up VLC, or opening
 * a capture device, for a thumbnail inside the editor is not worth what it costs.
 */
@Composable
internal fun SongBackgroundFill(background: SongBackground, modifier: Modifier) {
    // Measured, because a blur is stored against a 1920-wide output and this draws a tile a
    // fraction of that. Passing the stored radius straight to Modifier.blur made a tile roughly
    // twelve times softer than the screen it stands for.
    BoxWithConstraints(modifier) {
        val fill = Modifier.fillMaxSize()
        val blurred = if (background.blur > 0) {
            fill.graphicsLayer { scaleX = BLUR_OVERSCAN; scaleY = BLUR_OVERSCAN }
                .blur(backgroundBlurRadius(background.blur, maxWidth))
        } else {
            fill
        }
        // The presenter fades the background itself and then washes black over it; a preview that
        // skipped either would show a look the screen never produces.
        val shaped =
            if (background.opacity < SONG_BACKGROUND_FULL_OPACITY) blurred.alpha(background.opacity / PERCENT)
            else blurred
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
            // A camera is black here for the reason a clip is, and more so: this tile is drawn by
            // every quick-tray button and every settings strip at once, so a live preview would
            // hold the device open for as long as any of them is on screen.
            background.type == SongBackgroundType.VIDEO ||
                background.type == SongBackgroundType.CAMERA -> Box(shaped.background(Color.Black))
            else -> Box(shaped.background(parseHexColor(background.color)))
        }
        // The wash the comment above promises, which was never actually drawn.
        if (background.dim > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = background.dim / PERCENT)))
        }
    }
}

/** A percentage as a fraction. */
private const val PERCENT = 100f

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
