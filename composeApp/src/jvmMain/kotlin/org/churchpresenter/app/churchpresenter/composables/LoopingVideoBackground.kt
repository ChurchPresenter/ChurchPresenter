package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.churchpresenter.diagnostics.CrashReporter
import java.io.File

/** A background is silent; the file may still carry an audio track. */
private const val SILENT = 0f

/**
 * A looping video background with no audio, filling the available space.
 *
 * Drawn as a Compose `Image` rather than into a native surface so it respects normal z-order —
 * lyrics and verses draw on top of it.
 *
 * The decode itself is [SharedSceneVideoCache]'s, not this composable's. One background is composed
 * once per presenter output *and* once per sidebar live preview, and every instance used to build
 * its own `MediaPlayerFactory` and its own player: the same file decoded at full source resolution
 * once for each place it appeared, and converted to an `ImageBitmap` per instance at 60fps. Two
 * outputs meant four decodes of one loop. Per-song backgrounds multiply the instance count again.
 */
@Composable
fun LoopingVideoBackground(
    videoPath: String,
    modifier: Modifier = Modifier
) {
    val file = remember(videoPath) { File(videoPath) }
    val playable = videoPath.isNotBlank() && file.exists() &&
        isVlcAvailable && !CrashReporter.videoBackgroundsDisabled

    val spec = remember(videoPath) { SceneVideoSpec(videoPath, loop = true) }
    var frames by remember { mutableStateOf<StateFlow<ImageBitmap?>?>(null) }
    DisposableEffect(spec, playable) {
        if (playable) frames = SharedSceneVideoCache.acquire(spec, SILENT)
        onDispose {
            if (playable) {
                frames = null
                SharedSceneVideoCache.release(spec)
            }
        }
    }

    val noFrame = remember { MutableStateFlow<ImageBitmap?>(null) }
    val frame by (frames ?: noFrame).collectAsState()

    frame?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
