package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.app.churchpresenter.server.SongDetailDto
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.utils.Constants

/** The primary's catalog as the follower's song list: metadata only, lyrics fetched per song. */
internal fun SongCatalogResponse.toSongItems(): List<SongItem> = songBook.flatMap { entry ->
    entry.songs.map { dto ->
        SongItem(
            number = dto.number,
            title = dto.title,
            songbook = entry.bookName,
            tune = dto.tune,
            author = dto.author
        )
    }
}

/**
 * Reconstructs the raw header+line format the local parser produces, from structured sections, so
 * `SongsViewModel` splits remotely-fetched songs unchanged. Original header text (e.g. "Verse 2")
 * isn't preserved by the API, only the section type.
 */
internal fun SongDetailDto.toRawLyrics(): List<String> = sections.flatMap { section ->
    val header = if (section.type == Constants.SECTION_TYPE_CHORUS) {
        "{Chorus}"
    } else {
        "[${section.type.replaceFirstChar(Char::uppercase)}]"
    }
    listOf(header) + section.lines
}
