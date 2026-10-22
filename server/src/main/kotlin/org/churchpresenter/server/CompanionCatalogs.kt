package org.churchpresenter.server

import org.churchpresenter.bible.Bible
import org.churchpresenter.core.models.songs.SongItem
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
internal fun buildSongDetail(song: SongItem, keepMarkup: Boolean = false): SongDetailDto {
    val sections = mutableListOf<SongSectionDto>()
    var currentType = Constants.SECTION_TYPE_VERSE
    var currentLabel: String? = null
    var currentLines = mutableListOf<String>()
    for (line in song.lyrics) {
        val trimmed = line.trim()
        val isSectionHeader = isHeaderLine(trimmed)
        val isChorus = isChorusHeader(trimmed)
        if (isSectionHeader) {
            if (currentLines.isNotEmpty()) {
                sections.add(SongSectionDto(type = currentType, lines = currentLines.toList(), label = currentLabel))
                currentLines = mutableListOf()
            }
            currentType = if (isChorus) Constants.SECTION_TYPE_CHORUS else Constants.SECTION_TYPE_VERSE
            currentLabel = sectionLabelOf(trimmed).ifEmpty { null }
        } else if (keepMarkup) {
            if (trimmed.isNotEmpty()) currentLines.add(line)
        } else if (!isLyricDirective(trimmed)) {
            plainLyricLine(line).takeIf { it.isNotBlank() }?.let(currentLines::add)
        }
    }
    if (currentLines.isNotEmpty()) {
        sections.add(SongSectionDto(type = currentType, lines = currentLines.toList(), label = currentLabel))
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
                    SongDto(
                        id = indexMap[s] ?: 0,
                        number = s.number,
                        title = s.title,
                        tune = s.tune,
                        author = s.author,
                        secondaryTitle = s.secondaryTitle,
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
