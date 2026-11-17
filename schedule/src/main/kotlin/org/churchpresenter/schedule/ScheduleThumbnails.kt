package org.churchpresenter.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.core.models.schedule.ScheduleItem

/**
 * What a row's thumbnail is drawn from (#1050), which the app supplies.
 *
 * A picture needs the picture decoder, a deck its rasterizer and a video ffmpeg; all of them live
 * below the app, and this module has no reason to take any of them just to draw a tile. Null for
 * a row with nothing to show -- a song, a verse, a website -- which keeps its type chip.
 */
fun interface ScheduleThumbnails {
    /** [item]'s thumbnail, or null. Called off the event thread; may block for as long as it needs. */
    suspend fun thumbnail(item: ScheduleItem): ImageBitmap?
}

/** The app's [ScheduleThumbnails]; by default none, so every row keeps its chip. */
val LocalScheduleThumbnails: ProvidableCompositionLocal<ScheduleThumbnails> =
    staticCompositionLocalOf { ScheduleThumbnails { null } }

/** [item]'s thumbnail when [enabled] (never for a label): null until it is loaded, and for a row without one. */
@Composable
internal fun rememberScheduleThumbnail(item: ScheduleItem, enabled: Boolean): State<ImageBitmap?> {
    val thumbnails = LocalScheduleThumbnails.current
    return produceState<ImageBitmap?>(initialValue = null, item, enabled, thumbnails) {
        value = if (enabled) withContext(Dispatchers.IO) { thumbnails.thumbnail(item) } else null
    }
}
