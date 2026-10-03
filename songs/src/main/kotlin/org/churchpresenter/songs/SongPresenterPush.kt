package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.core.models.songs.withBackgroundsOf
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.songBackgroundDirectiveOf

/**
 * How a song reaches the presenter — the title and credit lines the panel shows and the section/line
 * a live edit lands on. Pulled out of SongsTab so the formatting and the coerce/fallback math are
 * tested apart from the composable and the presenter callbacks around them. The title slide itself
 * is built by the app, which knows how the presenter lays it out.
 */

/** The heading line of a title slide, optionally prefixed with the song number. */
internal fun songTitleLine(song: SongItem, showSongNumber: Boolean = true): String =
    listOfNotNull(song.number.takeIf { showSongNumber }, song.title)
        .filter { it.isNotBlank() }
        .joinToString(" – ")

/** The credit line of a title slide: `"<author> / <composer>"`, dropping either part when it's blank. */
internal fun songCreditLine(song: SongItem): String =
    listOf(song.author, song.composer).filter { it.isNotBlank() }.joinToString(" / ")

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
        translations = editedSong.presentableTranslations(),
        songNumber = editedSong.number.toIntOrNull() ?: 0,
        lines = editedSong.lyrics.filterNot { songBackgroundDirectiveOf(it) != null },
        type = Constants.SECTION_TYPE_SONG,
    )
    val lineIndex = liveLineIndex.coerceIn(0, (section.lines.size - 1).coerceAtLeast(0))
    return EditedSongPush(
        sectionIndex,
        lineIndex,
        section.copy(bpm = tuning.bpm, capo = tuning.capo).withBackgroundsOf(editedSong),
    )
}

/**
 * Every language beside the primary as slide translations, background directives stripped.
 *
 * For the slides built straight from a song rather than by splitting it into sections — the
 * whole-song slide and the live-edit fallback. A directive is configuration written among the
 * lyrics, and putting one on screen is never right.
 *
 * Here rather than beside [LyricSection] in `:core-models` because [songBackgroundDirectiveOf] is
 * a `:shared-ui` rule, and that module may not depend on this one.
 */
internal fun SongItem.presentableTranslations(): List<SectionTranslation> =
    extraTranslations().map { translation ->
        SectionTranslation(
            title = translation.title,
            lines = translation.lyrics.filterNot { songBackgroundDirectiveOf(it) != null },
        )
    }
