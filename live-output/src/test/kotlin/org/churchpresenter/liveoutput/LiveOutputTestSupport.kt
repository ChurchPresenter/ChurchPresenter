package org.churchpresenter.liveoutput

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongSettings

/**
 * [this] with [songs] as every profile's song settings, and the document's for its install-wide
 * keys -- so the main window, which follows a profile (`operatorSongSettings`), goes by them.
 */
internal fun AppSettings.withSongsEverywhere(songs: SongSettings): AppSettings = copy(
    songSettings = songs,
    projectionSettings = projectionSettings.copy(
        outputProfiles = projectionSettings.outputProfiles.map { it.copy(songSettings = songs) },
    ),
)

/** The Bible's [withSongsEverywhere]. */
internal fun AppSettings.withBibleEverywhere(bible: BibleSettings): AppSettings = copy(
    bibleSettings = bible,
    projectionSettings = projectionSettings.copy(
        outputProfiles = projectionSettings.outputProfiles.map { it.copy(bibleSettings = bible) },
    ),
)

/** Resolves skiko's host OS against the real `os.name` before any test can fake it. */
internal object TestSingletons {

    @Volatile private var skikoLatched = false

    fun latchSkikoHostOs() {
        if (skikoLatched) return
        synchronized(this) {
            if (skikoLatched) return
            Class.forName("org.jetbrains.skia.Surface")
            skikoLatched = true
        }
    }
}
