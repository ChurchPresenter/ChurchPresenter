package org.churchpresenter.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

/**
 * What the app can render for a preview that this module cannot on its own.
 *
 * Video needs the app's player and a deck needs its rasterizer; both live in the app, and this
 * module has neither a media pipeline nor a reason to grow one. Absent, the preview says so
 * rather than drawing nothing.
 */
data class PreviewSources(
    /** Plays the video at [path], muted, filling the modifier's box -- the app's own looping player. */
    val video: (@Composable (path: String, modifier: Modifier) -> Unit)? = null,
    /** The first [max] slides of the deck at the path, rendered small. Empty when it cannot. */
    val slideThumbnails: suspend (filePath: String, max: Int) -> List<ImageBitmap> = { _, _ -> emptyList() },
    /** Draws the canvas scene with this id into the modifier's box -- the app's own scene renderer. */
    val scene: (@Composable (sceneId: String, modifier: Modifier) -> Unit)? = null,
)

