package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.songs.SectionTranslation
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongTuning
import org.churchpresenter.core.models.songs.withBackgroundsOf
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants


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
        translations = song.titleTranslations(),
        songNumber = song.number.toIntOrNull() ?: 0,
        author = song.author,
        composer = song.composer,
        ccli = song.ccliNumber,
        bpm = tuning.bpm,
        capo = tuning.capo,
    ).withBackgroundsOf(song)
    return section.copy(lines = titleSlideLines(section, settings).map { it.plainText })
}

/** Each language's title with no lines, for a slide that carries only titles — the title slide. */
internal fun SongItem.titleTranslations(): List<SectionTranslation> =
    extraTranslations().map { SectionTranslation(title = it.title) }
