package org.churchpresenter.liveoutput.settings

import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.sharedui.filechooser.FileChooser
import java.nio.file.Path

/**
 * How the Projection pages ask for a file or a folder: the platform's own chooser, or -- under
 * test, through [LocalPathChooser] -- an answer given in advance, since a native dialog would block
 * the run. Null when nothing was chosen.
 */
fun interface PathChooser {
    suspend fun choose(start: Path?, title: String, directory: Boolean): Path?
}

/** The platform chooser, with no filters: VLC's folder, ffmpeg's binary, the NDI and OMT folders. */
internal val PlatformPathChooser = PathChooser { start, title, directory ->
    FileChooser.platformInstance.chooseSingle(
        path = start,
        title = title,
        selectDirectory = directory,
        filters = emptyList(),
    )
}

/** The chooser the Projection pages use. */
val LocalPathChooser = staticCompositionLocalOf { PlatformPathChooser }
