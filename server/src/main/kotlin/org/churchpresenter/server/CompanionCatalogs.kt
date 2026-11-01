package org.churchpresenter.server

import org.churchpresenter.bible.Bible
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.sectionKeysOf
import org.churchpresenter.songs.songPlayOrder
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.isChorusHeader
import org.churchpresenter.sharedui.utils.isHeaderLine
import org.churchpresenter.sharedui.utils.isLyricDirective
import org.churchpresenter.sharedui.utils.plainLyricLine
import org.churchpresenter.sharedui.utils.sectionLabelOf

/**
 * Builders that turn the desktop's own song/Bible/presentation models into the wire catalogues the
 * companion API serves. Pure functions of their arguments — they were members of `CompanionServer`
 * only because that is where the callers happened to live.
 */
/**
 * One song's sections. A phone or a browser gets the words as they are sung — the second voice's
 * markup and the directives taken out; an Instance Link follower asks for [keepMarkup], since it
 * rebuilds the song from these lines and draws the second voice itself.
 */
internal fun buildSongDetail(
    song: SongItem,
    keepMarkup: Boolean = false,
    repeatChorus: Boolean = true,
): SongDetailDto {
    val raw = mutableListOf<RawSection>()
    var current = RawSection(header = null, type = Constants.SECTION_TYPE_VERSE)
    for (line in song.lyrics) {
        val trimmed = line.trim()
        if (isHeaderLine(trimmed)) {
            if (current.header != null || current.lines.isNotEmpty()) raw.add(current)
            val type = if (isChorusHeader(trimmed)) Constants.SECTION_TYPE_CHORUS else Constants.SECTION_TYPE_VERSE
            current = RawSection(header = trimmed, type = type)
        } else if (keepMarkup) {
            if (trimmed.isNotEmpty()) current.lines.add(line)
        } else if (!isLyricDirective(trimmed)) {
            plainLyricLine(line).takeIf { it.isNotBlank() }?.let(current.lines::add)
        }
    }
    if (current.header != null || current.lines.isNotEmpty()) raw.add(current)
    // Keyed over every written section, an empty one included, the way the song's order is.
    val keys = sectionKeysOf(raw.map { it.header })
    val sections = raw.mapIndexedNotNull { index, section ->
        if (section.lines.isEmpty()) return@mapIndexedNotNull null
        SongSectionDto(
            type = section.type,
            lines = section.lines.toList(),
            label = section.header?.let(::sectionLabelOf)?.ifEmpty { null },
            key = keys[index],
        )
    }
    return SongDetailDto(
        number       = song.number,
        title        = song.title,
        songbook     = song.songbook,
        tune         = song.tune,
        author       = song.author,
        composer     = song.composer,
        sectionTotal = sections.size,
        sections     = sections,
        order        = song.sectionOrder,
        notes        = song.notes,
        cues         = song.sectionNotes,
        playOrder    = songPlayOrder(song, repeatChorus),
    )
}

/** A section of a song's lyrics as [buildSongDetail] reads it: its header as written, and its lines. */
private class RawSection(val header: String?, val type: String) {
    val lines = mutableListOf<String>()
}

internal fun buildCatalog(songs: List<SongItem>): SongCatalogResponse {
    // Build an index map so each SongDto gets a unique id (position in _songs)
    val indexMap = songs.withIndex().associate { (i, s) -> s to i }
    val entries = songs
        .groupBy { it.songbook }
        .entries
        .sortedBy { it.key }
        .map { (bookName, bookSongs) ->
            SongbookEntry(
                bookName = bookName,
                songTotal = bookSongs.size,
                songs = bookSongs.map { s ->
                    SongDto(
                        id = indexMap[s] ?: 0,
                        number = s.number,
                        title = s.title,
                        tune = s.tune,
                        author = s.author,
                        secondaryTitle = s.secondaryTitle,
                        order = s.sectionOrder,
                    )
                }
            )
        }
    return SongCatalogResponse(songBook = entries, songBooks = entries.size, total = songs.size)
}

internal fun buildBibleCatalog(bible: Bible, translation: String): BibleCatalogResponse {
    val bookNames = bible.getBooks()
    val bookDtos = mutableListOf<BibleBookDto>()
    var totalVerses = 0
    bookNames.forEachIndexed { bookIndex, bookName ->
        val bookId = bible.getBookId(bookIndex)
        val chapterCount = bible.getChapterCount(bookIndex)
        val chapterDtos = (1..chapterCount).map { chapterNum ->
            val verseCount = bible.getVerseCountForChapter(bookId, chapterNum)
            totalVerses += verseCount
            BibleChapterDto(chapter = chapterNum, verseTotal = verseCount)
        }
        bookDtos.add(BibleBookDto(bookId = bookId, bookName = bookName,
            chapterTotal = chapterCount, chapters = chapterDtos))
    }
    return BibleCatalogResponse(translation = translation, books = bookDtos,
        bookTotal = bookDtos.size, verseTotal = totalVerses)
}

internal fun buildPresentationCatalog(id: String, fileName: String, fileType: String,
                                     slideCount: Int): PresentationCatalogResponse {
    val slides = (0 until slideCount).map { index ->
        SlideDto(slideIndex = index,
            thumbnailUrl = "${Constants.ENDPOINT_PRESENTATIONS}/$id/slides/$index")
    }
    val dto = PresentationDto(id = id, fileName = fileName, fileType = fileType,
        slideTotal = slideCount, slides = slides)
    return PresentationCatalogResponse(presentations = listOf(dto), total = 1)
}
