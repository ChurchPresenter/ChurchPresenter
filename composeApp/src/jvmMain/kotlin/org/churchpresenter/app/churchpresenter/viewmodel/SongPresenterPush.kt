package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.utils.songBackgroundDirectiveOf
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.app.churchpresenter.presenter.titleSlideLines
import org.churchpresenter.core.models.songs.withBackgroundsOf
import org.churchpresenter.settings.utils.Constants

/**
 * How a song reaches the presenter — the title-slide it builds and the section/line a live edit
 * lands on. These were inline in SongsTab; pulled out here so the formatting and the coerce/fallback
 * math are tested apart from the composable and the presenter callbacks around them.
 */

/** The heading line of a title slide, optionally prefixed with the song number. */
internal fun songTitleLine(song: SongItem, showSongNumber: Boolean = true): String =
    listOfNotNull(song.number.takeIf { showSongNumber }, song.title)
        .filter { it.isNotBlank() }
        .joinToString(" – ")

/** The credit line of a title slide: `"<author> / <composer>"`, dropping either part when it's blank. */
internal fun songCreditLine(song: SongItem): String =
    listOf(song.author, song.composer).filter { it.isNotBlank() }.joinToString(" / ")

/**
 * The title-slide [LyricSection] for [song] at [tuning], under [settings].
 *
 * Carries the song's number, title and credits as fields, which is what the presenter draws -- each
 * in its own element's profile, see `SongTitleSlideContent` -- and the same content as plain
 * `lines`, one per element the slide is set to show, for the readers that only ever see text: the
 * stage monitor and the companion app. Both come from [titleSlideLines], so they cannot disagree.
 * Its `songNumber` is the numeric part of the song number, or 0 when the number isn't numeric.
 */
internal fun titleSlideSection(
    song: SongItem,
    tuning: SongTuning,
    settings: SongSettings = SongSettings(),
): LyricSection {
    val section = LyricSection(
        type = Constants.SECTION_TYPE_TITLE_SLIDE,
        title = song.title,
        secondaryTitle = song.secondaryTitle,
        songNumber = song.number.toIntOrNull() ?: 0,
        author = song.author,
        composer = song.composer,
        ccli = song.ccliNumber,
        bpm = tuning.bpm,
        capo = tuning.capo,
    ).withBackgroundsOf(song)
    return section.copy(lines = titleSlideLines(section, settings).map { it.plainText })
}

/** Where a live-edited song lands: the section/line to select and the section to send. */
internal data class EditedSongPush(val sectionIndex: Int, val lineIndex: Int, val section: LyricSection)

/**
 * Resolves where a live edit of [editedSong] should land. The previously-live [liveSectionIndex] is
 * clamped into `[-1, sections.lastIndex]`; when it points at no section (empty list, or the -1
 * "whole song" slot) a fallback section is built straight from the edited song. The line index is
 * then clamped into that section's line range, and [tuning] is stamped onto the section that goes out.
 */
internal fun resolveEditedSongPush(
    sections: List<LyricSection>,
    liveSectionIndex: Int,
    liveLineIndex: Int,
    editedSong: SongItem,
    tuning: SongTuning,
): EditedSongPush {
    val sectionIndex = liveSectionIndex.coerceIn(-1, sections.size - 1)
    val section = sections.getOrNull(sectionIndex) ?: LyricSection(
        title = editedSong.title,
        secondaryTitle = editedSong.secondaryTitle,
        songNumber = editedSong.number.toIntOrNull() ?: 0,
        lines = editedSong.lyrics.filterNot { songBackgroundDirectiveOf(it) != null },
        secondaryLines = editedSong.secondaryLyrics.filterNot { songBackgroundDirectiveOf(it) != null },
        type = Constants.SECTION_TYPE_SONG,
    )
    val lineIndex = liveLineIndex.coerceIn(0, (section.lines.size - 1).coerceAtLeast(0))
    return EditedSongPush(
        sectionIndex,
        lineIndex,
        section.copy(bpm = tuning.bpm, capo = tuning.capo).withBackgroundsOf(editedSong),
    )
}
