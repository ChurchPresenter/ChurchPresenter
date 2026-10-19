package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.server.SongCatalogResponse
import org.churchpresenter.server.SongDetailDto
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
 * `SongsViewModel` splits remotely-fetched songs unchanged. The header is the song's own ("Verse 2")
 * where the primary sends its label; a primary from before labels sends only the type, so the header
 * is made from that.
 */
internal fun SongDetailDto.toRawLyrics(): List<String> = sections.flatMap { section ->
    listOf(rawHeader(section.type, section.label)) + section.lines
}

/** A section header as the song file writes it: `{…}` for a chorus, `[…]` for anything else. */
internal fun rawHeader(type: String, label: String?): String {
    val isChorus = type == Constants.SECTION_TYPE_CHORUS
    val name = label ?: if (isChorus) "Chorus" else type.replaceFirstChar(Char::uppercase)
    return if (isChorus) "{$name}" else "[$name]"
}
