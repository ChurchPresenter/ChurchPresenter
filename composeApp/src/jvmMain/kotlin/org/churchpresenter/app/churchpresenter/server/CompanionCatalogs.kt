package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.data.Bible
import org.churchpresenter.app.churchpresenter.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.isChorusHeader
import org.churchpresenter.app.churchpresenter.utils.isHeaderLine

/**
 * Builders that turn the desktop's own song/Bible/presentation models into the wire catalogues the
 * companion API serves. Pure functions of their arguments — they were members of `CompanionServer`
 * only because that is where the callers happened to live.
 */
internal fun buildSongDetail(song: SongItem): SongDetailDto {
    val sections = mutableListOf<SongSectionDto>()
    var currentType = Constants.SECTION_TYPE_VERSE
    var currentLines = mutableListOf<String>()
    for (line in song.lyrics) {
        val trimmed = line.trim()
        val isSectionHeader = isHeaderLine(trimmed)
        val isChorus = isChorusHeader(trimmed)
        if (isSectionHeader) {
            if (currentLines.isNotEmpty()) {
                sections.add(SongSectionDto(type = currentType, lines = currentLines.toList()))
                currentLines = mutableListOf()
            }
            currentType = if (isChorus) Constants.SECTION_TYPE_CHORUS else Constants.SECTION_TYPE_VERSE
        } else if (trimmed.isNotEmpty()) {
            currentLines.add(line)
        }
    }
    if (currentLines.isNotEmpty()) {
        sections.add(SongSectionDto(type = currentType, lines = currentLines.toList()))
    }
    return SongDetailDto(
        number       = song.number,
        title        = song.title,
        songbook     = song.songbook,
        tune         = song.tune,
        author       = song.author,
        composer     = song.composer,
        sectionTotal = sections.size,
        sections     = sections
    )
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
                    SongDto(id = indexMap[s] ?: 0, number = s.number, title = s.title, tune = s.tune, author = s.author)
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
