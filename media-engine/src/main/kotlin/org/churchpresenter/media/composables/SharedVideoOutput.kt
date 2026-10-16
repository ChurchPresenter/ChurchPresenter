package org.churchpresenter.media.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.awt.image.BufferedImage
import androidx.compose.foundation.Image

/**
 * Singleton frame buffer written by the single master SoftwareVideoPlayer and read by
 * every SharedVideoOutputDisplay. This eliminates the need for multiple VLC decoder
 * instances when presenting on more than one screen.
 */
object SharedVideoOutput {
    val frame = mutableStateOf<ImageBitmap?>(null)
}

/**
 * A 1×1 transparent stand-in, drawn by [SharedVideoOutputDisplay] before any real frame exists.
 *
 * Every output window that can show media -- the presenter window, an NDI/Browser Source output --
 * composes this the moment it opens, long before a clip is ever loaded. Skipping the `Image` call
 * entirely until the first real frame (as this used to) meant that window's graphics surface had
 * never actually drawn a bitmap through Skia until the moment Go Live handed it its first one --
 * texture upload and shader compilation for that surface were still cold, paid for exactly when a
 * decoder was also actively converting and delivering frames, and the two together were the
 * stutter on the very first clip of a session. Drawing this placeholder as soon as the window
 * exists moves that one-time cost there instead, where nothing else is competing for it.
 */
private val emptyFramePlaceholder: ImageBitmap by lazy {
    BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).toComposeImageBitmap()
}

/**
 * Lightweight Compose composable that displays the latest frame from [SharedVideoOutput].
 * Uses no VLC instance — just renders the ImageBitmap written by the master SoftwareVideoPlayer.
 *
 * [contentScale] is the output's scale mode -- `AppSettings.mediaScaleMode`, through `contentScale`.
 */
@Composable
fun SharedVideoOutputDisplay(modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    Image(
        bitmap = SharedVideoOutput.frame.value ?: emptyFramePlaceholder,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier
    )
}
