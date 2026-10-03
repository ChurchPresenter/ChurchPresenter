package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants

/**
 * A title slide standing in for the app's, which lays its lines out for the presenter: the song's
 * number, title and credits as fields, with the tuning stamped on.
 */
fun fakeTitleSlide(song: SongItem, tuning: SongTuning, settings: SongSettings): LyricSection = LyricSection(
    type = Constants.SECTION_TYPE_TITLE_SLIDE,
    title = song.title,
    songNumber = song.number.toIntOrNull() ?: 0,
    author = song.author,
    composer = song.composer,
    bpm = tuning.bpm,
    capo = tuning.capo,
    lines = listOfNotNull(song.number.takeIf { settings.titleSlideShowSongNumber }, song.title),
)

/** [songs] on every output profile as well as the install-wide settings. */
fun AppSettings.withSongsEverywhere(songs: SongSettings): AppSettings = copy(
    songSettings = songs,
    projectionSettings = projectionSettings.copy(
        outputProfiles = projectionSettings.outputProfiles.map { it.copy(songSettings = songs) },
    ),
)
